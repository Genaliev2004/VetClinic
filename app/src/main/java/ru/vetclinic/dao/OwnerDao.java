package ru.vetclinic.dao;

import ru.vetclinic.db.Jdbc;
import ru.vetclinic.model.Owner;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/** DAO таблицы owner (владельцы питомцев). */
public class OwnerDao {

    private static final String SELECT = """
            SELECT owner_id, last_name, first_name, middle_name, phone, email, address, registered_on
            FROM owner
            """;

    /**
     * Список владельцев. Если задан текст поиска — ищет по началу/части фамилии
     * (без учёта регистра) и по части телефона.
     */
    public List<Owner> find(Connection c, String search) throws SQLException {
        if (search == null || search.isBlank()) {
            return Jdbc.list(c, SELECT + " ORDER BY last_name, first_name", ps -> { }, OwnerDao::map);
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        // LOWER(...) LIKE работает одинаково в PostgreSQL и SQL Server
        return Jdbc.list(c, SELECT + " WHERE LOWER(last_name) LIKE ? OR phone LIKE ? ORDER BY last_name, first_name",
                ps -> {
                    ps.setString(1, pattern);
                    ps.setString(2, pattern);
                }, OwnerDao::map);
    }

    public Owner findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, SELECT + " WHERE owner_id = ?", ps -> ps.setInt(1, id), OwnerDao::map);
    }

    public int insert(Connection c, Owner o) throws SQLException {
        // registered_on не передаём — сработает DEFAULT (текущая дата)
        return Jdbc.insertReturningId(c, """
                INSERT INTO owner (last_name, first_name, middle_name, phone, email, address)
                VALUES (?, ?, ?, ?, ?, ?)
                """, ps -> bind(ps, o));
    }

    public int update(Connection c, Owner o) throws SQLException {
        return Jdbc.update(c, """
                UPDATE owner
                SET last_name = ?, first_name = ?, middle_name = ?, phone = ?, email = ?, address = ?
                WHERE owner_id = ?
                """, ps -> {
            bind(ps, o);
            ps.setInt(7, o.id());
        });
    }

    public int delete(Connection c, int id) throws SQLException {
        return Jdbc.update(c, "DELETE FROM owner WHERE owner_id = ?", ps -> ps.setInt(1, id));
    }

    private static void bind(java.sql.PreparedStatement ps, Owner o) throws SQLException {
        Jdbc.setString(ps, 1, o.lastName());
        Jdbc.setString(ps, 2, o.firstName());
        Jdbc.setString(ps, 3, o.middleName());
        Jdbc.setString(ps, 4, o.phone());
        Jdbc.setString(ps, 5, o.email());
        Jdbc.setString(ps, 6, o.address());
    }

    private static Owner map(ResultSet rs) throws SQLException {
        return new Owner(rs.getInt("owner_id"), rs.getString("last_name"), rs.getString("first_name"),
                rs.getString("middle_name"), rs.getString("phone"), rs.getString("email"),
                rs.getString("address"), Jdbc.getDate(rs, "registered_on"));
    }
}
