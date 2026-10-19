package ru.vetclinic.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Настройки приложения. Собираются из нескольких файлов по порядку
 * (каждый следующий источник перекрывает предыдущий):
 * <ol>
 *   <li>{@code config/os/<ОС>.properties} — настройки, зависящие от операционной системы;</li>
 *   <li>{@code config/db.properties} — подключение к базам данных;</li>
 *   <li>{@code config/local.properties} — личные настройки пользователя (не хранится в git);</li>
 *   <li>системные свойства JVM ({@code -Dключ=значение}).</li>
 * </ol>
 */
public final class AppConfig {

    /** Операционные системы, для которых есть отдельный файл настроек. */
    public enum OsFamily {
        WINDOWS("windows"), LINUX("linux"), MACOS("macos");

        private final String fileName;

        OsFamily(String fileName) {
            this.fileName = fileName;
        }

        public String fileName() {
            return fileName;
        }

        /** Определяет семейство ОС по значению системного свойства {@code os.name}. */
        public static OsFamily detect(String osName) {
            String n = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
            if (n.contains("win")) {
                return WINDOWS;
            }
            if (n.contains("mac") || n.contains("darwin")) {
                return MACOS;
            }
            return LINUX;   // Linux и прочие Unix-подобные системы
        }
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private final Properties props;
    private final OsFamily os;
    private final Path configDir;
    private final List<String> loadedFiles;

    AppConfig(Properties props, OsFamily os, Path configDir, List<String> loadedFiles) {
        this.props = props;
        this.os = os;
        this.configDir = configDir;
        this.loadedFiles = loadedFiles;
    }

    /** Загружает настройки для текущей ОС из каталога, найденного {@link #findConfigDir()}. */
    public static AppConfig load() throws IOException {
        return load(findConfigDir(), OsFamily.detect(System.getProperty("os.name")), System.getProperties());
    }

    /** Загружает настройки из указанного каталога (метод открыт для модульных тестов). */
    public static AppConfig load(Path configDir, OsFamily os, Properties systemOverrides) throws IOException {
        Path main = configDir.resolve("db.properties");
        if (!Files.isRegularFile(main)) {
            throw new IOException("Не найден файл настроек " + main.toAbsolutePath()
                    + ". Запускайте программу из корня проекта или укажите -Dvetclinic.config=<каталог>.");
        }
        Properties merged = new Properties();
        List<String> loaded = new ArrayList<>();
        mergeFile(merged, configDir.resolve("os").resolve(os.fileName() + ".properties"), loaded);
        mergeFile(merged, main, loaded);
        mergeFile(merged, configDir.resolve("local.properties"), loaded);
        // из системных свойств берём только «наши» ключи, чтобы не тащить всё окружение JVM
        for (String key : systemOverrides.stringPropertyNames()) {
            if (merged.containsKey(key) || key.startsWith("db.") || key.startsWith("ui.")
                    || key.startsWith("export.") || key.startsWith("postgresql.") || key.startsWith("mssql.")) {
                merged.setProperty(key, systemOverrides.getProperty(key));
            }
        }
        return new AppConfig(merged, os, configDir, loaded);
    }

    private static void mergeFile(Properties target, Path file, List<String> loaded) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        // properties читаем в UTF-8, иначе кириллица в значениях испортится
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        target.putAll(p);
        loaded.add(file.toString());
    }

    /**
     * Ищет каталог config: свойство {@code vetclinic.config}, затем ./config
     * и ../config (если программу запустили из каталога app).
     */
    public static Path findConfigDir() {
        String explicit = System.getProperty("vetclinic.config");
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit);
        }
        for (String candidate : new String[]{"config", "../config"}) {
            Path p = Paths.get(candidate);
            if (Files.isRegularFile(p.resolve("db.properties"))) {
                return p;
            }
        }
        return Paths.get("config");
    }

    // ------------------------------------------------------------------ общие методы

    /** Значение параметра с подстановкой ${...} из системных свойств; null, если параметра нет. */
    public String get(String key) {
        String v = props.getProperty(key);
        return v == null ? null : resolvePlaceholders(v.trim());
    }

    public String get(String key, String defaultValue) {
        String v = get(key);
        return v == null || v.isEmpty() ? defaultValue : v;
    }

    public int getInt(String key, int defaultValue) {
        String v = get(key);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key);
        return v == null || v.isEmpty() ? defaultValue : Boolean.parseBoolean(v);
    }

    static String resolvePlaceholders(String value) {
        Matcher m = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String replacement = System.getProperty(name, System.getenv().getOrDefault(name, ""));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public OsFamily os() {
        return os;
    }

    public Path configDir() {
        return configDir;
    }

    public List<String> loadedFiles() {
        return List.copyOf(loadedFiles);
    }

    // ------------------------------------------------------------------ база данных

    /** Профиль СУБД, выбранный по умолчанию ({@code db.active}). */
    public String activeProfile() {
        return get("db.active", "postgresql");
    }

    /** Все профили СУБД из {@code db.profiles}. */
    public List<String> profiles() {
        String list = get("db.profiles", activeProfile());
        return Arrays.stream(list.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Параметры подключения для профиля (postgresql, mssql). */
    public DbSettings dbSettings(String profile) {
        String url = get(profile + ".url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("В настройках нет параметра " + profile + ".url");
        }
        return new DbSettings(
                profile,
                get(profile + ".title", profile),
                get(profile + ".driver", ""),
                url,
                get(profile + ".user", ""),
                get(profile + ".password", ""),
                getBoolean(profile + ".integratedSecurity", false));
    }

    // ------------------------------------------------------------------ интерфейс и экспорт

    public String lookAndFeel() {
        return get("ui.lookAndFeel", "system");
    }

    public String fontName() {
        return get("ui.fontName", "Dialog");
    }

    public int fontSize() {
        return getInt("ui.fontSize", 13);
    }

    /** Масштаб интерфейса (пустая строка — не задан). */
    public String uiScale() {
        return get("ui.scale", "");
    }

    public Path exportDir() {
        return Paths.get(get("export.dir", System.getProperty("user.home")));
    }

    /** Разделитель строк для текстовых файлов: CRLF (Windows) или LF (Linux, macOS). */
    public String lineSeparator() {
        return "CRLF".equalsIgnoreCase(get("export.lineSeparator", "LF")) ? "\r\n" : "\n";
    }

    public String exportCharset() {
        return get("export.charset", "UTF-8");
    }
}
