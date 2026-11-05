package ua.kpi.building_audit.data.remote

// Усі поля DTO nullable: Gson не перевіряє nullability Kotlin, тому перевірка робиться явно в маперах.

data class LoginRequestDto(val login: String, val password: String, val ttlSeconds: Int? = null)

data class LoginResponseDto(val accessToken: String?, val expiresAt: Long?, val user: UserDto?)

data class UserDto(val login: String?, val fullName: String?, val role: String?)

data class LayerDto(val material: String?, val thicknessMm: Double?, val conductivity: Double?)

data class EnvelopeElementDto(val kind: String?, val areaM2: Double?, val uValue: Double?, val layers: List<LayerDto>?)

data class InspectionNoteDto(val date: String?, val inspector: String?, val note: String?)

data class BuildingDto(
    val id: Long?,
    val name: String?,
    val address: String?,
    val type: String?,
    val heatedAreaM2: Double?,
    val yearBuilt: Int?,
    val auditorEmail: String?,
    val auditDate: String?,
    val envelope: List<EnvelopeElementDto>?,
    val inspectionLog: List<InspectionNoteDto>?
)

data class ClimateDto(val city: String?, val indoorTempC: Double?, val outdoorDesignTempC: Double?, val heatingDegreeDays: Double?)

data class MeasureDto(
    val id: String?,
    val title: String?,
    val target: String?,
    val thicknessMm: Double?,
    val conductivity: Double?,
    val newUValue: Double?,
    val priceUahPerM2: Double?
)

data class TariffsDto(val heatPriceUahPerGcal: Double?, val validFrom: String?, val climate: ClimateDto?, val measures: List<MeasureDto>?)

data class ChaosRequestDto(val mode: String, val failures: Int? = null)

data class ChaosStateDto(val mode: String?, val remainingFailures: Int?)

data class ErrorBodyDto(val code: String?, val message: String?, val fields: Map<String, String>?)

data class ErrorEnvelopeDto(val error: ErrorBodyDto?)
