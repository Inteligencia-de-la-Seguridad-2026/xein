import json
import os
import re
import sqlite3
import uuid
import hmac
from contextlib import contextmanager
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


DATABASE_PATH = Path(os.environ.get("PAYMENT_DB_PATH", "/data/transactions.sqlite"))
PORT = int(os.environ.get("PORT", "9090"))
IDENTIFIER_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
CARD_NUMBER_PATTERN = re.compile(r"^[0-9]{16}$")
EXPIRY_PATTERN = re.compile(r"^(0[1-9]|1[0-2])/[0-9]{2}$")
CVV_PATTERN = re.compile(r"^[0-9]{3,4}$")


@contextmanager
def connect_database():
    connection = sqlite3.connect(DATABASE_PATH, timeout=10)
    connection.row_factory = sqlite3.Row
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def initialize_database():
    DATABASE_PATH.parent.mkdir(parents=True, exist_ok=True)
    with connect_database() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS transactions (
                id TEXT PRIMARY KEY,
                reference TEXT UNIQUE NOT NULL,
                payer TEXT NOT NULL,
                payee TEXT NOT NULL,
                amount_cents INTEGER NOT NULL,
                currency TEXT NOT NULL,
                kind TEXT NOT NULL DEFAULT 'checkout',
                status TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )
        columns = {row[1] for row in connection.execute("PRAGMA table_info(transactions)")}
        if "kind" not in columns:
            connection.execute("ALTER TABLE transactions ADD COLUMN kind TEXT NOT NULL DEFAULT 'checkout'")


def validate_card(payload, require_fixture):
    card_number = payload.get("card_number")
    expiry = payload.get("expiry")
    cvv = payload.get("cvv")
    if not isinstance(card_number, str) or not CARD_NUMBER_PATTERN.fullmatch(re.sub(r"[ -]", "", card_number)):
        raise ValueError("Invalid card number format")
    if not isinstance(expiry, str) or not EXPIRY_PATTERN.fullmatch(expiry):
        raise ValueError("Invalid expiry format")
    if not isinstance(cvv, str) or not CVV_PATTERN.fullmatch(cvv):
        raise ValueError("Invalid CVV format")

    if require_fixture:
        expected = [
            os.environ.get("XEIN_LAB_CARD_NUMBER", ""),
            os.environ.get("XEIN_LAB_CARD_EXPIRY", ""),
            os.environ.get("XEIN_LAB_CARD_CVV", ""),
        ]
        if not all(expected):
            raise RuntimeError("Lab transfer card is not configured")
        supplied = [re.sub(r"[ -]", "", card_number), expiry, cvv]
        if not all(hmac.compare_digest(actual, configured) for actual, configured in zip(supplied, expected)):
            raise PermissionError("Card details do not match the lab fixture")


def validate_transaction(payload, kind):
    if not isinstance(payload, dict):
        raise ValueError("A JSON object is required")

    for field in ("reference", "payer", "payee"):
        value = payload.get(field)
        if not isinstance(value, str) or not IDENTIFIER_PATTERN.fullmatch(value):
            raise ValueError(f"{field} must contain 1-64 letters, digits, underscores or hyphens")

    amount = payload.get("amount_cents")
    if isinstance(amount, bool) or not isinstance(amount, int) or not 1 <= amount <= 100_000_000:
        raise ValueError("amount_cents must be an integer between 1 and 100000000")

    if payload.get("currency") != "LAB":
        raise ValueError("currency must be LAB")

    validate_card(payload, require_fixture=kind == "transfer")

    return {
        "reference": payload["reference"],
        "payer": payload["payer"],
        "payee": payload["payee"],
        "amount_cents": amount,
        "currency": "LAB",
        "kind": kind,
    }


class PaymentHandler(BaseHTTPRequestHandler):
    def send_json(self, status, body):
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/health":
            self.send_json(HTTPStatus.OK, {"status": "ok"})
            return

        if path == "/transactions":
            with connect_database() as connection:
                rows = connection.execute(
                    "SELECT * FROM transactions ORDER BY created_at DESC LIMIT 100"
                ).fetchall()
            self.send_json(HTTPStatus.OK, {"transactions": [dict(row) for row in rows]})
            return

        self.send_json(HTTPStatus.NOT_FOUND, {"error": "Unknown endpoint"})

    def do_POST(self):
        path = urlsplit(self.path).path
        if path not in ("/transactions", "/transfers"):
            self.send_json(HTTPStatus.NOT_FOUND, {"error": "Unknown endpoint"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            if not 0 < content_length <= 8192:
                raise ValueError("Request body must contain 1-8192 bytes")
            payload = json.loads(self.rfile.read(content_length))
            kind = "transfer" if path == "/transfers" else "checkout"
            transaction = validate_transaction(payload, kind)
        except PermissionError as error:
            self.send_json(HTTPStatus.FORBIDDEN, {"error": str(error)})
            return
        except RuntimeError as error:
            self.send_json(HTTPStatus.SERVICE_UNAVAILABLE, {"error": str(error)})
            return
        except (ValueError, json.JSONDecodeError, UnicodeDecodeError) as error:
            self.send_json(HTTPStatus.BAD_REQUEST, {"error": str(error)})
            return

        transaction["id"] = str(uuid.uuid4())
        transaction["status"] = "approved"
        transaction["created_at"] = datetime.now(timezone.utc).isoformat()

        with connect_database() as connection:
            try:
                connection.execute(
                    """
                    INSERT INTO transactions
                        (id, reference, payer, payee, amount_cents, currency, kind, status, created_at)
                    VALUES
                        (:id, :reference, :payer, :payee, :amount_cents, :currency, :kind, :status, :created_at)
                    """,
                    transaction,
                )
                status = HTTPStatus.OK if kind == "transfer" else HTTPStatus.CREATED
            except sqlite3.IntegrityError:
                existing = connection.execute(
                    "SELECT * FROM transactions WHERE reference = ?",
                    (transaction["reference"],),
                ).fetchone()
                if existing is None:
                    raise
                if any(existing[field] != transaction[field] for field in ("payer", "payee", "amount_cents", "currency", "kind")):
                    self.send_json(HTTPStatus.CONFLICT, {"error": "Reference already exists with different values"})
                    return
                transaction = dict(existing)
                status = HTTPStatus.OK

        print(
            json.dumps(
                {
                    "event": "simulated_transfer" if kind == "transfer" else "simulated_checkout",
                    "source_ip": self.client_address[0],
                    **transaction,
                },
                ensure_ascii=False,
            ),
            flush=True,
        )
        self.send_json(status, transaction)

    def log_message(self, format_string, *args):
        return


if __name__ == "__main__":
    initialize_database()
    print(f"Simulated payment service listening on port {PORT}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), PaymentHandler).serve_forever()
