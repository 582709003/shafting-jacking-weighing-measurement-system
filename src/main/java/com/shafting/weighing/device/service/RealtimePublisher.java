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
/** 按运行主键维护工作页 SSE 订阅者并广播实时快照。 */
public class RealtimePublisher {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

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

    /** 向同一 runId 的所有浏览器连接发送 measurement 事件。 */
    public void publish(RealtimeSnapshot snapshot) {
        List<SseEmitter> emitters = subscribers.getOrDefault(snapshot.runId(), new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("measurement").data(snapshot));
            } catch (IOException exception) {
                remove(snapshot.runId(), emitter);
            }
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
}
