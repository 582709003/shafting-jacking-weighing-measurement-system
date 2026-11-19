package com.shafting.weighing.alarm.service;

import com.shafting.weighing.alarm.dto.AlarmDto;
import com.shafting.weighing.alarm.entity.AlarmAction;
import com.shafting.weighing.alarm.entity.AlarmEvent;
import com.shafting.weighing.alarm.mapper.AlarmActionMapper;
import com.shafting.weighing.alarm.mapper.AlarmEventMapper;
import com.shafting.weighing.control.service.ControlCommandService;
import com.shafting.weighing.security.mapper.SysUserMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** PLC及模拟报警查询、严格状态流转和动作历史保存业务。 */
@Service
public class AlarmService {

    /**
     * 报警页面允许的唯一状态链，禁止跳过确认或恢复直接关闭。
     */
    private static final Map<String, Transition> TRANSITIONS = Map.of(
            "acknowledge", new Transition("ACTIVE", "ACKNOWLEDGED", "ACKNOWLEDGE"),
            "recover", new Transition("ACKNOWLEDGED", "RECOVERED", "RECOVER"),
            "close", new Transition("RECOVERED", "CLOSED", "CLOSE"));

    private final AlarmEventMapper eventMapper;
    private final AlarmActionMapper actionMapper;
    private final SysUserMapper userMapper;
    private final ControlCommandService controlCommandService;
    private final AlarmPublisher alarmPublisher;
    /** 当前仍为 1 的 PLC 报警信号；信号未恢复时不允许人工标记恢复。 */
    private final Set<String> activePlcSignals = ConcurrentHashMap.newKeySet();

    public AlarmService(AlarmEventMapper eventMapper, AlarmActionMapper actionMapper, SysUserMapper userMapper,
                        ControlCommandService controlCommandService, AlarmPublisher alarmPublisher) {
        this.eventMapper = eventMapper;
        this.actionMapper = actionMapper;
        this.userMapper = userMapper;
        this.controlCommandService = controlCommandService;
        this.alarmPublisher = alarmPublisher;
    }

    /** 注册新报警实时通知连接，不重放连接前已经存在的报警。 */
    public SseEmitter subscribe() {
        return alarmPublisher.subscribe();
    }

    /**
     * 按活动、处理中或已关闭页签分页筛选报警。
     *
     * @param statusGroup ACTIVE、PROCESSING 或 CLOSED
     * @param keyword 报警编码、来源或内容关键词
     * @param alarmLevel 报警级别，空字符串表示全部
     * @param startTime 报警发生时间下限，可为空
     * @param endTime 报警发生时间上限，可为空
     * @param page 页码，从1开始
     * @param size 每页数量，限制为1～100
     * @return 当前页报警及三个页签总数
     */
    public AlarmDto.PageResponse page(String statusGroup, String keyword, String alarmLevel,
                                      LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        String normalizedGroup = statusGroup == null ? "ACTIVE" : statusGroup.strip().toUpperCase();
        if (!Set.of("ACTIVE", "PROCESSING", "CLOSED").contains(normalizedGroup)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报警状态页签不正确");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "报警开始时间不能晚于结束时间");
        }
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
        String normalizedLevel = alarmLevel == null ? "" : alarmLevel.strip().toUpperCase();
        int pageSize = Math.min(Math.max(size, 1), 100);
        long total = eventMapper.countPage(normalizedGroup, normalizedKeyword, normalizedLevel, startTime, endTime);
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) pageSize));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        long offset = (long) (currentPage - 1) * pageSize;
        return new AlarmDto.PageResponse(eventMapper.selectPage(normalizedGroup, normalizedKeyword,
                normalizedLevel, startTime, endTime, offset, pageSize), currentPage, pageSize, total, totalPages,
                eventMapper.countByStatus("ACTIVE"),
                eventMapper.countByStatus("ACKNOWLEDGED") + eventMapper.countByStatus("RECOVERED"),
                eventMapper.countByStatus("CLOSED"));
    }

    /** 组合报警当前状态和全部处理动作。 */
    public AlarmDto.DetailResponse detail(Long id) {
        AlarmEvent event = requireEvent(id);
        return new AlarmDto.DetailResponse(event.getId(), event.getAlarmCode(), event.getAlarmLevel(),
                event.getSourceName(), event.getMessage(), event.getStatus(), actionMapper.selectByAlarmId(id));
    }

    /**
     * 在一个事务中保存动作历史并更新报警当前状态。
     *
     * @param id 报警主键
     * @param actionName 页面动作名称
     * @param remark 处理备注
     * @param username 操作账号
     * @return 更新后的报警详情
     */
    @Transactional
    public AlarmDto.DetailResponse action(Long id, String actionName, String remark, String username) {
        AlarmEvent event = requireEvent(id);
        // 动作名称和当前状态必须同时匹配，避免越级处理。
        Transition transition = TRANSITIONS.get(actionName);
        if (transition == null || !transition.from().equals(event.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前报警状态不能执行该操作");
        }
        if ("recover".equals(actionName) && activePlcSignals.contains(event.getAlarmCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PLC 报警信号尚未恢复，不能标记恢复");
        }
        LocalDateTime now = LocalDateTime.now();
        AlarmAction action = new AlarmAction();
        action.setAlarmId(id);
        action.setActionType(transition.type());
        action.setFromStatus(transition.from());
        action.setToStatus(transition.to());
        action.setRemark(remark);
        action.setActedBy(userMapper.selectIdByUsername(username));
        action.setActedAt(now);
        actionMapper.insert(action);
        event.setStatus(transition.to());
        event.setUpdatedAt(now);
        if ("CLOSED".equals(transition.to())) {
            // 关闭后释放数据库防重键，允许同一PLC报警下一次重新形成独立事件。
            event.setDeduplicationKey(null);
        }
        eventMapper.updateById(event);
        if ("CLOSED".equals(event.getStatus())) {
            eventMapper.clearDeduplicationKey(event.getId());
            // 仅当最后一条报警关闭后解除锁定，且不会自动恢复模式或动作。
            controlCommandService.clearAlarmLockWhenSafe();
        }
        return detail(id);
    }

    /**
     * 创建用于前后端联调的模拟报警；不代表已接入真实 PLC 报警。
     */
    @Transactional
    public AlarmDto.DetailResponse simulate(AlarmDto.SimulateRequest request) {
        AlarmEvent event = new AlarmEvent();
        event.setAlarmCode(request.alarmCode());
        event.setAlarmLevel(request.alarmLevel());
        event.setSourceName(request.sourceName());
        event.setMessage(request.message());
        event.setStatus("ACTIVE");
        event.setOccurredAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventMapper.insert(event);
        // 报警入库后立即执行全局停机，快泄动作也不能例外。
        controlCommandService.emergencyStop(event.getAlarmCode());
        publish(event);
        return detail(event.getId());
    }

    /**
     * PLC 轮询检测到通讯故障或设备故障时创建报警并执行全局停机。
     * 同一未关闭报警只创建一次，避免 200 ms 轮询持续产生重复记录。
     */
    public synchronized void activateFromPlc(String alarmCode, String sourceName, String message) {
        if (eventMapper.selectOpenByCode(alarmCode) != null) {
            return;
        }
        AlarmEvent event = new AlarmEvent();
        event.setAlarmCode(alarmCode);
        event.setAlarmLevel("CRITICAL");
        event.setSourceName(sourceName);
        event.setMessage(message);
        event.setStatus("ACTIVE");
        event.setOccurredAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        event.setDeduplicationKey(alarmCode);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException exception) {
            // 数据库唯一键负责多实例并发兜底；另一实例已创建时不重复停机和推送。
            return;
        }
        controlCommandService.emergencyStop(alarmCode);
        publish(event);
    }

    /**
     * 同步一次 PLC 报警位状态。
     *
     * <p>0→1 时创建报警并全局停机；恢复为 0 只允许后续人工执行恢复、关闭流程，
     * 不自动修改数据库报警状态。</p>
     *
     * @param alarmCode 稳定报警编码
     * @param active PLC 当前报警位是否为 1
     * @param sourceName 报警来源名称
     * @param message 页面报警说明
     */
    public void updatePlcSignal(String alarmCode, boolean active, String sourceName, String message) {
        if (!active) {
            activePlcSignals.remove(alarmCode);
            return;
        }
        activePlcSignals.add(alarmCode);
        activateFromPlc(alarmCode, sourceName, message);
    }

    private void publish(AlarmEvent event) {
        alarmPublisher.publish(new AlarmDto.NotificationResponse(event.getId(), event.getAlarmCode(),
                event.getAlarmLevel(), event.getSourceName(), event.getMessage(), event.getOccurredAt(),
                eventMapper.countByStatus("ACTIVE")));
    }

    private AlarmEvent requireEvent(Long id) {
        AlarmEvent event = eventMapper.selectById(id);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "报警不存在");
        }
        return event;
    }

    private record Transition(String from, String to, String type) { }
}
