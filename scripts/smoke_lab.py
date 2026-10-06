import argparse
import json
import ssl
import urllib.error
import urllib.request
import uuid
from pathlib import Path


def load_lab_config(path):
    config = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.lstrip().startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        config[key.strip()] = value.strip()
    return config


def request_json(url, payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"} if body is not None else {},
        method="POST" if body is not None else "GET",
    )
    context = ssl._create_unverified_context()
    try:
        response = urllib.request.urlopen(request, context=context, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.load(response)


def main():
    parser = argparse.ArgumentParser(description="Check the isolated Xein lab")
    parser.add_argument("--url", default="https://localhost:8443")
    parser.add_argument("--env-file", type=Path, default=Path(".env"))
    args = parser.parse_args()

    status, products = request_json(args.url.rstrip("/") + "/api/products/all")
    if status != 200 or not isinstance(products, list) or not products:
        raise SystemExit("Product API did not return products")

    config = load_lab_config(args.env_file)
    card_number = config.get("XEIN_LAB_CARD_NUMBER", "")
    expiry = config.get("XEIN_LAB_CARD_EXPIRY", "")
    cvv = config.get("XEIN_LAB_CARD_CVV", "")
    if not all((card_number, expiry, cvv)):
        raise SystemExit("Private lab card fixture is missing")

    transfer = {
        "reference": "smoke_" + uuid.uuid4().hex,
        "payer": "lab_test",
        "payee": "lab_destination",
        "amount_cents": 1,
        "currency": "LAB",
        "card_number": card_number,
        "expiry": expiry,
        "cvv": cvv,
    }
    endpoint = args.url.rstrip("/") + "/api/payments/transfer"
    rejected_status, _ = request_json(endpoint, dict(transfer, cvv="000"))
    if rejected_status != 403:
        raise SystemExit("The payment service did not reject an invalid card")

    approved_status, result = request_json(endpoint, transfer)
    if approved_status != 200 or result.get("status") != "approved":
        raise SystemExit("The lab transfer was not approved")
    if card_number in json.dumps(result) or "cvv" in result:
        raise SystemExit("The payment response exposed card data")

    print(f"Lab healthy: {len(products)} products, invalid transfer rejected, valid transfer approved")


if __name__ == "__main__":
    main()
