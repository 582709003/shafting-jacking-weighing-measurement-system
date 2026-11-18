package com.shafting.weighing.measurement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public final class ConfigDto {

    private ConfigDto() {
    }

    /** 设置页和工作页使用的测量点基础信息。 */
    public record PointResponse(
            /** 测量点主键。 */
            Long id,
            /** 程序使用的稳定编码，例如 TAIL_SHAFT。 */
            String pointCode,
            /** 页面显示名称，例如“尾轴”。 */
            String pointName,
            /** 测量点分类：轴系或主机。 */
            String pointType,
            /** 页面从左到右的显示顺序。 */
            Integer sortNo) {
    }

    /** 设置页中一个测量点的已保存参数。 */
    public record PointConfigResponse(
            /** 测量点编码。 */
            String pointCode,
            /** 测量点显示名称。 */
            String pointName,
            /** 标准顶升力，单位 kN。 */
            BigDecimal standardForce,
            /** 当前通道总值，单位 kN、精度 0.01；仅记录，不参与计算。 */
            BigDecimal totalValue,
            /** 工作页手动上升保存的目标位移，单位 mm、精度 0.01；0 表示未设置。 */
            BigDecimal manualTargetDisplacement,
            /** 将拟合截距换算为测量值的顶升系数。 */
            BigDecimal jackingCoefficient,
            /** 自动测试目标位移兼安全上限，单位 mm。 */
            BigDecimal maxDisplacement,
            /** 模拟或设备运行转速，单位 r/min。 */
            Integer motorRpm,
            /** 自动顶升累计力值间隔，单位 kN，只允许 1～32767 的正整数。 */
            BigDecimal forceIntervalKn,
            /** 到达目标后的保持时间，单位秒。 */
            Integer holdSeconds) {
    }

    /** 设置页读取或保存后返回的完整参数方案。 */
    public record ConfigResponse(
            /** 参数方案主键。 */
            Long id,
            /** 参数方案名称。 */
            String configName,
            /** 每次保存后递增的版本号。 */
            Integer configVersion,
            /** 参数方案状态，例如 DRAFT。 */
            String status,
            /** 六个测量点的参数。 */
            List<PointConfigResponse> points) {
    }

    /** 设置页提交的单个测量点参数。 */
    public record PointConfigRequest(
            /** 测量点编码，必须属于当前启用的六点集合。 */
            @NotBlank String pointCode,
            /** 标准顶升力，单位 kN，必须大于 0。 */
            @NotNull(message = "标准值必须大于0") @DecimalMin(value = "0", inclusive = false) BigDecimal standardForce,
            /** 当前通道总值，单位 kN、精度 0.01；仅保存数据库，必须大于 0。 */
            @NotNull(message = "总值必须大于0") @DecimalMin(value = "0", inclusive = false) BigDecimal totalValue,
            /** 顶升系数，必须大于 0。 */
            @NotNull(message = "顶升系数必须大于0") @DecimalMin(value = "0", inclusive = false) BigDecimal jackingCoefficient,
            /** 自动测试目标位移兼安全上限，单位 mm，必须大于 0。 */
            @NotNull(message = "自动目标位移必须大于0") @DecimalMin(value = "0", inclusive = false) BigDecimal maxDisplacement,
            /** 转速，单位 r/min，必须为正整数。 */
            @NotNull(message = "顶升速度必须大于0且是整数") @Positive Integer motorRpm,
            /** 自动顶升累计力值间隔，单位 kN，只允许 1～32767 的正整数。 */
            @NotNull(message = "力值间隔必须大于0且是整数")
            @DecimalMin(value = "1", message = "力值间隔必须大于0且是整数")
            @DecimalMax(value = "32767", message = "力值间隔不能超过32767kN")
            @Digits(integer = 5, fraction = 0, message = "力值间隔必须是整数") BigDecimal forceIntervalKn,
            /** 保持时间，单位秒，必须为正整数。 */
            @NotNull(message = "时间间隔必须大于0且是整数") @Positive Integer holdSeconds) {
    }

    /** 设置页一次保存提交的完整参数方案。 */
    public record UpdateConfigRequest(
            /** 参数方案名称。 */
            @NotBlank String configName,
            /** 必须包含全部六个测量点且不能重复。 */
            @NotEmpty List<@Valid PointConfigRequest> points) {
    }

    /** 工作页保存单个通道手动目标位移的请求。 */
    public record ManualTargetRequest(
            /** 手动上升目标位移，单位 mm、精度 0.01，必须大于 0。 */
            @NotNull @jakarta.validation.constraints.DecimalMin("0.01") BigDecimal targetDisplacementMm) {
    }
}
