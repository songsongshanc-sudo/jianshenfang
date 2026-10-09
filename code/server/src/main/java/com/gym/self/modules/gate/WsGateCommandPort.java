package com.gym.self.modules.gate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class WsGateCommandPort {

    private static final Logger log = LoggerFactory.getLogger(WsGateCommandPort.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionSn = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final GateDeviceMapper gateDeviceMapper;
    private final ObjectMapper objectMapper;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;

    public WsGateCommandPort(GateDeviceMapper gateDeviceMapper, ObjectMapper objectMapper, Snowflake snowflake,
                             TimeProvider timeProvider) {
        this.gateDeviceMapper = gateDeviceMapper;
        this.objectMapper = objectMapper;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
    }

    public boolean bind(String sn, WebSocketSession session, String firmware) {
        GateDevice device = find(sn);
        if (device == null) {
            log.info("未登记闸机 {}", sn);
            return false;
        }
        sessions.put(sn, session);
        sessionSn.put(session.getId(), sn);
        device.setFirmware(firmware == null ? "" : firmware);
        device.setLastBeatAt(timeProvider.now());
        gateDeviceMapper.updateById(device);
        return true;
    }

    public void unbind(WebSocketSession session) {
        String sn = sessionSn.remove(session.getId());
        if (sn != null) {
            sessions.remove(sn, session);
        }
    }

    public boolean registered(String sn) {
        return find(sn) != null;
    }

    public boolean online(String sn) {
        WebSocketSession session = sessions.get(sn);
        return session != null && session.isOpen();
    }

    public void pong(WebSocketSession session, String sn) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage("{\"cmd\":\"pong\"}"));
            }
        } catch (Exception exception) {
            log.warn("闸机心跳回复失败 {}", sn);
            return;
        }
        touch(sn);
    }

    public JsonNode exchange(String sn, Map<String, Object> data) {
        WebSocketSession session = sessions.get(sn);
        if (session == null || !session.isOpen()) {
            return null;
        }
        String id = String.valueOf(snowflake.next());
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("cmd", "to_device");
        envelope.put("from", id);
        envelope.put("extra", "");
        envelope.put("to", sn);
        envelope.put("data", data);
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
            }
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            pending.remove(id);
            log.warn("下发闸机指令失败 {} {}", sn, data.get("cmd"));
            return null;
        }
    }

    public void complete(JsonNode node) {
        String to = node.path("to").asText("");
        if (to.isBlank()) {
            to = node.path("form").asText("");
        }
        CompletableFuture<JsonNode> future = pending.remove(to);
        if (future != null) {
            future.complete(node.path("data"));
        }
    }

    private void touch(String sn) {
        GateDevice device = find(sn);
        if (device == null) {
            return;
        }
        device.setLastBeatAt(timeProvider.now());
        gateDeviceMapper.updateById(device);
    }

    private GateDevice find(String sn) {
        if (sn == null || sn.isBlank()) {
            return null;
        }
        return gateDeviceMapper.selectOne(new LambdaQueryWrapper<GateDevice>().eq(GateDevice::getDeviceSn, sn));
    }
}
