"""Інтеграційні тести API через справжні HTTP-запити до сервера у фоновому потоці.

Запуск із теки server: python -m unittest discover -s tests -v
"""

import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

from audit_server.api import AuditApi, make_handler
from audit_server.auth import JwtCodec
from audit_server.chaos import ChaosController
from audit_server.store import BuildingStore


class FakeClock:
    def __init__(self, now: float = 1_800_000_000.0):
        self.now = now

    def __call__(self) -> float:
        return self.now


class ApiTestCase(unittest.TestCase):
    def setUp(self):
        self.clock = FakeClock()
        self.sleeps: list[float] = []
        api = AuditApi(BuildingStore(), JwtCodec(b"test-secret", clock=self.clock), ChaosController(),
                       sleep=self.sleeps.append)
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(api, log=lambda _: None))
        self.base = f"http://127.0.0.1:{self.server.server_address[1]}/api/v1"
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def call(self, method: str, path: str, body=None, token: str | None = None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        if token:
            request.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                raw = response.read()
                return response.status, dict(response.headers), raw
        except urllib.error.HTTPError as error:
            return error.code, dict(error.headers), error.read()

    def call_json(self, *args, **kwargs):
        status, headers, raw = self.call(*args, **kwargs)
        return status, headers, json.loads(raw) if raw else None

    def login(self, login: str, password: str, ttl: int | None = None) -> str:
        body = {"login": login, "password": password}
        if ttl is not None:
            body["ttlSeconds"] = ttl
        status, _, payload = self.call_json("POST", "/auth/login", body)
        self.assertEqual(200, status)
        return payload["accessToken"]


class AuthTests(ApiTestCase):
    def test_login_returns_jwt_with_role_and_expiry(self):
        status, _, payload = self.call_json("POST", "/auth/login", {"login": "auditor", "password": "audit2026"})
        self.assertEqual(200, status)
        self.assertEqual("AUDITOR", payload["user"]["role"])
        self.assertEqual(3, len(payload["accessToken"].split(".")))
        self.assertEqual(int(self.clock.now) + 600, payload["expiresAt"])

    def test_login_validation_and_bad_credentials(self):
        status, _, payload = self.call_json("POST", "/auth/login", {"login": "", "password": ""})
        self.assertEqual(400, status)
        self.assertEqual({"login", "password"}, set(payload["error"]["fields"]))
        status, _, payload = self.call_json("POST", "/auth/login", {"login": "admin", "password": "wrong"})
        self.assertEqual(401, status)
        self.assertEqual("BAD_CREDENTIALS", payload["error"]["code"])

    def test_ttl_is_clamped(self):
        token = self.login("viewer", "view2026", ttl=5)
        self.clock.now += 29
        self.assertEqual(200, self.call_json("GET", "/auth/me", token=token)[0])
        self.clock.now += 1
        status, _, payload = self.call_json("GET", "/auth/me", token=token)
        self.assertEqual((401, "TOKEN_EXPIRED"), (status, payload["error"]["code"]))

    def test_missing_and_tampered_tokens(self):
        status, _, payload = self.call_json("GET", "/buildings")
        self.assertEqual((401, "TOKEN_MISSING"), (status, payload["error"]["code"]))
        token = self.login("viewer", "view2026")
        tampered = token[:-2] + ("AA" if not token.endswith("AA") else "BB")
        status, _, payload = self.call_json("GET", "/buildings", token=tampered)
        self.assertEqual((401, "TOKEN_INVALID"), (status, payload["error"]["code"]))

    def test_role_ranks(self):
        viewer = self.login("viewer", "view2026")
        auditor = self.login("auditor", "audit2026")
        admin = self.login("admin", "admin2026")
        self.assertEqual(403, self.call_json("GET", "/admin/users", token=auditor)[0])
        status, _, users = self.call_json("GET", "/admin/users", token=admin)
        self.assertEqual(200, status)
        self.assertNotIn("password", users[0])
        self.assertEqual(403, self.call_json("DELETE", "/buildings/1", token=auditor)[0])
        self.assertEqual(403, self.call_json("POST", "/buildings", NEW_BUILDING, token=viewer)[0])


NEW_BUILDING = {
    "name": "Котельня кампусу",
    "address": "м. Київ, вул. Політехнічна, 39",
    "type": "INDUSTRIAL",
    "heatedAreaM2": 300,
    "yearBuilt": 1990,
    "auditorEmail": "auditor@kpi.ua",
    "auditDate": "2026-11-01",
    "envelope": [
        {"kind": "WALL", "areaM2": 280, "uValue": None,
         "layers": [{"material": "Цегла", "thicknessMm": 380, "conductivity": 0.81}]},
        {"kind": "WINDOW", "areaM2": 20, "uValue": 2.8, "layers": []},
    ],
}


class BuildingTests(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.admin = self.login("admin", "admin2026")

    def test_list_has_nested_envelope_without_inspection_log(self):
        status, _, items = self.call_json("GET", "/buildings", token=self.admin)
        self.assertEqual(200, status)
        self.assertEqual(6, len(items))
        self.assertIn("layers", items[0]["envelope"][0])
        self.assertNotIn("inspectionLog", items[0])

    def test_details_include_inspection_log(self):
        status, _, item = self.call_json("GET", "/buildings/1", token=self.admin)
        self.assertEqual(200, status)
        self.assertGreater(len(item["inspectionLog"]), 0)
        self.assertEqual(404, self.call_json("GET", "/buildings/999", token=self.admin)[0])

    def test_crud_cycle(self):
        status, _, created = self.call_json("POST", "/buildings", NEW_BUILDING, token=self.admin)
        self.assertEqual(201, status)
        self.assertEqual(7, created["id"])
        changed = {**NEW_BUILDING, "name": "Котельня № 2"}
        status, _, updated = self.call_json("PUT", f"/buildings/{created['id']}", changed, token=self.admin)
        self.assertEqual((200, "Котельня № 2"), (status, updated["name"]))
        status, _, raw = self.call("DELETE", f"/buildings/{created['id']}", token=self.admin)
        self.assertEqual((204, b""), (status, raw))
        self.assertEqual(404, self.call_json("DELETE", f"/buildings/{created['id']}", token=self.admin)[0])

    def test_validation_reports_fields(self):
        broken = {**NEW_BUILDING, "heatedAreaM2": 5, "auditorEmail": "x@", "envelope": [{"kind": "WINDOW", "areaM2": 3}]}
        status, _, payload = self.call_json("POST", "/buildings", broken, token=self.admin)
        self.assertEqual(422, status)
        self.assertEqual({"heatedAreaM2", "auditorEmail", "envelope[0]"}, set(payload["error"]["fields"]))

    def test_tariffs_contain_climate_and_measures(self):
        status, _, tariffs = self.call_json("GET", "/tariffs", token=self.admin)
        self.assertEqual(200, status)
        self.assertEqual(35.0, tariffs["climate"]["indoorTempC"] - tariffs["climate"]["outdoorDesignTempC"])
        self.assertEqual(4, len(tariffs["measures"]))


class ChaosTests(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.token = self.login("auditor", "audit2026")

    def set_mode(self, mode: str, failures: int | None = None):
        body = {"mode": mode} if failures is None else {"mode": mode, "failures": failures}
        status, _, state = self.call_json("POST", "/debug/chaos", body, token=self.token)
        self.assertEqual(200, status)
        return state

    def test_flaky_recovers_after_configured_failures(self):
        self.set_mode("FLAKY", failures=2)
        codes = [self.call("GET", "/tariffs", token=self.token)[0] for _ in range(3)]
        self.assertEqual([503, 503, 200], codes)
        self.assertEqual("NONE", self.call_json("GET", "/debug/chaos", token=self.token)[2]["mode"])

    def test_retry_after_headers(self):
        self.set_mode("UNAVAILABLE")
        status, headers, _ = self.call("GET", "/buildings", token=self.token)
        self.assertEqual((503, "2"), (status, headers.get("Retry-After")))
        self.set_mode("RATE_LIMIT")
        status, headers, _ = self.call("GET", "/buildings", token=self.token)
        self.assertEqual((429, "1"), (status, headers.get("Retry-After")))

    def test_server_error_and_malformed_json(self):
        self.set_mode("SERVER_ERROR")
        self.assertEqual(500, self.call("GET", "/buildings", token=self.token)[0])
        self.set_mode("MALFORMED_JSON")
        status, _, raw = self.call("GET", "/buildings", token=self.token)
        self.assertEqual(200, status)
        with self.assertRaises(json.JSONDecodeError):
            json.loads(raw)

    def test_timeout_delays_response(self):
        self.set_mode("TIMEOUT")
        self.assertEqual(200, self.call("GET", "/tariffs", token=self.token)[0])
        self.assertEqual([12.0], self.sleeps)

    def test_login_and_chaos_control_are_not_affected(self):
        self.set_mode("SERVER_ERROR")
        self.login("viewer", "view2026")
        self.assertEqual("SERVER_ERROR", self.set_mode("SERVER_ERROR")["mode"])

    def test_unknown_mode_rejected(self):
        status, _, payload = self.call_json("POST", "/debug/chaos", {"mode": "BOOM"}, token=self.token)
        self.assertEqual((400, "VALIDATION_ERROR"), (status, payload["error"]["code"]))


if __name__ == "__main__":
    unittest.main()
