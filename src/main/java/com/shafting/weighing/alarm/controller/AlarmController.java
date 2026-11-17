package com.shafting.weighing.alarm.controller;

import com.shafting.weighing.alarm.dto.AlarmDto;
import com.shafting.weighing.alarm.entity.AlarmEvent;
import com.shafting.weighing.alarm.service.AlarmService;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
/** 报警查询和处理接口，对应前端“报警界面”。 */
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    /**
     * 读取报警页面列表，按发生时间倒序返回。
     *
     * @return 全部模拟报警事件
     */
    @GetMapping("/alarms")
    public List<AlarmEvent> alarms() { return alarmService.list(); }

    /**
     * 读取单个报警及其完整处理历史。
     *
     * @param id 报警事件主键
     * @return 报警详情和动作记录
     */
    @GetMapping("/alarms/{id}")
    public AlarmDto.DetailResponse detail(@PathVariable Long id) { return alarmService.detail(id); }

    /**
     * 管理员在报警页面执行确认、标记恢复或关闭。
     *
     * <p>允许的顺序为 ACTIVE → ACKNOWLEDGED → RECOVERED → CLOSED，不能越级。</p>
     *
     * @param id 报警事件主键
     * @param action acknowledge、recover 或 close
     * @param request 可选处理备注
     * @param authentication 当前管理员，用于记录处理人
     * @return 更新后的报警及动作历史
     */
    @PostMapping("/alarms/{id}/{action:acknowledge|recover|close}")
    @PreAuthorize("hasRole('ADMIN')")
    public AlarmDto.DetailResponse action(@PathVariable Long id, @PathVariable String action,
                                          @RequestBody(required = false) AlarmDto.ActionRequest request,
                                          Authentication authentication) {
        String remark = request == null ? null : request.remark();
        return alarmService.action(id, action, remark, authentication.getName());
    }

    /**
     * 无现场硬件时注入一条模拟报警，供报警页面联调。
     *
     * @param request 模拟报警内容
     * @return 新建的 ACTIVE 报警
     */
    @PostMapping("/simulator/alarms")
    @PreAuthorize("hasRole('ADMIN')")
    public AlarmDto.DetailResponse simulate(@Valid @RequestBody AlarmDto.SimulateRequest request) {
        return alarmService.simulate(request);
    }
}
