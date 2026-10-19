package ru.vetclinic.db;

import java.sql.SQLException;

/** Особенности Microsoft SQL Server. Ошибки различаются по номеру ошибки сервера. */
public class MssqlDialect implements Dialect {

    /** Номера ошибок от 50000 — пользовательские (THROW в наших триггерах: 50001–50005). */
    static final int FIRST_USER_ERROR = 50000;
    /** Ошибка входа (неверный логин/пароль, нет прав на базу). */
    static final int LOGIN_FAILED = 18456;
    /** Не удаётся открыть базу данных. */
    static final int CANNOT_OPEN_DATABASE = 4060;

    @Override
    public String name() {
        return "Microsoft SQL Server";
    }

    @Override
    public String versionQuery() {
        return "SELECT @@VERSION";
    }

    @Override
    public boolean isBusinessRuleError(SQLException e) {
        return e.getErrorCode() >= FIRST_USER_ERROR;
    }

    @Override
    public String businessRuleMessage(SQLException e) {
        return e.getMessage();   // SQL Server отдаёт текст THROW без префиксов
    }

    @Override
    public boolean isConnectionError(SQLException e) {
        String state = e.getSQLState();
        return (state != null && state.startsWith("08"))
                || e.getErrorCode() == LOGIN_FAILED || e.getErrorCode() == CANNOT_OPEN_DATABASE;
    }
}
