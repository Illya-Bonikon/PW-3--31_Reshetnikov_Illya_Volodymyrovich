package ua.kpi.building_audit.domain

enum class SortOrder(val title: String) {
    SPECIFIC_LOSS("Тепловтрати"),
    ANNUAL_COST("Вартість"),
    AREA("Площа"),
    NAME("Назва")
}

/** Пошук, фільтр за класами й сортування реєстру. Однаково працює для будь-якого джерела даних. */
data class BuildingQuery(
    val text: String = "",
    val classes: Set<EnergyClass> = emptySet(),
    val sort: SortOrder = SortOrder.SPECIFIC_LOSS
) {
    fun apply(items: List<BuildingAssessment>): List<BuildingAssessment> {
        val needle = text.trim().lowercase()
        val filtered = items.filter { item ->
            (classes.isEmpty() || item.energyClass in classes) &&
                (needle.isEmpty() || needle in item.building.name.lowercase() || needle in item.building.address.lowercase())
        }
        return when (sort) {
            SortOrder.SPECIFIC_LOSS -> filtered.sortedByDescending { it.metrics.specificHeatLossWPerM2 }
            SortOrder.ANNUAL_COST -> filtered.sortedByDescending { it.annualCostUah }
            SortOrder.AREA -> filtered.sortedByDescending { it.building.heatedAreaM2 }
            SortOrder.NAME -> filtered.sortedBy { it.building.name.lowercase() }
        }
    }

    fun toggle(energyClass: EnergyClass): BuildingQuery =
        copy(classes = if (energyClass in classes) classes - energyClass else classes + energyClass)
}
