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
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_result_run UNIQUE (run_id),
    CONSTRAINT fk_result_run FOREIGN KEY (run_id) REFERENCES test_run (id)
);
