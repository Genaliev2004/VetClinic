package ru.vetclinic.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Проверка и нормализация данных на стороне приложения. Повторяет ограничения
 * базы данных (CHECK), чтобы пользователь получил сообщение сразу, ещё до запроса.
 * Окончательную проверку всё равно выполняет СУБД.
 */
public final class Validator {

    public static final LocalDate MIN_DATE = LocalDate.of(1980, 1, 1);

    private static final Pattern PHONE = Pattern.compile("^\\+7\\d{10}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern CHIP = Pattern.compile("^\\d{15}$");

    private Validator() {
    }

    /** Обязательная строка: обрезает пробелы, проверяет пустоту и длину. */
    public static String required(String value, String field, int maxLength) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            throw new ValidationException("Заполните поле «" + field + "».");
        }
        return checkLength(v, field, maxLength);
    }

    /** Необязательная строка: пустая превращается в null. */
    public static String optional(String value, String field, int maxLength) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? null : checkLength(v, field, maxLength);
    }

    private static String checkLength(String v, String field, int maxLength) {
        if (v.length() > maxLength) {
            throw new ValidationException("Поле «" + field + "» длиннее " + maxLength + " символов.");
        }
        return v;
    }

    /**
     * Телефон приводится к виду +7XXXXXXXXXX: убираются пробелы, скобки и дефисы,
     * ведущая 8 заменяется на +7.
     */
    public static String phone(String value) {
        String digits = value == null ? "" : value.replaceAll("[\\s()\\-]", "");
        if (digits.matches("^8\\d{10}$")) {
            digits = "+7" + digits.substring(1);
        } else if (digits.matches("^7\\d{10}$")) {
            digits = "+" + digits;
        }
        if (!PHONE.matcher(digits).matches()) {
            throw new ValidationException("Телефон должен быть в формате +7XXXXXXXXXX (например, +79031234567).");
        }
        return digits;
    }

    public static String email(String value) {
        String v = optional(value, "E-mail", 100);
        if (v != null && !EMAIL.matcher(v).matches()) {
            throw new ValidationException("E-mail указан неверно.");
        }
        return v;
    }

    public static String chip(String value) {
        String v = optional(value, "Номер чипа", 15);
        if (v != null && !CHIP.matcher(v).matches()) {
            throw new ValidationException("Номер чипа — ровно 15 цифр.");
        }
        return v;
    }

    public static String sex(String value) {
        if (!"M".equals(value) && !"F".equals(value)) {
            throw new ValidationException("Укажите пол питомца.");
        }
        return value;
    }

    /** Вес в килограммах: число больше 0 и меньше 1000, запятая или точка. */
    public static BigDecimal weight(String value) {
        String v = value == null ? "" : value.trim().replace(',', '.');
        if (v.isEmpty()) {
            return null;
        }
        try {
            BigDecimal w = new BigDecimal(v);
            if (w.signum() <= 0 || w.compareTo(BigDecimal.valueOf(1000)) >= 0) {
                throw new ValidationException("Вес должен быть больше 0 и меньше 1000 кг.");
            }
            if (w.scale() > 2) {
                throw new ValidationException("Вес указывается с точностью до 0,01 кг.");
            }
            return w;
        } catch (NumberFormatException e) {
            throw new ValidationException("Вес должен быть числом, например 4,5.");
        }
    }

    /** Цена в рублях: число не меньше 0, не более двух знаков после запятой. */
    public static BigDecimal price(String value) {
        String v = value == null ? "" : value.trim().replace(',', '.').replace(" ", "");
        if (v.isEmpty()) {
            throw new ValidationException("Укажите цену.");
        }
        try {
            BigDecimal p = new BigDecimal(v);
            if (p.signum() < 0) {
                throw new ValidationException("Цена не может быть отрицательной.");
            }
            if (p.scale() > 2) {
                throw new ValidationException("Цена указывается с точностью до копеек.");
            }
            if (p.compareTo(new BigDecimal("99999999.99")) > 0) {
                throw new ValidationException("Слишком большая цена.");
            }
            return p.setScale(2);
        } catch (NumberFormatException e) {
            throw new ValidationException("Цена должна быть числом, например 1200 или 1200,50.");
        }
    }

    /** Дата в прошлом или сегодня, не раньше 1980 года (дата рождения, дата приёма на работу). */
    public static LocalDate pastDate(LocalDate value, String field, boolean required, LocalDate today) {
        if (value == null) {
            if (required) {
                throw new ValidationException("Заполните поле «" + field + "».");
            }
            return null;
        }
        if (value.isAfter(today)) {
            throw new ValidationException("«" + field + "» не может быть в будущем.");
        }
        if (value.isBefore(MIN_DATE)) {
            throw new ValidationException("«" + field + "» не может быть раньше 1980 года.");
        }
        return value;
    }

    public static int quantity(int value) {
        if (value < 1 || value > 100) {
            throw new ValidationException("Количество услуги — от 1 до 100.");
        }
        return value;
    }
}
