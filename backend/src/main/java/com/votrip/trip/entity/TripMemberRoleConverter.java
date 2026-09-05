package com.votrip.trip.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Bridges {@link TripMemberRole} to the lowercase strings the V2 migration's CHECK expects. */
@Converter(autoApply = true)
public class TripMemberRoleConverter implements AttributeConverter<TripMemberRole, String> {

    @Override
    public String convertToDatabaseColumn(TripMemberRole attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public TripMemberRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TripMemberRole.fromValue(dbData);
    }
}
