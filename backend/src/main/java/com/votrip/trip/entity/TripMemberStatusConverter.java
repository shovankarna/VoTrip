package com.votrip.trip.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Bridges {@link TripMemberStatus} to the lowercase strings the V2 migration's CHECK expects. */
@Converter(autoApply = true)
public class TripMemberStatusConverter implements AttributeConverter<TripMemberStatus, String> {

    @Override
    public String convertToDatabaseColumn(TripMemberStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public TripMemberStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TripMemberStatus.fromValue(dbData);
    }
}
