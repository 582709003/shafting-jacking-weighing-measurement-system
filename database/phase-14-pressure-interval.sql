-- 第十四阶段增量迁移：恢复 PLC 实际使用的 MPa 压力间隔。
-- kN 力值无法在缺少油缸有效面积时可靠换算为 MPa，因此保留旧列并要求重新配置。
SET @pressure_interval_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'pressure_interval_mpa'
);

SET @pressure_step_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'pressure_step'
);

SET @pressure_interval_sql = IF(
    @pressure_interval_exists = 0,
    IF(
        @pressure_step_exists = 1,
        'ALTER TABLE test_config_point CHANGE COLUMN pressure_step pressure_interval_mpa DECIMAL(12, 4) NOT NULL DEFAULT 0',
        'ALTER TABLE test_config_point ADD COLUMN pressure_interval_mpa DECIMAL(12, 4) NOT NULL DEFAULT 0 AFTER motor_rpm'
    ),
    'SELECT 1'
);

PREPARE pressure_interval_statement FROM @pressure_interval_sql;
EXECUTE pressure_interval_statement;
DEALLOCATE PREPARE pressure_interval_statement;
