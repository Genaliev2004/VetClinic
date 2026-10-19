package ru.vetclinic.integration;

import org.junit.jupiter.api.DisplayName;

/** Интеграционные тесты на PostgreSQL. */
@DisplayName("PostgreSQL")
class PostgresIntegrationTest extends AbstractIntegrationTest {

    @Override
    protected String profile() {
        return "postgresql";
    }
}
