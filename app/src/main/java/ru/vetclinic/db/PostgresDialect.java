package ru.vetclinic.db;

import java.sql.SQLException;

/** Особенности PostgreSQL. Ошибки различаются по SQLSTATE. */
public class PostgresDialect implements Dialect {

    /** SQLSTATE для RAISE EXCEPTION без явного кода (raise_exception). */
    static final String RAISE_EXCEPTION = "P0001";

    @Override
    public String name() {
        return "PostgreSQL";
    }

    @Override
    public String versionQuery() {
        return "SELECT version()";
    }

    @Override
    public boolean isBusinessRuleError(SQLException e) {
        return RAISE_EXCEPTION.equals(e.getSQLState());
    }

    @Override
    public String businessRuleMessage(SQLException e) {
        // Текст драйвера: «ОШИБКА: <сообщение>\n  Где: функция PL/pgSQL ...» (или «ERROR: ...»).
        // Оставляем только само сообщение из RAISE EXCEPTION.
        String msg = e.getMessage() == null ? "" : e.getMessage();
        int nl = msg.indexOf('\n');
        if (nl >= 0) {
            msg = msg.substring(0, nl);
        }
        int colon = msg.indexOf(": ");
        return colon >= 0 && colon < 20 ? msg.substring(colon + 2).trim() : msg.trim();
    }

    @Override
    public boolean isConnectionError(SQLException e) {
        String state = e.getSQLState();
        // 08xxx — ошибки соединения, 28xxx — ошибки аутентификации, 3D000 — нет такой базы
        return state != null && (state.startsWith("08") || state.startsWith("28") || state.equals("3D000"));
    }
}
