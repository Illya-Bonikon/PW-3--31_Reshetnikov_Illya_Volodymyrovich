"""Сховище будівель у пам'яті процесу та довідник тарифів.

Дані тестові: при кожному запуску сервер стартує з однакового набору об'єктів,
тому результати на емуляторі відтворюються.
"""

import copy
import re
import threading

BUILDING_TYPES = {"RESIDENTIAL", "OFFICE", "INDUSTRIAL", "EDUCATIONAL"}
ELEMENT_KINDS = {"WALL", "ROOF", "FLOOR", "WINDOW"}
EMAIL_PATTERN = re.compile(r"^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$")

BRICK = ("Цегла силікатна", 0.81)
AEROCRETE = ("Газобетон автоклавний", 0.20)
CONCRETE = ("Залізобетон", 2.04)
MINERAL_WOOL = ("Мінеральна вата", 0.038)
EPS = ("Пінополістирол", 0.039)
CLAYDITE = ("Керамзитовий гравій", 0.21)
SCREED = ("Цементно-піщана стяжка", 0.93)


def _layer(material: tuple[str, float], thickness_mm: int) -> dict:
    return {"material": material[0], "thicknessMm": thickness_mm, "conductivity": material[1]}


def _opaque(kind: str, area: float, *layers: dict) -> dict:
    return {"kind": kind, "areaM2": area, "uValue": None, "layers": list(layers)}


def _window(area: float, u_value: float) -> dict:
    return {"kind": "WINDOW", "areaM2": area, "uValue": u_value, "layers": []}


SEED_BUILDINGS = [
    {
        "id": 1,
        "name": "Навчальний корпус № 18 КПІ",
        "address": "м. Київ, вул. Політехнічна, 41",
        "type": "EDUCATIONAL",
        "heatedAreaM2": 4200.0,
        "yearBuilt": 1972,
        "auditorEmail": "o.kovalenko@energy-audit.ua",
        "auditDate": "2026-09-14",
        "envelope": [
            _opaque("WALL", 2100.0, _layer(BRICK, 510), _layer(MINERAL_WOOL, 100)),
            _opaque("ROOF", 950.0, _layer(CONCRETE, 220), _layer(CLAYDITE, 150)),
            _opaque("FLOOR", 950.0, _layer(CONCRETE, 220), _layer(SCREED, 50)),
            _window(640.0, 1.3),
        ],
        "inspectionLog": [
            {"date": "2026-09-14", "inspector": "Коваленко О.", "note": "Фасад утеплено у 2021 році, містки холоду на відкосах"},
            {"date": "2026-02-03", "inspector": "Коваленко О.", "note": "Тепловізійне обстеження покрівлі: зволоження керамзиту"},
        ],
    },
    {
        "id": 2,
        "name": "Гуртожиток № 19",
        "address": "м. Київ, вул. Академіка Янгеля, 22",
        "type": "RESIDENTIAL",
        "heatedAreaM2": 3100.0,
        "yearBuilt": 1968,
        "auditorEmail": "o.kovalenko@energy-audit.ua",
        "auditDate": "2026-08-30",
        "envelope": [
            _opaque("WALL", 1850.0, _layer(BRICK, 510)),
            _opaque("ROOF", 620.0, _layer(CONCRETE, 220), _layer(CLAYDITE, 100)),
            _opaque("FLOOR", 620.0, _layer(CONCRETE, 220)),
            _window(410.0, 2.8),
        ],
        "inspectionLog": [
            {"date": "2026-08-30", "inspector": "Коваленко О.", "note": "Дерев'яні вікна з однокамерним склопакетом, щілини у притворах"},
        ],
    },
    {
        "id": 3,
        "name": "ЖК «Світанок»",
        "address": "м. Київ, вул. Польова, 14",
        "type": "RESIDENTIAL",
        "heatedAreaM2": 2450.0,
        "yearBuilt": 2019,
        "auditorEmail": "i.melnyk@energy-audit.ua",
        "auditDate": "2026-10-12",
        "envelope": [
            _opaque("WALL", 1320.0, _layer(AEROCRETE, 375), _layer(MINERAL_WOOL, 150)),
            _opaque("ROOF", 410.0, _layer(CONCRETE, 200), _layer(MINERAL_WOOL, 250)),
            _opaque("FLOOR", 410.0, _layer(CONCRETE, 200), _layer(EPS, 100)),
            _window(290.0, 0.8),
        ],
        "inspectionLog": [
            {"date": "2026-10-12", "inspector": "Мельник І.", "note": "Відповідає вимогам ДБН В.2.6-31, зауважень немає"},
        ],
    },
    {
        "id": 4,
        "name": "Бізнес-центр «Енерго-Плаза»",
        "address": "м. Київ, просп. Берестейський, 37",
        "type": "OFFICE",
        "heatedAreaM2": 5800.0,
        "yearBuilt": 2006,
        "auditorEmail": "i.melnyk@energy-audit.ua",
        "auditDate": "2026-10-14",
        "envelope": [
            _opaque("WALL", 2600.0, _layer(CONCRETE, 300), _layer(EPS, 50)),
            _opaque("ROOF", 1100.0, _layer(CONCRETE, 250), _layer(MINERAL_WOOL, 120)),
            _opaque("FLOOR", 1100.0, _layer(CONCRETE, 250), _layer(EPS, 50)),
            _window(1450.0, 1.6),
        ],
        "inspectionLog": [
            {"date": "2026-10-14", "inspector": "Мельник І.", "note": "Суцільне скління південного фасаду, перегрів улітку"},
            {"date": "2025-12-20", "inspector": "Мельник І.", "note": "Тонкий шар пінополістиролу, рекомендовано донутеплення"},
        ],
    },
    {
        "id": 5,
        "name": "Технопарк «Політехнічний»",
        "address": "м. Київ, вул. Борщагівська, 115",
        "type": "INDUSTRIAL",
        "heatedAreaM2": 1200.0,
        "yearBuilt": 1985,
        "auditorEmail": "o.kovalenko@energy-audit.ua",
        "auditDate": "2026-07-21",
        "envelope": [
            _opaque("WALL", 980.0, _layer(CONCRETE, 300), _layer(AEROCRETE, 200)),
            _opaque("ROOF", 600.0, _layer(CONCRETE, 200)),
            _opaque("FLOOR", 600.0, _layer(CONCRETE, 200), _layer(SCREED, 40)),
            _window(220.0, 2.8),
        ],
        "inspectionLog": [
            {"date": "2026-07-21", "inspector": "Коваленко О.", "note": "Неутеплене покриття, конденсат на внутрішній поверхні"},
        ],
    },
    {
        "id": 6,
        "name": "Дитячий садок № 512",
        "address": "м. Київ, вул. Метробудівська, 7",
        "type": "EDUCATIONAL",
        "heatedAreaM2": 1650.0,
        "yearBuilt": 1979,
        "auditorEmail": "i.melnyk@energy-audit.ua",
        "auditDate": "2026-06-05",
        "envelope": [
            _opaque("WALL", 1150.0, _layer(BRICK, 380), _layer(EPS, 80)),
            _opaque("ROOF", 720.0, _layer(CONCRETE, 220), _layer(MINERAL_WOOL, 150)),
            _opaque("FLOOR", 720.0, _layer(CONCRETE, 220), _layer(SCREED, 50)),
            _window(260.0, 1.3),
        ],
        "inspectionLog": [
            {"date": "2026-06-05", "inspector": "Мельник І.", "note": "Після часткової термомодернізації 2018 року"},
        ],
    },
]

TARIFFS = {
    "currency": "UAH",
    "heatPriceUahPerGcal": 1654.06,
    "validFrom": "2026-10-01",
    "climate": {
        "city": "Київ",
        "indoorTempC": 20.0,
        "outdoorDesignTempC": -15.0,
        "heatingDegreeDays": 3500.0,
    },
    "measures": [
        {"id": "WALL_MW_100", "title": "Утеплення фасаду мінеральною ватою 100 мм", "target": "WALL",
         "thicknessMm": 100, "conductivity": 0.038, "newUValue": None, "priceUahPerM2": 1450.0},
        {"id": "WALL_EPS_150", "title": "Утеплення фасаду пінополістиролом 150 мм", "target": "WALL",
         "thicknessMm": 150, "conductivity": 0.039, "newUValue": None, "priceUahPerM2": 1250.0},
        {"id": "ROOF_MW_200", "title": "Утеплення покрівлі мінеральною ватою 200 мм", "target": "ROOF",
         "thicknessMm": 200, "conductivity": 0.040, "newUValue": None, "priceUahPerM2": 980.0},
        {"id": "WINDOW_LOW_E", "title": "Заміна вікон на двокамерні з Low-E склом", "target": "WINDOW",
         "thicknessMm": None, "conductivity": None, "newUValue": 1.1, "priceUahPerM2": 6800.0},
    ],
}


class ValidationError(Exception):
    def __init__(self, fields: dict[str, str]):
        super().__init__("Дані будівлі не пройшли перевірку")
        self.fields = fields


def validate_building(payload: object) -> dict:
    """Повертає нормалізовану копію або кидає ValidationError зі списком полів."""
    if not isinstance(payload, dict):
        raise ValidationError({"body": "Очікується JSON-об'єкт"})
    errors: dict[str, str] = {}

    def text(field: str, min_len: int) -> str:
        value = payload.get(field)
        if not isinstance(value, str) or len(value.strip()) < min_len:
            errors[field] = f"Мінімум {min_len} символи"
            return ""
        return value.strip()

    def number(field: str, low: float, high: float) -> float:
        value = payload.get(field)
        if not isinstance(value, (int, float)) or isinstance(value, bool) or not low <= value <= high:
            errors[field] = f"Допустимий діапазон {low:g}..{high:g}"
            return 0.0
        return float(value)

    result = {
        "name": text("name", 3),
        "address": text("address", 5),
        "type": payload.get("type"),
        "heatedAreaM2": number("heatedAreaM2", 20, 100_000),
        "yearBuilt": int(number("yearBuilt", 1850, 2026)),
        "auditorEmail": payload.get("auditorEmail"),
        "auditDate": payload.get("auditDate") or "",
        "envelope": [],
    }
    if result["type"] not in BUILDING_TYPES:
        errors["type"] = "Невідомий тип будівлі"
    if not isinstance(result["auditorEmail"], str) or not EMAIL_PATTERN.match(result["auditorEmail"]):
        errors["auditorEmail"] = "Некоректна електронна адреса"

    envelope = payload.get("envelope")
    if not isinstance(envelope, list) or not envelope:
        errors["envelope"] = "Потрібен хоча б один елемент огородження"
    else:
        for index, element in enumerate(envelope):
            problem = _element_problem(element)
            if problem:
                errors[f"envelope[{index}]"] = problem
            else:
                result["envelope"].append(copy.deepcopy(element))

    if errors:
        raise ValidationError(errors)
    return result


def _element_problem(element: object) -> str | None:
    if not isinstance(element, dict) or element.get("kind") not in ELEMENT_KINDS:
        return "Невідомий тип огородження"
    area = element.get("areaM2")
    if not isinstance(area, (int, float)) or area <= 0:
        return "Площа має бути додатною"
    if element["kind"] == "WINDOW":
        u_value = element.get("uValue")
        if not isinstance(u_value, (int, float)) or not 0.5 <= u_value <= 6.0:
            return "Коефіцієнт U вікна має бути в межах 0.5..6.0"
        return None
    layers = element.get("layers")
    if not isinstance(layers, list) or not layers:
        return "Непрозоре огородження потребує хоча б одного шару"
    for layer in layers:
        if not isinstance(layer, dict):
            return "Шар має бути об'єктом"
        thickness, conductivity = layer.get("thicknessMm"), layer.get("conductivity")
        if not isinstance(thickness, (int, float)) or not 1 <= thickness <= 1000:
            return "Товщина шару має бути в межах 1..1000 мм"
        if not isinstance(conductivity, (int, float)) or not 0.01 <= conductivity <= 5.0:
            return "Теплопровідність має бути в межах 0.01..5.0 Вт/(м·К)"
    return None


class BuildingStore:
    def __init__(self, seed: list[dict] = SEED_BUILDINGS):
        self._lock = threading.Lock()
        self._items = {item["id"]: copy.deepcopy(item) for item in seed}
        self._next_id = max(self._items, default=0) + 1

    def list_summaries(self) -> list[dict]:
        with self._lock:
            return [self._summary(item) for item in sorted(self._items.values(), key=lambda b: b["id"])]

    def get(self, building_id: int) -> dict | None:
        with self._lock:
            item = self._items.get(building_id)
            return copy.deepcopy(item) if item else None

    def create(self, data: dict) -> dict:
        with self._lock:
            item = {"id": self._next_id, **data, "inspectionLog": []}
            self._items[item["id"]] = item
            self._next_id += 1
            return copy.deepcopy(item)

    def update(self, building_id: int, data: dict) -> dict | None:
        with self._lock:
            current = self._items.get(building_id)
            if current is None:
                return None
            current.update(data)
            return copy.deepcopy(current)

    def delete(self, building_id: int) -> bool:
        with self._lock:
            return self._items.pop(building_id, None) is not None

    @staticmethod
    def _summary(item: dict) -> dict:
        # Журнал обстежень віддається лише в деталях, щоб список залишався легким.
        return {key: copy.deepcopy(value) for key, value in item.items() if key != "inspectionLog"}
