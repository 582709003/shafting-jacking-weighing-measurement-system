-- 第六阶段增量迁移：为六点参数增加压力上限。
-- 旧数据保留为 0，控制状态机据此阻止上升和自动测试，等待操作员在设置页确认后保存。
SET @max_pressure_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'max_pressure'
);
SET @max_pressure_sql = IF(
    @max_pressure_exists = 0,
    'ALTER TABLE test_config_point ADD COLUMN max_pressure DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER max_displacement',
    'SELECT 1'
);
PREPARE max_pressure_statement FROM @max_pressure_sql;
EXECUTE max_pressure_statement;
DEALLOCATE PREPARE max_pressure_statement;
