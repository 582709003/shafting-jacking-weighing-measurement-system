-- 创建一条可在“轴系曲线-尾轴”中直接查看的完整自动测试演示数据。
START TRANSACTION;

SET @config_id = 1;
SET @point_code = 'TAIL_SHAFT';
SET @point_id = (SELECT id FROM measurement_point WHERE point_code = @point_code AND deleted = 0 LIMIT 1);
SET @operator_id = (SELECT id FROM sys_user WHERE username = 'admin' AND deleted = 0 LIMIT 1);
SET @started_at = TIMESTAMPADD(SECOND, -18, NOW(3));
SET @ended_at = NOW(3);
SET @task_no = CONCAT('DEMO-AUTO-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'), '-',
        UPPER(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 6)));

UPDATE test_config_point
SET standard_force = 120.00,
    total_value = 600.00,
    jacking_coefficient = 1.0000,
    max_displacement = 1.00,
    motor_rpm = 200,
    force_interval_kn = 20.0000,
    hold_seconds = 1
WHERE config_id = @config_id
  AND point_id = @point_id
  AND deleted = 0;

UPDATE test_config
SET config_version = config_version + 1
WHERE id = @config_id
  AND deleted = 0;

INSERT INTO test_task
    (task_no, task_name, config_id, operator_id, task_status, started_at, completed_at)
VALUES
    (@task_no, '尾轴自动测试曲线演示', @config_id, @operator_id, 'COMPLETED', @started_at, @ended_at);
SET @task_id = LAST_INSERT_ID();

INSERT INTO test_run
    (task_id, point_id, run_no, mode, state, process_stage, started_at, ended_at)
VALUES
    (@task_id, @point_id, CONCAT(@task_no, '-01'), 'AUTOMATIC', 'COMPLETED', 'COMPLETED',
     @started_at, @ended_at);
SET @run_id = LAST_INSERT_ID();

INSERT INTO device_command
    (command_no, run_id, command_type, payload_json, status, sent_at, acked_at)
VALUES
    (CONCAT('DEMO-START-', @run_id), @run_id, 'START',
     '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 'ACKED', @started_at, @started_at);

-- 上升阶段：从零逐级顶升至 1.00 mm。
INSERT INTO measurement_sample
    (run_id, point_id, stage, sequence_no, captured_at,
     force_kn, displacement_mm, raw_payload_json, valid)
VALUES
    (@run_id, @point_id, 'LIFTING',  1, TIMESTAMPADD(SECOND,  0, @started_at),   0.0, 0.00, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LIFTING',  2, TIMESTAMPADD(SECOND,  1, @started_at),  20.0, 0.03, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LIFTING',  3, TIMESTAMPADD(SECOND,  2, @started_at),  40.0, 0.07, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LIFTING',  4, TIMESTAMPADD(SECOND,  3, @started_at),  60.0, 0.13, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LIFTING',  5, TIMESTAMPADD(SECOND,  4, @started_at),  80.0, 0.24, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LIFTING',  6, TIMESTAMPADD(SECOND,  5, @started_at), 100.0, 0.43, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LIFTING',  7, TIMESTAMPADD(SECOND,  6, @started_at), 120.0, 0.68, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LIFTING',  8, TIMESTAMPADD(SECOND,  7, @started_at), 140.0, 0.90, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LIFTING',  9, TIMESTAMPADD(SECOND,  8, @started_at), 150.0, 1.00, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),

-- 下降阶段：从最高位移逐级卸载，最终力值和位移均回到零。
    (@run_id, @point_id, 'LOWERING', 10, TIMESTAMPADD(SECOND,  9, @started_at), 145.0, 1.00, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LOWERING', 11, TIMESTAMPADD(SECOND, 10, @started_at), 130.0, 0.91, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LOWERING', 12, TIMESTAMPADD(SECOND, 11, @started_at), 115.0, 0.72, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LOWERING', 13, TIMESTAMPADD(SECOND, 12, @started_at),  95.0, 0.49, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LOWERING', 14, TIMESTAMPADD(SECOND, 13, @started_at),  75.0, 0.29, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LOWERING', 15, TIMESTAMPADD(SECOND, 14, @started_at),  55.0, 0.16, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LOWERING', 16, TIMESTAMPADD(SECOND, 15, @started_at),  35.0, 0.08, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LOWERING', 17, TIMESTAMPADD(SECOND, 16, @started_at),  15.0, 0.03, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1),
    (@run_id, @point_id, 'LOWERING', 18, TIMESTAMPADD(SECOND, 17, @started_at),   0.0, 0.00, '{"source":"DEMO_AUTOMATIC_FULL_PROCESS"}', 1);

COMMIT;

SELECT @task_id AS task_id, @run_id AS run_id, @task_no AS task_no;
