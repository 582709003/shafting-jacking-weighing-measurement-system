ALTER TABLE alarm_event
    ADD COLUMN deduplication_key VARCHAR(64) NULL AFTER status;

UPDATE alarm_event
SET deduplication_key = alarm_code
WHERE status <> 'CLOSED'
  AND LEFT(alarm_code, 4) = 'PLC_';

ALTER TABLE alarm_event
    ADD CONSTRAINT uk_alarm_open_deduplication UNIQUE (deduplication_key);
