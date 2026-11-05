"""Керована імітація збоїв для перевірки обробки помилок на клієнті (ПЗ 12)."""

import threading
from dataclasses import dataclass

MODES = (
    "NONE",            # звичайна робота
    "FLAKY",           # перші N запитів отримують 503, далі сервер відновлюється сам
    "UNAVAILABLE",     # постійно 503 із заголовком Retry-After
    "RATE_LIMIT",      # постійно 429 із заголовком Retry-After
    "SERVER_ERROR",    # постійно 500
    "TIMEOUT",         # відповідь затримується довше за таймаут клієнта
    "MALFORMED_JSON",  # обірваний JSON у тілі відповіді з кодом 200
)

TIMEOUT_DELAY_SECONDS = 12.0
DEFAULT_FLAKY_FAILURES = 2


@dataclass(frozen=True)
class Fault:
    status: int
    code: str
    message: str
    retry_after: int | None = None
    delay_seconds: float = 0.0
    malformed_body: bool = False


_FAULTS = {
    "UNAVAILABLE": Fault(503, "SERVICE_UNAVAILABLE", "Сервіс тимчасово недоступний", retry_after=2),
    "RATE_LIMIT": Fault(429, "RATE_LIMITED", "Забагато запитів, повторіть пізніше", retry_after=1),
    "SERVER_ERROR": Fault(500, "INTERNAL_ERROR", "Внутрішня помилка сервера"),
    "TIMEOUT": Fault(200, "", "", delay_seconds=TIMEOUT_DELAY_SECONDS),
    "MALFORMED_JSON": Fault(200, "", "", malformed_body=True),
}
_FLAKY_FAULT = Fault(503, "SERVICE_UNAVAILABLE", "Сервіс перезапускається", retry_after=1)


class ChaosController:
    def __init__(self):
        self._lock = threading.Lock()
        self._mode = "NONE"
        self._remaining_failures = 0

    def configure(self, mode: str, failures: int | None = None) -> dict:
        if mode not in MODES:
            raise ValueError(f"Невідомий режим: {mode}")
        with self._lock:
            self._mode = mode
            self._remaining_failures = (failures or DEFAULT_FLAKY_FAILURES) if mode == "FLAKY" else 0
            return self._state()

    def state(self) -> dict:
        with self._lock:
            return self._state()

    def next_fault(self) -> Fault | None:
        """Повертає збій для поточного запиту або None, якщо запит треба обслужити."""
        with self._lock:
            if self._mode == "FLAKY":
                if self._remaining_failures > 0:
                    self._remaining_failures -= 1
                    return _FLAKY_FAULT
                self._mode = "NONE"
                return None
            return _FAULTS.get(self._mode)

    def _state(self) -> dict:
        return {"mode": self._mode, "remainingFailures": self._remaining_failures, "availableModes": list(MODES)}
