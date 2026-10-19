package ru.vetclinic.service;

/** Ошибка проверки введённых данных (до обращения к базе данных). */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
