package com.shafting.weighing.device.controller;

import com.shafting.weighing.device.service.RealtimePublisher;
import com.shafting.weighing.task.mapper.TestRunMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/runs")
/** 实时测量事件接口，对应工作页的实时数值和自动流程状态。 */
public class RealtimeController {

    private final TestRunMapper testRunMapper;
    private final RealtimePublisher realtimePublisher;

    public RealtimeController(TestRunMapper testRunMapper, RealtimePublisher realtimePublisher) {
        this.testRunMapper = testRunMapper;
        this.realtimePublisher = realtimePublisher;
    }

    /**
     * 为指定运行建立 SSE 长连接。
     *
     * <p>浏览器收到 measurement 事件后更新位移、压力、转速、顶升力和流程步骤。</p>
     *
     * @param runId 测量运行主键
     * @return 服务器事件发射器
     */
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long runId) {
        if (testRunMapper.selectById(runId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "测量运行不存在");
        }
        return realtimePublisher.subscribe(runId);
    }
}
