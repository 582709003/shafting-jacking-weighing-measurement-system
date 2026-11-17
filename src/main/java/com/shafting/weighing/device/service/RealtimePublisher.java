package com.shafting.weighing.device.service;

import com.shafting.weighing.device.dto.RealtimeSnapshot;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.Map;

@Component
/** 按自动运行主键或手动通道编码维护工作页 SSE 订阅者并广播实时快照。 */
public class RealtimePublisher {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> pointSubscribers = new ConcurrentHashMap<>();

    /**
     * 注册一个无限超时的 SSE 连接，并在结束、超时或异常时清理。
     */
    public SseEmitter subscribe(Long runId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(error -> remove(runId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(runId));
        } catch (IOException exception) {
            remove(runId, emitter);
        }
        return emitter;
    }

    /** 为没有数据库运行记录的手动操作注册通道级 SSE 连接。 */
    public SseEmitter subscribePoint(String pointCode) {
        SseEmitter emitter = new SseEmitter(0L);
        pointSubscribers.computeIfAbsent(pointCode, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removePoint(pointCode, emitter));
        emitter.onTimeout(() -> removePoint(pointCode, emitter));
        emitter.onError(error -> removePoint(pointCode, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(pointCode));
        } catch (IOException exception) {
            removePoint(pointCode, emitter);
        }
        return emitter;
    }

    /** 向对应自动运行和手动通道的浏览器连接发送 measurement 事件。 */
    public void publish(RealtimeSnapshot snapshot) {
        if (snapshot.runId() != null) {
            publishRun(snapshot);
        }
        List<SseEmitter> emitters = pointSubscribers.get(snapshot.pointCode());
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendPoint(snapshot, emitter);
        }
    }

    private void publishRun(RealtimeSnapshot snapshot) {
        List<SseEmitter> emitters = subscribers.get(snapshot.runId());
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("measurement").data(snapshot));
            } catch (IOException exception) {
                remove(snapshot.runId(), emitter);
            }
        }
    }

    private void sendPoint(RealtimeSnapshot snapshot, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("measurement").data(snapshot));
        } catch (IOException exception) {
            removePoint(snapshot.pointCode(), emitter);
        }
    }

    private void remove(Long runId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(runId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(runId);
        }
    }

    private void removePoint(String pointCode, SseEmitter emitter) {
        List<SseEmitter> emitters = pointSubscribers.get(pointCode);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            pointSubscribers.remove(pointCode);
        }
    }
}
