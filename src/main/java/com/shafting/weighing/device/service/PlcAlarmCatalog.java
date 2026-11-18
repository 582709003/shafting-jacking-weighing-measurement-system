package com.shafting.weighing.device.service;

import java.util.List;

/**
 * 点位表 0505 中 DB12 报警位的固定映射。
 *
 * <p>该目录只描述 PLC 已提供的报警地址和页面文案，不包含报警处理流程。</p>
 */
public final class PlcAlarmCatalog {

    private static final List<AlarmTag> TAGS = List.of(
            tag("PLC_PRESSURE_SENSOR_FAULT", 0, 0, "压力传感器故障"),
            tag("PLC_PRESSURE_SENSOR_DISCONNECTED", 0, 1, "压力传感器断线"),
            tag("PLC_DISPLACEMENT_SENSOR_FAULT", 0, 2, "位移传感器故障"),
            tag("PLC_LOAD_SENSOR_FAULT", 0, 3, "称重传感器故障"),
            tag("PLC_DISPLACEMENT_COMMUNICATION", 0, 4, "位移传感器通讯异常"),
            tag("PLC_LOAD_COMMUNICATION", 0, 5, "称重传感器通讯异常"),
            tag("PLC_VALVE_7_1_NO_ACTION", 0, 6, "7-1电磁换向阀无动作"),
            tag("PLC_VALVE_7_2_NO_ACTION", 0, 7, "7-2电磁换向阀无动作"),
            tag("PLC_VALVE_7_3_NO_ACTION", 1, 0, "7-3电磁换向阀无动作"),
            tag("PLC_VALVE_7_4_NO_ACTION", 1, 1, "7-4电磁换向阀无动作"),
            tag("PLC_VALVE_7_5_NO_ACTION", 1, 2, "7-5电磁换向阀无动作"),
            tag("PLC_VALVE_7_6_NO_ACTION", 1, 3, "7-6电磁换向阀无动作"),
            tag("PLC_VALVE_7_7_NO_ACTION", 1, 4, "7-7电磁换向阀无动作"),
            tag("PLC_VALVE_7_8_NO_ACTION", 1, 5, "7-8电磁换向阀无动作"),
            tag("PLC_CYLINDER_NO_ACTION", 1, 6, "油缸无动作")
    );

    private PlcAlarmCatalog() {
    }

    /** 返回按 Excel 点位顺序排列的全部报警位。 */
    public static List<AlarmTag> tags() {
        return TAGS;
    }

    private static AlarmTag tag(String code, int byteOffset, int bitOffset, String message) {
        return new AlarmTag(code, "%DB12.DBX" + byteOffset + "." + bitOffset + ":BOOL", message);
    }

    /** 单个 PLC 报警位的稳定编码、PLC4X 地址和页面说明。 */
    public record AlarmTag(String code, String address, String message) {
    }
}
