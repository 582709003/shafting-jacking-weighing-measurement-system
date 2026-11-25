INSERT INTO sys_role (id, role_code, role_name) VALUES
    (1, 'ADMIN', '管理员'),
    (2, 'ENGINEER', '工程师'),
    (3, 'OPERATOR', '操作员'),
    (4, 'VIEWER', '查看者')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO measurement_point (id, point_code, point_name, point_type, sort_no) VALUES
    (1, 'TAIL_SHAFT', '尾轴', 'SHAFTING', 1),
    (2, 'INTERMEDIATE_1', '中轴1', 'SHAFTING', 2),
    (3, 'INTERMEDIATE_2', '中轴2', 'SHAFTING', 3),
    (4, 'FLYWHEEL', '飞轮', 'SHAFTING', 4),
    (5, 'MAIN_ENGINE_1', '1#', 'MAIN_ENGINE', 5),
    (6, 'MAIN_ENGINE_2', '2#', 'MAIN_ENGINE', 6)
ON DUPLICATE KEY UPDATE point_name = VALUES(point_name), point_type = VALUES(point_type), sort_no = VALUES(sort_no);

INSERT INTO test_config (id, config_name, config_version, status) VALUES
    (1, '默认六点参数', 1, 'DRAFT')
ON DUPLICATE KEY UPDATE config_name = VALUES(config_name);

INSERT INTO test_config_point
    (id, config_id, point_id, standard_force, total_value, manual_target_displacement, jacking_coefficient, max_displacement, motor_rpm, pressure_interval_mpa, hold_seconds)
VALUES
    (1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0),
    (2, 1, 2, 0, 0, 0, 1, 0, 0, 0, 0),
    (3, 1, 3, 0, 0, 0, 1, 0, 0, 0, 0),
    (4, 1, 4, 0, 0, 0, 1, 0, 0, 0, 0),
    (5, 1, 5, 0, 0, 0, 1, 0, 0, 0, 0),
    (6, 1, 6, 0, 0, 0, 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE config_id = VALUES(config_id), point_id = VALUES(point_id);
