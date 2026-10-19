package ru.vetclinic.integration;

import org.junit.jupiter.api.DisplayName;

/** Интеграционные тесты на Microsoft SQL Server. */
@DisplayName("Microsoft SQL Server")
class MssqlIntegrationTest extends AbstractIntegrationTest {

    @Override
    protected String profile() {
        return "mssql";
    }
}
