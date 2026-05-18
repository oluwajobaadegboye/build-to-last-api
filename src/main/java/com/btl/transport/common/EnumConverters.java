package com.btl.transport.common;

import com.btl.transport.common.enums.*;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converters to bridge Java uppercase enum names and PostgreSQL lowercase enum values.
 * autoApply = true means no @Convert annotation is needed on entity fields.
 */
public final class EnumConverters {

    private EnumConverters() {}

    abstract static class LowercaseEnumConverter<E extends Enum<E>>
        implements AttributeConverter<E, String> {

        private final Class<E> type;

        LowercaseEnumConverter(Class<E> type) {
            this.type = type;
        }

        @Override
        public String convertToDatabaseColumn(E attr) {
            return attr != null ? attr.name().toLowerCase() : null;
        }

        @Override
        public E convertToEntityAttribute(String db) {
            return db != null ? Enum.valueOf(type, db.toUpperCase()) : null;
        }
    }

    @Converter(autoApply = true)
    public static class ParticipantStatusConverter extends LowercaseEnumConverter<ParticipantStatus> {
        public ParticipantStatusConverter() { super(ParticipantStatus.class); }
    }

    @Converter(autoApply = true)
    public static class FlightStatusTypeConverter extends LowercaseEnumConverter<FlightStatusType> {
        public FlightStatusTypeConverter() { super(FlightStatusType.class); }
    }

    @Converter(autoApply = true)
    public static class Leg4PickupFromConverter extends LowercaseEnumConverter<Leg4PickupFrom> {
        public Leg4PickupFromConverter() { super(Leg4PickupFrom.class); }
    }

    @Converter(autoApply = true)
    public static class RunStatusEnumConverter extends LowercaseEnumConverter<RunStatusEnum> {
        public RunStatusEnumConverter() { super(RunStatusEnum.class); }
    }

    @Converter(autoApply = true)
    public static class RunTypeConverter extends LowercaseEnumConverter<RunType> {
        public RunTypeConverter() { super(RunType.class); }
    }

    @Converter(autoApply = true)
    public static class DirectionConverter extends LowercaseEnumConverter<Direction> {
        public DirectionConverter() { super(Direction.class); }
    }

    @Converter(autoApply = true)
    public static class ConferenceDayConverter extends LowercaseEnumConverter<ConferenceDay> {
        public ConferenceDayConverter() { super(ConferenceDay.class); }
    }

    @Converter(autoApply = true)
    public static class VehicleTypeConverter extends LowercaseEnumConverter<VehicleType> {
        public VehicleTypeConverter() { super(VehicleType.class); }
    }
}
