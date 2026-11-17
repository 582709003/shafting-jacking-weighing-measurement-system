-- 第十二阶段增量迁移：采样仅保留位移和顶升力，删除重复的压力字段。
SET @sample_pressure_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'measurement_sample'
      AND COLUMN_NAME = 'pressure_mpa'
);

SET @drop_sample_pressure_sql = IF(
    @sample_pressure_exists = 1,
    'ALTER TABLE measurement_sample DROP COLUMN pressure_mpa',
    'SELECT 1'
);
PREPARE drop_sample_pressure_statement FROM @drop_sample_pressure_sql;
EXECUTE drop_sample_pressure_statement;
DEALLOCATE PREPARE drop_sample_pressure_statement;
