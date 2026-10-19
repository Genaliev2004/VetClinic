package ru.vetclinic.db;

import java.sql.SQLException;

/**
 * Различия между СУБД, которые нужны приложению.
 * Большая часть SQL в DAO написана так, что работает в обеих СУБД без изменений
 * (CONCAT, LOWER(...) LIKE, OFFSET ... FETCH, RETURN_GENERATED_KEYS),
 * а здесь собрано то, что отличается.
 */
public interface Dialect {

    /** Название СУБД для пользователя. */
    String name();

    /** Запрос, возвращающий версию сервера. */
    String versionQuery();

    /** Ошибка, брошенная триггером (RAISE EXCEPTION / THROW) — её текст уже понятен пользователю. */
    boolean isBusinessRuleError(SQLException e);

    /** Текст ошибки триггера без служебных префиксов СУБД. */
    String businessRuleMessage(SQLException e);

    /** Ошибка соединения или входа (сервер недоступен, неверный пароль). */
    boolean isConnectionError(SQLException e);

    /** Сообщение для пользователя по исключению СУБД. */
    default String userMessage(SQLException e) {
        if (isBusinessRuleError(e)) {
            return businessRuleMessage(e);
        }
        String known = ConstraintMessages.find(e.getMessage());
        if (known != null) {
            return known;
        }
        if (isConnectionError(e)) {
            return "Нет подключения к базе данных (" + name() + "): " + e.getMessage();
        }
        return "Ошибка базы данных (" + name() + "): " + e.getMessage();
    }

    /** Диалект по коду профиля из файла настроек. */
    static Dialect forProfile(String profile) {
        return switch (profile) {
            case "postgresql" -> new PostgresDialect();
            case "mssql" -> new MssqlDialect();
            default -> throw new IllegalArgumentException("Неизвестная СУБД в настройках: " + profile
                    + " (допустимо: postgresql, mssql)");
        };
    }
}
