import json
import os
import re
import sqlite3
import uuid
from datetime import datetime, timezone
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


DATABASE_PATH = Path(os.environ.get("PAYMENT_DB_PATH", "/data/transactions.sqlite"))
PORT = int(os.environ.get("PORT", "9090"))
IDENTIFIER_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,64}$")


def connect_database():
    connection = sqlite3.connect(DATABASE_PATH, timeout=10)
    connection.row_factory = sqlite3.Row
    return connection


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
                status TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )


def validate_transaction(payload):
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

    return {
        "reference": payload["reference"],
        "payer": payload["payer"],
        "payee": payload["payee"],
        "amount_cents": amount,
        "currency": "LAB",
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
        if urlsplit(self.path).path != "/transactions":
            self.send_json(HTTPStatus.NOT_FOUND, {"error": "Unknown endpoint"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            if not 0 < content_length <= 8192:
                raise ValueError("Request body must contain 1-8192 bytes")
            payload = json.loads(self.rfile.read(content_length))
            transaction = validate_transaction(payload)
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
                        (id, reference, payer, payee, amount_cents, currency, status, created_at)
                    VALUES
                        (:id, :reference, :payer, :payee, :amount_cents, :currency, :status, :created_at)
                    """,
                    transaction,
                )
                status = HTTPStatus.CREATED
            except sqlite3.IntegrityError:
                existing = connection.execute(
                    "SELECT * FROM transactions WHERE reference = ?",
                    (transaction["reference"],),
                ).fetchone()
                if existing is None:
                    raise
                if any(existing[field] != transaction[field] for field in ("payer", "payee", "amount_cents", "currency")):
                    self.send_json(HTTPStatus.CONFLICT, {"error": "Reference already exists with different values"})
                    return
                transaction = dict(existing)
                status = HTTPStatus.OK

        print(
            json.dumps(
                {
                    "event": "simulated_transaction",
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
