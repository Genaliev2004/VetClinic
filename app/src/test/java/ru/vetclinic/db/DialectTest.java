package ru.vetclinic.db;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Модульные тесты перевода ошибок СУБД в сообщения для пользователя.
 * Тексты исключений взяты из реальных ответов PostgreSQL 18 и SQL Server 2022.
 */
class DialectTest {

    private final Dialect pg = new PostgresDialect();
    private final Dialect ms = new MssqlDialect();

    @Test
    void dialectByProfile() {
        assertInstanceOf(PostgresDialect.class, Dialect.forProfile("postgresql"));
        assertInstanceOf(MssqlDialect.class, Dialect.forProfile("mssql"));
        assertThrows(IllegalArgumentException.class, () -> Dialect.forProfile("oracle"));
    }

    @Test
    void postgresTriggerMessageWithoutPrefixAndContext() {
        SQLException e = new SQLException("ОШИБКА: Врач не работает в клинике: запись к нему невозможна\n"
                + "  Где: функция PL/pgSQL fn_appointment_rules(), строка 10, оператор RAISE", "P0001");
        assertEquals("Врач не работает в клинике: запись к нему невозможна", pg.userMessage(e));
        SQLException en = new SQLException("ERROR: Приём отменён: добавлять в него услуги нельзя", "P0001");
        assertEquals("Приём отменён: добавлять в него услуги нельзя", pg.userMessage(en));
    }

    @Test
    void mssqlTriggerMessageAsIs() {
        SQLException e = new SQLException("Услуга больше не оказывается: добавить её нельзя", "S0001", 50005);
        assertEquals("Услуга больше не оказывается: добавить её нельзя", ms.userMessage(e));
    }

    @Test
    void uniqueConstraintIsRecognisedInBothDbms() {
        SQLException pgError = new SQLException("ОШИБКА: повторяющееся значение ключа нарушает ограничение "
                + "уникальности \"uq_owner_phone\"", "23505");
        SQLException msError = new SQLException("Нарушено \"uq_owner_phone\" ограничения UNIQUE KEY.", "23000", 2627);
        String expected = "Владелец с таким телефоном уже зарегистрирован.";
        assertEquals(expected, pg.userMessage(pgError));
        assertEquals(expected, ms.userMessage(msError));
    }

    @Test
    void filteredIndexNameInSqlServer() {
        SQLException e = new SQLException("Не удается вставить повторяющуюся строку ключа в объект \"dbo.appointment\" "
                + "с уникальным индексом \"ux_appointment_vet_slot\".", "23000", 2601);
        assertEquals("У врача уже есть приём на это время.", ms.userMessage(e));
    }

    @Test
    void foreignKeyMessageDependsOnOperation() {
        SQLException delete = new SQLException("ОШИБКА: UPDATE или DELETE в таблице \"owner\" нарушает ограничение "
                + "внешнего ключа \"fk_pet_owner\" таблицы \"pet\"", "23503");
        SQLException insert = new SQLException("The INSERT statement conflicted with the FOREIGN KEY constraint "
                + "\"fk_pet_owner\".", "23000", 547);
        assertEquals("Нельзя удалить владельца: у него есть питомцы.", pg.userMessage(delete));
        assertEquals("Владелец не найден.", ms.userMessage(insert));
    }

    @Test
    void connectionErrors() {
        SQLException pgAuth = new SQLException("пользователь \"vet_app\" не прошёл проверку подлинности", "28P01");
        SQLException msLogin = new SQLException("Login failed for user 'vet_app'.", "S0001", 18456);
        assertTrue(pg.userMessage(pgAuth).startsWith("Нет подключения к базе данных"));
        assertTrue(ms.userMessage(msLogin).startsWith("Нет подключения к базе данных"));
    }

    @Test
    void unknownErrorKeepsOriginalText() {
        SQLException e = new SQLException("что-то совсем другое", "XX000");
        assertTrue(pg.userMessage(e).contains("что-то совсем другое"));
    }
}
