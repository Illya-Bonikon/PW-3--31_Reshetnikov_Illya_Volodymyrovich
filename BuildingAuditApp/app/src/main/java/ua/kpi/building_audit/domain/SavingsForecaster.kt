package ua.kpi.building_audit.domain

import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.EnvelopeElement
import ua.kpi.building_audit.domain.model.Layer
import ua.kpi.building_audit.domain.model.RetrofitMeasure
import ua.kpi.building_audit.domain.model.Tariffs

data class SavingsForecast(
    val measure: RetrofitMeasure,
    val affectedAreaM2: Double,
    val annualSavingGcal: Double,
    val annualSavingUah: Double,
    val investmentUah: Double,
    /** null, якщо захід не дає економії і не окупається. */
    val paybackYears: Double?,
    val classBefore: EnergyClass,
    val classAfter: EnergyClass
)

/** Прогноз економії від заходів термомодернізації, які сервер повертає разом із тарифами. */
object SavingsForecaster {

    fun forecastAll(building: Building, tariffs: Tariffs): List<SavingsForecast> =
        tariffs.measures
            .mapNotNull { forecast(building, it, tariffs) }
            .sortedBy { it.paybackYears ?: Double.MAX_VALUE }

    fun forecast(building: Building, measure: RetrofitMeasure, tariffs: Tariffs): SavingsForecast? {
        val affectedArea = building.envelope.filter { it.kind == measure.target }.sumOf { it.areaM2 }
        if (affectedArea <= 0.0) return null

        val before = EnergyCalculator.metrics(building.envelope, tariffs.climate)
        val improved = building.envelope.map { if (it.kind == measure.target) apply(it, measure) else it }
        val after = EnergyCalculator.metrics(improved, tariffs.climate)

        val savedGcal = before.annualHeatGcal - after.annualHeatGcal
        val savedUah = savedGcal * tariffs.heatPriceUahPerGcal
        val investment = affectedArea * measure.priceUahPerM2
        return SavingsForecast(
            measure = measure,
            affectedAreaM2 = affectedArea,
            annualSavingGcal = savedGcal,
            annualSavingUah = savedUah,
            investmentUah = investment,
            paybackYears = if (savedUah > 0.0) investment / savedUah else null,
            classBefore = EnergyClass.of(before.specificHeatLossWPerM2),
            classAfter = EnergyClass.of(after.specificHeatLossWPerM2)
        )
    }

    private fun apply(element: EnvelopeElement, measure: RetrofitMeasure): EnvelopeElement = when {
        measure.newUValue != null -> element.copy(uValue = measure.newUValue, layers = emptyList())
        measure.thicknessMm != null && measure.conductivity != null ->
            element.copy(layers = element.layers + Layer(measure.title, measure.thicknessMm, measure.conductivity))
        else -> element
    }
}
