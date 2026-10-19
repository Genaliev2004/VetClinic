package ru.vetclinic.dao;

import ru.vetclinic.db.Jdbc;
import ru.vetclinic.model.RefItem;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * DAO простого справочника «код — название». Один класс обслуживает
 * две таблицы с одинаковой структурой: species и specialization.
 * Имена таблиц и столбцов задаются только константами ниже, а не вводом
 * пользователя, поэтому подстановка их в текст SQL безопасна.
 */
public final class ReferenceDao {

    private final String table;
    private final String idColumn;
    private final String nameColumn;

    private ReferenceDao(String table, String idColumn, String nameColumn) {
        this.table = table;
        this.idColumn = idColumn;
        this.nameColumn = nameColumn;
    }

    /** Справочник видов животных. */
    public static ReferenceDao species() {
        return new ReferenceDao("species", "species_id", "species_name");
    }

    /** Справочник специализаций врачей. */
    public static ReferenceDao specializations() {
        return new ReferenceDao("specialization", "specialization_id", "specialization_name");
    }

    public List<RefItem> findAll(Connection c) throws SQLException {
        String sql = "SELECT " + idColumn + ", " + nameColumn + " FROM " + table + " ORDER BY " + nameColumn;
        return Jdbc.list(c, sql, ps -> { }, rs -> new RefItem(rs.getInt(1), rs.getString(2)));
    }

    public int insert(Connection c, String name) throws SQLException {
        String sql = "INSERT INTO " + table + " (" + nameColumn + ") VALUES (?)";
        return Jdbc.insertReturningId(c, sql, ps -> ps.setString(1, name.trim()));
    }

    public int update(Connection c, RefItem item) throws SQLException {
        String sql = "UPDATE " + table + " SET " + nameColumn + " = ? WHERE " + idColumn + " = ?";
        return Jdbc.update(c, sql, ps -> {
            ps.setString(1, item.name().trim());
            ps.setInt(2, item.id());
        });
    }

    public int delete(Connection c, int id) throws SQLException {
        return Jdbc.update(c, "DELETE FROM " + table + " WHERE " + idColumn + " = ?", ps -> ps.setInt(1, id));
    }
}
