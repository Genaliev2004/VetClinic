package ru.vetclinic.dao;

import ru.vetclinic.db.Jdbc;
import ru.vetclinic.model.Vet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** DAO таблицы vet (врачи). */
public class VetDao {

    private static final String SELECT = """
            SELECT v.vet_id, v.specialization_id, sp.specialization_name, v.last_name, v.first_name,
                   v.middle_name, v.phone, v.hire_date, v.is_active
            FROM vet v
            JOIN specialization sp ON sp.specialization_id = v.specialization_id
            """;

    /** Все врачи или только работающие (onlyActive = true). */
    public List<Vet> findAll(Connection c, boolean onlyActive) throws SQLException {
        if (onlyActive) {
            // setBoolean подходит и для BOOLEAN (PostgreSQL), и для BIT (SQL Server)
            return Jdbc.list(c, SELECT + " WHERE v.is_active = ? ORDER BY v.last_name, v.first_name",
                    ps -> ps.setBoolean(1, true), VetDao::map);
        }
        return Jdbc.list(c, SELECT + " ORDER BY v.last_name, v.first_name", ps -> { }, VetDao::map);
    }

    public Vet findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, SELECT + " WHERE v.vet_id = ?", ps -> ps.setInt(1, id), VetDao::map);
    }

    public int insert(Connection c, Vet v) throws SQLException {
        return Jdbc.insertReturningId(c, """
                INSERT INTO vet (specialization_id, last_name, first_name, middle_name, phone, hire_date, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, ps -> bind(ps, v));
    }

    public int update(Connection c, Vet v) throws SQLException {
        return Jdbc.update(c, """
                UPDATE vet
                SET specialization_id = ?, last_name = ?, first_name = ?, middle_name = ?,
                    phone = ?, hire_date = ?, is_active = ?
                WHERE vet_id = ?
                """, ps -> {
            bind(ps, v);
            ps.setInt(8, v.id());
        });
    }

    public int delete(Connection c, int id) throws SQLException {
        return Jdbc.update(c, "DELETE FROM vet WHERE vet_id = ?", ps -> ps.setInt(1, id));
    }

    private static void bind(PreparedStatement ps, Vet v) throws SQLException {
        ps.setInt(1, v.specializationId());
        Jdbc.setString(ps, 2, v.lastName());
        Jdbc.setString(ps, 3, v.firstName());
        Jdbc.setString(ps, 4, v.middleName());
        Jdbc.setString(ps, 5, v.phone());
        Jdbc.setDate(ps, 6, v.hireDate());
        ps.setBoolean(7, v.active());
    }

    private static Vet map(ResultSet rs) throws SQLException {
        return new Vet(rs.getInt("vet_id"), rs.getInt("specialization_id"), rs.getString("specialization_name"),
                rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name"),
                rs.getString("phone"), Jdbc.getDate(rs, "hire_date"), rs.getBoolean("is_active"));
    }
}
