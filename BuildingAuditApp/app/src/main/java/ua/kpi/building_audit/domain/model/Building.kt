package ua.kpi.building_audit.domain.model

enum class BuildingType(val title: String) {
    RESIDENTIAL("Житловий будинок"),
    OFFICE("Офісний центр"),
    INDUSTRIAL("Промислова споруда"),
    EDUCATIONAL("Заклад освіти")
}

enum class ElementKind(val title: String) {
    WALL("Зовнішні стіни"),
    ROOF("Покрівля"),
    FLOOR("Підлога над підвалом"),
    WINDOW("Вікна")
}

/** Шар непрозорого огородження: товщина в мм, теплопровідність λ у Вт/(м·К). */
data class Layer(
    val material: String,
    val thicknessMm: Double,
    val conductivity: Double
)

/**
 * Елемент огородження. Для вікон задається коефіцієнт теплопередачі U,
 * для стін, покрівлі й підлоги — перелік шарів.
 */
data class EnvelopeElement(
    val kind: ElementKind,
    val areaM2: Double,
    val layers: List<Layer> = emptyList(),
    val uValue: Double? = null
)

data class InspectionNote(
    val date: String,
    val inspector: String,
    val note: String
)

data class Building(
    val id: Long,
    val name: String,
    val address: String,
    val type: BuildingType,
    val heatedAreaM2: Double,
    val yearBuilt: Int,
    val auditorEmail: String,
    val auditDate: String,
    val envelope: List<EnvelopeElement>,
    val inspectionLog: List<InspectionNote> = emptyList()
) {
    companion object {
        /** id ще не збереженого об'єкта. Записи, створені в локальному режимі, отримують від'ємні id. */
        const val NEW_ID = 0L
    }
}
