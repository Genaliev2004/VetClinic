package ru.vetclinic.dao;

import ru.vetclinic.db.Jdbc;
import ru.vetclinic.model.MedicalService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** DAO таблицы service (прайс услуг). */
public class ServiceDao {

    private static final String SELECT = "SELECT service_id, service_name, price, is_active FROM service";

    public List<MedicalService> findAll(Connection c, boolean onlyActive) throws SQLException {
        if (onlyActive) {
            return Jdbc.list(c, SELECT + " WHERE is_active = ? ORDER BY service_name",
                    ps -> ps.setBoolean(1, true), ServiceDao::map);
        }
        return Jdbc.list(c, SELECT + " ORDER BY service_name", ps -> { }, ServiceDao::map);
    }

    public MedicalService findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, SELECT + " WHERE service_id = ?", ps -> ps.setInt(1, id), ServiceDao::map);
    }

    public int insert(Connection c, MedicalService s) throws SQLException {
        return Jdbc.insertReturningId(c, "INSERT INTO service (service_name, price, is_active) VALUES (?, ?, ?)",
                ps -> {
                    Jdbc.setString(ps, 1, s.name());
                    Jdbc.setDecimal(ps, 2, s.price());
                    ps.setBoolean(3, s.active());
                });
    }

    public int update(Connection c, MedicalService s) throws SQLException {
        return Jdbc.update(c, "UPDATE service SET service_name = ?, price = ?, is_active = ? WHERE service_id = ?",
                ps -> {
                    Jdbc.setString(ps, 1, s.name());
                    Jdbc.setDecimal(ps, 2, s.price());
                    ps.setBoolean(3, s.active());
                    ps.setInt(4, s.id());
                });
    }

    public int delete(Connection c, int id) throws SQLException {
        return Jdbc.update(c, "DELETE FROM service WHERE service_id = ?", ps -> ps.setInt(1, id));
    }

    private static MedicalService map(ResultSet rs) throws SQLException {
        return new MedicalService(rs.getInt("service_id"), rs.getString("service_name"),
                rs.getBigDecimal("price"), rs.getBoolean("is_active"));
    }
}
