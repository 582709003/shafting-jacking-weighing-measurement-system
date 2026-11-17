-- 第九阶段增量迁移：为每个通道增加工作页手动目标位移。
SET @manual_target_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'test_config_point'
      AND COLUMN_NAME = 'manual_target_displacement'
);
SET @manual_target_sql = IF(
    @manual_target_exists = 0,
    'ALTER TABLE test_config_point ADD COLUMN manual_target_displacement DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER total_value',
    'SELECT 1'
);
PREPARE manual_target_statement FROM @manual_target_sql;
EXECUTE manual_target_statement;
DEALLOCATE PREPARE manual_target_statement;
