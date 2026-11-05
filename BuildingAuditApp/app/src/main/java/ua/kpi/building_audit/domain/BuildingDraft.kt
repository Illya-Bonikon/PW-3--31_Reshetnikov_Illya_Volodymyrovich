package ua.kpi.building_audit.domain

import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.BuildingType
import ua.kpi.building_audit.domain.model.ElementKind
import ua.kpi.building_audit.domain.model.EnvelopeElement
import ua.kpi.building_audit.domain.model.Layer

/** Значення форми додавання об'єкта у вигляді рядків, як їх ввів користувач. */
data class BuildingDraft(
    val name: String = "",
    val address: String = "",
    val heatedArea: String = "",
    val auditorEmail: String = "",
    val wallArea: String = "",
    val insulationMm: String = "100"
) {
    /** Повертає текст помилки для кожного некоректного поля; порожня мапа означає, що форма валідна. */
    fun validate(): Map<String, String> = buildMap<String, String> {
        if (name.trim().length < 3) put("name", "Мінімум 3 символи")
        if (address.trim().length < 5) put("address", "Мінімум 5 символів")
        if (heatedArea.number()?.takeIf { it in 20.0..100_000.0 } == null) put("heatedArea", "Допустимо 20..100000 м²")
        if (!EMAIL.matches(auditorEmail.trim())) put("auditorEmail", "Некоректна електронна адреса")
        if (wallArea.number()?.takeIf { it > 0 } == null) put("wallArea", "Площа стін має бути додатною")
        if (insulationMm.number()?.takeIf { it in 0.0..500.0 } == null) put("insulationMm", "Допустимо 0..500 мм")
    }

    /**
     * Спрощена оболонка: стіна з цегли з утеплювачем і вікна, що займають 20 % площі стін.
     * Детальну оболонку з шарами повертає сервер для наявних будівель.
     */
    fun toBuilding(auditDate: String): Building {
        val wall = wallArea.number()!!
        val insulation = insulationMm.number()!!
        val wallLayers = listOf(Layer("Цегла силікатна", 510.0, 0.81)) +
            if (insulation > 0) listOf(Layer("Мінеральна вата", insulation, 0.038)) else emptyList()
        return Building(
            id = Building.NEW_ID,
            name = name.trim(),
            address = address.trim(),
            type = BuildingType.RESIDENTIAL,
            heatedAreaM2 = heatedArea.number()!!,
            yearBuilt = 2000,
            auditorEmail = auditorEmail.trim(),
            auditDate = auditDate,
            envelope = listOf(
                EnvelopeElement(ElementKind.WALL, wall, wallLayers),
                EnvelopeElement(ElementKind.WINDOW, wall * 0.2, uValue = 1.3)
            )
        )
    }

    private fun String.number(): Double? = trim().replace(',', '.').toDoubleOrNull()

    private companion object {
        val EMAIL = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
