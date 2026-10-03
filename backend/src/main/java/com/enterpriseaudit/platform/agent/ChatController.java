package com.enterpriseaudit.platform.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private final AuditAgentService agent;
    public ChatController(AuditAgentService agent) { this.agent = agent; }

    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AuditAgentService.ChatEvent>> stream(@Valid @RequestBody ChatRequest request) {
        return agent.stream(request.question()).map(event -> ServerSentEvent.builder(event).event(event.type()).build());
    }

    public record ChatRequest(@NotBlank String question) {}
}
