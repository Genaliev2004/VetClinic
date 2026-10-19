package ru.vetclinic.dao;

import ru.vetclinic.db.Jdbc;
import ru.vetclinic.model.Pet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** DAO таблицы pet (питомцы). */
public class PetDao {

    private static final String SELECT = """
            SELECT p.pet_id, p.owner_id, p.species_id, s.species_name, p.pet_name, p.breed,
                   p.sex, p.birth_date, p.weight_kg, p.chip_number
            FROM pet p
            JOIN species s ON s.species_id = p.species_id
            """;

    public List<Pet> findByOwner(Connection c, int ownerId) throws SQLException {
        return Jdbc.list(c, SELECT + " WHERE p.owner_id = ? ORDER BY p.pet_name",
                ps -> ps.setInt(1, ownerId), PetDao::map);
    }

    public Pet findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, SELECT + " WHERE p.pet_id = ?", ps -> ps.setInt(1, id), PetDao::map);
    }

    public int insert(Connection c, Pet p) throws SQLException {
        return Jdbc.insertReturningId(c, """
                INSERT INTO pet (owner_id, species_id, pet_name, breed, sex, birth_date, weight_kg, chip_number)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, ps -> bind(ps, p));
    }

    public int update(Connection c, Pet p) throws SQLException {
        return Jdbc.update(c, """
                UPDATE pet
                SET owner_id = ?, species_id = ?, pet_name = ?, breed = ?, sex = ?,
                    birth_date = ?, weight_kg = ?, chip_number = ?
                WHERE pet_id = ?
                """, ps -> {
            bind(ps, p);
            ps.setInt(9, p.id());
        });
    }

    public int delete(Connection c, int id) throws SQLException {
        return Jdbc.update(c, "DELETE FROM pet WHERE pet_id = ?", ps -> ps.setInt(1, id));
    }

    private static void bind(PreparedStatement ps, Pet p) throws SQLException {
        ps.setInt(1, p.ownerId());
        ps.setInt(2, p.speciesId());
        Jdbc.setString(ps, 3, p.name());
        Jdbc.setString(ps, 4, p.breed());
        Jdbc.setString(ps, 5, p.sex());
        Jdbc.setDate(ps, 6, p.birthDate());
        Jdbc.setDecimal(ps, 7, p.weightKg());
        Jdbc.setString(ps, 8, p.chipNumber());
    }

    private static Pet map(ResultSet rs) throws SQLException {
        return new Pet(rs.getInt("pet_id"), rs.getInt("owner_id"), rs.getInt("species_id"),
                rs.getString("species_name"), rs.getString("pet_name"), rs.getString("breed"),
                Jdbc.getTrimmed(rs, "sex"), Jdbc.getDate(rs, "birth_date"), rs.getBigDecimal("weight_kg"),
                Jdbc.getTrimmed(rs, "chip_number"));
    }
}
