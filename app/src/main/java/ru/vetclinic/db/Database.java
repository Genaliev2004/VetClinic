package ru.vetclinic.db;

import ru.vetclinic.config.DbSettings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Точка доступа к базе данных: открывает JDBC-соединения по настройкам
 * и выполняет работу в режиме автофиксации или в транзакции.
 * Пул соединений не используется: для настольного приложения с одним
 * пользователем достаточно открывать соединение на каждую операцию.
 */
public class Database {

    private static final int LOGIN_TIMEOUT_SECONDS = 10;

    private final DbSettings settings;
    private final Dialect dialect;

    public Database(DbSettings settings) {
        this.settings = settings;
        this.dialect = Dialect.forProfile(settings.profile());
    }

    public DbSettings settings() {
        return settings;
    }

    public Dialect dialect() {
        return dialect;
    }

    /** Открывает новое соединение. Закрывать его должен вызывающий код (try-with-resources). */
    public Connection openConnection() throws SQLException {
        if (settings.driver() != null && !settings.driver().isBlank()) {
            try {
                Class.forName(settings.driver());
            } catch (ClassNotFoundException e) {
                throw new SQLException("JDBC-драйвер не найден: " + settings.driver(), "08001", e);
            }
        }
        Properties p = new Properties();
        if (settings.integratedSecurity()) {
            p.setProperty("integratedSecurity", "true");
        } else {
            p.setProperty("user", settings.user());
            p.setProperty("password", settings.password());
        }
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        return DriverManager.getConnection(settings.url(), p);
    }

    /** Выполняет работу в режиме автофиксации (для чтения и одиночных изменений). */
    public <T> T run(SqlFunction<Connection, T> work) {
        try (Connection c = openConnection()) {
            return work.apply(c);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /**
     * Выполняет работу в одной транзакции: COMMIT при успехе,
     * ROLLBACK при любой ошибке (в том числе ошибке триггера).
     */
    public <T> T inTransaction(SqlFunction<Connection, T> work) {
        try (Connection c = openConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** Проверяет соединение и возвращает строку версии сервера. */
    public String serverVersion() {
        return run(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(dialect.versionQuery())) {
                rs.next();
                String v = rs.getString(1);
                int nl = v.indexOf('\n');
                return nl > 0 ? v.substring(0, nl).trim() : v.trim();
            }
        });
    }

    /** Преобразует ошибку СУБД в исключение с понятным сообщением. */
    public DbException translate(SQLException e) {
        return new DbException(dialect.userMessage(e), e);
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // транзакцию уже откатил сервер (например, после THROW в триггере SQL Server)
        }
    }
}
