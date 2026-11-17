CREATE TABLE IF NOT EXISTS alarm_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alarm_code VARCHAR(64) NOT NULL,
    alarm_level VARCHAR(16) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    message VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
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
