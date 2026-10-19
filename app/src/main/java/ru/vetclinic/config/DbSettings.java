package ru.vetclinic.config;

/**
 * Параметры подключения к одной СУБД, прочитанные из файла настроек.
 *
 * @param profile             код профиля: postgresql или mssql
 * @param title               название СУБД для показа пользователю
 * @param driver              класс JDBC-драйвера (можно не указывать — JDBC 4 найдёт сам)
 * @param url                 строка подключения JDBC
 * @param user                имя пользователя
 * @param password            пароль
 * @param integratedSecurity  вход под учётной записью Windows (только SQL Server)
 */
public record DbSettings(String profile, String title, String driver, String url,
                         String user, String password, boolean integratedSecurity) {

    /** Строка подключения без пароля — для показа в строке состояния и в логах. */
    public String safeDescription() {
        return title + " — " + url + (integratedSecurity ? " (Windows-аутентификация)" : " (пользователь " + user + ")");
    }

    @Override
    public String toString() {
        return title;
    }
}
