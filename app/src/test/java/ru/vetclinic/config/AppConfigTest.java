package ru.vetclinic.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Модульные тесты чтения настроек: порядок файлов, ОС, подстановки. */
class AppConfigTest {

    @TempDir
    Path dir;

    private void write(String relative, String content) throws IOException {
        Path p = dir.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private void writeBase() throws IOException {
        write("db.properties", """
                db.active=postgresql
                db.profiles=postgresql,mssql
                postgresql.title=PostgreSQL
                postgresql.url=jdbc:postgresql://localhost:5432/vet_clinic
                postgresql.user=vet_app
                postgresql.password=secret
                mssql.title=Microsoft SQL Server
                mssql.url=jdbc:sqlserver://localhost:1433;databaseName=vet_clinic
                ui.fontSize=14
                """);
    }

    @Test
    void detectsOperatingSystemFamily() {
        assertEquals(AppConfig.OsFamily.WINDOWS, AppConfig.OsFamily.detect("Windows 11"));
        assertEquals(AppConfig.OsFamily.LINUX, AppConfig.OsFamily.detect("Linux"));
        assertEquals(AppConfig.OsFamily.MACOS, AppConfig.OsFamily.detect("Mac OS X"));
        assertEquals(AppConfig.OsFamily.LINUX, AppConfig.OsFamily.detect("FreeBSD"));
    }

    @Test
    void osFileIsReadFirstAndDbPropertiesOverridesIt() throws IOException {
        writeBase();
        write("os/windows.properties", "ui.fontName=Segoe UI\nui.fontSize=10\nmssql.integratedSecurity=true\n");
        write("os/linux.properties", "ui.fontName=DejaVu Sans\nmssql.integratedSecurity=false\n");

        AppConfig win = AppConfig.load(dir, AppConfig.OsFamily.WINDOWS, new Properties());
        assertEquals("Segoe UI", win.fontName());
        assertEquals(14, win.fontSize(), "db.properties важнее файла ОС");
        assertTrue(win.dbSettings("mssql").integratedSecurity());

        AppConfig linux = AppConfig.load(dir, AppConfig.OsFamily.LINUX, new Properties());
        assertEquals("DejaVu Sans", linux.fontName());
        assertFalse(linux.dbSettings("mssql").integratedSecurity());
    }

    @Test
    void localPropertiesAndSystemPropertiesWin() throws IOException {
        writeBase();
        write("local.properties", "postgresql.user=student\n");
        Properties system = new Properties();
        system.setProperty("db.active", "mssql");
        system.setProperty("java.version", "не должен попасть в настройки");

        AppConfig cfg = AppConfig.load(dir, AppConfig.OsFamily.LINUX, system);
        assertEquals("student", cfg.dbSettings("postgresql").user());
        assertEquals("mssql", cfg.activeProfile());
        assertEquals(null, cfg.get("java.version"));
        assertEquals(List.of("postgresql", "mssql"), cfg.profiles());
    }

    @Test
    void lineSeparatorDependsOnOsSettings() throws IOException {
        writeBase();
        write("os/windows.properties", "export.lineSeparator=CRLF\n");
        write("os/linux.properties", "export.lineSeparator=LF\n");
        assertEquals("\r\n", AppConfig.load(dir, AppConfig.OsFamily.WINDOWS, new Properties()).lineSeparator());
        assertEquals("\n", AppConfig.load(dir, AppConfig.OsFamily.LINUX, new Properties()).lineSeparator());
    }

    @Test
    void placeholdersAreResolvedFromSystemProperties() throws IOException {
        writeBase();
        write("os/linux.properties", "export.dir=${user.home}/invoices\n");
        AppConfig cfg = AppConfig.load(dir, AppConfig.OsFamily.LINUX, new Properties());
        assertEquals(System.getProperty("user.home") + "/invoices", cfg.get("export.dir"));
    }

    @Test
    void missingMainFileIsReported() {
        IOException e = assertThrows(IOException.class,
                () -> AppConfig.load(dir, AppConfig.OsFamily.WINDOWS, new Properties()));
        assertTrue(e.getMessage().contains("db.properties"));
    }

    @Test
    void unknownProfileHasNoUrl() throws IOException {
        writeBase();
        AppConfig cfg = AppConfig.load(dir, AppConfig.OsFamily.WINDOWS, new Properties());
        assertThrows(IllegalArgumentException.class, () -> cfg.dbSettings("oracle"));
    }

    @Test
    void safeDescriptionHidesPassword() throws IOException {
        writeBase();
        DbSettings s = AppConfig.load(dir, AppConfig.OsFamily.LINUX, new Properties()).dbSettings("postgresql");
        assertFalse(s.safeDescription().contains("secret"));
    }
}
