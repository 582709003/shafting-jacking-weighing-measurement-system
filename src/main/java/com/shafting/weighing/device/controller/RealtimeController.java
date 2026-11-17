package com.shafting.weighing.device.controller;

import com.shafting.weighing.device.service.RealtimePublisher;
import com.shafting.weighing.measurement.entity.MeasurementPoint;
import com.shafting.weighing.measurement.mapper.MeasurementPointMapper;
import com.shafting.weighing.task.mapper.TestRunMapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
/** 实时测量事件接口，对应工作页的实时数值和自动流程状态。 */
public class RealtimeController {

    private final TestRunMapper testRunMapper;
    private final MeasurementPointMapper measurementPointMapper;
    private final RealtimePublisher realtimePublisher;

    public RealtimeController(TestRunMapper testRunMapper, MeasurementPointMapper measurementPointMapper,
                              RealtimePublisher realtimePublisher) {
        this.testRunMapper = testRunMapper;
        this.measurementPointMapper = measurementPointMapper;
        this.realtimePublisher = realtimePublisher;
    }

    /**
     * 为指定运行建立 SSE 长连接。
     *
     * <p>供工作页自动测试使用。登录用户均可订阅；浏览器收到 measurement 事件后更新
     * 位移、转速、顶升力和流程步骤。运行不存在时返回 404。</p>
     *
     * @param runId 测量运行主键
     * @return 服务器事件发射器
     */
    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long runId) {
        if (testRunMapper.selectById(runId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "测量运行不存在");
        }
        return realtimePublisher.subscribe(runId);
    }

    /**
     * 为指定通道建立无运行记录动作的 SSE 长连接。
     *
     * <p>供工作页手动上升、下降、快泄和六通道自动快泄显示传感器即时值。这些动作没有数据库
     * 运行主键，因此按通道编码订阅；登录用户均可订阅，通道不存在或停用时返回 404。</p>
     *
     * @param pointCode 测量点稳定编码
     * @return 服务器事件发射器
     */
    @GetMapping(value = "/points/{pointCode}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pointEvents(@PathVariable String pointCode) {
        MeasurementPoint point = measurementPointMapper.selectOne(Wrappers.<MeasurementPoint>lambdaQuery()
                .eq(MeasurementPoint::getPointCode, pointCode)
                .eq(MeasurementPoint::getEnabled, 1));
        if (point == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "测量点不存在或已停用");
        }
        return realtimePublisher.subscribePoint(pointCode);
    }
}
