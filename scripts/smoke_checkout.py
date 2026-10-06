import argparse
import http.cookiejar
import re
import ssl
import urllib.parse
import urllib.request
from pathlib import Path

from smoke_lab import load_lab_config


def csrf_token(page):
    match = re.search(r'name="_csrf" value="([^"]+)"', page)
    if not match:
        match = re.search(r'name="csrf-token" content="([^"]+)"', page)
    if not match:
        raise RuntimeError("CSRF token was not found")
    return match.group(1)


def main():
    parser = argparse.ArgumentParser(description="Check a complete simulated checkout")
    parser.add_argument("--url", default="https://localhost:8443")
    parser.add_argument("--env-file", type=Path, default=Path(".env"))
    args = parser.parse_args()
    config = load_lab_config(args.env_file)
    email = config.get("XEIN_TEST_EMAIL", "")
    password = config.get("XEIN_TEST_PASSWORD", "")
    if not email or not password:
        raise SystemExit("Private test user credentials are missing")

    cookies = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(cookies),
        urllib.request.HTTPSHandler(context=ssl._create_unverified_context()),
    )
    base = args.url.rstrip("/")

    login_page = opener.open(base + "/login", timeout=10).read().decode("utf-8")
    login_data = urllib.parse.urlencode({
        "email": email,
        "password": password,
        "_csrf": csrf_token(login_page),
    }).encode("utf-8")
    login_response = opener.open(base + "/login", data=login_data, timeout=10)
    if not login_response.url.endswith("/products"):
        raise SystemExit("Test user could not log in")
    products_page = login_response.read().decode("utf-8")

    add_data = urllib.parse.urlencode({
        "productId": "1",
        "_csrf": csrf_token(products_page),
    }).encode("utf-8")
    add_response = opener.open(base + "/add-to-order", data=add_data, timeout=10)
    if b'"success":true' not in add_response.read().replace(b" ", b""):
        raise SystemExit("Product could not be added to the order")

    order_page = opener.open(base + "/view-order", timeout=10).read().decode("utf-8")
    order_data = urllib.parse.urlencode({
        "address": "Lab address 1",
        "cardNumber": config["XEIN_LAB_CARD_NUMBER"],
        "expiry": config["XEIN_LAB_CARD_EXPIRY"],
        "cvv": config["XEIN_LAB_CARD_CVV"],
        "_csrf": csrf_token(order_page),
    }).encode("utf-8")
    confirmation = opener.open(base + "/confirm-order", data=order_data, timeout=10)
    page = confirmation.read().decode("utf-8")
    if "Order Confirmed" not in page:
        raise SystemExit("Order was not confirmed after simulated payment")
    print("Complete checkout approved and order confirmed")


if __name__ == "__main__":
    main()
