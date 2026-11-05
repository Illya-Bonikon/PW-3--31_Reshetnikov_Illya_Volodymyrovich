"""Запуск: python -m audit_server [--host 127.0.0.1] [--port 5156]

Емулятор Android бачить комп'ютер-хост за адресою 10.0.2.2, тому застосунок
звертається до http://10.0.2.2:5156/api/v1/.
"""

import argparse
import os
import secrets
from http.server import ThreadingHTTPServer

from .api import AuditApi, make_handler
from .auth import JwtCodec
from .chaos import ChaosController
from .store import BuildingStore


def build_server(host: str, port: int, secret: bytes) -> ThreadingHTTPServer:
    api = AuditApi(BuildingStore(), JwtCodec(secret), ChaosController())
    return ThreadingHTTPServer((host, port), make_handler(api))


def main() -> None:
    parser = argparse.ArgumentParser(description="Тестовий REST API для застосунку «Енергоаудит будівель»")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5156)
    args = parser.parse_args()

    # Без змінної середовища секрет генерується заново, і старі токени після перезапуску стають недійсними.
    secret = os.environ.get("AUDIT_JWT_SECRET", "").encode() or secrets.token_bytes(32)
    server = build_server(args.host, args.port, secret)
    print(f"Energy audit API: http://{args.host}:{args.port}/api/v1/")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
