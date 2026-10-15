#!/usr/bin/env python3
"""Provision the development realm in a running OEA development instance through the API.

Change us-001-oidc-login, design D11; ADR-036 §4: the OEA-side configuration of the
development realm is created by this seed, never by code paths that exist only in the
profile 'dev'. The script logs in as the local system admin account and creates, if missing:

- the provider for the Keycloak realm 'oea-dev' (client 'oea-portal' with its development secret)
- one person with an identity binding per realm user (except the e-mail test users, which
  must stay unbound for REQ-166)
- the eight IdP group mappings of ADR-036 §4

During the setup phase (ADR-042) the first binding needs the admin's second factor. If the
session has none, the script enrolls a TOTP credential and prints its secret once; keep it in
OEA_ADMIN_TOTP_SECRET for later runs, which then log in with password and TOTP.

The script is idempotent and uses only the Python standard library.

Usage:
    OEA_ADMIN_PASSWORD=... python3 scripts/dev_seed.py [--url http://localhost:8080] [--login-name admin]
"""

from __future__ import annotations

import argparse
import base64
import getpass
import hashlib
import hmac
import json
import os
import struct
import sys
import time
import urllib.error
import urllib.request
from http.cookies import SimpleCookie
from pathlib import Path

REALM_FILE = Path(__file__).resolve().parent.parent / "dev" / "keycloak" / "realm-oea-dev.json"
ISSUER = "http://localhost:8180/realms/oea-dev"
CLIENT_ID = "oea-portal"

# ADR-036 §4: realm group -> authorization role
GROUP_ROLES = {
    "oea-portal-readers": "portal-reader",
    "oea-viewers": "viewer",
    "oea-analysts": "analyst",
    "oea-enterprise-architects": "enterprise-architect",
    "oea-auditors": "auditor",
    "oea-metamodel-admins": "metamodel-admin",
    "oea-security-admins": "security-admin",
    "oea-system-admins": "system-admin",
}

# REQ-166 test users: their identities must not be bound
UNBOUND_USERS = {"dev-email-twin", "dev-email-unverified"}


class Api:
    """Minimal client for the OEA API with the session cookie and the CSRF token."""

    def __init__(self, url: str) -> None:
        self.base = url.rstrip("/") + "/api/v1"
        self.session = ""
        self.csrf = ""

    def call(self, method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | None, dict]:
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method)
        request.add_header("Accept", "application/json, application/problem+json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        if self.session:
            request.add_header("Cookie", f"__Host-OEA_SESSION={self.session}")
            request.add_header("X-XSRF-TOKEN", self.csrf)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.status, _json(response.read()), dict(response.headers)
        except urllib.error.HTTPError as error:
            return error.code, _json(error.read()), dict(error.headers)

    def remember_session(self, headers: dict, body: dict) -> None:
        cookie = SimpleCookie()
        cookie.load(headers.get("Set-Cookie", ""))
        if "__Host-OEA_SESSION" in cookie:
            self.session = cookie["__Host-OEA_SESSION"].value
        self.csrf = body.get("csrfToken", self.csrf)


def _json(raw: bytes):
    return json.loads(raw) if raw else None


def totp(secret: str, offset: int = 0) -> str:
    """RFC 6238 code (SHA-1, 6 digits, 30 s) for a Base32 secret."""
    key = base64.b32decode(secret + "=" * (-len(secret) % 8))
    digest = hmac.new(key, struct.pack(">Q", int(time.time()) // 30 + offset), hashlib.sha1).digest()
    offset_bits = digest[-1] & 0x0F
    value = struct.unpack(">I", digest[offset_bits:offset_bits + 4])[0] & 0x7FFFFFFF
    return f"{value % 1_000_000:06d}"


def fail(message: str) -> None:
    print(f"ERROR {message}", file=sys.stderr)
    sys.exit(1)


def login(api: Api, login_name: str, password: str) -> dict:
    status, body, headers = api.call("POST", "/auth/local/login", {"loginName": login_name, "password": password})
    if status == 401 and body and body.get("type", "").endswith(":second-factor-required"):
        secret = os.environ.get("OEA_ADMIN_TOTP_SECRET")
        if not secret:
            fail("the login needs a TOTP code: set OEA_ADMIN_TOTP_SECRET to the secret printed by the first run")
        status, body, headers = api.call("POST", "/auth/local/login/totp",
                                         {"loginTransaction": body["loginTransaction"], "code": totp(secret)})
    if status != 201:
        fail(f"login failed (HTTP {status})")
    api.remember_session(headers, body)
    return body


def ensure_second_factor(api: Api) -> None:
    """ADR-042: the first binding or mapping needs a second factor confirmed in the session."""
    _, me, _ = api.call("GET", "/me")
    if me and me.get("mfaSatisfied"):
        return
    status, enrollment, _ = api.call("POST", "/me/second-factors/totp", {"label": "dev seed"})
    if status != 201:
        fail(f"TOTP enrollment could not be started (HTTP {status})")
    secret = enrollment["secret"]
    status, _, _ = api.call("POST", f"/me/second-factors/totp/{enrollment['credentialId']}/confirm",
                            {"code": totp(secret)})
    if status != 200:
        fail(f"TOTP enrollment could not be confirmed (HTTP {status})")
    print("Enrolled a TOTP credential for the system admin account. Keep the secret for later logins:")
    print(f"  OEA_ADMIN_TOTP_SECRET={secret}")


def ensure_provider(api: Api, client_secret: str) -> str:
    _, providers, _ = api.call("GET", "/admin/auth/providers")
    for provider in providers or []:
        if provider.get("issuerUrl") == ISSUER:
            print(f"provider exists: {provider['name']}")
            return provider["id"]
    status, provider, _ = api.call("POST", "/admin/auth/providers", {
        "name": "Keycloak (development realm)", "type": "oidc", "vendor": "generic-oidc",
        "issuerUrl": ISSUER, "clientId": CLIENT_ID, "clientSecret": client_secret,
        "scopes": "openid profile email",
    })
    if status != 201:
        fail(f"provider could not be created (HTTP {status}): {provider}")
    print(f"provider created: {provider['name']}")
    return provider["id"]


def ensure_people(api: Api, realm: dict, provider_id: str) -> None:
    _, persons, _ = api.call("GET", "/admin/persons")
    by_email = {person.get("email"): person for person in persons or []}
    for user in realm["users"]:
        if user["username"] in UNBOUND_USERS:
            continue
        email = user.get("email") or f"{user['username']}@oea.localhost"
        person = by_email.get(email)
        if person is None:
            name = " ".join(part for part in (user.get("firstName"), user.get("lastName")) if part) or user["username"]
            status, person, _ = api.call("POST", "/admin/persons", {"name": name, "email": email})
            if status != 201:
                fail(f"person {user['username']} could not be created (HTTP {status})")
            print(f"person created: {user['username']}")
        _, identities, _ = api.call("GET", f"/admin/persons/{person['id']}/identities")
        if any(identity.get("subject") == user["id"] for identity in identities or []):
            continue
        status, body, _ = api.call("POST", f"/admin/persons/{person['id']}/identities",
                                   {"authProviderId": provider_id, "subject": user["id"]})
        if status not in (201, 409):
            fail(f"binding of {user['username']} failed (HTTP {status}): {body}")
        print(f"binding created: {user['username']}")


def ensure_mappings(api: Api, realm: dict, provider_id: str) -> None:
    groups = {group["name"]: group["id"] for group in realm["groups"]}
    _, mappings, _ = api.call("GET", f"/admin/idp-group-mappings?authProviderId={provider_id}")
    existing = {(mapping["groupIdentifier"], mapping["roleId"]) for mapping in mappings or []}
    for group, role in GROUP_ROLES.items():
        if (groups[group], role) in existing:
            continue
        status, body, _ = api.call("POST", "/admin/idp-group-mappings",
                                   {"authProviderId": provider_id, "groupIdentifier": groups[group], "roleId": role})
        if status != 201:
            fail(f"mapping {group} -> {role} failed (HTTP {status}): {body}")
        print(f"mapping created: {group} -> {role}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--url", default=os.environ.get("OEA_URL", "http://localhost:8080"),
                        help="base URL of the backend (default http://localhost:8080)")
    parser.add_argument("--login-name", default=os.environ.get("OEA_ADMIN_LOGIN_NAME", "admin"))
    args = parser.parse_args()
    password = os.environ.get("OEA_ADMIN_PASSWORD") or getpass.getpass("Password of the system admin account: ")
    realm = json.loads(REALM_FILE.read_text(encoding="utf-8"))
    client_secret = next(client["secret"] for client in realm["clients"] if client["clientId"] == CLIENT_ID)

    api = Api(args.url)
    login(api, args.login_name, password)
    provider_id = ensure_provider(api, client_secret)
    ensure_second_factor(api)
    ensure_people(api, realm, provider_id)
    ensure_mappings(api, realm, provider_id)
    print("Development realm provisioned.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
