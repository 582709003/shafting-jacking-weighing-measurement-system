package com.shafting.weighing.alarm.controller;

import com.shafting.weighing.alarm.dto.AlarmDto;
import com.shafting.weighing.alarm.service.AlarmService;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

/** 报警查询和处理接口，对应前端“报警界面”。 */
@RestController
@RequestMapping("/api")
public class AlarmController {

    private final AlarmService alarmService;

    public AlarmController(AlarmService alarmService) {
        this.alarmService = alarmService;
    }

    /**
     * 分页读取报警页面当前页签，支持关键词、级别和发生时间范围筛选。
     *
     * @param statusGroup ACTIVE活动报警、PROCESSING处理中报警、CLOSED已关闭报警
     * @param keyword 报警编码、来源或内容关键词，允许为空
     * @param alarmLevel 报警级别，允许为空
     * @param startTime 发生时间下限，允许为空
     * @param endTime 发生时间上限，允许为空
     * @param page 页码，从1开始
     * @param size 每页数量，允许1～100
     * @return 当前页报警、分页信息和三个页签数量
     */
    @GetMapping("/alarms")
    public AlarmDto.PageResponse alarms(@RequestParam(defaultValue = "ACTIVE") String statusGroup,
                                        @RequestParam(defaultValue = "") String keyword,
                                        @RequestParam(defaultValue = "") String alarmLevel,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return alarmService.page(statusGroup, keyword, alarmLevel, startTime, endTime, page, size);
    }

    /**
     * 建立新报警实时通知连接；只推送连接建立后的新事件，不重放历史报警。
     *
     * @return 报警服务器事件发射器，所有登录角色均可订阅
     */
    @GetMapping(value = "/alarms/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return alarmService.subscribe();
    }

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
