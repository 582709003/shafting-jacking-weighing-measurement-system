SET @has_process_stage = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'test_run' AND column_name = 'process_stage'
);
SET @add_process_stage = IF(
    @has_process_stage = 0,
    'ALTER TABLE test_run ADD COLUMN process_stage VARCHAR(24) NOT NULL DEFAULT ''WAITING'' AFTER state',
    'SELECT 1'
);
PREPARE phase3_statement FROM @add_process_stage;
EXECUTE phase3_statement;
DEALLOCATE PREPARE phase3_statement;

CREATE TABLE IF NOT EXISTS measurement_sample (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    stage VARCHAR(24) NOT NULL,
    sequence_no BIGINT NOT NULL,
    captured_at TIMESTAMP(3) NOT NULL,
    pressure_mpa DECIMAL(10, 2) NOT NULL,
    force_kn DECIMAL(12, 1) NOT NULL,
    displacement_mm DECIMAL(10, 2) NOT NULL,
    raw_payload_json TEXT,
    valid TINYINT NOT NULL DEFAULT 1,
    CONSTRAINT uk_sample_sequence UNIQUE (run_id, sequence_no),
    CONSTRAINT fk_sample_run FOREIGN KEY (run_id) REFERENCES test_run (id),
    CONSTRAINT fk_sample_point FOREIGN KEY (point_id) REFERENCES measurement_point (id)
);
