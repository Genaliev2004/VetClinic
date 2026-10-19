package ru.vetclinic.db;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Понятные пользователю сообщения о нарушении ограничений.
 * Имена ограничений одинаковы в PostgreSQL и SQL Server (см. sql/&#42;/01_schema.sql),
 * поэтому достаточно найти имя ограничения в тексте ошибки СУБД.
 */
final class ConstraintMessages {

    private static final Map<String, String> CHECK_AND_UNIQUE = new LinkedHashMap<>();
    /** Для внешних ключей: [0] — при вставке/изменении, [1] — при удалении родителя. */
    private static final Map<String, String[]> FOREIGN_KEYS = new LinkedHashMap<>();

    static {
        CHECK_AND_UNIQUE.put("uq_species_name", "Такой вид животного уже есть.");
        CHECK_AND_UNIQUE.put("ck_species_name", "Название вида не может быть пустым.");
        CHECK_AND_UNIQUE.put("uq_specialization_name", "Такая специализация уже есть.");
        CHECK_AND_UNIQUE.put("ck_specialization_name", "Название специализации не может быть пустым.");
        CHECK_AND_UNIQUE.put("uq_owner_phone", "Владелец с таким телефоном уже зарегистрирован.");
        CHECK_AND_UNIQUE.put("uq_owner_email", "Владелец с таким e-mail уже зарегистрирован.");
        CHECK_AND_UNIQUE.put("ux_owner_email", "Владелец с таким e-mail уже зарегистрирован.");
        CHECK_AND_UNIQUE.put("ck_owner_names", "Фамилия и имя владельца обязательны.");
        CHECK_AND_UNIQUE.put("ck_owner_phone", "Телефон должен быть в формате +7XXXXXXXXXX.");
        CHECK_AND_UNIQUE.put("ck_owner_email", "E-mail указан неверно.");
        CHECK_AND_UNIQUE.put("uq_pet_chip", "Питомец с таким номером чипа уже есть.");
        CHECK_AND_UNIQUE.put("ux_pet_chip", "Питомец с таким номером чипа уже есть.");
        CHECK_AND_UNIQUE.put("ck_pet_name", "Кличка не может быть пустой.");
        CHECK_AND_UNIQUE.put("ck_pet_sex", "Пол питомца: самец или самка.");
        CHECK_AND_UNIQUE.put("ck_pet_birth", "Дата рождения — не позже сегодняшнего дня и не раньше 1980 года.");
        CHECK_AND_UNIQUE.put("ck_pet_weight", "Вес должен быть больше 0 и меньше 1000 кг.");
        CHECK_AND_UNIQUE.put("ck_pet_chip", "Номер чипа — ровно 15 цифр.");
        CHECK_AND_UNIQUE.put("uq_vet_phone", "Врач с таким телефоном уже есть.");
        CHECK_AND_UNIQUE.put("ck_vet_names", "Фамилия и имя врача обязательны.");
        CHECK_AND_UNIQUE.put("ck_vet_phone", "Телефон должен быть в формате +7XXXXXXXXXX.");
        CHECK_AND_UNIQUE.put("ck_vet_hire_date", "Дата приёма на работу — не позже сегодняшнего дня и не раньше 1980 года.");
        CHECK_AND_UNIQUE.put("uq_service_name", "Услуга с таким названием уже есть.");
        CHECK_AND_UNIQUE.put("ck_service_name", "Название услуги не может быть пустым.");
        CHECK_AND_UNIQUE.put("ck_service_price", "Цена услуги не может быть отрицательной.");
        CHECK_AND_UNIQUE.put("ck_appointment_status", "Недопустимый статус приёма.");
        CHECK_AND_UNIQUE.put("ck_appointment_slot", "Приём возможен с 08:00 до 19:30, в начале или в середине часа.");
        CHECK_AND_UNIQUE.put("ck_appointment_done", "Чтобы завершить приём, укажите диагноз.");
        CHECK_AND_UNIQUE.put("ux_appointment_vet_slot", "У врача уже есть приём на это время.");
        CHECK_AND_UNIQUE.put("ux_appointment_pet_slot", "Питомец уже записан на это время.");
        CHECK_AND_UNIQUE.put("pk_appointment_service", "Эта услуга уже есть в приёме — измените количество.");
        CHECK_AND_UNIQUE.put("ck_aps_quantity", "Количество услуги — от 1 до 100.");
        CHECK_AND_UNIQUE.put("ck_aps_price", "Цена не может быть отрицательной.");

        FOREIGN_KEYS.put("fk_pet_owner", new String[]{
                "Владелец не найден.", "Нельзя удалить владельца: у него есть питомцы."});
        FOREIGN_KEYS.put("fk_pet_species", new String[]{
                "Вид животного не найден.", "Нельзя удалить вид: есть питомцы этого вида."});
        FOREIGN_KEYS.put("fk_vet_specialization", new String[]{
                "Специализация не найдена.", "Нельзя удалить специализацию: есть врачи с этой специализацией."});
        FOREIGN_KEYS.put("fk_appointment_pet", new String[]{
                "Питомец не найден.", "Нельзя удалить питомца: у него есть приёмы (история болезни)."});
        FOREIGN_KEYS.put("fk_appointment_vet", new String[]{
                "Врач не найден.", "Нельзя удалить врача: у него есть приёмы. Отметьте его как неработающего."});
        FOREIGN_KEYS.put("fk_aps_appointment", new String[]{
                "Приём не найден.", "Нельзя удалить приём."});
        FOREIGN_KEYS.put("fk_aps_service", new String[]{
                "Услуга не найдена.", "Нельзя удалить услугу: она уже оказывалась. Снимите её с прайса."});
    }

    private ConstraintMessages() {
    }

    /** Ищет имя известного ограничения в тексте ошибки; null — если не нашли. */
    static String find(String errorText) {
        if (errorText == null) {
            return null;
        }
        String text = errorText.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : CHECK_AND_UNIQUE.entrySet()) {
            if (text.contains(e.getKey())) {
                return e.getValue();
            }
        }
        for (Map.Entry<String, String[]> e : FOREIGN_KEYS.entrySet()) {
            if (text.contains(e.getKey())) {
                // и PostgreSQL, и SQL Server (на любом языке) пишут слово DELETE при удалении родителя
                return text.contains("delete") ? e.getValue()[1] : e.getValue()[0];
            }
        }
        return null;
    }
}
