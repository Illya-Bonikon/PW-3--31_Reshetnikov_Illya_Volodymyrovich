package ua.kpi.building_audit.domain

import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.Climate
import ua.kpi.building_audit.domain.model.EnvelopeElement
import ua.kpi.building_audit.domain.model.Tariffs

/** Теплотехнічні показники всієї оболонки будівлі. */
data class EnvelopeMetrics(
    val envelopeAreaM2: Double,
    /** Коефіцієнт теплопередачі оболонки H = Σ(Aᵢ / Rᵢ), Вт/К. */
    val heatTransferWPerK: Double,
    /** Приведений опір теплопередачі R = ΣA / H, м²·К/Вт. */
    val effectiveResistance: Double,
    val designHeatLossKw: Double,
    val specificHeatLossWPerM2: Double,
    val annualHeatGcal: Double
)

data class BuildingAssessment(
    val building: Building,
    val metrics: EnvelopeMetrics,
    val energyClass: EnergyClass,
    val annualCostUah: Double
)

object EnergyCalculator {
    private const val R_SI = 0.13 // опір тепловіддачі внутрішньої поверхні, м²·К/Вт
    private const val R_SE = 0.04 // опір тепловіддачі зовнішньої поверхні, м²·К/Вт
    private const val WATT_HOURS_PER_GCAL = 1_163_000.0
    private const val HOURS_PER_DAY = 24.0

    fun resistance(element: EnvelopeElement): Double =
        element.uValue?.let { 1.0 / it }
            ?: (R_SI + element.layers.sumOf { it.thicknessMm / 1000.0 / it.conductivity } + R_SE)

    fun metrics(envelope: List<EnvelopeElement>, climate: Climate): EnvelopeMetrics {
        require(envelope.isNotEmpty()) { "Оболонка будівлі не містить жодного елемента" }
        val area = envelope.sumOf { it.areaM2 }
        val heatTransfer = envelope.sumOf { it.areaM2 / resistance(it) }
        val deltaT = climate.designDeltaT
        return EnvelopeMetrics(
            envelopeAreaM2 = area,
            heatTransferWPerK = heatTransfer,
            effectiveResistance = area / heatTransfer,
            designHeatLossKw = heatTransfer * deltaT / 1000.0,
            specificHeatLossWPerM2 = heatTransfer * deltaT / area,
            annualHeatGcal = annualHeatGcal(heatTransfer, climate)
        )
    }

    /** Річна потреба в теплі через градусо-добу опалювального періоду. */
    fun annualHeatGcal(heatTransferWPerK: Double, climate: Climate): Double =
        heatTransferWPerK * climate.heatingDegreeDays * HOURS_PER_DAY / WATT_HOURS_PER_GCAL

    fun assess(building: Building, tariffs: Tariffs): BuildingAssessment {
        val metrics = metrics(building.envelope, tariffs.climate)
        return BuildingAssessment(
            building = building,
            metrics = metrics,
            energyClass = EnergyClass.of(metrics.specificHeatLossWPerM2),
            annualCostUah = metrics.annualHeatGcal * tariffs.heatPriceUahPerGcal
        )
    }
}
