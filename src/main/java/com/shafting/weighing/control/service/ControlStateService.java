package com.shafting.weighing.control.service;

import com.shafting.weighing.control.dto.ControlDto;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保存工作页控制状态并执行不涉及数据库和设备的状态流转校验。
 *
 * <p>状态仅保存在单机内存中，应用启动时模式和动作均为断开状态。
 * 所有修改方法同步执行，保证同一进程内全局动作互斥。</p>
 */
@Service
public class ControlStateService {

    private final Map<String, MutableChannelState> channels = new LinkedHashMap<>();
    private boolean alarmLocked;
    private ControlDto.GlobalAction globalAction = ControlDto.GlobalAction.IDLE;
    private boolean globalPaused;

    public ControlStateService() {
        addChannel("TAIL_SHAFT", "尾轴");
        addChannel("INTERMEDIATE_1", "中轴1");
        addChannel("INTERMEDIATE_2", "中轴2");
        addChannel("FLYWHEEL", "飞轮");
        addChannel("MAIN_ENGINE_1", "1#");
        addChannel("MAIN_ENGINE_2", "2#");
    }

    /** 返回前端渲染所需的不可变状态快照。 */
    public synchronized ControlDto.ControlStateResponse snapshot() {
        return new ControlDto.ControlStateResponse(alarmLocked, globalAction, globalPaused,
                channels.values().stream().map(MutableChannelState::snapshot).toList());
    }

    /** 返回指定通道的不可变状态，主要供业务编排和测试使用。 */
    public synchronized ControlDto.ChannelState channel(String pointCode) {
        return requireChannel(pointCode).snapshot();
    }

    /**
     * 安全复位全部模式、动作、清零标记和运行关联。
     * 仅供服务启动、测试清理或调用方已经停止设备会话后使用。
     */
    public synchronized void resetControls() {
        resetChannels();
    }

    /** 报警触发后的强制复位；锁定会一直保持到全部报警关闭。 */
    public synchronized void alarmStop() {
        alarmLocked = true;
        resetChannels();
    }

    /** 全部报警关闭后只解除锁定，不自动恢复任何模式或动作。 */
    public synchronized void clearAlarmLock() {
        alarmLocked = false;
    }

    /** 接通指定通道的手动或自动模式，同一通道两种模式互斥。 */
    public synchronized void connectMode(String pointCode, ControlDto.ControlMode mode) {
        requireUnlocked();
        if (mode == ControlDto.ControlMode.OFF) {
            throw new IllegalArgumentException("接通模式不能为 OFF");
        }
        MutableChannelState channel = requireChannel(pointCode);
        if (activeChannel() != null || globalAction != ControlDto.GlobalAction.IDLE) {
            throw conflict("动作执行期间不能切换模式");
        }
        if (channel.mode != mode) {
            // 切换同一通道的手动/自动模式时，清除上一模式的使能和清零状态。
            channel.reset();
            channel.mode = mode;
        }
    }

    /** 断开指定模式；动作仍在执行时由业务层先停止运行。 */
    public synchronized void disconnectMode(String pointCode, ControlDto.ControlMode expectedMode) {
        MutableChannelState channel = requireChannel(pointCode);
        if (channel.mode != expectedMode) {
            throw conflict("当前通道未接通该模式");
        }
        if (globalAction != ControlDto.GlobalAction.IDLE) {
            throw conflict("请先停止自动快泄");
        }
        channel.reset();
    }

    /** 记录当前通道位移已清零；下一次手动上升或自动测试启动时消费。 */
    public synchronized void markZeroed(String pointCode) {
        requireUnlocked();
        MutableChannelState channel = requireChannel(pointCode);
        if (channel.mode == ControlDto.ControlMode.OFF) {
            throw conflict("请先选择手动或自动模式");
        }
        if (channel.action != ControlDto.ChannelAction.IDLE) {
            throw conflict("动作执行期间不能清零");
        }
        channel.zeroed = true;
    }

    /** 按下手动模式的启动按钮；之后可重复执行动作，直至模式断开或切换。 */
    public synchronized void startManual(String pointCode) {
        requireUnlocked();
        MutableChannelState channel = requireChannel(pointCode);
        if (channel.mode != ControlDto.ControlMode.MANUAL) {
            throw conflict("请先接通手动模式");
        }
        if (channel.action != ControlDto.ChannelAction.IDLE) {
            throw conflict("当前通道动作尚未停止");
        }
        channel.manualStarted = true;
    }

    /**
     * 开始单通道动作，并执行报警锁定、模式、全局互斥和清零校验。
     *
     * @param consumesZero 是否要求并消费一次新鲜的位移清零标记
     */
    public synchronized void beginAction(String pointCode, ControlDto.ChannelAction action,
                                         BigDecimal targetDisplacementMm, boolean consumesZero) {
        requireUnlocked();
        if (globalAction != ControlDto.GlobalAction.IDLE || activeChannel() != null) {
            throw conflict("已有通道正在执行动作");
        }
        MutableChannelState channel = requireChannel(pointCode);
        requireMode(channel, action);
        if (channel.mode == ControlDto.ControlMode.MANUAL && !channel.manualStarted) {
            throw conflict("请先按下手动启动按钮");
        }
        if (consumesZero && !channel.zeroed) {
            throw conflict("请先执行本通道位移清零");
        }
        if (consumesZero) {
            // 清零只对一次上升或自动测试有效，启动成功后立即消费。
            channel.zeroed = false;
        }
        channel.action = action;
        channel.paused = false;
        channel.targetDisplacementMm = targetDisplacementMm;
    }

    /** 关联动作创建的数据库运行。 */
    public synchronized void attachRun(String pointCode, Long runId) {
        requireChannel(pointCode).runId = runId;
    }

    /** 暂停当前单通道动作，保留原动作类型和运行关联供恢复使用。 */
    public synchronized void pauseChannelAction(String pointCode) {
        MutableChannelState channel = requireChannel(pointCode);
        if (channel.action == ControlDto.ChannelAction.IDLE || channel.paused) {
            throw conflict("当前通道没有可暂停的动作");
        }
        channel.paused = true;
    }

    /** 恢复当前通道暂停的动作。 */
    public synchronized void resumeChannelAction(String pointCode) {
        requireUnlocked();
        MutableChannelState channel = requireChannel(pointCode);
        if (channel.action == ControlDto.ChannelAction.IDLE || !channel.paused) {
            throw conflict("当前通道没有暂停的动作");
        }
        channel.paused = false;
    }

    /** 动作完成后恢复为空闲；runId 为空表示保留当前运行关联。 */
    public synchronized void finishAction(String pointCode, Long runId) {
        MutableChannelState channel = requireChannel(pointCode);
        channel.action = ControlDto.ChannelAction.IDLE;
        channel.paused = false;
        channel.targetDisplacementMm = null;
        if (runId != null && runId.equals(channel.runId)) {
            channel.runId = null;
        }
    }

    /** 启动六通道自动快泄；其他动作未停止时禁止启动。 */
    public synchronized void connectAutoQuickRelease(String pointCode) {
        requireUnlocked();
        MutableChannelState channel = requireChannel(pointCode);
        if (channel.mode != ControlDto.ControlMode.AUTO) {
            throw conflict("请先接通当前通道自动模式");
        }
        if (activeChannel() != null) {
            throw conflict("仍有通道动作未停止");
        }
        if (globalAction != ControlDto.GlobalAction.IDLE) {
            throw conflict("自动快泄已经启动");
        }
        globalAction = ControlDto.GlobalAction.AUTO_QUICK_RELEASE;
        globalPaused = false;
    }

    /** 暂停六通道自动快泄。 */
    public synchronized void pauseAutoQuickRelease() {
        if (globalAction != ControlDto.GlobalAction.AUTO_QUICK_RELEASE || globalPaused) {
            throw conflict("没有可暂停的自动快泄");
        }
        globalPaused = true;
    }

    /** 恢复六通道自动快泄。 */
    public synchronized void resumeAutoQuickRelease() {
        requireUnlocked();
        if (globalAction != ControlDto.GlobalAction.AUTO_QUICK_RELEASE || !globalPaused) {
            throw conflict("没有已暂停的自动快泄");
        }
        globalPaused = false;
    }

    /** 断开六通道自动快泄。 */
    public synchronized void disconnectAutoQuickRelease() {
        if (globalAction != ControlDto.GlobalAction.AUTO_QUICK_RELEASE) {
            throw conflict("自动快泄未接通");
        }
        globalAction = ControlDto.GlobalAction.IDLE;
        globalPaused = false;
    }

    /** 返回当前动作关联的运行主键，没有活动运行时为空。 */
    public synchronized Long activeRunId() {
        MutableChannelState active = activeChannel();
        return active == null ? null : active.runId;
    }

    /** 返回当前执行动作的通道编码，没有活动动作时为空。 */
    public synchronized String activePointCode() {
        MutableChannelState active = activeChannel();
        return active == null ? null : active.pointCode;
    }

    private void requireMode(MutableChannelState channel, ControlDto.ChannelAction action) {
        ControlDto.ControlMode required = switch (action) {
            case AUTO_TEST -> ControlDto.ControlMode.AUTO;
            case ASCENDING, DESCENDING, MANUAL_QUICK_RELEASE -> ControlDto.ControlMode.MANUAL;
            case IDLE -> throw new IllegalArgumentException("不能启动 IDLE 动作");
        };
        if (channel.mode != required) {
            throw conflict(required == ControlDto.ControlMode.AUTO ? "请先接通自动模式" : "请先接通手动模式");
        }
    }

    private void requireUnlocked() {
        if (alarmLocked) {
            throw conflict("存在未关闭报警，禁止执行控制操作");
        }
    }

    private MutableChannelState activeChannel() {
        return channels.values().stream()
                .filter(channel -> channel.action != ControlDto.ChannelAction.IDLE)
                .findFirst().orElse(null);
    }

    private MutableChannelState requireChannel(String pointCode) {
        MutableChannelState channel = channels.get(pointCode);
        if (channel == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测量点不存在或未启用");
        }
        return channel;
    }

    private void resetChannels() {
        globalAction = ControlDto.GlobalAction.IDLE;
        globalPaused = false;
        channels.values().forEach(MutableChannelState::reset);
    }

    private void addChannel(String pointCode, String pointName) {
        channels.put(pointCode, new MutableChannelState(pointCode, pointName));
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static final class MutableChannelState {

        private final String pointCode;
        private final String pointName;
        private ControlDto.ControlMode mode = ControlDto.ControlMode.OFF;
        private ControlDto.ChannelAction action = ControlDto.ChannelAction.IDLE;
        private boolean manualStarted;
        private boolean paused;
        private boolean zeroed;
        private BigDecimal targetDisplacementMm;
        private Long runId;

        private MutableChannelState(String pointCode, String pointName) {
            this.pointCode = pointCode;
            this.pointName = pointName;
        }

        private ControlDto.ChannelState snapshot() {
            return new ControlDto.ChannelState(pointCode, pointName, mode, action, manualStarted, paused, zeroed,
                    targetDisplacementMm, runId);
        }

        private void reset() {
            mode = ControlDto.ControlMode.OFF;
            action = ControlDto.ChannelAction.IDLE;
            manualStarted = false;
            paused = false;
            zeroed = false;
            targetDisplacementMm = null;
            runId = null;
        }
    }
}
