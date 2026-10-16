package com.gym.self.modules.user.face;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.gate.GateDevice;
import com.gym.self.modules.gate.GateDeviceMapper;
import com.gym.self.modules.gate.GatePhotoCodec;
import com.gym.self.modules.gate.WsGateCommandPort;
import com.gym.self.modules.user.application.FaceFileService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 正式环境把照片发给在线闸机做质量检测。本地和开发环境仍用 {@link MockFaceVendor}。
 */
@Component
@Profile("prod")
public class RemoteFaceVendor implements FaceVendorPort {

    private final FaceFileService faceFileService;
    private final GateDeviceMapper gateDeviceMapper;
    private final WsGateCommandPort gateCommandPort;
    private final Object queue = new Object();
    private final Set<String> busy = new HashSet<>();

    public RemoteFaceVendor(FaceFileService faceFileService, GateDeviceMapper gateDeviceMapper,
                            WsGateCommandPort gateCommandPort) {
        this.faceFileService = faceFileService;
        this.gateDeviceMapper = gateDeviceMapper;
        this.gateCommandPort = gateCommandPort;
    }

    @Override
    public Result enroll(long userId, String objectKey, long sizeBytes) {
        if (sizeBytes < 32) {
            return new Result(false, null, "未检测到正脸，请重新拍摄");
        }
        byte[] photo;
        try {
            photo = faceFileService.read(userId, objectKey);
        } catch (BizException exception) {
            return new Result(false, null, exception.getMessage());
        }
        String template;
        try {
            template = GatePhotoCodec.encode(photo);
        } catch (IllegalArgumentException exception) {
            return new Result(false, null, exception.getMessage());
        }
        String deviceSn = acquireDevice();
        if (deviceSn == null) {
            return new Result(false, null, "门店闸机不在线，暂时无法核验人脸");
        }
        JsonNode reply;
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cmd", "verifyPhoto");
            data.put("face_template", template);
            reply = gateCommandPort.exchange(deviceSn, data, 20);
        } finally {
            releaseDevice(deviceSn);
        }
        if (reply == null) {
            return new Result(false, null, "闸机没有返回照片检测结果，请稍后再试");
        }
        if (reply.path("code").asInt(-1) == 0) {
            return new Result(true, "gate-" + userId, null);
        }
        String message = reply.path("msg").asText("").trim();
        if (message.isEmpty()) {
            message = "照片不符合闸机要求，请重新拍摄正脸";
        }
        return new Result(false, null, message);
    }

    @Override
    public void revoke(String vendorFaceId) {
    }

    private String acquireDevice() {
        synchronized (queue) {
            while (true) {
                boolean anyOnline = false;
                for (GateDevice device : gateDeviceMapper.selectList(new LambdaQueryWrapper<GateDevice>()
                        .eq(GateDevice::getStatus, "ENABLED")
                        .orderByAsc(GateDevice::getId))) {
                    String sn = device.getDeviceSn();
                    if (sn == null || !gateCommandPort.online(sn)) {
                        continue;
                    }
                    anyOnline = true;
                    if (!busy.contains(sn)) {
                        busy.add(sn);
                        return sn;
                    }
                }
                if (!anyOnline) {
                    return null;
                }
                try {
                    queue.wait(1000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
    }

    private void releaseDevice(String deviceSn) {
        synchronized (queue) {
            busy.remove(deviceSn);
            queue.notifyAll();
        }
    }
}
