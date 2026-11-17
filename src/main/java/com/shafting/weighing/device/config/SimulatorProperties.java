package com.shafting.weighing.device.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "app.simulator")
/** application.yml 中 app.simulator 配置的类型化映射。 */
public class SimulatorProperties {

    /** 是否启用定时模拟。测试环境关闭。 */
    private boolean enabled = true;
    /** 无真实传感器读数时是否生成手动动作实时值。 */
    private boolean manualEnabled = true;
    /** 采样和推送周期，单位毫秒。 */
    private long intervalMs = 500;
    /** 启动检查阶段时长，单位毫秒。 */
    private long precheckMs = 1_000;
    /** 顶升阶段时长，单位毫秒。 */
    private long liftingMs = 8_000;
    /** 下降阶段时长，单位毫秒。 */
    private long loweringMs = 8_000;
    /** 参数方案标准力为 0 时使用的模拟峰值力，单位 kN。 */
    private BigDecimal fallbackForceKn = new BigDecimal("180.0");
    /** 仅用于生成模拟压力采样的峰值，单位 MPa，不作为业务停止条件。 */
    private BigDecimal fallbackPressureMpa = new BigDecimal("15.00");
    /** 手动设备模拟动作期间显示的电机转速，单位 r/min。 */
    private int manualMotorRpm = 800;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isManualEnabled() {
        return manualEnabled;
    }

    public void setManualEnabled(boolean manualEnabled) {
        this.manualEnabled = manualEnabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public long getPrecheckMs() {
        return precheckMs;
    }

    public void setPrecheckMs(long precheckMs) {
        this.precheckMs = precheckMs;
    }

    public long getLiftingMs() {
        return liftingMs;
    }

    public void setLiftingMs(long liftingMs) {
        this.liftingMs = liftingMs;
    }

    public long getLoweringMs() {
        return loweringMs;
    }

    public void setLoweringMs(long loweringMs) {
        this.loweringMs = loweringMs;
    }

    public BigDecimal getFallbackForceKn() {
        return fallbackForceKn;
    }

    public void setFallbackForceKn(BigDecimal fallbackForceKn) {
        this.fallbackForceKn = fallbackForceKn;
    }

    public BigDecimal getFallbackPressureMpa() {
        return fallbackPressureMpa;
    }

    public void setFallbackPressureMpa(BigDecimal fallbackPressureMpa) {
        this.fallbackPressureMpa = fallbackPressureMpa;
    }

    public int getManualMotorRpm() {
        return manualMotorRpm;
    }

    public void setManualMotorRpm(int manualMotorRpm) {
        this.manualMotorRpm = manualMotorRpm;
    }
}
