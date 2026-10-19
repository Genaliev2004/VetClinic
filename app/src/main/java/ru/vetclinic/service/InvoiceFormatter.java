package ru.vetclinic.service;

import ru.vetclinic.model.AppointmentInfo;
import ru.vetclinic.model.ServiceLine;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Текст счёта для сохранения в файл. Разделитель строк передаётся снаружи
 * (из настроек ОС: CRLF для Windows, LF для Linux).
 */
public final class InvoiceFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private InvoiceFormatter() {
    }

    /** Денежная сумма: «1 200,00». */
    public static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("ru-RU"));
        symbols.setGroupingSeparator(' ');   // обычный пробел вместо неразрывного — для любых редакторов
        symbols.setDecimalSeparator(',');
        DecimalFormat f = new DecimalFormat("#,##0.00", symbols);
        return f.format(value == null ? BigDecimal.ZERO : value);
    }

    /** Итог по строкам счёта. */
    public static BigDecimal total(List<ServiceLine> lines) {
        return lines.stream().map(ServiceLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static String fileName(AppointmentInfo a) {
        return "Счёт_" + a.id() + "_" + a.startTime().format(FILE_DATE) + ".txt";
    }

    public static String format(AppointmentInfo a, List<ServiceLine> lines, String nl) {
        StringBuilder sb = new StringBuilder();
        sb.append("ВЕТЕРИНАРНАЯ КЛИНИКА «ВЕТКЛИНИК»").append(nl);
        sb.append("СЧЁТ № ").append(a.id()).append(nl).append(nl);
        sb.append("Дата приёма: ").append(a.startTime().format(DATE_TIME)).append(nl);
        sb.append("Врач:        ").append(a.vetName()).append(" (").append(a.specializationName()).append(')').append(nl);
        sb.append("Питомец:     ").append(a.petName()).append(" (").append(a.speciesName()).append(')').append(nl);
        sb.append("Владелец:    ").append(a.ownerName()).append(", ").append(a.ownerPhone()).append(nl);
        if (a.diagnosis() != null) {
            sb.append("Диагноз:     ").append(a.diagnosis()).append(nl);
        }
        sb.append(nl);
        sb.append(String.format("%-3s %-34s %5s %12s %12s", "№", "Услуга", "Кол.", "Цена", "Сумма")).append(nl);
        sb.append("-".repeat(70)).append(nl);
        int i = 1;
        for (ServiceLine l : lines) {
            sb.append(String.format("%-3d %-34s %5d %12s %12s", i++, cut(l.serviceName(), 34), l.quantity(),
                    money(l.price()), money(l.amount()))).append(nl);
        }
        sb.append("-".repeat(70)).append(nl);
        sb.append(String.format("%-56s %13s", "ИТОГО, руб.:", money(total(lines)))).append(nl);
        return sb.toString();
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
