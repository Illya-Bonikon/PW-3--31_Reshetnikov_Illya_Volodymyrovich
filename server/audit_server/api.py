"""Маршрутизація REST API «Енергоаудит будівель» поверх http.server."""

import json
import re
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler
from typing import Callable

from .auth import ROLE_ADMIN, ROLE_AUDITOR, ROLE_RANK, ROLE_VIEWER, USERS, JwtCodec, TokenError, authenticate, clamp_ttl
from .chaos import ChaosController
from .store import TARIFFS, BuildingStore, ValidationError, validate_building

API_PREFIX = "/api/v1"
MALFORMED_BODY = '[{"id": 1, "name": "Навчальний корпус'.encode("utf-8")


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str, fields: dict | None = None, headers: dict | None = None):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message
        self.fields = fields
        self.headers = headers or {}

    def body(self) -> dict:
        error = {"code": self.code, "message": self.message}
        if self.fields:
            error["fields"] = self.fields
        return {"error": error}


@dataclass
class Request:
    method: str
    path: str
    headers: dict
    body: bytes
    params: dict = field(default_factory=dict)
    claims: dict | None = None

    def json(self) -> object:
        if not self.body:
            return {}
        try:
            return json.loads(self.body.decode("utf-8"))
        except (ValueError, UnicodeDecodeError) as error:
            raise ApiError(400, "BAD_JSON", "Тіло запиту не є коректним JSON") from error


@dataclass
class Response:
    status: int
    body: object = None
    headers: dict = field(default_factory=dict)
    raw: bytes | None = None


@dataclass(frozen=True)
class Route:
    method: str
    pattern: re.Pattern
    handler: Callable[[Request], Response]
    min_role: str | None  # None означає публічний маршрут
    chaos: bool


class AuditApi:
    def __init__(self, store: BuildingStore, codec: JwtCodec, chaos: ChaosController, sleep=time.sleep):
        self._store = store
        self._codec = codec
        self._chaos = chaos
        self._sleep = sleep
        self._routes = [
            self._route("GET", "/health", self._health, None, chaos=False),
            self._route("POST", "/auth/login", self._login, None, chaos=False),
            self._route("GET", "/auth/me", self._me, ROLE_VIEWER),
            self._route("GET", "/admin/users", self._users, ROLE_ADMIN),
            self._route("GET", "/buildings", self._list_buildings, ROLE_VIEWER),
            self._route("POST", "/buildings", self._create_building, ROLE_AUDITOR),
            self._route("GET", r"/buildings/(?P<id>\d+)", self._get_building, ROLE_VIEWER),
            self._route("PUT", r"/buildings/(?P<id>\d+)", self._update_building, ROLE_AUDITOR),
            self._route("DELETE", r"/buildings/(?P<id>\d+)", self._delete_building, ROLE_ADMIN),
            self._route("GET", "/tariffs", self._tariffs, ROLE_VIEWER),
            self._route("GET", "/debug/chaos", self._chaos_state, ROLE_VIEWER, chaos=False),
            self._route("POST", "/debug/chaos", self._chaos_configure, ROLE_VIEWER, chaos=False),
        ]

    @staticmethod
    def _route(method, path, handler, min_role, chaos=True) -> Route:
        return Route(method, re.compile(f"^{API_PREFIX}{path}$"), handler, min_role, chaos)

    def dispatch(self, request: Request) -> Response:
        try:
            route, params = self._match(request)
            request.params = params
            if route.chaos:
                injected = self._inject_fault()
                if injected is not None:
                    return injected
            if route.min_role is not None:
                request.claims = self._authorize(request, route.min_role)
            return route.handler(request)
        except ApiError as error:
            return Response(error.status, error.body(), error.headers)

    def _match(self, request: Request) -> tuple[Route, dict]:
        path = request.path.split("?", 1)[0]
        for route in self._routes:
            match = route.pattern.match(path)
            if match and route.method == request.method:
                return route, match.groupdict()
        raise ApiError(404, "NOT_FOUND", "Ресурс не знайдено")

    def _inject_fault(self) -> Response | None:
        fault = self._chaos.next_fault()
        if fault is None:
            return None
        if fault.delay_seconds:
            # Після затримки запит обслуговується звичайно: клієнт має відвалитися раніше за таймаутом.
            self._sleep(fault.delay_seconds)
            return None
        if fault.malformed_body:
            return Response(200, raw=MALFORMED_BODY)
        headers = {"Retry-After": str(fault.retry_after)} if fault.retry_after else {}
        raise ApiError(fault.status, fault.code, fault.message, headers=headers)

    def _authorize(self, request: Request, min_role: str) -> dict:
        header = request.headers.get("Authorization", "")
        if not header:
            raise ApiError(401, "TOKEN_MISSING", "Потрібна автентифікація")
        scheme, _, token = header.partition(" ")
        if scheme != "Bearer" or not token:
            raise ApiError(401, "TOKEN_INVALID", "Очікується заголовок Authorization: Bearer <token>")
        try:
            claims = self._codec.verify(token)
        except TokenError as error:
            raise ApiError(401, error.code, error.message) from error
        if ROLE_RANK.get(claims.get("role"), 0) < ROLE_RANK[min_role]:
            raise ApiError(403, "FORBIDDEN", "Недостатньо прав для цієї дії")
        return claims

    # --- обробники ---

    @staticmethod
    def _health(_: Request) -> Response:
        return Response(200, {"status": "ok"})

    def _login(self, request: Request) -> Response:
        payload = request.json()
        if not isinstance(payload, dict):
            raise ApiError(400, "VALIDATION_ERROR", "Очікується JSON-об'єкт")
        login, password = payload.get("login"), payload.get("password")
        fields = {name: "Поле обов'язкове" for name, value in (("login", login), ("password", password))
                  if not isinstance(value, str) or not value.strip()}
        if fields:
            raise ApiError(400, "VALIDATION_ERROR", "Заповніть логін і пароль", fields)
        user = authenticate(login.strip(), password)
        if user is None:
            raise ApiError(401, "BAD_CREDENTIALS", "Невірний логін або пароль")
        token, expires_at = self._codec.issue(user, clamp_ttl(payload.get("ttlSeconds")))
        return Response(200, {
            "accessToken": token,
            "tokenType": "Bearer",
            "expiresAt": expires_at,
            "user": user.to_public_dict(),
        })

    @staticmethod
    def _me(request: Request) -> Response:
        return Response(200, USERS[request.claims["sub"]].to_public_dict())

    @staticmethod
    def _users(_: Request) -> Response:
        return Response(200, [user.to_public_dict() for user in USERS.values()])

    def _list_buildings(self, _: Request) -> Response:
        return Response(200, self._store.list_summaries())

    def _get_building(self, request: Request) -> Response:
        return Response(200, self._require_building(int(request.params["id"])))

    def _create_building(self, request: Request) -> Response:
        return Response(201, self._store.create(self._validated(request)))

    def _update_building(self, request: Request) -> Response:
        building_id = int(request.params["id"])
        updated = self._store.update(building_id, self._validated(request))
        if updated is None:
            raise self._not_found(building_id)
        return Response(200, updated)

    def _delete_building(self, request: Request) -> Response:
        building_id = int(request.params["id"])
        if not self._store.delete(building_id):
            raise self._not_found(building_id)
        return Response(204)

    @staticmethod
    def _tariffs(_: Request) -> Response:
        return Response(200, TARIFFS)

    def _chaos_state(self, _: Request) -> Response:
        return Response(200, self._chaos.state())

    def _chaos_configure(self, request: Request) -> Response:
        payload = request.json()
        mode = payload.get("mode") if isinstance(payload, dict) else None
        failures = payload.get("failures") if isinstance(payload, dict) else None
        try:
            return Response(200, self._chaos.configure(str(mode), failures if isinstance(failures, int) else None))
        except ValueError as error:
            raise ApiError(400, "VALIDATION_ERROR", str(error)) from error

    # --- допоміжне ---

    @staticmethod
    def _validated(request: Request) -> dict:
        try:
            return validate_building(request.json())
        except ValidationError as error:
            raise ApiError(422, "VALIDATION_ERROR", str(error), error.fields) from error

    def _require_building(self, building_id: int) -> dict:
        building = self._store.get(building_id)
        if building is None:
            raise self._not_found(building_id)
        return building

    @staticmethod
    def _not_found(building_id: int) -> ApiError:
        return ApiError(404, "NOT_FOUND", f"Будівлю з id={building_id} не знайдено")


def make_handler(api: AuditApi, log: Callable[[str], None] = print) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def _serve(self):
            length = int(self.headers.get("Content-Length") or 0)
            request = Request(self.command, self.path, self.headers, self.rfile.read(length))
            response = api.dispatch(request)
            if response.raw is not None:
                payload = response.raw
            elif response.body is None:
                payload = b""
            else:
                payload = json.dumps(response.body, ensure_ascii=False).encode("utf-8")
            self.send_response(response.status)
            if payload:
                self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            for name, value in response.headers.items():
                self.send_header(name, value)
            self.end_headers()
            self.wfile.write(payload)

        do_GET = do_POST = do_PUT = do_DELETE = _serve

        def log_message(self, format, *args):
            log(f"{self.command} {self.path} -> {args[1] if len(args) > 1 else ''}")

    return Handler

