package com.promptline.backend.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseHub {

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Optional: send a hello event
        try {
            emitter.send(SseEmitter.event()
                    .name("CONNECTED")
                    .data("{\"at\":\"" + Instant.now() + "\"}"));
        } catch (IOException ignored) {}

        return emitter;
    }

    public void broadcast(String eventName, String jsonPayload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(jsonPayload));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
