-- 第八阶段增量迁移：每个测量点增加仅记录、不参与计算的总值。
SET @total_value_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'total_value'
);
SET @total_value_sql = IF(
    @total_value_exists = 0,
    'ALTER TABLE test_config_point ADD COLUMN total_value DECIMAL(12, 2) NOT NULL DEFAULT 0 AFTER standard_force',
    'SELECT 1'
);
PREPARE total_value_statement FROM @total_value_sql;
EXECUTE total_value_statement;
DEALLOCATE PREPARE total_value_statement;
