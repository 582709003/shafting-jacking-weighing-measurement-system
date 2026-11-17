package com.shafting.weighing.device.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 自动测试状态机与现场设备之间的命令边界。
 *
 * <p>当前尚未提供 PLC 协议和传感器点位，因此各方法只保留调用位置；
 * 接入真实设备时在本类内实现下发与读取，不修改自动流程状态机。</p>
 */
@Component
public class DeviceCommandGateway {

    /** 向指定通道下发上升命令。 */
    public void sendAscending(String pointCode) {
        // 真实设备指令待 PLC 协议确认后实现。
    }

    /** 向指定通道下发暂停命令。 */
    public void sendPause(String pointCode) {
        // 真实设备指令待 PLC 协议确认后实现。
    }

    /** 向指定通道下发下降命令。 */
    public void sendDescending(String pointCode) {
        // 真实设备指令待 PLC 协议确认后实现。
    }

    /** 向指定通道下发快泄命令。 */
    public void sendQuickRelease(String pointCode) {
        // 真实设备指令待 PLC 协议确认后实现。
    }

    /** 向指定通道下发停止命令。 */
    public void sendStop(String pointCode) {
        // 真实设备指令待 PLC 协议确认后实现。
    }

    /**
     * 同时读取指定通道的位移、称重和电机转速即时值。
     *
     * @param pointCode 测量点稳定编码
     * @return 真实设备读数；尚未接入设备时返回空，手动操作不会自行生成读数
     */
    public SensorReading readSensorValues(String pointCode) {
        return null;
    }
}

/** 一次同时读取的通道即时值，用于工作页实时显示和自动流程判断。 */
record SensorReading(
        /** 位移传感器读数，单位 mm、精度 0.01。 */
        BigDecimal displacementMm,
        /** 称重传感器顶升力，单位 kN、精度 0.1。 */
        BigDecimal forceKn,
        /** 电机转速即时值，单位 r/min。 */
        int motorRpm,
        /** 各项读数的统一采集时间。 */
        Instant capturedAt) {
}
