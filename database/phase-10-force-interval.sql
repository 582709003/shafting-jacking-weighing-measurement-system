-- 第十阶段增量迁移：将原 MPa 压力间隔替换为按 N 记录的累计力值间隔。
-- 两种单位无法可靠换算，旧库首次迁移后置 0，必须由操作员重新确认。
SET @force_interval_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'force_interval_n'
);
SET @pressure_step_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'pressure_step'
);
SET @force_interval_sql = IF(
    @force_interval_exists = 0,
    IF(
        @pressure_step_exists = 1,
        'ALTER TABLE test_config_point CHANGE COLUMN pressure_step force_interval_n DECIMAL(12, 2) NOT NULL DEFAULT 0',
        'ALTER TABLE test_config_point ADD COLUMN force_interval_n DECIMAL(12, 2) NOT NULL DEFAULT 0 AFTER motor_rpm'
    ),
    'SELECT 1'
);
PREPARE force_interval_statement FROM @force_interval_sql;
EXECUTE force_interval_statement;
DEALLOCATE PREPARE force_interval_statement;

SET @force_interval_reset_sql = IF(
    @force_interval_exists = 0,
    'UPDATE test_config_point SET force_interval_n = 0',
    'SELECT 1'
);
PREPARE force_interval_reset_statement FROM @force_interval_reset_sql;
EXECUTE force_interval_reset_statement;
DEALLOCATE PREPARE force_interval_reset_statement;
