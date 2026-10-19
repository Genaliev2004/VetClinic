package ru.vetclinic.db;

import java.sql.SQLException;

/** Функция, которая может бросить {@link SQLException} (для лямбд с JDBC-кодом). */
@FunctionalInterface
public interface SqlFunction<A, R> {
    R apply(A arg) throws SQLException;
}
