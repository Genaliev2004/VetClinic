-- =====================================================================
-- ВетКлиник. Microsoft SQL Server. Шаг 4: примеры запросов (используются в приложении).
--   sqlcmd -S localhost -E -C -I -f 65001 -d vet_clinic -i sql\mssql\04_queries.sql
-- Отличие от PostgreSQL: склейка строк через CONCAT, литералы дат без слова DATE.
-- =====================================================================
SET NOCOUNT ON;

-- 1. Расписание врачей на день
SELECT a.start_time, CONCAT(v.last_name, N' ', v.first_name) AS vet, p.pet_name,
       s.species_name, o.last_name AS owner, a.status
FROM appointment a
JOIN vet v     ON v.vet_id = a.vet_id
JOIN pet p     ON p.pet_id = a.pet_id
JOIN species s ON s.species_id = p.species_id
JOIN owner o   ON o.owner_id = p.owner_id
WHERE a.start_time >= '2026-10-21' AND a.start_time < '2026-10-22'
ORDER BY a.start_time, vet;

-- 2. История болезни питомца
SELECT a.start_time, v.last_name AS vet, a.complaint, a.diagnosis, t.total_amount
FROM appointment a
JOIN vet v                 ON v.vet_id = a.vet_id
JOIN v_appointment_total t ON t.appointment_id = a.appointment_id
WHERE a.pet_id = 1 AND a.status = 'DONE'
ORDER BY a.start_time DESC;

-- 3. Счёт по приёму
SELECT sv.service_name, aps.quantity, aps.price, aps.quantity * aps.price AS amount
FROM appointment_service aps
JOIN service sv ON sv.service_id = aps.service_id
WHERE aps.appointment_id = 10
ORDER BY sv.service_name;

-- 4. Выручка по врачам за период
SELECT CONCAT(v.last_name, N' ', v.first_name) AS vet, sp.specialization_name,
       COUNT(DISTINCT a.appointment_id)           AS visits,
       COALESCE(SUM(aps.price * aps.quantity), 0) AS revenue
FROM vet v
JOIN specialization sp            ON sp.specialization_id = v.specialization_id
LEFT JOIN appointment a           ON a.vet_id = v.vet_id AND a.status = 'DONE'
                                 AND a.start_time >= '2026-01-01' AND a.start_time < '2027-01-01'
LEFT JOIN appointment_service aps ON aps.appointment_id = a.appointment_id
GROUP BY v.vet_id, v.last_name, v.first_name, sp.specialization_name
ORDER BY revenue DESC;

-- 5. Популярные услуги за период
SELECT sv.service_name, SUM(aps.quantity) AS times, SUM(aps.price * aps.quantity) AS revenue
FROM appointment_service aps
JOIN service sv     ON sv.service_id = aps.service_id
JOIN appointment a  ON a.appointment_id = aps.appointment_id
WHERE a.status = 'DONE' AND a.start_time >= '2026-01-01' AND a.start_time < '2027-01-01'
GROUP BY sv.service_id, sv.service_name
ORDER BY times DESC, revenue DESC;

-- 6. Владельцы, у которых больше одного питомца
SELECT o.last_name, o.first_name, o.phone, COUNT(*) AS pets
FROM owner o
JOIN pet p ON p.owner_id = o.owner_id
GROUP BY o.owner_id, o.last_name, o.first_name, o.phone
HAVING COUNT(*) > 1
ORDER BY pets DESC, o.last_name;
GO
