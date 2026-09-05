package com.votrip.trip.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Bridges {@link TripStatus} to the lowercase strings the V2 migration's CHECK expects. */
@Converter(autoApply = true)
public class TripStatusConverter implements AttributeConverter<TripStatus, String> {

    @Override
    public String convertToDatabaseColumn(TripStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public TripStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TripStatus.fromValue(dbData);
    }
}
