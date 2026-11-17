package com.shafting.weighing.alarm.service;

import com.shafting.weighing.alarm.dto.AlarmDto;
import com.shafting.weighing.alarm.entity.AlarmAction;
import com.shafting.weighing.alarm.entity.AlarmEvent;
import com.shafting.weighing.alarm.mapper.AlarmActionMapper;
import com.shafting.weighing.alarm.mapper.AlarmEventMapper;
import com.shafting.weighing.control.service.ControlCommandService;
import com.shafting.weighing.security.mapper.SysUserMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
/** 模拟报警查询、严格状态流转和动作历史保存业务。 */
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

    public AlarmService(AlarmEventMapper eventMapper, AlarmActionMapper actionMapper, SysUserMapper userMapper,
                        ControlCommandService controlCommandService) {
        this.eventMapper = eventMapper;
        this.actionMapper = actionMapper;
        this.userMapper = userMapper;
        this.controlCommandService = controlCommandService;
    }

    /** 按发生时间倒序读取报警列表。 */
    public List<AlarmEvent> list() {
        return eventMapper.selectList(Wrappers.<AlarmEvent>lambdaQuery().orderByDesc(AlarmEvent::getOccurredAt));
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
        eventMapper.updateById(event);
        if ("CLOSED".equals(event.getStatus())) {
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
        return detail(event.getId());
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
