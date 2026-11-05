"""Тестові користувачі та JWT (HS256) без сторонніх бібліотек."""

import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass

ROLE_ADMIN = "ADMIN"
ROLE_AUDITOR = "AUDITOR"
ROLE_VIEWER = "VIEWER"

# Вищий ранг включає права нижчих, тому перевірка доступу зводиться до порівняння чисел.
ROLE_RANK = {ROLE_VIEWER: 1, ROLE_AUDITOR: 2, ROLE_ADMIN: 3}

MIN_TTL_SECONDS = 30
MAX_TTL_SECONDS = 3600
DEFAULT_TTL_SECONDS = 600


@dataclass(frozen=True)
class User:
    login: str
    password: str
    full_name: str
    role: str

    def to_public_dict(self) -> dict:
        return {"login": self.login, "fullName": self.full_name, "role": self.role}


USERS = {
    user.login: user
    for user in (
        User("admin", "admin2026", "Адміністратор системи", ROLE_ADMIN),
        User("auditor", "audit2026", "Коваленко Олена, енергоаудитор", ROLE_AUDITOR),
        User("viewer", "view2026", "Представник ОСББ", ROLE_VIEWER),
    )
}


class TokenError(Exception):
    """Причина відмови передається клієнту як машинний код помилки."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def authenticate(login: str, password: str) -> User | None:
    user = USERS.get(login)
    if user is None or not hmac.compare_digest(user.password, password):
        return None
    return user


def clamp_ttl(requested: object) -> int:
    if not isinstance(requested, int) or isinstance(requested, bool):
        return DEFAULT_TTL_SECONDS
    return max(MIN_TTL_SECONDS, min(MAX_TTL_SECONDS, requested))


def _b64url_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64url_decode(text: str) -> bytes:
    padding = "=" * (-len(text) % 4)
    return base64.urlsafe_b64decode(text + padding)


class JwtCodec:
    _HEADER = _b64url_encode(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())

    def __init__(self, secret: bytes, clock=time.time):
        self._secret = secret
        self._clock = clock

    def issue(self, user: User, ttl_seconds: int) -> tuple[str, int]:
        issued_at = int(self._clock())
        expires_at = issued_at + ttl_seconds
        claims = {
            "sub": user.login,
            "name": user.full_name,
            "role": user.role,
            "iat": issued_at,
            "exp": expires_at,
        }
        payload = _b64url_encode(json.dumps(claims, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
        signing_input = f"{self._HEADER}.{payload}"
        return f"{signing_input}.{self._sign(signing_input)}", expires_at

    def verify(self, token: str) -> dict:
        parts = token.split(".")
        if len(parts) != 3:
            raise TokenError("TOKEN_INVALID", "Токен має неправильну структуру")
        signing_input = f"{parts[0]}.{parts[1]}"
        if not hmac.compare_digest(self._sign(signing_input), parts[2]):
            raise TokenError("TOKEN_INVALID", "Підпис токена не збігається")
        try:
            claims = json.loads(_b64url_decode(parts[1]))
        except (ValueError, UnicodeDecodeError) as error:
            raise TokenError("TOKEN_INVALID", "Не вдалося прочитати вміст токена") from error
        if claims.get("sub") not in USERS:
            raise TokenError("TOKEN_INVALID", "Користувача з токена не існує")
        if int(claims.get("exp", 0)) <= self._clock():
            raise TokenError("TOKEN_EXPIRED", "Термін дії токена минув")
        return claims

    def _sign(self, signing_input: str) -> str:
        digest = hmac.new(self._secret, signing_input.encode("ascii"), hashlib.sha256).digest()
        return _b64url_encode(digest)
