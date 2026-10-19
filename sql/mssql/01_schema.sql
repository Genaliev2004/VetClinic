-- =====================================================================
-- ВетКлиник. Microsoft SQL Server. Шаг 1: схема базы данных (8 таблиц).
--   sqlcmd -S localhost -E -C -I -f 65001 -d vet_clinic -i sql\mssql\01_schema.sql
-- Скрипт пересоздаёт все объекты, данные при этом удаляются.
--
-- Отличия от версии для PostgreSQL:
--   * IDENTITY вместо GENERATED ... AS IDENTITY, BIT вместо BOOLEAN,
--     DATETIME2(0) вместо TIMESTAMP(0), NVARCHAR для текста на кириллице;
--   * UNIQUE в SQL Server считает NULL равными, поэтому уникальность
--     необязательных e-mail и номера чипа — через фильтрованные индексы;
--   * регулярных выражений нет — форматы проверяются через LIKE;
--   * триггеры AFTER, работают с наборами строк inserted/deleted.
-- =====================================================================
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;   -- обязательно для фильтрованных индексов
GO

DROP VIEW  IF EXISTS v_appointment_total;
DROP TABLE IF EXISTS appointment_service;
DROP TABLE IF EXISTS appointment;
DROP TABLE IF EXISTS service;
DROP TABLE IF EXISTS vet;
DROP TABLE IF EXISTS specialization;
DROP TABLE IF EXISTS pet;
DROP TABLE IF EXISTS owner;
DROP TABLE IF EXISTS species;
GO

-- ---------------------------------------------------------------------
-- 1. Вид животного (справочник)
-- ---------------------------------------------------------------------
CREATE TABLE species (
    species_id   INT           IDENTITY(1,1) NOT NULL,
    species_name NVARCHAR(40)  NOT NULL,
    CONSTRAINT pk_species      PRIMARY KEY (species_id),
    CONSTRAINT uq_species_name UNIQUE (species_name),
    CONSTRAINT ck_species_name CHECK (LEN(TRIM(species_name)) > 0)
);

-- ---------------------------------------------------------------------
-- 2. Владелец питомца
-- ---------------------------------------------------------------------
CREATE TABLE owner (
    owner_id      INT           IDENTITY(1,1) NOT NULL,
    last_name     NVARCHAR(50)  NOT NULL,
    first_name    NVARCHAR(50)  NOT NULL,
    middle_name   NVARCHAR(50)  NULL,
    phone         VARCHAR(12)   NOT NULL,
    email         VARCHAR(100)  NULL,
    address       NVARCHAR(200) NULL,
    registered_on DATE          NOT NULL CONSTRAINT df_owner_registered DEFAULT CAST(GETDATE() AS DATE),
    CONSTRAINT pk_owner       PRIMARY KEY (owner_id),
    CONSTRAINT uq_owner_phone UNIQUE (phone),
    CONSTRAINT ck_owner_names CHECK (LEN(TRIM(last_name)) > 0 AND LEN(TRIM(first_name)) > 0),
    CONSTRAINT ck_owner_phone CHECK (phone LIKE '+7[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
    CONSTRAINT ck_owner_email CHECK (email LIKE '_%@_%._%')
);
CREATE UNIQUE INDEX ux_owner_email ON owner (email) WHERE email IS NOT NULL;

-- ---------------------------------------------------------------------
-- 3. Питомец
-- ---------------------------------------------------------------------
CREATE TABLE pet (
    pet_id      INT           IDENTITY(1,1) NOT NULL,
    owner_id    INT           NOT NULL,
    species_id  INT           NOT NULL,
    pet_name    NVARCHAR(40)  NOT NULL,
    breed       NVARCHAR(60)  NULL,
    sex         CHAR(1)       NOT NULL,
    birth_date  DATE          NULL,
    weight_kg   DECIMAL(6,2)  NULL,
    chip_number CHAR(15)      NULL,
    CONSTRAINT pk_pet         PRIMARY KEY (pet_id),
    CONSTRAINT fk_pet_owner   FOREIGN KEY (owner_id)   REFERENCES owner (owner_id),
    CONSTRAINT fk_pet_species FOREIGN KEY (species_id) REFERENCES species (species_id),
    CONSTRAINT ck_pet_name    CHECK (LEN(TRIM(pet_name)) > 0),
    CONSTRAINT ck_pet_sex     CHECK (sex IN ('M', 'F')),
    CONSTRAINT ck_pet_birth   CHECK (birth_date BETWEEN '1980-01-01' AND CAST(GETDATE() AS DATE)),
    CONSTRAINT ck_pet_weight  CHECK (weight_kg > 0 AND weight_kg < 1000),
    CONSTRAINT ck_pet_chip    CHECK (LEN(chip_number) = 15 AND chip_number NOT LIKE '%[^0-9]%')
);
CREATE UNIQUE INDEX ux_pet_chip ON pet (chip_number) WHERE chip_number IS NOT NULL;
CREATE INDEX ix_pet_owner   ON pet (owner_id);
CREATE INDEX ix_pet_species ON pet (species_id);

-- ---------------------------------------------------------------------
-- 4. Специализация врача (справочник)
-- ---------------------------------------------------------------------
CREATE TABLE specialization (
    specialization_id   INT          IDENTITY(1,1) NOT NULL,
    specialization_name NVARCHAR(60) NOT NULL,
    CONSTRAINT pk_specialization      PRIMARY KEY (specialization_id),
    CONSTRAINT uq_specialization_name UNIQUE (specialization_name),
    CONSTRAINT ck_specialization_name CHECK (LEN(TRIM(specialization_name)) > 0)
);

-- ---------------------------------------------------------------------
-- 5. Врач
-- ---------------------------------------------------------------------
CREATE TABLE vet (
    vet_id            INT          IDENTITY(1,1) NOT NULL,
    specialization_id INT          NOT NULL,
    last_name         NVARCHAR(50) NOT NULL,
    first_name        NVARCHAR(50) NOT NULL,
    middle_name       NVARCHAR(50) NULL,
    phone             VARCHAR(12)  NOT NULL,
    hire_date         DATE         NOT NULL,
    is_active         BIT          NOT NULL CONSTRAINT df_vet_active DEFAULT 1,
    CONSTRAINT pk_vet                PRIMARY KEY (vet_id),
    CONSTRAINT fk_vet_specialization FOREIGN KEY (specialization_id)
                                     REFERENCES specialization (specialization_id),
    CONSTRAINT uq_vet_phone          UNIQUE (phone),
    CONSTRAINT ck_vet_names          CHECK (LEN(TRIM(last_name)) > 0 AND LEN(TRIM(first_name)) > 0),
    CONSTRAINT ck_vet_phone          CHECK (phone LIKE '+7[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
    CONSTRAINT ck_vet_hire_date      CHECK (hire_date BETWEEN '1980-01-01' AND CAST(GETDATE() AS DATE))
);
CREATE INDEX ix_vet_specialization ON vet (specialization_id);

-- ---------------------------------------------------------------------
-- 6. Услуга клиники (текущий прайс)
-- ---------------------------------------------------------------------
CREATE TABLE service (
    service_id   INT            IDENTITY(1,1) NOT NULL,
    service_name NVARCHAR(100)  NOT NULL,
    price        DECIMAL(10,2)  NOT NULL,
    is_active    BIT            NOT NULL CONSTRAINT df_service_active DEFAULT 1,
    CONSTRAINT pk_service       PRIMARY KEY (service_id),
    CONSTRAINT uq_service_name  UNIQUE (service_name),
    CONSTRAINT ck_service_name  CHECK (LEN(TRIM(service_name)) > 0),
    CONSTRAINT ck_service_price CHECK (price >= 0)
);

-- ---------------------------------------------------------------------
-- 7. Приём (запись питомца к врачу)
-- ---------------------------------------------------------------------
CREATE TABLE appointment (
    appointment_id INT            IDENTITY(1,1) NOT NULL,
    pet_id         INT            NOT NULL,
    vet_id         INT            NOT NULL,
    start_time     DATETIME2(0)   NOT NULL,
    status         VARCHAR(10)    NOT NULL CONSTRAINT df_appointment_status DEFAULT 'SCHEDULED',
    complaint      NVARCHAR(500)  NULL,
    diagnosis      NVARCHAR(500)  NULL,
    CONSTRAINT pk_appointment        PRIMARY KEY (appointment_id),
    CONSTRAINT fk_appointment_pet    FOREIGN KEY (pet_id) REFERENCES pet (pet_id),
    CONSTRAINT fk_appointment_vet    FOREIGN KEY (vet_id) REFERENCES vet (vet_id),
    CONSTRAINT ck_appointment_status CHECK (status IN ('SCHEDULED', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_appointment_slot   CHECK (DATEPART(HOUR, start_time) BETWEEN 8 AND 19
                                       AND DATEPART(MINUTE, start_time) IN (0, 30)
                                       AND DATEPART(SECOND, start_time) = 0),
    CONSTRAINT ck_appointment_done   CHECK (status <> 'DONE'
                                       OR (diagnosis IS NOT NULL AND LEN(TRIM(diagnosis)) > 0))
);
CREATE UNIQUE INDEX ux_appointment_vet_slot ON appointment (vet_id, start_time)
    WHERE status IN ('SCHEDULED', 'DONE');
CREATE UNIQUE INDEX ux_appointment_pet_slot ON appointment (pet_id, start_time)
    WHERE status IN ('SCHEDULED', 'DONE');
CREATE INDEX ix_appointment_start ON appointment (start_time);

-- ---------------------------------------------------------------------
-- 8. Услуга, оказанная на приёме (разрешение связи M:N appointment—service)
-- ---------------------------------------------------------------------
CREATE TABLE appointment_service (
    appointment_id INT           NOT NULL,
    service_id     INT           NOT NULL,
    quantity       SMALLINT      NOT NULL CONSTRAINT df_aps_quantity DEFAULT 1,
    price          DECIMAL(10,2) NOT NULL,   -- цена на момент оказания
    CONSTRAINT pk_appointment_service PRIMARY KEY (appointment_id, service_id),
    CONSTRAINT fk_aps_appointment     FOREIGN KEY (appointment_id)
                                      REFERENCES appointment (appointment_id) ON DELETE CASCADE,
    CONSTRAINT fk_aps_service         FOREIGN KEY (service_id) REFERENCES service (service_id),
    CONSTRAINT ck_aps_quantity        CHECK (quantity BETWEEN 1 AND 100),
    CONSTRAINT ck_aps_price           CHECK (price >= 0)
);
CREATE INDEX ix_aps_service ON appointment_service (service_id);
GO

-- ---------------------------------------------------------------------
-- Триггер приёма: BR-15, BR-16, BR-17. Ошибка THROW внутри триггера
-- откатывает всю транзакцию, поэтому изменения не сохраняются.
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_appointment_rules ON appointment
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN deleted d ON d.appointment_id = i.appointment_id
               WHERE d.status IN ('DONE', 'CANCELLED') AND i.status <> d.status)
    BEGIN
        ;THROW 50001, N'Приём уже проведён или отменён: изменить его статус нельзя', 1;
    END;

    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN vet v ON v.vet_id = i.vet_id
               LEFT JOIN deleted d ON d.appointment_id = i.appointment_id
               WHERE i.status = 'SCHEDULED' AND v.is_active = 0
                 AND (d.appointment_id IS NULL OR d.vet_id <> i.vet_id OR d.start_time <> i.start_time))
    BEGIN
        ;THROW 50002, N'Врач не работает в клинике: запись к нему невозможна', 1;
    END;

    IF EXISTS (SELECT 1
               FROM inserted i
               WHERE i.status = 'CANCELLED'
                 AND EXISTS (SELECT 1 FROM appointment_service s
                             WHERE s.appointment_id = i.appointment_id))
    BEGIN
        ;THROW 50003, N'По приёму уже оказаны услуги: отменить его нельзя', 1;
    END;
END;
GO

-- ---------------------------------------------------------------------
-- Триггер услуг приёма: BR-17
-- ---------------------------------------------------------------------
CREATE TRIGGER trg_appointment_service_rules ON appointment_service
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN appointment a ON a.appointment_id = i.appointment_id
               WHERE a.status = 'CANCELLED')
    BEGIN
        ;THROW 50004, N'Приём отменён: добавлять в него услуги нельзя', 1;
    END;

    -- новая услуга в приёме = строки inserted, которых не было в deleted
    IF EXISTS (SELECT 1
               FROM inserted i
               JOIN service s ON s.service_id = i.service_id
               WHERE s.is_active = 0
                 AND NOT EXISTS (SELECT 1 FROM deleted d
                                 WHERE d.appointment_id = i.appointment_id
                                   AND d.service_id = i.service_id))
    BEGIN
        ;THROW 50005, N'Услуга больше не оказывается: добавить её нельзя', 1;
    END;
END;
GO

-- ---------------------------------------------------------------------
-- Представление: сумма счёта по каждому приёму
-- ---------------------------------------------------------------------
CREATE VIEW v_appointment_total AS
SELECT a.appointment_id,
       a.start_time,
       a.status,
       a.pet_id,
       a.vet_id,
       COUNT(s.service_id)                    AS service_count,
       COALESCE(SUM(s.price * s.quantity), 0) AS total_amount
FROM appointment a
LEFT JOIN appointment_service s ON s.appointment_id = a.appointment_id
GROUP BY a.appointment_id, a.start_time, a.status, a.pet_id, a.vet_id;
GO
