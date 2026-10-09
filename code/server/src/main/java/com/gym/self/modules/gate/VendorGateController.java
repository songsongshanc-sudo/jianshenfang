package com.gym.self.modules.gate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.time.TimeProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
public class VendorGateController {

    private static final Logger log = LoggerFactory.getLogger(VendorGateController.class);
    private static final DateTimeFormatter RECOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final GateService gateService;
    private final GateState gateState;
    private final TimeProvider timeProvider;
    private final ObjectMapper objectMapper;

    public VendorGateController(GateService gateService, GateState gateState, TimeProvider timeProvider,
                                ObjectMapper objectMapper) {
        this.gateService = gateService;
        this.gateState = gateState;
        this.timeProvider = timeProvider;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/verify_user")
    public VendorBody verifyUser(@RequestBody(required = false) String body, HttpServletRequest request) {
        try {
            JsonNode node = parse(body);
            String sn = node.path("sn").asText("");
            VendorReply rejected = guard(request, sn);
            if (rejected != null) {
                return VendorBody.from(rejected);
            }
            return VendorBody.from(gateService.vendorVerify(sn, node.path("user_id").asText(""),
                    parseTime(node.path("recog_time").asText(""))));
        } catch (Exception exception) {
            log.error("在线验证失败", exception);
            return VendorBody.from(VendorReply.of(3, "系统繁忙", Map.of()));
        }
    }

    @PostMapping("/api/v1/record/face")
    public VendorBody recordFace(@RequestBody(required = false) String body, HttpServletRequest request) {
        return records(body, request, true);
    }

    @PostMapping("/api/v1/stranger")
    public VendorBody stranger(@RequestBody(required = false) String body, HttpServletRequest request) {
        return records(body, request, false);
    }

    private VendorBody records(String body, HttpServletRequest request, boolean success) {
        try {
            JsonNode node = parse(body);
            String sn = node.path("sn").asText("");
            VendorReply rejected = guard(request, sn);
            if (rejected != null) {
                return VendorBody.from(rejected);
            }
            JsonNode logs = node.path("logs");
            if (logs.isArray()) {
                for (JsonNode logNode : logs) {
                    if (success) {
                        gateService.vendorRecord(sn, logNode.path("user_id").asText(""),
                                parseTime(logNode.path("recog_time").asText("")), logNode.path("pass_status").asInt(0));
                    } else {
                        gateService.vendorStranger(sn, logNode.path("user_id").asText(""),
                                logNode.path("real_user_id").asText(""),
                                parseTime(logNode.path("recog_time").asText("")), logNode.path("pass_status").asInt(1));
                    }
                }
            }
            return VendorBody.from(VendorReply.ok());
        } catch (Exception exception) {
            log.error("通行记录保存失败", exception);
            return VendorBody.from(VendorReply.of(3, "系统繁忙", Map.of()));
        }
    }

    private VendorReply guard(HttpServletRequest request, String sn) {
        GateDevice device = gateService.deviceBySn(sn);
        if (device == null) {
            return VendorReply.missing();
        }
        if (device.getToken() == null || device.getToken().isBlank()) {
            return null;
        }
        String headerSn = request.getHeader("iot_sn");
        String timestamp = request.getHeader("iot_timestamp");
        String nonce = request.getHeader("iot_nonce");
        String signature = request.getHeader("iot_signature");
        if ((headerSn != null && !headerSn.isBlank() && !headerSn.equals(sn))
                || !GateSignature.valid(sn, device.getToken(), timestamp, nonce, signature, timeProvider)
                || !gateState.firstNonce(sn, nonce, Duration.ofMinutes(1))) {
            return VendorReply.of(3, "签名无效", Map.of());
        }
        return null;
    }

    private JsonNode parse(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private LocalDateTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return timeProvider.now().withNano(0);
        }
        try {
            return LocalDateTime.parse(text, RECOG);
        } catch (DateTimeParseException exception) {
            return timeProvider.now().withNano(0);
        }
    }

    public record VendorBody(
            @JsonProperty("Result") int result,
            @JsonProperty("Msg") String msg,
            @JsonProperty("Content") Object content) {

        static VendorBody from(VendorReply reply) {
            return new VendorBody(reply.result(), reply.msg(), reply.content());
        }
    }
}
