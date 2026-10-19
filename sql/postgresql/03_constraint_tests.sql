-- =====================================================================
-- ВетКлиник. PostgreSQL. Шаг 3: проверка ограничений целостности.
--   psql -h localhost -U vet_app -d vet_clinic -f sql/postgresql/03_constraint_tests.sql
-- Каждый тест пытается нарушить одно правило и ожидает ошибку (или,
-- наоборот, проверяет, что допустимые данные принимаются).
-- Результат: строки «PASS …» / «FAIL …». Данные не изменяются:
-- каждая попытка выполняется во вложенном блоке, который откатывается.
-- Номера тестов совпадают с sql/mssql/03_constraint_tests.sql.
-- =====================================================================
SET client_encoding = 'UTF8';
SET client_min_messages = NOTICE;

CREATE TEMP TABLE test_result (code VARCHAR(5), passed BOOLEAN);

-- p_expect_ok = FALSE — ждём ошибку, TRUE — ждём успех.
-- p_setup — необязательная подготовка (откатывается вместе с тестом).
CREATE FUNCTION pg_temp.run_test(p_code TEXT, p_rule TEXT, p_expect_ok BOOLEAN,
                                 p_sql TEXT, p_setup TEXT DEFAULT NULL)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    v_passed BOOLEAN;
    v_stage  TEXT := 'setup';
BEGIN
    BEGIN
        IF p_setup IS NOT NULL THEN
            EXECUTE p_setup;
        END IF;
        v_stage := 'test';
        EXECUTE p_sql;
        v_passed := p_expect_ok;
        IF v_passed THEN
            RAISE NOTICE 'PASS % %: допустимые данные приняты', p_code, p_rule;
        ELSE
            RAISE NOTICE 'FAIL % %: оператор выполнился без ошибки', p_code, p_rule;
        END IF;
        RAISE EXCEPTION USING ERRCODE = 'VC999';   -- откатываем изменения теста
    EXCEPTION
        WHEN SQLSTATE 'VC999' THEN NULL;
        WHEN OTHERS THEN
            v_passed := NOT p_expect_ok AND v_stage = 'test';
            RAISE NOTICE '% % % (%) [%] %', CASE WHEN v_passed THEN 'PASS' ELSE 'FAIL' END,
                         p_code, p_rule, v_stage, SQLSTATE, SQLERRM;
    END;
    INSERT INTO test_result VALUES (p_code, v_passed);
END $$;

-- внешний блок с меткой $tests$, т.к. внутри тестов встречается «$$»
DO $tests$
BEGIN
    PERFORM pg_temp.run_test('T01', 'BR-01 фамилия владельца обязательна', FALSE,
        $q$INSERT INTO owner (last_name, first_name, phone) VALUES (NULL, 'Иван', '+79990000001')$q$);
    PERFORM pg_temp.run_test('T02', 'BR-01 пустая фамилия врача', FALSE,
        $q$INSERT INTO vet (specialization_id, last_name, first_name, phone, hire_date) VALUES (1, '  ', 'Иван', '+79990000002', DATE '2020-01-01')$q$);
    PERFORM pg_temp.run_test('T03', 'BR-02 неверный формат телефона', FALSE,
        $q$INSERT INTO owner (last_name, first_name, phone) VALUES ('Тест', 'Тест', '89031112233')$q$);
    PERFORM pg_temp.run_test('T04', 'BR-02 повтор телефона владельца', FALSE,
        $q$INSERT INTO owner (last_name, first_name, phone) VALUES ('Тест', 'Тест', '+79031112201')$q$);
    PERFORM pg_temp.run_test('T05', 'BR-03 e-mail без @', FALSE,
        $q$INSERT INTO owner (last_name, first_name, phone, email) VALUES ('Тест', 'Тест', '+79990000003', 'mail.ru')$q$);
    PERFORM pg_temp.run_test('T06', 'BR-03 повтор e-mail', FALSE,
        $q$INSERT INTO owner (last_name, first_name, phone, email) VALUES ('Тест', 'Тест', '+79990000004', 'ivanov.sp@mail.ru')$q$);
    PERFORM pg_temp.run_test('T07', 'BR-03 несколько владельцев без e-mail', TRUE,
        $q$INSERT INTO owner (last_name, first_name, phone) VALUES ('А', 'А', '+79990000005'), ('Б', 'Б', '+79990000006')$q$);
    PERFORM pg_temp.run_test('T08', 'BR-04 несуществующий владелец', FALSE,
        $q$INSERT INTO pet (owner_id, species_id, pet_name, sex) VALUES (999, 1, 'Тест', 'M')$q$);
    PERFORM pg_temp.run_test('T09', 'BR-05 недопустимый пол', FALSE,
        $q$INSERT INTO pet (owner_id, species_id, pet_name, sex) VALUES (1, 1, 'Тест', 'X')$q$);
    PERFORM pg_temp.run_test('T10', 'BR-06 дата рождения в будущем', FALSE,
        $q$INSERT INTO pet (owner_id, species_id, pet_name, sex, birth_date) VALUES (1, 1, 'Тест', 'M', CURRENT_DATE + 1)$q$);
    PERFORM pg_temp.run_test('T11', 'BR-07 нулевой вес', FALSE,
        $q$INSERT INTO pet (owner_id, species_id, pet_name, sex, weight_kg) VALUES (1, 1, 'Тест', 'M', 0)$q$);
    PERFORM pg_temp.run_test('T12', 'BR-08 чип не из 15 цифр', FALSE,
        $q$INSERT INTO pet (owner_id, species_id, pet_name, sex, chip_number) VALUES (1, 1, 'Тест', 'M', '12345ABC')$q$);
    PERFORM pg_temp.run_test('T13', 'BR-08 повтор номера чипа', FALSE,
        $q$INSERT INTO pet (owner_id, species_id, pet_name, sex, chip_number) VALUES (1, 1, 'Тест', 'M', '643094100000001')$q$);
    PERFORM pg_temp.run_test('T14', 'BR-09 повтор названия вида', FALSE,
        $q$INSERT INTO species (species_name) VALUES ('Кошка')$q$);
    PERFORM pg_temp.run_test('T15', 'BR-10 отрицательная цена услуги', FALSE,
        $q$INSERT INTO service (service_name, price) VALUES ('Тест', -1)$q$);
    PERFORM pg_temp.run_test('T16', 'BR-23 дата приёма на работу в будущем', FALSE,
        $q$INSERT INTO vet (specialization_id, last_name, first_name, phone, hire_date) VALUES (1, 'Тест', 'Тест', '+79990000007', CURRENT_DATE + 30)$q$);
    PERFORM pg_temp.run_test('T17', 'BR-11 приём в 07:30', FALSE,
        $q$INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (1, 1, TIMESTAMP '2026-12-01 07:30:00')$q$);
    PERFORM pg_temp.run_test('T18', 'BR-11 приём в 10:15', FALSE,
        $q$INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (1, 1, TIMESTAMP '2026-12-01 10:15:00')$q$);
    PERFORM pg_temp.run_test('T19', 'BR-11 недопустимый статус', FALSE,
        $q$INSERT INTO appointment (pet_id, vet_id, start_time, status) VALUES (1, 1, TIMESTAMP '2026-12-01 10:00:00', 'LOST')$q$);
    PERFORM pg_temp.run_test('T20', 'BR-12 врач занят в это время', FALSE,
        $q$INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (2, 1, TIMESTAMP '2026-10-21 10:00:00')$q$);
    PERFORM pg_temp.run_test('T21', 'BR-13 питомец уже записан на это время', FALSE,
        $q$INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (1, 2, TIMESTAMP '2026-10-21 10:00:00')$q$);
    PERFORM pg_temp.run_test('T22', 'BR-12 слот отменённого приёма можно занять', TRUE,
        $q$INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (13, 3, TIMESTAMP '2026-10-16 08:00:00')$q$);
    PERFORM pg_temp.run_test('T23', 'BR-14 проведённый приём без диагноза', FALSE,
        $q$UPDATE appointment SET status = 'DONE' WHERE appointment_id = 17$q$);
    PERFORM pg_temp.run_test('T24', 'BR-15 запись к уволенному врачу', FALSE,
        $q$INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (1, 5, TIMESTAMP '2026-12-01 10:00:00')$q$);
    PERFORM pg_temp.run_test('T25', 'BR-16 возврат отменённого приёма в работу', FALSE,
        $q$UPDATE appointment SET status = 'SCHEDULED' WHERE appointment_id = 12$q$);
    PERFORM pg_temp.run_test('T26', 'BR-16 возврат проведённого приёма в работу', FALSE,
        $q$UPDATE appointment SET status = 'SCHEDULED' WHERE appointment_id = 1$q$);
    PERFORM pg_temp.run_test('T27', 'BR-17 отмена приёма с оказанными услугами', FALSE,
        $q$UPDATE appointment SET status = 'CANCELLED' WHERE appointment_id = 17$q$,
        $q$INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (17, 1, 1, 1200)$q$);
    PERFORM pg_temp.run_test('T28', 'BR-17 услуга в отменённом приёме', FALSE,
        $q$INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (12, 1, 1, 1200)$q$);
    PERFORM pg_temp.run_test('T29', 'BR-17 неактивная услуга', FALSE,
        $q$INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (17, 11, 1, 400)$q$);
    PERFORM pg_temp.run_test('T30', 'BR-18 количество 0', FALSE,
        $q$INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (17, 1, 0, 1200)$q$);
    PERFORM pg_temp.run_test('T31', 'BR-19 повтор услуги в приёме', FALSE,
        $q$INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (1, 1, 1, 1200)$q$);
    PERFORM pg_temp.run_test('T32', 'BR-20 удаление владельца с питомцами', FALSE,
        $q$DELETE FROM owner WHERE owner_id = 1$q$);
    PERFORM pg_temp.run_test('T33', 'BR-20 удаление услуги, которая есть в приёмах', FALSE,
        $q$DELETE FROM service WHERE service_id = 1$q$);
    -- для проверки каскада: удаляем приём (setup), затем ищем его строки услуг
    PERFORM pg_temp.run_test('T34', 'BR-21 удаление приёма каскадно удаляет его услуги', TRUE,
        $q$DO $c$ BEGIN
               IF EXISTS (SELECT 1 FROM appointment_service WHERE appointment_id = 1) THEN
                   RAISE EXCEPTION 'Строки услуг не удалились';
               END IF;
           END $c$$q$,
        $q$DELETE FROM appointment WHERE appointment_id = 1$q$);
END $tests$;

-- Итог и контроль того, что данные не изменились
SELECT COUNT(*) FILTER (WHERE passed)     AS pass,
       COUNT(*) FILTER (WHERE NOT passed) AS fail
FROM test_result;

SELECT (SELECT COUNT(*) FROM owner)               AS owners,
       (SELECT COUNT(*) FROM appointment)         AS appointments,
       (SELECT COUNT(*) FROM appointment_service) AS appointment_services;
