package ua.kpi.building_audit

import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.BuildingType
import ua.kpi.building_audit.domain.model.ElementKind
import ua.kpi.building_audit.domain.model.EnvelopeElement
import ua.kpi.building_audit.domain.model.Layer
import ua.kpi.building_audit.domain.model.RetrofitMeasure
import ua.kpi.building_audit.domain.model.Tariffs

/** Гуртожиток № 19 з тестового сервера: контрольний приклад звіту. */
val DORMITORY = Building(
    id = 2,
    name = "Гуртожиток № 19",
    address = "м. Київ, вул. Академіка Янгеля, 22",
    type = BuildingType.RESIDENTIAL,
    heatedAreaM2 = 3100.0,
    yearBuilt = 1968,
    auditorEmail = "o.kovalenko@energy-audit.ua",
    auditDate = "2026-08-30",
    envelope = listOf(
        EnvelopeElement(ElementKind.WALL, 1850.0, listOf(Layer("Цегла силікатна", 510.0, 0.81))),
        EnvelopeElement(ElementKind.ROOF, 620.0, listOf(Layer("Залізобетон", 220.0, 2.04), Layer("Керамзитовий гравій", 100.0, 0.21))),
        EnvelopeElement(ElementKind.FLOOR, 620.0, listOf(Layer("Залізобетон", 220.0, 2.04))),
        EnvelopeElement(ElementKind.WINDOW, 410.0, uValue = 2.8)
    )
)

val TARIFFS = Tariffs.FALLBACK.copy(
    measures = listOf(
        RetrofitMeasure("WALL_MW_100", "Мінвата 100 мм", ElementKind.WALL, 100.0, 0.038, null, 1450.0),
        RetrofitMeasure("ROOF_MW_200", "Мінвата 200 мм", ElementKind.ROOF, 200.0, 0.040, null, 980.0),
        RetrofitMeasure("WINDOW_LOW_E", "Вікна Low-E", ElementKind.WINDOW, null, null, 1.1, 6800.0)
    )
)
