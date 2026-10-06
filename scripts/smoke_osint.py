import argparse
import http.cookiejar
import json
import ssl
import urllib.request
from pathlib import Path

from smoke_lab import load_lab_config


def main():
    parser = argparse.ArgumentParser(description="Check the fictional administrator OSINT link")
    parser.add_argument("--url", default="https://localhost:8443")
    parser.add_argument("--env-file", type=Path, default=Path(".env"))
    args = parser.parse_args()
    config = load_lab_config(args.env_file)
    email = config.get("XEIN_TEST_ADMIN_EMAIL", "")
    password = config.get("XEIN_TEST_ADMIN_PASSWORD", "")
    if not email or not password:
        raise SystemExit("Private administrator test credentials are missing")

    cookies = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(cookies),
        urllib.request.HTTPSHandler(context=ssl._create_unverified_context()),
    )
    base = args.url.rstrip("/")
    login_request = urllib.request.Request(
        base + "/api/auth/login",
        data=json.dumps({"username": email, "password": password}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with opener.open(login_request, timeout=10) as response:
        if response.status != 200:
            raise SystemExit("Administrator API login failed")

    with opener.open(base + "/api/users/", timeout=10) as response:
        users = json.load(response)
    if not isinstance(users, list) or not any(
        user.get("admin") and isinstance(user.get("twitter"), str) and user["twitter"].startswith("@")
        for user in users
    ):
        raise SystemExit("The administrator social handle is missing from the private seed or API")
    print("Fictional administrator social handle is present in the protected user API")


if __name__ == "__main__":
    main()
