package ru.vetclinic.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Вспомогательные методы JDBC: установка параметров с NULL,
 * чтение дат, выполнение запросов. Даты передаются через java.sql.Date/Timestamp —
 * это одинаково работает в драйверах PostgreSQL и SQL Server.
 */
public final class Jdbc {

    private Jdbc() {
    }

    /** Преобразование строки результата в объект. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** Установка параметров запроса. */
    @FunctionalInterface
    public interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    public static <T> List<T> list(Connection c, String sql, Binder binder, RowMapper<T> mapper) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        }
    }

    public static <T> T one(Connection c, String sql, Binder binder, RowMapper<T> mapper) throws SQLException {
        List<T> rows = list(c, sql, binder, mapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** INSERT/UPDATE/DELETE; возвращает число изменённых строк. */
    public static int update(Connection c, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        }
    }

    /**
     * INSERT с автогенерируемым ключом. Первичный ключ во всех таблицах — первый столбец,
     * поэтому getInt(1) подходит и для PostgreSQL (RETURNING *), и для SQL Server (SCOPE_IDENTITY).
     */
    public static int insertReturningId(Connection c, String sql, Binder binder) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(ps);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("СУБД не вернула сгенерированный ключ");
                }
                return keys.getInt(1);
            }
        }
    }

    // ------------------------------------------------------------ параметры

    public static void setString(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null || v.isBlank()) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            ps.setString(i, v.trim());
        }
    }

    public static void setDate(PreparedStatement ps, int i, LocalDate v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.DATE);
        } else {
            ps.setDate(i, Date.valueOf(v));
        }
    }

    public static void setTimestamp(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(i, Timestamp.valueOf(v));
        }
    }

    public static void setDecimal(PreparedStatement ps, int i, BigDecimal v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.DECIMAL);
        } else {
            ps.setBigDecimal(i, v);
        }
    }

    public static void setInteger(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.INTEGER);
        } else {
            ps.setInt(i, v);
        }
    }

    // ------------------------------------------------------------ чтение

    public static LocalDate getDate(ResultSet rs, String column) throws SQLException {
        Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    public static LocalDateTime getTimestamp(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }

    /** CHAR(n) в SQL Server возвращается с пробелами в конце — обрезаем. */
    public static String getTrimmed(ResultSet rs, String column) throws SQLException {
        String s = rs.getString(column);
        return s == null ? null : s.trim();
    }
}
