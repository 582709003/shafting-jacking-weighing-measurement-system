package com.shafting.weighing.device.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** application.yml 中真实 PLC 连接、轮询和新增点位的类型化配置。 */
@Component
@ConfigurationProperties(prefix = "app.plc")
public class PlcProperties {

    /** 设备运行模式；SIMULATOR 用于本机联调，PLC 用于现场设备。 */
    private Mode mode = Mode.SIMULATOR;
    /** S7-1200 IPv4 地址；PLC 模式必填。 */
    private String host;
    /** COTP 远端机架号；由现场 TIA 配置提供。 */
    private Integer rack;
    /** COTP 远端槽号；由现场 TIA 配置提供。 */
    private Integer slot;
    /** 建立 ISO-on-TCP 连接的超时时间，单位毫秒。 */
    private int connectTimeoutMs = 3_000;
    /** 单次 S7 读取的超时时间，单位毫秒。 */
    private int readTimeoutMs = 3_000;
    /** 单次 S7 写入的超时时间，单位毫秒。 */
    private int writeTimeoutMs = 3_000;
    /** BOOL 按钮脉冲保持时间，单位毫秒。 */
    private int pulseMs = 200;
    /** 等待 PLC 状态反馈的最长时间，单位毫秒。 */
    private int feedbackTimeoutMs = 3_000;
    /** PLC 状态和传感器轮询周期，单位毫秒。 */
    private int pollIntervalMs = 200;
    /** PLC 反馈的当前选择通道，INT；PLC 模式必填。 */
    private String feedbackSelectedChannel;
    /** PLC 反馈的当前模式，INT：0断开、1手动、2自动；PLC 模式必填。 */
    private String feedbackMode;
    /** PLC 反馈的当前动作，INT；PLC 模式必填。 */
    private String feedbackAction;
    /** PLC 反馈的运行状态，INT；PLC 模式必填。 */
    private String feedbackRunState;
    /** PLC 反馈的自动流程阶段，INT；PLC 模式必填。 */
    private String feedbackProcessStage;
    /** PLC 自动采样序号，DINT；PLC 模式必填。 */
    private String feedbackSampleSequence;
    /** PLC 故障编码，INT；PLC 模式必填。 */
    private String feedbackFaultCode;
    /** 各通道独立位移清零 BOOL 命令点。 */
    private Map<String, String> zeroDisplacement = new LinkedHashMap<>();
    /** 各通道独立手动目标位移 REAL 参数点。 */
    private Map<String, String> manualTarget = new LinkedHashMap<>();

    /** PLC 模式启动前校验现场必须提供的连接和新增点位，禁止使用猜测地址。 */
    @PostConstruct
    public void validatePlcConfiguration() {
        if (mode != Mode.PLC) {
            return;
        }
        requireText(host, "app.plc.host");
        if (rack == null || slot == null) {
            throw new IllegalStateException("PLC 模式必须配置 app.plc.rack 和 app.plc.slot");
        }
        requireText(feedbackSelectedChannel, "app.plc.feedback-selected-channel");
        requireText(feedbackMode, "app.plc.feedback-mode");
        requireText(feedbackAction, "app.plc.feedback-action");
        requireText(feedbackRunState, "app.plc.feedback-run-state");
        requireText(feedbackProcessStage, "app.plc.feedback-process-stage");
        requireText(feedbackSampleSequence, "app.plc.feedback-sample-sequence");
        requireText(feedbackFaultCode, "app.plc.feedback-fault-code");
        for (String pointCode : PlcPointCodes.ALL) {
            requireText(zeroDisplacement.get(pointCode), "app.plc.zero-displacement." + pointCode);
            requireText(manualTarget.get(pointCode), "app.plc.manual-target." + pointCode);
        }
    }

    private void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("PLC 模式缺少必填配置：" + propertyName);
        }
    }

    public boolean isPlcMode() {
        return mode == Mode.PLC;
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public Integer getRack() { return rack; }
    public void setRack(Integer rack) { this.rack = rack; }
    public Integer getSlot() { return slot; }
    public void setSlot(Integer slot) { this.slot = slot; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { connectTimeoutMs = value; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int value) { readTimeoutMs = value; }
    public int getWriteTimeoutMs() { return writeTimeoutMs; }
    public void setWriteTimeoutMs(int value) { writeTimeoutMs = value; }
    public int getPulseMs() { return pulseMs; }
    public void setPulseMs(int value) { pulseMs = value; }
    public int getFeedbackTimeoutMs() { return feedbackTimeoutMs; }
    public void setFeedbackTimeoutMs(int value) { feedbackTimeoutMs = value; }
    public int getPollIntervalMs() { return pollIntervalMs; }
    public void setPollIntervalMs(int value) { pollIntervalMs = value; }
    public String getFeedbackSelectedChannel() { return feedbackSelectedChannel; }
    public void setFeedbackSelectedChannel(String value) { feedbackSelectedChannel = value; }
    public String getFeedbackMode() { return feedbackMode; }
    public void setFeedbackMode(String value) { feedbackMode = value; }
    public String getFeedbackAction() { return feedbackAction; }
    public void setFeedbackAction(String value) { feedbackAction = value; }
    public String getFeedbackRunState() { return feedbackRunState; }
    public void setFeedbackRunState(String value) { feedbackRunState = value; }
    public String getFeedbackProcessStage() { return feedbackProcessStage; }
    public void setFeedbackProcessStage(String value) { feedbackProcessStage = value; }
    public String getFeedbackSampleSequence() { return feedbackSampleSequence; }
    public void setFeedbackSampleSequence(String value) { feedbackSampleSequence = value; }
    public String getFeedbackFaultCode() { return feedbackFaultCode; }
    public void setFeedbackFaultCode(String value) { feedbackFaultCode = value; }
    public Map<String, String> getZeroDisplacement() { return zeroDisplacement; }
    public void setZeroDisplacement(Map<String, String> value) { zeroDisplacement = value; }
    public Map<String, String> getManualTarget() { return manualTarget; }
    public void setManualTarget(Map<String, String> value) { manualTarget = value; }

    /** 支持的设备运行模式。 */
    public enum Mode { SIMULATOR, PLC }

    /** 六个稳定测量点编码，集中供配置完整性校验使用。 */
    private static final class PlcPointCodes {
        private static final String[] ALL = {
                "TAIL_SHAFT", "INTERMEDIATE_1", "INTERMEDIATE_2",
                "FLYWHEEL", "MAIN_ENGINE_1", "MAIN_ENGINE_2"
        };
        private PlcPointCodes() { }
    }
}
