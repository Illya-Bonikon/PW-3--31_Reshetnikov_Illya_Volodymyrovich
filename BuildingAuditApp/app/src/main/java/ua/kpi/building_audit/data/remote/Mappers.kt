package ua.kpi.building_audit.data.remote

import ua.kpi.building_audit.domain.model.Building
import ua.kpi.building_audit.domain.model.BuildingType
import ua.kpi.building_audit.domain.model.Climate
import ua.kpi.building_audit.domain.model.ElementKind
import ua.kpi.building_audit.domain.model.EnvelopeElement
import ua.kpi.building_audit.domain.model.InspectionNote
import ua.kpi.building_audit.domain.model.Layer
import ua.kpi.building_audit.domain.model.RetrofitMeasure
import ua.kpi.building_audit.domain.model.Tariffs

/** Відповідь сервера не відповідає очікуваній структурі. */
class InvalidPayloadException(message: String) : Exception(message)

private fun <T> required(value: T?, field: String): T = value ?: throw InvalidPayloadException("Відсутнє поле $field")

private inline fun <reified E : Enum<E>> enumOf(value: String?, field: String): E =
    enumValues<E>().firstOrNull { it.name == value } ?: throw InvalidPayloadException("Невідоме значення $field: $value")

fun BuildingDto.toDomain(): Building {
    val envelope = required(envelope, "envelope").map { it.toDomain() }
    if (envelope.isEmpty()) throw InvalidPayloadException("Будівля без огороджувальних конструкцій")
    return Building(
        id = required(id, "id"),
        name = required(name, "name"),
        address = required(address, "address"),
        type = enumOf(type, "type"),
        heatedAreaM2 = required(heatedAreaM2, "heatedAreaM2"),
        yearBuilt = required(yearBuilt, "yearBuilt"),
        auditorEmail = auditorEmail.orEmpty(),
        auditDate = auditDate.orEmpty(),
        envelope = envelope,
        inspectionLog = inspectionLog.orEmpty().map { InspectionNote(it.date.orEmpty(), it.inspector.orEmpty(), it.note.orEmpty()) }
    )
}

private fun EnvelopeElementDto.toDomain(): EnvelopeElement {
    val kind = enumOf<ElementKind>(kind, "kind")
    val area = required(areaM2, "areaM2")
    if (area <= 0) throw InvalidPayloadException("Площа огородження має бути додатною")
    val layers = layers.orEmpty().map {
        Layer(it.material.orEmpty(), required(it.thicknessMm, "thicknessMm"), required(it.conductivity, "conductivity"))
    }
    if (uValue == null && layers.isEmpty()) throw InvalidPayloadException("Огородження $kind без шарів і без U")
    return EnvelopeElement(kind, area, layers, uValue)
}

fun Building.toDto() = BuildingDto(
    id = id,
    name = name,
    address = address,
    type = type.name,
    heatedAreaM2 = heatedAreaM2,
    yearBuilt = yearBuilt,
    auditorEmail = auditorEmail,
    auditDate = auditDate,
    envelope = envelope.map { element ->
        EnvelopeElementDto(element.kind.name, element.areaM2, element.uValue, element.layers.map { LayerDto(it.material, it.thicknessMm, it.conductivity) })
    },
    inspectionLog = inspectionLog.map { InspectionNoteDto(it.date, it.inspector, it.note) }
)

fun TariffsDto.toDomain(): Tariffs {
    val climate = required(climate, "climate")
    return Tariffs(
        heatPriceUahPerGcal = required(heatPriceUahPerGcal, "heatPriceUahPerGcal"),
        validFrom = validFrom.orEmpty(),
        climate = Climate(
            city = climate.city.orEmpty(),
            indoorTempC = required(climate.indoorTempC, "indoorTempC"),
            outdoorDesignTempC = required(climate.outdoorDesignTempC, "outdoorDesignTempC"),
            heatingDegreeDays = required(climate.heatingDegreeDays, "heatingDegreeDays")
        ),
        measures = measures.orEmpty().map {
            RetrofitMeasure(
                id = required(it.id, "measure.id"),
                title = it.title.orEmpty(),
                target = enumOf(it.target, "measure.target"),
                thicknessMm = it.thicknessMm,
                conductivity = it.conductivity,
                newUValue = it.newUValue,
                priceUahPerM2 = required(it.priceUahPerM2, "priceUahPerM2")
            )
        }
    )
}
