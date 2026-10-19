package ru.vetclinic.model;

/**
 * Элемент простого справочника «код — название»:
 * вид животного (species) или специализация врача (specialization).
 */
public record RefItem(Integer id, String name) {

    @Override
    public String toString() {
        return name;   // так элемент показывается в выпадающих списках
    }
}
