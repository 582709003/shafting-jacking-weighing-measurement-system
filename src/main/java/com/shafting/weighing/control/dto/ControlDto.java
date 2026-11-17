package com.shafting.weighing.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/** 工作页控制面板请求、响应和状态枚举。 */
public final class ControlDto {

    private ControlDto() {
    }

    /** 工作页提交的统一控制命令。 */
    public record CommandRequest(
            /** 客户端生成的唯一命令号，用于审计定位；不允许为空。 */
            @NotBlank String commandNo,
            /** 控制命令类型。 */
            @NotNull CommandType commandType,
            /** 测量点编码；除纯全局停止命令外不允许为空。 */
            String pointCode,
            /** 兼容旧客户端的目标位移；手动上升以数据库已保存值为准，其他命令为空。 */
            BigDecimal targetDisplacementMm,
            /** 使用的参数方案主键；为空时使用默认方案 1。 */
            @Positive Long configId) {
    }

    /** 控制面板完整状态，由前端据此决定按钮颜色和可用性。 */
    public record ControlStateResponse(
            /** 是否因仍有未关闭报警而锁定。 */
            boolean alarmLocked,
            /** 全局动作；IDLE 或 AUTO_QUICK_RELEASE。 */
            GlobalAction globalAction,
            /** 全局自动快泄是否暂停；没有全局动作时固定为 false。 */
            boolean globalPaused,
            /** 按页面顺序返回的六个通道状态。 */
            List<ChannelState> channels) {
    }

    /** 单个测量点的控制状态。 */
    public record ChannelState(
            /** 测量点稳定编码。 */
            String pointCode,
            /** 页面显示名称。 */
            String pointName,
            /** 当前模式：OFF、MANUAL 或 AUTO。 */
            ControlMode mode,
            /** 当前通道动作；IDLE 表示未执行动作。 */
            ChannelAction action,
            /** 手动模式是否已经按下“启动”；停止动作不会清除此状态。 */
            boolean manualStarted,
            /** 当前动作是否暂停；恢复时继续暂停前的 action。 */
            boolean paused,
            /** 位移是否已清零且尚未被上升或自动测试消费。 */
            boolean zeroed,
            /** 手动上升目标位移，单位 mm、精度 0.01；无目标时为空。 */
            BigDecimal targetDisplacementMm,
            /** 当前关联的运行主键；没有已创建运行时为空。 */
            Long runId) {
    }

    /** 通道模式。 */
    public enum ControlMode {
        /** 手动和自动模式均断开。 */
        OFF,
        /** 手动模式接通。 */
        MANUAL,
        /** 自动模式接通。 */
        AUTO
    }

    /** 通道动作。 */
    public enum ChannelAction {
        /** 未执行动作。 */
        IDLE,
        /** 手动上升。 */
        ASCENDING,
        /** 手动下降。 */
        DESCENDING,
        /** 当前通道手动快泄。 */
        MANUAL_QUICK_RELEASE,
        /** 自动测试；是否暂停由 paused 字段表示。 */
        AUTO_TEST
    }

    /** 影响多个通道的动作。 */
    public enum GlobalAction {
        /** 没有全局动作。 */
        IDLE,
        /** 六个通道正在自动快泄。 */
        AUTO_QUICK_RELEASE
    }

    /** 控制面板支持的全部命令类型。 */
    public enum CommandType {
        /** 接通指定通道手动模式。 */
        MANUAL_CONNECT,
        /** 断开指定通道手动模式。 */
        MANUAL_DISCONNECT,
        /** 接通指定通道自动模式。 */
        AUTO_CONNECT,
        /** 断开指定通道自动模式。 */
        AUTO_DISCONNECT,
        /** 将指定通道位移显示清零。 */
        ZERO_DISPLACEMENT,
        /** 在手动模式下启动动作使能。 */
        MANUAL_ENABLE,
        /** 按目标位移执行手动上升。 */
        MANUAL_ASCEND,
        /** 执行当前通道手动下降。 */
        MANUAL_DESCEND,
        /** 执行当前通道手动快泄。 */
        MANUAL_QUICK_RELEASE,
        /** 暂停当前通道手动动作。 */
        MANUAL_PAUSE,
        /** 恢复当前通道暂停的手动动作。 */
        MANUAL_RESUME,
        /** 停止当前通道手动运行。 */
        MANUAL_STOP,
        /** 启动指定通道自动测试。 */
        AUTO_TEST_START,
        /** 启动六通道自动快泄。 */
        AUTO_QUICK_RELEASE,
        /** 暂停当前自动动作。 */
        AUTO_PAUSE,
        /** 恢复当前暂停的自动动作。 */
        AUTO_RESUME,
        /** 停止当前自动动作。 */
        AUTO_STOP
    }
}
