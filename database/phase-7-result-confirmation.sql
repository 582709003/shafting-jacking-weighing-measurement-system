-- 第七阶段增量迁移：显示数值页显式保存、逻辑删除和曲线采样可见性。
SET @result_status_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'measurement_result' AND COLUMN_NAME = 'result_status'
);
SET @result_status_sql = IF(
    @result_status_exists = 0,
    'ALTER TABLE measurement_result ADD COLUMN result_status VARCHAR(16) NOT NULL DEFAULT ''DRAFT'' AFTER revision_no',
    'SELECT 1'
);
PREPARE result_status_statement FROM @result_status_sql;
EXECUTE result_status_statement;
DEALLOCATE PREPARE result_status_statement;

SET @saved_by_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'measurement_result' AND COLUMN_NAME = 'saved_by'
);
SET @saved_by_sql = IF(
    @saved_by_exists = 0,
    'ALTER TABLE measurement_result ADD COLUMN saved_by BIGINT NULL AFTER result_status',
    'SELECT 1'
);
PREPARE saved_by_statement FROM @saved_by_sql;
EXECUTE saved_by_statement;
DEALLOCATE PREPARE saved_by_statement;

SET @saved_at_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'measurement_result' AND COLUMN_NAME = 'saved_at'
);
SET @saved_at_sql = IF(
    @saved_at_exists = 0,
    'ALTER TABLE measurement_result ADD COLUMN saved_at TIMESTAMP(3) NULL AFTER saved_by',
    'SELECT 1'
);
PREPARE saved_at_statement FROM @saved_at_sql;
EXECUTE saved_at_statement;
DEALLOCATE PREPARE saved_at_statement;

SET @result_deleted_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'measurement_result' AND COLUMN_NAME = 'deleted'
);
SET @result_deleted_sql = IF(
    @result_deleted_exists = 0,
    'ALTER TABLE measurement_result ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 AFTER saved_at',
    'SELECT 1'
);
PREPARE result_deleted_statement FROM @result_deleted_sql;
EXECUTE result_deleted_statement;
DEALLOCATE PREPARE result_deleted_statement;

SET @saved_by_fk_exists = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'measurement_result'
      AND CONSTRAINT_NAME = 'fk_result_saved_by'
);
SET @saved_by_fk_sql = IF(
    @saved_by_fk_exists = 0,
    'ALTER TABLE measurement_result ADD CONSTRAINT fk_result_saved_by FOREIGN KEY (saved_by) REFERENCES sys_user (id)',
    'SELECT 1'
);
PREPARE saved_by_fk_statement FROM @saved_by_fk_sql;
EXECUTE saved_by_fk_statement;
DEALLOCATE PREPARE saved_by_fk_statement;

-- 迁移前已经存在的结果视为历史已保存数据，避免升级后历史曲线突然不可见。
UPDATE measurement_result
SET result_status = 'SAVED', saved_at = COALESCE(saved_at, updated_at)
WHERE @result_status_exists = 0 AND deleted = 0 AND result_status = 'DRAFT';
