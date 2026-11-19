package com.shafting.weighing.alarm.service;

import com.shafting.weighing.alarm.dto.AlarmDto;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 向已登录浏览器广播新报警，不重放连接前已存在的报警。 */
@Component
public class AlarmPublisher {

    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    /** 注册报警SSE连接，仅发送连接确认，不发送历史报警。 */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("alarm-stream"));
        } catch (IOException exception) {
            subscribers.remove(emitter);
        }
        return emitter;
    }

    /** 向当前在线浏览器发送一次新报警事件；失败连接立即移除。 */
    public void publish(AlarmDto.NotificationResponse notification) {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name("alarm").data(notification));
            } catch (IOException exception) {
                subscribers.remove(emitter);
            }
        }
    }
}
