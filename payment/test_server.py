import json
import os
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from unittest.mock import patch

from payment import server


class PaymentServiceTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent)
        self.original_database_path = server.DATABASE_PATH
        server.DATABASE_PATH = Path(self.directory.name) / "transactions.sqlite"
        server.initialize_database()
        self.httpd = server.ThreadingHTTPServer(("127.0.0.1", 0), server.PaymentHandler)
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.httpd.server_port}"

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join()
        server.DATABASE_PATH = self.original_database_path
        assert Path(self.directory.name).resolve().is_relative_to(Path(__file__).resolve().parent)
        self.directory.cleanup()

    def post(self, path, payload):
        request = urllib.request.Request(
            self.base_url + path,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            response = urllib.request.urlopen(request, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, json.load(response)

    def test_checkout_and_transfer_record_only_non_card_fields(self):
        checkout = {
            "reference": "checkout_1",
            "payer": "user_1",
            "payee": "xein_store",
            "amount_cents": 2599,
            "currency": "LAB",
            "card_number": "1234567890123456",
            "expiry": "09/25",
            "cvv": "784",
        }
        status, result = self.post("/transactions", checkout)
        self.assertEqual(status, 201)
        self.assertEqual(result["kind"], "checkout")

        transfer = dict(checkout, reference="transfer_1", card_number="1111222233334444")
        fixture = {
            "XEIN_LAB_CARD_NUMBER": "1111222233334444",
            "XEIN_LAB_CARD_EXPIRY": "09/25",
            "XEIN_LAB_CARD_CVV": "784",
        }
        with patch.dict(os.environ, fixture):
            status, _ = self.post("/transfers", dict(transfer, cvv="000"))
            self.assertEqual(status, 403)
            status, result = self.post("/transfers", transfer)
            self.assertEqual(status, 200)
            self.assertEqual(result["kind"], "transfer")
            status, _ = self.post("/transfers", transfer)
            self.assertEqual(status, 200)

        with server.connect_database() as connection:
            count = connection.execute("SELECT COUNT(*) FROM transactions").fetchone()[0]
            self.assertEqual(count, 2)
        database_bytes = server.DATABASE_PATH.read_bytes()
        self.assertNotIn(b"1111222233334444", database_bytes)
        self.assertNotIn("card_number", result)
        self.assertNotIn("cvv", result)


if __name__ == "__main__":
    unittest.main()
