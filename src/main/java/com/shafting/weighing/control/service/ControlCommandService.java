package com.shafting.weighing.control.service;

import com.shafting.weighing.alarm.mapper.AlarmEventMapper;
import com.shafting.weighing.control.dto.ControlDto;
import com.shafting.weighing.device.service.DeviceSimulator;
import com.shafting.weighing.measurement.entity.MeasurementPoint;
import com.shafting.weighing.measurement.entity.TestConfigPoint;
import com.shafting.weighing.measurement.mapper.MeasurementPointMapper;
import com.shafting.weighing.measurement.mapper.OperationLogMapper;
import com.shafting.weighing.measurement.mapper.TestConfigPointMapper;
import com.shafting.weighing.security.mapper.SysUserMapper;
import com.shafting.weighing.task.dto.TaskDto;
import com.shafting.weighing.task.service.MeasurementTaskService;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 编排工作页控制命令、任务持久化、设备模拟器和报警联锁。
 *
 * <p>前端不参与状态判定；每条命令必须先通过本服务校验，再修改内存状态和设备会话。
 * 当前实现只驱动软件模拟器，真实 PLC、电磁阀和硬件安全联锁仍属于设备接入边界。</p>
 */
@Service
public class ControlCommandService {

    private static final Long DEFAULT_CONFIG_ID = 1L;

    private final ControlStateService stateService;
    private final MeasurementTaskService taskService;
    private final DeviceSimulator deviceSimulator;
    private final MeasurementPointMapper pointMapper;
    private final TestConfigPointMapper configPointMapper;
    private final AlarmEventMapper alarmEventMapper;
    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public ControlCommandService(ControlStateService stateService,
                                 MeasurementTaskService taskService,
                                 DeviceSimulator deviceSimulator,
                                 MeasurementPointMapper pointMapper,
                                 TestConfigPointMapper configPointMapper,
                                 AlarmEventMapper alarmEventMapper,
                                 OperationLogMapper operationLogMapper,
                                 SysUserMapper userMapper,
                                 ObjectMapper objectMapper) {
        this.stateService = stateService;
        this.taskService = taskService;
        this.deviceSimulator = deviceSimulator;
        this.pointMapper = pointMapper;
        this.configPointMapper = configPointMapper;
        this.alarmEventMapper = alarmEventMapper;
        this.operationLogMapper = operationLogMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    /** 应用重启后若数据库仍有未关闭报警，保持控制系统报警锁定。 */
    @PostConstruct
    public void initializeAlarmLock() {
        if (alarmEventMapper.countOpenAlarms() > 0) {
            stateService.alarmStop();
        }
    }

    /** 返回控制面板当前完整状态。 */
    public ControlDto.ControlStateResponse state() {
        return stateService.snapshot();
    }

    /**
     * 串行执行一条控制命令，并在成功后写入操作审计。
     *
     * @param request 页面提交的统一命令
     * @param username 当前登录账号
     * @return 命令完成后的控制状态
     */
    public synchronized ControlDto.ControlStateResponse execute(ControlDto.CommandRequest request,
                                                                 String username) {
        Long configId = request.configId() == null ? DEFAULT_CONFIG_ID : request.configId();
        switch (request.commandType()) {
            case MANUAL_CONNECT -> connectMode(requirePointCode(request), ControlDto.ControlMode.MANUAL);
            case MANUAL_DISCONNECT -> disconnectMode(requirePointCode(request), ControlDto.ControlMode.MANUAL);
            case AUTO_CONNECT -> connectMode(requirePointCode(request), ControlDto.ControlMode.AUTO);
            case AUTO_DISCONNECT -> disconnectMode(requirePointCode(request), ControlDto.ControlMode.AUTO);
            case ZERO_DISPLACEMENT -> stateService.markZeroed(requirePointCode(request));
            case MANUAL_ENABLE -> stateService.startManual(requirePointCode(request));
            case MANUAL_ASCEND -> startManual(request, configId, ControlDto.ChannelAction.ASCENDING);
            case MANUAL_DESCEND -> startManual(request, configId, ControlDto.ChannelAction.DESCENDING);
            case MANUAL_QUICK_RELEASE -> startManual(request, configId,
                    ControlDto.ChannelAction.MANUAL_QUICK_RELEASE);
            case MANUAL_PAUSE -> pauseManual(requirePointCode(request));
            case MANUAL_RESUME -> resumeManual(requirePointCode(request));
            case MANUAL_STOP -> stopManual(requirePointCode(request));
            case AUTO_TEST_START -> startAutoTest(request, configId, username);
            case AUTO_QUICK_RELEASE -> stateService.connectAutoQuickRelease(requirePointCode(request));
            case AUTO_PAUSE -> pauseAutomatic(requirePointCode(request), request.commandNo());
            case AUTO_RESUME -> resumeAutomatic(requirePointCode(request), request.commandNo());
            case AUTO_STOP -> stopAutomatic(requirePointCode(request));
        }
        ControlDto.ControlStateResponse response = stateService.snapshot();
        writeAudit(request, username, response);
        return response;
    }

    /** 报警发生时停止全部运行并锁定控制，故障码保存报警编码。 */
    public synchronized void emergencyStop(String alarmCode) {
        deviceSimulator.stopAllManual();
        stopAllRuns(alarmCode);
        stateService.alarmStop();
    }

    /** 全部报警关闭后解除锁定，但不恢复任何模式或动作。 */
    public synchronized void clearAlarmLockWhenSafe() {
        if (alarmEventMapper.countOpenAlarms() == 0) {
            stateService.clearAlarmLock();
        }
    }

    private void connectMode(String pointCode, ControlDto.ControlMode mode) {
        ControlDto.ChannelState channel = stateService.channel(pointCode);
        if (channel.mode() != mode && channel.action() == ControlDto.ChannelAction.IDLE
                && channel.runId() != null) {
            // 手动上升到位后运行可处于空闲，切换模式前仍需关闭该运行，避免遗留模拟会话。
            taskService.forceStop(channel.runId(), null);
        }
        stateService.connectMode(pointCode, mode);
    }

    private void disconnectMode(String pointCode, ControlDto.ControlMode mode) {
        ControlDto.ChannelState channel = stateService.channel(pointCode);
        if (mode == ControlDto.ControlMode.MANUAL && channel.action() != ControlDto.ChannelAction.IDLE) {
            deviceSimulator.stopManual(pointCode);
        }
        if (channel.runId() != null) {
            taskService.forceStop(channel.runId(), null);
        }
        stateService.disconnectMode(pointCode, mode);
    }

    private void startManual(ControlDto.CommandRequest request, Long configId,
                             ControlDto.ChannelAction action) {
        String pointCode = requirePointCode(request);
        MeasurementPoint point = requirePoint(pointCode);
        BigDecimal target = null;
        if (action == ControlDto.ChannelAction.ASCENDING) {
            TestConfigPoint config = requireConfigPoint(configId, point.getId());
            target = validateAscent(config.getManualTargetDisplacement());
        }
        boolean consumesZero = action == ControlDto.ChannelAction.ASCENDING;
        stateService.beginAction(pointCode, action, target, consumesZero);
        try {
            // 手动操作只下发设备动作并监视实时位移，不创建测量任务、运行或采样记录。
            deviceSimulator.startManualAction(pointCode, action, target);
        } catch (RuntimeException exception) {
            stateService.finishAction(pointCode, null);
            throw exception;
        }
    }

    private void stopManual(String pointCode) {
        ControlDto.ChannelState channel = stateService.channel(pointCode);
        boolean manualAction = channel.action() == ControlDto.ChannelAction.ASCENDING
                || channel.action() == ControlDto.ChannelAction.DESCENDING
                || channel.action() == ControlDto.ChannelAction.MANUAL_QUICK_RELEASE;
        if (channel.mode() != ControlDto.ControlMode.MANUAL || !manualAction) {
            throw conflict("当前通道没有可停止的手动运行");
        }
        deviceSimulator.stopManual(pointCode);
        stateService.finishAction(pointCode, null);
    }

    private void pauseManual(String pointCode) {
        requireManualAction(pointCode, false);
        deviceSimulator.pauseManual(pointCode);
        stateService.pauseChannelAction(pointCode);
    }

    private void resumeManual(String pointCode) {
        requireManualAction(pointCode, true);
        deviceSimulator.resumeManual(pointCode);
        stateService.resumeChannelAction(pointCode);
    }

    private ControlDto.ChannelState requireManualAction(String pointCode, boolean paused) {
        ControlDto.ChannelState channel = stateService.channel(pointCode);
        boolean manualAction = channel.action() == ControlDto.ChannelAction.ASCENDING
                || channel.action() == ControlDto.ChannelAction.DESCENDING
                || channel.action() == ControlDto.ChannelAction.MANUAL_QUICK_RELEASE;
        if (!manualAction || channel.paused() != paused) {
            throw conflict(paused ? "当前通道没有暂停的手动动作" : "当前通道没有可暂停的手动动作");
        }
        return channel;
    }

    private void startAutoTest(ControlDto.CommandRequest request, Long configId, String username) {
        String pointCode = requirePointCode(request);
        MeasurementPoint point = requirePoint(pointCode);
        TestConfigPoint config = requireConfigPoint(configId, point.getId());
        validateAutomaticLimits(config);
        if (alarmEventMapper.countOpenAlarms() > 0) {
            emergencyStop("ACTIVE_ALARM");
            throw conflict("存在未关闭报警，不能启动自动测试");
        }
        stateService.beginAction(pointCode, ControlDto.ChannelAction.AUTO_TEST, null, true);
        try {
            TaskDto.TaskResponse task = taskService.createControlTask(configId, pointCode,
                    point.getPointName() + "自动测试", username, "AUTOMATIC", false);
            TaskDto.TaskResponse running = taskService.command(task.runId(),
                    new TaskDto.CommandRequest(request.commandNo(), TaskDto.CommandType.START));
            stateService.attachRun(pointCode, running.runId());
        } catch (RuntimeException exception) {
            stateService.finishAction(pointCode, null);
            throw exception;
        }
    }

    private ControlDto.ChannelState requireAutoRun(String pointCode, boolean paused) {
        ControlDto.ChannelState channel = stateService.channel(pointCode);
        if (channel.action() != ControlDto.ChannelAction.AUTO_TEST
                || channel.paused() != paused || channel.runId() == null) {
            throw conflict("当前通道自动测试状态不允许该操作");
        }
        return channel;
    }

    private void pauseAutomatic(String pointCode, String commandNo) {
        if (stateService.snapshot().globalAction() == ControlDto.GlobalAction.AUTO_QUICK_RELEASE) {
            stateService.pauseAutoQuickRelease();
            return;
        }
        ControlDto.ChannelState channel = requireAutoRun(pointCode, false);
        taskService.command(channel.runId(), new TaskDto.CommandRequest(commandNo, TaskDto.CommandType.PAUSE));
        stateService.pauseChannelAction(pointCode);
    }

    private void resumeAutomatic(String pointCode, String commandNo) {
        if (stateService.snapshot().globalAction() == ControlDto.GlobalAction.AUTO_QUICK_RELEASE) {
            stateService.resumeAutoQuickRelease();
            return;
        }
        ControlDto.ChannelState channel = requireAutoRun(pointCode, true);
        taskService.command(channel.runId(), new TaskDto.CommandRequest(commandNo, TaskDto.CommandType.RESUME));
        stateService.resumeChannelAction(pointCode);
    }

    private void stopAutomatic(String pointCode) {
        if (stateService.snapshot().globalAction() == ControlDto.GlobalAction.AUTO_QUICK_RELEASE) {
            stateService.disconnectAutoQuickRelease();
            return;
        }
        ControlDto.ChannelState channel = stateService.channel(pointCode);
        if (channel.mode() != ControlDto.ControlMode.AUTO
                || channel.action() != ControlDto.ChannelAction.AUTO_TEST || channel.runId() == null) {
            throw conflict("当前通道没有可停止的自动动作");
        }
        taskService.forceStop(channel.runId(), null);
        stateService.finishAction(pointCode, channel.runId());
    }

    private BigDecimal validateAscent(BigDecimal target) {
        if (target == null || target.signum() <= 0) {
            throw conflict("请先保存大于 0 mm 的手动目标位移");
        }
        return target.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void validateAutomaticLimits(TestConfigPoint config) {
        validateMotionLimits(config);
        if (config.getForceIntervalKn() == null || config.getForceIntervalKn().signum() <= 0) {
            throw conflict("请先到设置页配置力值间隔");
        }
    }

    private void validateMotionLimits(TestConfigPoint config) {
        if (config.getMaxDisplacement() == null || config.getMaxDisplacement().signum() <= 0) {
            throw conflict("请先到设置页配置自动目标位移");
        }
    }

    private TestConfigPoint requireConfigPoint(Long configId, Long pointId) {
        TestConfigPoint config = configPointMapper.selectOne(Wrappers.<TestConfigPoint>lambdaQuery()
                .eq(TestConfigPoint::getConfigId, configId)
                .eq(TestConfigPoint::getPointId, pointId));
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测量点参数不存在");
        }
        return config;
    }

    private MeasurementPoint requirePoint(String pointCode) {
        MeasurementPoint point = pointMapper.selectOne(Wrappers.<MeasurementPoint>lambdaQuery()
                .eq(MeasurementPoint::getPointCode, pointCode)
                .eq(MeasurementPoint::getEnabled, 1));
        if (point == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测量点不存在或已停用");
        }
        return point;
    }

    private String requirePointCode(ControlDto.CommandRequest request) {
        if (request.pointCode() == null || request.pointCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该命令必须指定测量点");
        }
        return request.pointCode();
    }

    private void stopAllRuns(String faultCode) {
        stateService.snapshot().channels().stream()
                .map(ControlDto.ChannelState::runId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(runId -> taskService.forceStop(runId, faultCode));
    }

    private void writeAudit(ControlDto.CommandRequest request, String username,
                            ControlDto.ControlStateResponse response) {
        try {
            operationLogMapper.insertOperation(userMapper.selectIdByUsername(username), "CONTROL",
                    request.commandType().name(), stateService.activeRunId(),
                    objectMapper.writeValueAsString(MapAudit.of(request, response)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("控制命令审计序列化失败", exception);
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** 审计只保存命令和执行后安全状态，避免记录无关会话信息。 */
    private record MapAudit(ControlDto.CommandRequest command,
                            boolean alarmLocked,
                            ControlDto.GlobalAction globalAction,
                            boolean globalPaused) {

        private static MapAudit of(ControlDto.CommandRequest command,
                                   ControlDto.ControlStateResponse state) {
            return new MapAudit(command, state.alarmLocked(), state.globalAction(), state.globalPaused());
        }
    }
}
