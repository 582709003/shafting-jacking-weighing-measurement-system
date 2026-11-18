package com.shafting.weighing.device.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 将《点位表0505》中的固定地址集中映射为 PLC4X S7 标签。 */
public final class PlcPointCatalog {

    private static final List<String> POINT_CODES = List.of(
            "TAIL_SHAFT", "INTERMEDIATE_1", "INTERMEDIATE_2",
            "FLYWHEEL", "MAIN_ENGINE_1", "MAIN_ENGINE_2");

    private static final Map<Control, String> CONTROLS = Map.ofEntries(
            Map.entry(Control.MANUAL, "%DB100.DBX0.0:BOOL"),
            Map.entry(Control.AUTO, "%DB100.DBX0.1:BOOL"),
            Map.entry(Control.QUICK_RELEASE, "%DB100.DBX0.2:BOOL"),
            Map.entry(Control.START, "%DB100.DBX0.3:BOOL"),
            Map.entry(Control.STOP, "%DB100.DBX0.4:BOOL"),
            Map.entry(Control.PAUSE, "%DB100.DBX0.5:BOOL"),
            Map.entry(Control.ASCEND, "%DB100.DBX0.6:BOOL"),
            Map.entry(Control.DESCEND, "%DB100.DBX0.7:BOOL"),
            Map.entry(Control.AUTO_TEST, "%DB100.DBX1.0:BOOL"),
            Map.entry(Control.AUTO_QUICK_RELEASE, "%DB100.DBX1.1:BOOL")
    );

    private static final Map<String, ChannelTags> CHANNELS = Map.of(
            "TAIL_SHAFT", tags(1, 0, 4, 12, 16, 196),
            "INTERMEDIATE_2", tags(2, 1, 20, 28, 32, 222),
            "INTERMEDIATE_1", tags(3, 2, 36, 44, 48, 248),
            "FLYWHEEL", tags(4, 3, 52, 60, 64, 274),
            "MAIN_ENGINE_1", tags(5, 4, 68, 76, 80, 300),
            "MAIN_ENGINE_2", tags(6, 5, 84, 92, 96, 326)
    );

    private PlcPointCatalog() { }

    /** 返回控制按钮标签。 */
    public static String control(Control control) {
        return CONTROLS.get(control);
    }

    /** 返回指定测量点的选择、实时和参数标签。 */
    public static ChannelTags channel(String pointCode) {
        ChannelTags tags = CHANNELS.get(pointCode);
        if (tags == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测量点没有 PLC 点位映射");
        }
        return tags;
    }

    /** 返回页面稳定顺序的六个测量点编码。 */
    public static List<String> pointCodes() {
        return POINT_CODES;
    }

    /** 将 PLC 当前通道反馈值转换为稳定测量点编码。 */
    public static String pointCode(int channelValue) {
        return CHANNELS.entrySet().stream()
                .filter(entry -> entry.getValue().channelValue() == channelValue)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static ChannelTags tags(int channelValue, int selectionBit, int displacement,
                                    int motorRpm, int force, int configStart) {
        return new ChannelTags(channelValue, "%DB100.DBX2." + selectionBit + ":BOOL",
                real(displacement), real(motorRpm), real(force), real(configStart),
                real(configStart + 4), real(configStart + 8), real(configStart + 12),
                "%DB100.DBW" + (configStart + 16) + ":INT", real(configStart + 18));
    }

    private static String real(int byteOffset) {
        return "%DB100.DBD" + byteOffset + ":REAL";
    }

    /** 点位表中的控制按钮。 */
    public enum Control {
        MANUAL, AUTO, QUICK_RELEASE, START, STOP, PAUSE, ASCEND, DESCEND, AUTO_TEST, AUTO_QUICK_RELEASE
    }

    /** 单个测量点的通道值、实时值和设置参数地址。 */
    public record ChannelTags(
            /** PLC反馈使用的通道编号，按点位位序为1到6。 */ int channelValue,
            /** 通道选择BOOL点。 */ String selection,
            /** 顶升距离REAL点，单位mm。 */ String displacement,
            /** 顶升速度REAL点，单位r/min。 */ String motorRpm,
            /** 顶升力REAL点，单位kN。 */ String force,
            /** 标准值REAL点，单位kN。 */ String standardForce,
            /** 顶升系数REAL点。 */ String jackingCoefficient,
            /** 自动目标位移REAL点，单位mm。 */ String maxDisplacement,
            /** 顶升速度设置REAL点，单位r/min。 */ String configuredMotorRpm,
            /** 力值间隔INT点，单位kN。 */ String forceInterval,
            /** 时间间隔REAL点，单位秒。 */ String holdSeconds) { }
}
