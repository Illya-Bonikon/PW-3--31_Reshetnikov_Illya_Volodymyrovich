package ua.kpi.building_audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.kpi.building_audit.domain.BuildingQuery
import ua.kpi.building_audit.domain.EnergyCalculator
import ua.kpi.building_audit.domain.EnergyClass
import ua.kpi.building_audit.domain.PortfolioAggregator
import ua.kpi.building_audit.domain.SavingsForecaster
import ua.kpi.building_audit.domain.SortOrder
import ua.kpi.building_audit.domain.model.ElementKind

class EnergyCalculatorTest {

    @Test
    fun wallResistanceIncludesSurfaceResistances() {
        // R = 0.13 + 0.51 / 0.81 + 0.04 = 0.800 м²·К/Вт
        assertEquals(0.800, EnergyCalculator.resistance(DORMITORY.envelope.first()), 0.001)
    }

    @Test
    fun windowResistanceIsInverseOfU() {
        assertEquals(1 / 2.8, EnergyCalculator.resistance(DORMITORY.envelope.last()), 1e-9)
    }

    @Test
    fun dormitoryControlExample() {
        val result = EnergyCalculator.assess(DORMITORY, TARIFFS)
        // H = 2313.6 + 822.2 + 2231.5 + 1148.0 = 6515.3 Вт/К
        assertEquals(6515.3, result.metrics.heatTransferWPerK, 0.5)
        assertEquals(228.0, result.metrics.designHeatLossKw, 0.1)
        assertEquals(65.15, result.metrics.specificHeatLossWPerM2, 0.05)
        assertEquals(470.6, result.metrics.annualHeatGcal, 0.1)
        assertEquals(EnergyClass.E, result.energyClass)
    }

    @Test
    fun wallInsulationForecast() {
        val forecast = SavingsForecaster.forecast(DORMITORY, TARIFFS.measures.first(), TARIFFS)!!
        assertEquals(128.2, forecast.annualSavingGcal, 0.1)
        assertEquals(2_682_500.0, forecast.investmentUah, 0.1)
        assertEquals(12.7, forecast.paybackYears!!, 0.1)
        assertEquals(EnergyClass.D, forecast.classAfter)
    }

    @Test
    fun forecastsAreSortedByPaybackAndSkipMissingElements() {
        val withoutWindows = DORMITORY.copy(envelope = DORMITORY.envelope.filter { it.kind != ElementKind.WINDOW })
        val forecasts = SavingsForecaster.forecastAll(withoutWindows, TARIFFS)
        assertEquals(listOf("ROOF_MW_200", "WALL_MW_100"), forecasts.map { it.measure.id })
        assertNull(SavingsForecaster.forecast(withoutWindows, TARIFFS.measures.last(), TARIFFS))
    }

    @Test
    fun queryFiltersAndSorts() {
        val good = DORMITORY.copy(id = 3, name = "Корпус Б", envelope = DORMITORY.envelope.take(1).map { it.copy(uValue = 0.25) })
        val items = listOf(DORMITORY, good).map { EnergyCalculator.assess(it, TARIFFS) }

        assertEquals(listOf(2L, 3L), BuildingQuery(sort = SortOrder.SPECIFIC_LOSS).apply(items).map { it.building.id })
        assertEquals(listOf(3L), BuildingQuery(text = "корпус").apply(items).map { it.building.id })
        assertEquals(listOf(2L), BuildingQuery(classes = setOf(EnergyClass.E)).apply(items).map { it.building.id })

        val summary = PortfolioAggregator.summarize(items)
        assertEquals(2, summary.buildingCount)
        assertEquals(2L, summary.worst?.building?.id)
    }
}
