CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
);

CREATE TABLE IF NOT EXISTS measurement_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_code VARCHAR(32) NOT NULL,
    point_name VARCHAR(32) NOT NULL,
    point_type VARCHAR(32) NOT NULL,
    sort_no INT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_measurement_point_code UNIQUE (point_code),
    CONSTRAINT uk_measurement_point_sort UNIQUE (sort_no)
);

CREATE TABLE IF NOT EXISTS test_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL,
    config_version INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS test_config_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    standard_force DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_value DECIMAL(12, 2) NOT NULL DEFAULT 0,
    manual_target_displacement DECIMAL(10, 2) NOT NULL DEFAULT 0,
    jacking_coefficient DECIMAL(12, 4) NOT NULL DEFAULT 1,
    max_displacement DECIMAL(10, 2) NOT NULL DEFAULT 0,
    motor_rpm INT NOT NULL DEFAULT 0,
    pressure_interval_mpa DECIMAL(12, 4) NOT NULL DEFAULT 0,
    hold_seconds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_config_point UNIQUE (config_id, point_id),
    CONSTRAINT fk_config_point_config FOREIGN KEY (config_id) REFERENCES test_config (id),
    CONSTRAINT fk_config_point_point FOREIGN KEY (point_id) REFERENCES measurement_point (id)
);

CREATE TABLE IF NOT EXISTS test_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no VARCHAR(40) NOT NULL,
    task_name VARCHAR(100) NOT NULL,
    config_id BIGINT NOT NULL,
    operator_id BIGINT,
    task_status VARCHAR(16) NOT NULL,
    started_at TIMESTAMP(3),
    completed_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_test_task_no UNIQUE (task_no),
    CONSTRAINT fk_task_config FOREIGN KEY (config_id) REFERENCES test_config (id),
    CONSTRAINT fk_task_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
);

CREATE TABLE IF NOT EXISTS test_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    run_no VARCHAR(48) NOT NULL,
    mode VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    process_stage VARCHAR(24) NOT NULL DEFAULT 'WAITING',
    started_at TIMESTAMP(3),
    ended_at TIMESTAMP(3),
    fault_code VARCHAR(64),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_test_run_no UNIQUE (run_no),
    CONSTRAINT fk_run_task FOREIGN KEY (task_id) REFERENCES test_task (id),
    CONSTRAINT fk_run_point FOREIGN KEY (point_id) REFERENCES measurement_point (id)
);

CREATE TABLE IF NOT EXISTS device_command (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    command_no VARCHAR(64) NOT NULL,
    run_id BIGINT NOT NULL,
    command_type VARCHAR(16) NOT NULL,
    payload_json TEXT,
    status VARCHAR(16) NOT NULL,
    sent_at TIMESTAMP(3) NOT NULL,
    acked_at TIMESTAMP(3),
    CONSTRAINT uk_device_command_no UNIQUE (command_no),
    CONSTRAINT fk_command_run FOREIGN KEY (run_id) REFERENCES test_run (id)
);

CREATE TABLE IF NOT EXISTS measurement_sample (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    stage VARCHAR(24) NOT NULL,
    sequence_no BIGINT NOT NULL,
    captured_at TIMESTAMP(3) NOT NULL,
    force_kn DECIMAL(12, 1) NOT NULL,
    displacement_mm DECIMAL(10, 2) NOT NULL,
    raw_payload_json TEXT,
    valid TINYINT NOT NULL DEFAULT 1,
    CONSTRAINT uk_sample_sequence UNIQUE (run_id, sequence_no),
    CONSTRAINT fk_sample_run FOREIGN KEY (run_id) REFERENCES test_run (id),
    CONSTRAINT fk_sample_point FOREIGN KEY (point_id) REFERENCES measurement_point (id)
);

CREATE TABLE IF NOT EXISTS curve_fit_revision (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    source VARCHAR(16) NOT NULL,
    lifting_x1 DECIMAL(12, 2) NOT NULL,
    lifting_y1 DECIMAL(10, 2) NOT NULL,
    lifting_x2 DECIMAL(12, 2) NOT NULL,
    lifting_y2 DECIMAL(10, 2) NOT NULL,
    lowering_x1 DECIMAL(12, 2) NOT NULL,
    lowering_y1 DECIMAL(10, 2) NOT NULL,
    lowering_x2 DECIMAL(12, 2) NOT NULL,
    lowering_y2 DECIMAL(10, 2) NOT NULL,
    lifting_intercept DECIMAL(12, 2) NOT NULL,
    lowering_intercept DECIMAL(12, 2) NOT NULL,
    mean_intercept DECIMAL(12, 2) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_curve_revision UNIQUE (run_id, revision_no),
    CONSTRAINT fk_curve_run FOREIGN KEY (run_id) REFERENCES test_run (id),
    CONSTRAINT fk_curve_user FOREIGN KEY (created_by) REFERENCES sys_user (id)
);

CREATE TABLE IF NOT EXISTS measurement_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    standard_force_kn DECIMAL(12, 2) NOT NULL,
    measured_force_kn DECIMAL(12, 2) NOT NULL,
    deviation_kn DECIMAL(12, 2) NOT NULL,
    deviation_rate DECIMAL(12, 4),
    adjustment_coefficient DECIMAL(12, 4),
    adjustment_amount_mm DECIMAL(10, 2),
    revision_no INT NOT NULL,
    result_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    saved_by BIGINT,
    saved_at TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_result_run UNIQUE (run_id),
    CONSTRAINT fk_result_run FOREIGN KEY (run_id) REFERENCES test_run (id),
    CONSTRAINT fk_result_saved_by FOREIGN KEY (saved_by) REFERENCES sys_user (id)
);

CREATE TABLE IF NOT EXISTS alarm_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_code VARCHAR(64) NOT NULL,
    alarm_level VARCHAR(16) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    message VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    deduplication_key VARCHAR(64),
    occurred_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_alarm_open_deduplication UNIQUE (deduplication_key)
);

CREATE TABLE IF NOT EXISTS alarm_action (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_id BIGINT NOT NULL,
    action_type VARCHAR(24) NOT NULL,
    from_status VARCHAR(24) NOT NULL,
    to_status VARCHAR(24) NOT NULL,
    remark VARCHAR(255),
    acted_by BIGINT,
    acted_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT fk_alarm_action_event FOREIGN KEY (alarm_id) REFERENCES alarm_event (id),
    CONSTRAINT fk_alarm_action_user FOREIGN KEY (acted_by) REFERENCES sys_user (id)
);

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    module VARCHAR(32) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    business_id BIGINT,
    result VARCHAR(16) NOT NULL,
    detail_json TEXT,
    operated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_operation_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

