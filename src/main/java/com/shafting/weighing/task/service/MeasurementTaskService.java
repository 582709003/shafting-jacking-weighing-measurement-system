package com.shafting.weighing.task.service;

import com.shafting.weighing.device.dto.SimulationStart;
import com.shafting.weighing.device.service.DeviceSimulator;
import com.shafting.weighing.measurement.entity.MeasurementPoint;
import com.shafting.weighing.measurement.entity.TestConfigPoint;
import com.shafting.weighing.measurement.mapper.MeasurementPointMapper;
import com.shafting.weighing.measurement.mapper.TestConfigMapper;
import com.shafting.weighing.measurement.mapper.TestConfigPointMapper;
import com.shafting.weighing.security.mapper.SysUserMapper;
import com.shafting.weighing.task.dto.TaskDto;
import com.shafting.weighing.task.entity.MeasurementTask;
import com.shafting.weighing.task.entity.TestRun;
import com.shafting.weighing.task.mapper.DeviceCommandMapper;
import com.shafting.weighing.task.mapper.MeasurementTaskMapper;
import com.shafting.weighing.task.mapper.TestRunMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
/** 测量任务创建、命令幂等、运行状态机和模拟器调度业务。 */
public class MeasurementTaskService {

    private static final DateTimeFormatter TASK_NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MeasurementTaskMapper measurementTaskMapper;
    private final TestRunMapper testRunMapper;
    private final DeviceCommandMapper deviceCommandMapper;
    private final MeasurementPointMapper measurementPointMapper;
    private final TestConfigMapper testConfigMapper;
    private final TestConfigPointMapper testConfigPointMapper;
    private final SysUserMapper sysUserMapper;
    private final DeviceSimulator deviceSimulator;

    public MeasurementTaskService(MeasurementTaskMapper measurementTaskMapper,
                                  TestRunMapper testRunMapper,
                                  DeviceCommandMapper deviceCommandMapper,
                                  MeasurementPointMapper measurementPointMapper,
                                  TestConfigMapper testConfigMapper,
                                  TestConfigPointMapper testConfigPointMapper,
                                  SysUserMapper sysUserMapper,
                                  DeviceSimulator deviceSimulator) {
        this.measurementTaskMapper = measurementTaskMapper;
        this.testRunMapper = testRunMapper;
        this.deviceCommandMapper = deviceCommandMapper;
        this.measurementPointMapper = measurementPointMapper;
        this.testConfigMapper = testConfigMapper;
        this.testConfigPointMapper = testConfigPointMapper;
        this.sysUserMapper = sysUserMapper;
        this.deviceSimulator = deviceSimulator;
    }

    /**
     * 创建一条任务和一条待启动运行记录。
     *
     * <p>兼容旧任务接口时模式为 SIMULATED；自动测试通过 createAutomaticControlTask 创建
     * AUTOMATIC 运行。手动操作不创建任务。当前每个任务仍只有一个运行。</p>
     *
     * @param request 工作页提交的任务信息
     * @param username 当前操作账号
     * @return 新建任务状态
     */
    @Transactional
    public TaskDto.TaskResponse createTask(TaskDto.CreateTaskRequest request, String username) {
        return createTask(request.configId(), request.pointCode(), request.taskName(), username, "SIMULATED", false);
    }

    /**
     * 由工作页自动测试创建任务和运行。
     *
     * @param configId 参数方案主键
     * @param pointCode 测量点编码
     * @param taskName 自动生成的任务名称
     * @param username 当前操作账号
     * @return 新任务和运行状态
     */
    @Transactional
    public TaskDto.TaskResponse createAutomaticControlTask(Long configId, String pointCode, String taskName,
                                                            String username) {
        return createTask(configId, pointCode, taskName, username, "AUTOMATIC", false);
    }

    private TaskDto.TaskResponse createTask(Long configId, String pointCode, String taskName,
                                            String username, String mode, boolean started) {
        if (testConfigMapper.selectById(configId) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数方案不存在");
        }
        MeasurementPoint point = measurementPointMapper.selectOne(Wrappers.<MeasurementPoint>lambdaQuery()
                .eq(MeasurementPoint::getPointCode, pointCode)
                .eq(MeasurementPoint::getEnabled, 1));
        if (point == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "测量点不存在或已停用");
        }
        String taskNo = nextTaskNumber();
        MeasurementTask task = new MeasurementTask();
        task.setTaskNo(taskNo);
        task.setTaskName(taskName);
        task.setConfigId(configId);
        task.setOperatorId(sysUserMapper.selectIdByUsername(username));
        task.setTaskStatus(started ? "RUNNING" : "CREATED");
        if (started) {
            task.setStartedAt(LocalDateTime.now());
        }
        measurementTaskMapper.insert(task);

        // 任务保存用户可识别的信息，运行记录保存设备状态和测量点。
        TestRun run = new TestRun();
        run.setTaskId(task.getId());
        run.setPointId(point.getId());
        run.setRunNo(taskNo + "-01");
        run.setMode(mode);
        run.setState(started ? "RUNNING" : "IDLE");
        run.setProcessStage(started ? "LIFTING" : "WAITING");
        if (started) {
            run.setStartedAt(task.getStartedAt());
        }
        testRunMapper.insert(run);
        return response(task, run, point);
    }

    /** 按任务主键读取任务及其首个运行状态。 */
    public TaskDto.TaskResponse getTask(Long taskId) {
        MeasurementTask task = requireTask(taskId);
        TestRun run = testRunMapper.selectFirstByTaskId(taskId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务缺少运行记录");
        }
        return response(task, run, measurementPointMapper.selectById(run.getPointId()));
    }

    /**
     * 校验并执行一条运行命令。
     *
     * <p>同一 commandNo 重试时直接返回已有状态；同一进程内只允许一个活动运行。
     * 当前使用同步锁覆盖“检查活动运行并启动”的临界区，部署多实例前应替换为 Redis 分布式锁。</p>
     */
    @Transactional
    public synchronized TaskDto.TaskResponse command(Long runId, TaskDto.CommandRequest request) {
        TestRun run = requireRun(runId);
        // 先检查命令号，避免浏览器重试导致重复启动或重复停止。
        Long existingRunId = deviceCommandMapper.selectRunIdByCommandNo(request.commandNo());
        if (existingRunId != null) {
            if (!existingRunId.equals(runId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "命令号已被其他运行使用");
            }
            return response(requireTask(run.getTaskId()), run, measurementPointMapper.selectById(run.getPointId()));
        }
        String nextState = nextState(run.getState(), request.commandType());
        // 设备结构未确认前采用全局单通道互斥，避免多个模拟流程同时运行。
        if (request.commandType() == TaskDto.CommandType.START && testRunMapper.countActiveRuns() > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有测量点正在运行");
        }
        MeasurementTask task = requireTask(run.getTaskId());
        LocalDateTime now = LocalDateTime.now();
        applyState(task, run, nextState, now);
        testRunMapper.updateById(run);
        measurementTaskMapper.updateById(task);
        deviceCommandMapper.insertCommand(request.commandNo(), runId, request.commandType().name());
        controlSimulator(run, task, request.commandType());
        return response(task, run, measurementPointMapper.selectById(run.getPointId()));
    }

    /**
     * 模式断开、操作员停止或报警时强制停止运行。
     * 重复停止已结束运行不会改写原结束状态，便于紧急停机调用保持幂等。
     *
     * @param runId 运行主键
     * @param faultCode 报警编码；普通停止为空
     */
    @Transactional
    public synchronized void forceStop(Long runId, String faultCode) {
        TestRun run = requireRun(runId);
        if ("STOPPED".equals(run.getState()) || "COMPLETED".equals(run.getState())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        run.setState("STOPPED");
        run.setProcessStage("STOPPED");
        run.setEndedAt(now);
        run.setFaultCode(faultCode);
        testRunMapper.updateById(run);
        MeasurementTask task = requireTask(run.getTaskId());
        task.setTaskStatus("STOPPED");
        task.setCompletedAt(now);
        measurementTaskMapper.updateById(task);
        deviceSimulator.stop(runId);
    }

    /** 根据当前状态和命令计算下一状态，非法跳转统一返回 409。 */
    private String nextState(String currentState, TaskDto.CommandType commandType) {
        return switch (commandType) {
            case START -> requireTransition(currentState, "IDLE", "RUNNING");
            case PAUSE -> requireTransition(currentState, "RUNNING", "PAUSED");
            case RESUME -> requireTransition(currentState, "PAUSED", "RUNNING");
            case STOP -> {
                if (!"RUNNING".equals(currentState) && !"PAUSED".equals(currentState)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不能停止");
                }
                yield "STOPPED";
            }
        };
    }

    private String requireTransition(String currentState, String requiredState, String nextState) {
        if (!requiredState.equals(currentState)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不能执行该命令");
        }
        return nextState;
    }

    /** 同步更新任务状态、运行状态以及首次开始/结束时间。 */
    private void applyState(MeasurementTask task, TestRun run, String state, LocalDateTime now) {
        run.setState(state);
        task.setTaskStatus(state);
        if ("RUNNING".equals(state) && run.getStartedAt() == null) {
            run.setStartedAt(now);
            task.setStartedAt(now);
        }
        if ("STOPPED".equals(state)) {
            run.setEndedAt(now);
            task.setCompletedAt(now);
        }
    }

    /** 将已通过状态校验的命令转发给设备模拟器。 */
    private void controlSimulator(TestRun run, MeasurementTask task, TaskDto.CommandType commandType) {
        MeasurementPoint point = measurementPointMapper.selectById(run.getPointId());
        switch (commandType) {
            case START -> {
                // 启动参数取任务绑定方案中的当前测量点配置，不使用前端临时值。
                TestConfigPoint configPoint = testConfigPointMapper.selectOne(
                        Wrappers.<TestConfigPoint>lambdaQuery()
                                .eq(TestConfigPoint::getConfigId, task.getConfigId())
                                .eq(TestConfigPoint::getPointId, run.getPointId()));
                if (configPoint == null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "测量点参数不存在");
                }
                if (configPoint.getMaxDisplacement() == null || configPoint.getMaxDisplacement().signum() <= 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "请先到设置页配置最大位移");
                }
                if (configPoint.getForceIntervalKn() == null || configPoint.getForceIntervalKn().signum() <= 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "请先到设置页配置力值间隔");
                }
                deviceSimulator.start(new SimulationStart(run.getId(), task.getId(), run.getPointId(),
                        point.getPointCode(), configPoint.getMaxDisplacement(), configPoint.getMotorRpm(),
                        configPoint.getStandardForce(), configPoint.getForceIntervalKn(),
                        configPoint.getHoldSeconds()));
            }
            case PAUSE -> deviceSimulator.pause(run.getId());
            case RESUME -> deviceSimulator.resume(run.getId());
            case STOP -> deviceSimulator.stop(run.getId());
        }
    }

    private MeasurementTask requireTask(Long taskId) {
        MeasurementTask task = measurementTaskMapper.selectById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "测量任务不存在");
        }
        return task;
    }

    private TestRun requireRun(Long runId) {
        TestRun run = testRunMapper.selectById(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "测量运行不存在");
        }
        return run;
    }

    private TaskDto.TaskResponse response(MeasurementTask task, TestRun run, MeasurementPoint point) {
        return new TaskDto.TaskResponse(task.getId(), run.getId(), task.getTaskNo(), task.getTaskName(),
                point.getPointCode(), point.getPointName(), run.getMode(), run.getState(), run.getProcessStage());
    }

    private String nextTaskNumber() {
        return "TASK-" + LocalDateTime.now().format(TASK_NUMBER_TIME) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
