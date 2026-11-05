package ua.kpi.building_audit.domain

data class PortfolioSummary(
    val buildingCount: Int,
    val heatedAreaM2: Double,
    val annualHeatGcal: Double,
    val annualCostUah: Double,
    /** Питомі тепловтрати, зважені за площею оболонки. */
    val weightedSpecificLossWPerM2: Double,
    val classDistribution: Map<EnergyClass, Int>,
    val worst: BuildingAssessment?
) {
    companion object {
        val EMPTY = PortfolioSummary(0, 0.0, 0.0, 0.0, 0.0, emptyMap(), null)
    }
}

object PortfolioAggregator {
    fun summarize(items: List<BuildingAssessment>): PortfolioSummary {
        if (items.isEmpty()) return PortfolioSummary.EMPTY
        val envelopeArea = items.sumOf { it.metrics.envelopeAreaM2 }
        return PortfolioSummary(
            buildingCount = items.size,
            heatedAreaM2 = items.sumOf { it.building.heatedAreaM2 },
            annualHeatGcal = items.sumOf { it.metrics.annualHeatGcal },
            annualCostUah = items.sumOf { it.annualCostUah },
            weightedSpecificLossWPerM2 = items.sumOf { it.metrics.specificHeatLossWPerM2 * it.metrics.envelopeAreaM2 } / envelopeArea,
            classDistribution = items.groupingBy { it.energyClass }.eachCount().toSortedMap(),
            worst = items.maxByOrNull { it.metrics.specificHeatLossWPerM2 }
        )
    }
}
