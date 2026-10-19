package ru.vetclinic.db;

/**
 * Ошибка работы с базой данных с уже подготовленным сообщением для пользователя.
 * Исходное {@link java.sql.SQLException} сохраняется как причина.
 */
public class DbException extends RuntimeException {

    public DbException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
