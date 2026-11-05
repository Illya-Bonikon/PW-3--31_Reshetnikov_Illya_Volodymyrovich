package ua.kpi.building_audit.domain.model

data class Climate(
    val city: String,
    val indoorTempC: Double,
    val outdoorDesignTempC: Double,
    val heatingDegreeDays: Double
) {
    val designDeltaT: Double get() = indoorTempC - outdoorDesignTempC
}

/**
 * Захід термомодернізації: або додатковий шар утеплювача, або заміна вікон на нове значення U.
 */
data class RetrofitMeasure(
    val id: String,
    val title: String,
    val target: ElementKind,
    val thicknessMm: Double?,
    val conductivity: Double?,
    val newUValue: Double?,
    val priceUahPerM2: Double
)

data class Tariffs(
    val heatPriceUahPerGcal: Double,
    val validFrom: String,
    val climate: Climate,
    val measures: List<RetrofitMeasure>
) {
    companion object {
        /** Довідкові значення на випадок першого запуску без мережі й без кешу. */
        val FALLBACK = Tariffs(
            heatPriceUahPerGcal = 1654.06,
            validFrom = "довідкове значення",
            climate = Climate("Київ", indoorTempC = 20.0, outdoorDesignTempC = -15.0, heatingDegreeDays = 3500.0),
            measures = emptyList()
        )
    }
}
