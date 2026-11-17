-- 第十一阶段增量迁移：力值间隔由 N 改为 kN，并保持实际阈值不变。
SET @force_interval_kn_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'force_interval_kn'
);
SET @force_interval_n_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'force_interval_n'
);

SET @force_interval_column_sql = IF(
    @force_interval_kn_exists = 0 AND @force_interval_n_exists = 1,
    'ALTER TABLE test_config_point CHANGE COLUMN force_interval_n force_interval_kn DECIMAL(12, 4) NOT NULL DEFAULT 0',
    IF(
        @force_interval_kn_exists = 0,
        'ALTER TABLE test_config_point ADD COLUMN force_interval_kn DECIMAL(12, 4) NOT NULL DEFAULT 0 AFTER motor_rpm',
        'SELECT 1'
    )
);
PREPARE force_interval_column_statement FROM @force_interval_column_sql;
EXECUTE force_interval_column_statement;
DEALLOCATE PREPARE force_interval_column_statement;

-- 只在本次由旧列迁移时换算，脚本重复执行不会再次除以 1000。
SET @force_interval_convert_sql = IF(
    @force_interval_kn_exists = 0 AND @force_interval_n_exists = 1,
    'UPDATE test_config_point SET force_interval_kn = force_interval_kn / 1000',
    'SELECT 1'
);
PREPARE force_interval_convert_statement FROM @force_interval_convert_sql;
EXECUTE force_interval_convert_statement;
DEALLOCATE PREPARE force_interval_convert_statement;
