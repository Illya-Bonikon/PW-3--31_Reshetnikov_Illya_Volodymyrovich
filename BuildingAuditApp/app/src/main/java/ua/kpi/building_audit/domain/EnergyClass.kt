package ua.kpi.building_audit.domain

/**
 * Клас енергоефективності за питомими тепловтратами огороджень q = ΔT / R (Вт/м²).
 * Межі ті самі, що й у калькуляторі ПР2, тому класи будівель між роботами збігаються.
 */
enum class EnergyClass(val label: String, val upperBoundWPerM2: Double) {
    A_PLUS("A+", 12.0),
    A("A", 18.0),
    B("B", 28.0),
    C("C", 42.0),
    D("D", 60.0),
    E("E", 80.0),
    F("F", Double.POSITIVE_INFINITY);

    companion object {
        fun of(specificHeatLossWPerM2: Double): EnergyClass =
            entries.first { specificHeatLossWPerM2 <= it.upperBoundWPerM2 }
    }
}
