package ru.vetclinic.model;

import java.math.BigDecimal;

/**
 * Строка счёта: услуга, оказанная на приёме (таблица appointment_service).
 * price — цена на момент оказания; null у новой строки, которую ещё не сохранили
 * (тогда при сохранении берётся текущая цена из прайса).
 */
public record ServiceLine(int serviceId, String serviceName, int quantity, BigDecimal price) {

    /** Сумма по строке (цена × количество). */
    public BigDecimal amount() {
        return price == null ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(quantity));
    }

    public ServiceLine withQuantity(int newQuantity) {
        return new ServiceLine(serviceId, serviceName, newQuantity, price);
    }
}
