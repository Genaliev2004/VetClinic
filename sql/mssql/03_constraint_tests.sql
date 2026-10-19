-- =====================================================================
-- ВетКлиник. Microsoft SQL Server. Шаг 3: проверка ограничений целостности.
--   sqlcmd -S localhost -E -C -I -f 65001 -d vet_clinic -i sql\mssql\03_constraint_tests.sql
-- Каждый тест выполняется в своей транзакции, которая затем откатывается,
-- поэтому данные не изменяются. Результат: строки «PASS …» / «FAIL …».
-- =====================================================================
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;

DECLARE @tests TABLE (
    code      VARCHAR(5)    NOT NULL,
    rule_name NVARCHAR(100) NOT NULL,
    expect_ok BIT           NOT NULL,   -- 0 — ждём ошибку, 1 — ждём успех
    test_sql  NVARCHAR(MAX) NOT NULL,
    setup_sql NVARCHAR(MAX) NULL
);

INSERT INTO @tests (code, rule_name, expect_ok, test_sql, setup_sql) VALUES
('T01', N'BR-01 фамилия владельца обязательна', 0,
 N'INSERT INTO owner (last_name, first_name, phone) VALUES (NULL, N''Иван'', ''+79990000001'')', NULL),
('T02', N'BR-01 пустая фамилия врача', 0,
 N'INSERT INTO vet (specialization_id, last_name, first_name, phone, hire_date) VALUES (1, N''  '', N''Иван'', ''+79990000002'', ''2020-01-01'')', NULL),
('T03', N'BR-02 неверный формат телефона', 0,
 N'INSERT INTO owner (last_name, first_name, phone) VALUES (N''Тест'', N''Тест'', ''89031112233'')', NULL),
('T04', N'BR-02 повтор телефона владельца', 0,
 N'INSERT INTO owner (last_name, first_name, phone) VALUES (N''Тест'', N''Тест'', ''+79031112201'')', NULL),
('T05', N'BR-03 e-mail без @', 0,
 N'INSERT INTO owner (last_name, first_name, phone, email) VALUES (N''Тест'', N''Тест'', ''+79990000003'', ''mail.ru'')', NULL),
('T06', N'BR-03 повтор e-mail', 0,
 N'INSERT INTO owner (last_name, first_name, phone, email) VALUES (N''Тест'', N''Тест'', ''+79990000004'', ''ivanov.sp@mail.ru'')', NULL),
('T07', N'BR-03 несколько владельцев без e-mail', 1,
 N'INSERT INTO owner (last_name, first_name, phone) VALUES (N''А'', N''А'', ''+79990000005''), (N''Б'', N''Б'', ''+79990000006'')', NULL),
('T08', N'BR-04 несуществующий владелец', 0,
 N'INSERT INTO pet (owner_id, species_id, pet_name, sex) VALUES (999, 1, N''Тест'', ''M'')', NULL),
('T09', N'BR-05 недопустимый пол', 0,
 N'INSERT INTO pet (owner_id, species_id, pet_name, sex) VALUES (1, 1, N''Тест'', ''X'')', NULL),
('T10', N'BR-06 дата рождения в будущем', 0,
 N'INSERT INTO pet (owner_id, species_id, pet_name, sex, birth_date) VALUES (1, 1, N''Тест'', ''M'', DATEADD(DAY, 1, CAST(GETDATE() AS DATE)))', NULL),
('T11', N'BR-07 нулевой вес', 0,
 N'INSERT INTO pet (owner_id, species_id, pet_name, sex, weight_kg) VALUES (1, 1, N''Тест'', ''M'', 0)', NULL),
('T12', N'BR-08 чип не из 15 цифр', 0,
 N'INSERT INTO pet (owner_id, species_id, pet_name, sex, chip_number) VALUES (1, 1, N''Тест'', ''M'', ''12345ABC'')', NULL),
('T13', N'BR-08 повтор номера чипа', 0,
 N'INSERT INTO pet (owner_id, species_id, pet_name, sex, chip_number) VALUES (1, 1, N''Тест'', ''M'', ''643094100000001'')', NULL),
('T14', N'BR-09 повтор названия вида', 0,
 N'INSERT INTO species (species_name) VALUES (N''Кошка'')', NULL),
('T15', N'BR-10 отрицательная цена услуги', 0,
 N'INSERT INTO service (service_name, price) VALUES (N''Тест'', -1)', NULL),
('T16', N'BR-23 дата приёма на работу в будущем', 0,
 N'INSERT INTO vet (specialization_id, last_name, first_name, phone, hire_date) VALUES (1, N''Тест'', N''Тест'', ''+79990000007'', DATEADD(DAY, 30, CAST(GETDATE() AS DATE)))', NULL),
('T17', N'BR-11 приём в 07:30', 0,
 N'INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (1, 1, ''2026-12-01 07:30:00'')', NULL),
('T18', N'BR-11 приём в 10:15', 0,
 N'INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (1, 1, ''2026-12-01 10:15:00'')', NULL),
('T19', N'BR-11 недопустимый статус', 0,
 N'INSERT INTO appointment (pet_id, vet_id, start_time, status) VALUES (1, 1, ''2026-12-01 10:00:00'', ''LOST'')', NULL),
('T20', N'BR-12 врач занят в это время', 0,
 N'INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (2, 1, ''2026-10-21 10:00:00'')', NULL),
('T21', N'BR-13 питомец уже записан на это время', 0,
 N'INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (1, 2, ''2026-10-21 10:00:00'')', NULL),
('T22', N'BR-12 слот отменённого приёма можно занять', 1,
 N'INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (13, 3, ''2026-10-16 08:00:00'')', NULL),
('T23', N'BR-14 проведённый приём без диагноза', 0,
 N'UPDATE appointment SET status = ''DONE'' WHERE appointment_id = 17', NULL),
('T24', N'BR-15 запись к уволенному врачу', 0,
 N'INSERT INTO appointment (pet_id, vet_id, start_time) VALUES (1, 5, ''2026-12-01 10:00:00'')', NULL),
('T25', N'BR-16 возврат отменённого приёма в работу', 0,
 N'UPDATE appointment SET status = ''SCHEDULED'' WHERE appointment_id = 12', NULL),
('T26', N'BR-16 возврат проведённого приёма в работу', 0,
 N'UPDATE appointment SET status = ''SCHEDULED'' WHERE appointment_id = 1', NULL),
('T27', N'BR-17 отмена приёма с оказанными услугами', 0,
 N'UPDATE appointment SET status = ''CANCELLED'' WHERE appointment_id = 17',
 N'INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (17, 1, 1, 1200)'),
('T28', N'BR-17 услуга в отменённом приёме', 0,
 N'INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (12, 1, 1, 1200)', NULL),
('T29', N'BR-17 неактивная услуга', 0,
 N'INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (17, 11, 1, 400)', NULL),
('T30', N'BR-18 количество 0', 0,
 N'INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (17, 1, 0, 1200)', NULL),
('T31', N'BR-19 повтор услуги в приёме', 0,
 N'INSERT INTO appointment_service (appointment_id, service_id, quantity, price) VALUES (1, 1, 1, 1200)', NULL),
('T32', N'BR-20 удаление владельца с питомцами', 0,
 N'DELETE FROM owner WHERE owner_id = 1', NULL),
('T33', N'BR-20 удаление услуги, которая есть в приёмах', 0,
 N'DELETE FROM service WHERE service_id = 1', NULL),
('T34', N'BR-21 удаление приёма каскадно удаляет его услуги', 1,
 N'DELETE FROM appointment WHERE appointment_id = 1;
   IF EXISTS (SELECT 1 FROM appointment_service WHERE appointment_id = 1)
       THROW 50999, N''Строки услуг не удалились'', 1;', NULL);

DECLARE @code VARCHAR(5), @rule NVARCHAR(100), @ok BIT, @sql NVARCHAR(MAX), @setup NVARCHAR(MAX),
        @stage VARCHAR(10), @pass INT = 0, @fail INT = 0;

DECLARE cur CURSOR LOCAL FAST_FORWARD FOR
    SELECT code, rule_name, expect_ok, test_sql, setup_sql FROM @tests ORDER BY code;
OPEN cur;
FETCH NEXT FROM cur INTO @code, @rule, @ok, @sql, @setup;
WHILE @@FETCH_STATUS = 0
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION;
        SET @stage = 'setup';
        IF @setup IS NOT NULL EXEC sp_executesql @setup;
        SET @stage = 'test';
        EXEC sp_executesql @sql;
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        IF @ok = 1
        BEGIN
            PRINT N'PASS ' + @code + N' ' + @rule + N': допустимые данные приняты'; SET @pass += 1;
        END
        ELSE
        BEGIN
            PRINT N'FAIL ' + @code + N' ' + @rule + N': оператор выполнился без ошибки'; SET @fail += 1;
        END;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        IF @ok = 0 AND @stage = 'test'
        BEGIN
            PRINT N'PASS ' + @code + N' ' + @rule + N' [' + CAST(ERROR_NUMBER() AS NVARCHAR(10)) + N'] ' + ERROR_MESSAGE();
            SET @pass += 1;
        END
        ELSE
        BEGIN
            PRINT N'FAIL ' + @code + N' ' + @rule + N' (' + @stage + N') [' + CAST(ERROR_NUMBER() AS NVARCHAR(10)) + N'] ' + ERROR_MESSAGE();
            SET @fail += 1;
        END;
    END CATCH;
    FETCH NEXT FROM cur INTO @code, @rule, @ok, @sql, @setup;
END;
CLOSE cur;
DEALLOCATE cur;

PRINT N'Итого: PASS = ' + CAST(@pass AS NVARCHAR(10)) + N', FAIL = ' + CAST(@fail AS NVARCHAR(10));

-- Контроль: данные не изменились
SELECT (SELECT COUNT(*) FROM owner)               AS owners,
       (SELECT COUNT(*) FROM appointment)         AS appointments,
       (SELECT COUNT(*) FROM appointment_service) AS appointment_services;
GO
