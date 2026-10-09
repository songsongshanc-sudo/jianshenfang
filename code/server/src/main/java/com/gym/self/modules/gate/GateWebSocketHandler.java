package com.gym.self.modules.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.Executor;

@Component
public class GateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GateWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final WsGateCommandPort port;
    private final GateFaceSync gateFaceSync;
    private final Executor gateExecutor;

    public GateWebSocketHandler(ObjectMapper objectMapper, WsGateCommandPort port, GateFaceSync gateFaceSync,
                                @Qualifier("gateExecutor") Executor gateExecutor) {
        this.objectMapper = objectMapper;
        this.port = port;
        this.gateFaceSync = gateFaceSync;
        this.gateExecutor = gateExecutor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setTextMessageSizeLimit(1024 * 1024);
        session.setBinaryMessageSizeLimit(1024 * 1024);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String cmd = node.path("cmd").asText("");
            if ("declare".equals(cmd)) {
                String sn = node.path("sn").asText("");
                if (port.bind(sn, session, node.path("version_name").asText(""))) {
                    gateExecutor.execute(() -> gateFaceSync.onConnected(sn));
                }
                return;
            }
            if ("ping".equals(cmd)) {
                String sn = node.path("sn").asText("");
                if (port.registered(sn)) {
                    port.pong(session, sn);
                }
                return;
            }
            if ("to_client".equals(cmd)) {
                port.complete(node);
            }
        } catch (Exception exception) {
            log.warn("闸机长连接消息无法解析");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        port.unbind(session);
    }
}
