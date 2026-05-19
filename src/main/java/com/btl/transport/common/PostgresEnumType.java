package com.btl.transport.common;

import com.btl.transport.common.enums.*;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;

/**
 * Hibernate 6 UserType base that binds enums via setObject(Types.OTHER),
 * allowing PostgreSQL to cast the string to the native enum column type.
 * AttributeConverter cannot do this — it always uses Types.VARCHAR.
 */
public abstract class PostgresEnumType<E extends Enum<E>> implements UserType<E> {

    private final Class<E> enumClass;

    protected PostgresEnumType(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<E> returnedClass() {
        return enumClass;
    }

    @Override
    public boolean equals(E x, E y) {
        return x == y;
    }

    @Override
    public int hashCode(E x) {
        return x == null ? 0 : x.hashCode();
    }

    @Override
    public E nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        String value = rs.getString(position);
        return (value == null || rs.wasNull()) ? null : Enum.valueOf(enumClass, value.toUpperCase());
    }

    @Override
    public void nullSafeSet(PreparedStatement st, E value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value.name().toLowerCase(), Types.OTHER);
        }
    }

    @Override
    public E deepCopy(E value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(E value) {
        return value == null ? null : value.name();
    }

    @Override
    public E assemble(Serializable cached, Object owner) {
        return cached == null ? null : Enum.valueOf(enumClass, (String) cached);
    }

    // ── Concrete types ─────────────────────────────────────────────────────

    public static final class ParticipantStatusPgType extends PostgresEnumType<ParticipantStatus> {
        public ParticipantStatusPgType() { super(ParticipantStatus.class); }
    }

    public static final class FlightStatusPgType extends PostgresEnumType<FlightStatusType> {
        public FlightStatusPgType() { super(FlightStatusType.class); }
    }

    public static final class Leg4PickupFromPgType extends PostgresEnumType<Leg4PickupFrom> {
        public Leg4PickupFromPgType() { super(Leg4PickupFrom.class); }
    }

    public static final class RunStatusPgType extends PostgresEnumType<RunStatusEnum> {
        public RunStatusPgType() { super(RunStatusEnum.class); }
    }

    public static final class RunTypePgType extends PostgresEnumType<RunType> {
        public RunTypePgType() { super(RunType.class); }
    }

    public static final class DirectionPgType extends PostgresEnumType<Direction> {
        public DirectionPgType() { super(Direction.class); }
    }

    public static final class ConferenceDayPgType extends PostgresEnumType<ConferenceDay> {
        public ConferenceDayPgType() { super(ConferenceDay.class); }
    }

    public static final class VehicleTypePgType extends PostgresEnumType<VehicleType> {
        public VehicleTypePgType() { super(VehicleType.class); }
    }
}
