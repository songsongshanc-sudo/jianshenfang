package com.gym.self.modules.gate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.user.application.FaceFileService;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.gym.self.modules.user.domain.UserFace;
import com.gym.self.modules.user.domain.UserFaceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class GateFaceSync {

    private static final Logger log = LoggerFactory.getLogger(GateFaceSync.class);
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final GateDeviceMapper gateDeviceMapper;
    private final GateSyncTaskMapper taskMapper;
    private final MembershipMapper membershipMapper;
    private final GymUserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final FaceFileService faceFileService;
    private final WsGateCommandPort port;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;
    private final Executor gateExecutor;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public GateFaceSync(GateDeviceMapper gateDeviceMapper, GateSyncTaskMapper taskMapper, MembershipMapper membershipMapper,
                        GymUserMapper userMapper, UserFaceMapper userFaceMapper, FaceFileService faceFileService,
                        WsGateCommandPort port, Snowflake snowflake, TimeProvider timeProvider,
                        @Qualifier("gateExecutor") Executor gateExecutor) {
        this.gateDeviceMapper = gateDeviceMapper;
        this.taskMapper = taskMapper;
        this.membershipMapper = membershipMapper;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.faceFileService = faceFileService;
        this.port = port;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.gateExecutor = gateExecutor;
    }

    @Transactional
    public void onMembership(long userId, long storeId) {
        List<GateDevice> devices = enabled(storeId);
        for (GateDevice device : devices) {
            enqueue(userId, device.getId());
        }
        afterCommit(() -> gateExecutor.execute(() -> flushStore(storeId)));
    }

    @Transactional
    public void onFaceChanged(long userId) {
        List<Membership> rows = membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, userId)
                .ne(Membership::getStatus, "REVOKED"));
        Set<Long> stores = rows.stream().map(Membership::getStoreId).collect(Collectors.toSet());
        for (Long storeId : stores) {
            for (GateDevice device : enabled(storeId)) {
                enqueue(userId, device.getId());
            }
        }
        afterCommit(() -> gateExecutor.execute(() -> stores.forEach(this::flushStore)));
    }

    @Transactional
    public void onDeviceReady(long deviceId) {
        GateDevice device = gateDeviceMapper.selectById(deviceId);
        if (device == null || !"ENABLED".equals(device.getStatus())) {
            return;
        }
        List<Membership> rows = membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getStoreId, device.getStoreId())
                .ne(Membership::getStatus, "REVOKED"));
        rows.stream().map(Membership::getUserId).distinct().forEach(userId -> enqueue(userId, device.getId()));
        afterCommit(() -> gateExecutor.execute(() -> flushDevice(device.getDeviceSn())));
    }

    public void retryFailed(long deviceId, String sn) {
        List<GateSyncTask> failed = taskMapper.selectList(new LambdaQueryWrapper<GateSyncTask>()
                .eq(GateSyncTask::getDeviceId, deviceId)
                .eq(GateSyncTask::getStatus, "FAILED"));
        LocalDateTime now = timeProvider.now();
        for (GateSyncTask task : failed) {
            task.setStatus("PENDING");
            task.setAttempts(0);
            task.setLastError(null);
            task.setUpdatedAt(now);
            taskMapper.updateById(task);
        }
        gateExecutor.execute(() -> flushDevice(sn));
    }

    public void pushToken(String sn) {
        gateExecutor.execute(() -> {
            synchronized (lock(sn)) {
                tuneToken(sn);
            }
        });
    }

    public void onConnected(String sn) {
        synchronized (lock(sn)) {
            tune(sn);
            drain(sn);
        }
    }

    public void flushDevice(String sn) {
        synchronized (lock(sn)) {
            drain(sn);
        }
    }

    public String status(long userId, Long storeId) {
        List<GateSyncTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GateSyncTask>()
                .eq(GateSyncTask::getUserId, userId));
        if (storeId != null) {
            Set<Long> deviceIds = enabled(storeId).stream().map(GateDevice::getId).collect(Collectors.toSet());
            gateDeviceMapper.selectList(new LambdaQueryWrapper<GateDevice>().eq(GateDevice::getStoreId, storeId))
                    .forEach(device -> deviceIds.add(device.getId()));
            tasks = tasks.stream().filter(task -> deviceIds.contains(task.getDeviceId())).toList();
        }
        if (tasks.isEmpty()) {
            return "NONE";
        }
        if (tasks.stream().anyMatch(task -> "PENDING".equals(task.getStatus()) || "SENT".equals(task.getStatus()))) {
            return "PENDING";
        }
        if (tasks.stream().anyMatch(task -> "FAILED".equals(task.getStatus()))) {
            return "FAILED";
        }
        if (tasks.stream().anyMatch(task -> "OK".equals(task.getStatus()))) {
            return "OK";
        }
        return "NONE";
    }

    @Scheduled(fixedDelay = 15000)
    public void flushOnline() {
        List<GateDevice> devices = gateDeviceMapper.selectList(new LambdaQueryWrapper<>());
        for (GateDevice device : devices) {
            if (!"ENABLED".equals(device.getStatus()) || !port.online(device.getDeviceSn())) {
                continue;
            }
            try {
                flushDevice(device.getDeviceSn());
            } catch (Exception exception) {
                log.warn("补发闸机人脸失败 {}", device.getDeviceSn());
            }
        }
    }

    private void flushStore(long storeId) {
        for (GateDevice device : enabled(storeId)) {
            flushDevice(device.getDeviceSn());
        }
    }

    private void tune(String sn) {
        if (!port.online(sn)) {
            return;
        }
        Map<String, Object> time = new LinkedHashMap<>();
        time.put("cmd", "setTime");
        time.put("value", timeProvider.now().atZone(TimeProvider.ZONE).toEpochSecond());
        port.exchange(sn, time);
        Map<String, Object> interval = new LinkedHashMap<>();
        interval.put("cmd", "setRecognitionInterval");
        interval.put("value", 15);
        port.exchange(sn, interval);
        Map<String, Object> pass = new LinkedHashMap<>();
        pass.put("cmd", "setOnlineVerifyFailurePass");
        pass.put("value", "off");
        port.exchange(sn, pass);
        tuneToken(sn);
    }

    private void tuneToken(String sn) {
        GateDevice device = findSn(sn);
        if (device == null || device.getToken() == null || device.getToken().isBlank() || !port.online(sn)) {
            return;
        }
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("cmd", "setHttpToken");
        token.put("token_key", "token");
        token.put("token_value", device.getToken());
        token.put("signature_mode", 1);
        port.exchange(sn, token);
    }

    private void drain(String sn) {
        GateDevice device = findSn(sn);
        if (device == null || !"ENABLED".equals(device.getStatus()) || !port.online(sn)) {
            return;
        }
        recoverStale(device.getId());
        for (int i = 0; i < 50; i++) {
            GateSyncTask task = taskMapper.selectOne(new LambdaQueryWrapper<GateSyncTask>()
                    .eq(GateSyncTask::getDeviceId, device.getId())
                    .eq(GateSyncTask::getStatus, "PENDING")
                    .orderByAsc(GateSyncTask::getId)
                    .last("LIMIT 1"));
            if (task == null) {
                return;
            }
            dispatch(device, task);
        }
    }

    private void dispatch(GateDevice device, GateSyncTask task) {
        LocalDateTime now = timeProvider.now();
        task.setStatus("SENT");
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        try {
            Built built = build(device, task);
            if (built.error != null) {
                fail(task, built.error);
                return;
            }
            JsonNode reply = port.exchange(device.getDeviceSn(), built.data);
            int code = reply == null ? -1 : reply.path("code").asInt(-1);
            if (code == 6 && "editUser".equals(built.data.get("cmd"))) {
                built.data.put("cmd", "addUser");
                built.data.remove("edit_mode");
                reply = port.exchange(device.getDeviceSn(), built.data);
                code = reply == null ? -1 : reply.path("code").asInt(-1);
            }
            if (code == 0) {
                task.setStatus("OK");
                task.setCmd(String.valueOf(built.data.get("cmd")));
                task.setLastError(null);
                task.setUpdatedAt(timeProvider.now());
                taskMapper.updateById(task);
                return;
            }
            String message = reply == null ? "闸机没有响应" : reply.path("msg").asText("下发失败");
            fail(task, message);
        } catch (Exception exception) {
            fail(task, exception.getMessage() == null ? "下发失败" : exception.getMessage());
        }
    }

    private Built build(GateDevice device, GateSyncTask task) {
        UserFace face = enrolled(task.getUserId());
        if (face == null) {
            return Built.error("没有人脸");
        }
        GymUser user = userMapper.selectById(task.getUserId());
        if (user == null || user.getMemberNo() == null || user.getMemberNo().isBlank()) {
            return Built.error("没有会员编号");
        }
        byte[] photo;
        try {
            photo = faceFileService.read(user.getId(), face.getObjectKey());
        } catch (BizException exception) {
            return Built.error(exception.getMessage());
        }
        String template;
        try {
            template = GatePhotoCodec.encode(photo);
        } catch (IllegalArgumentException exception) {
            return Built.error(exception.getMessage());
        }
        long synced = taskMapper.selectCount(new LambdaQueryWrapper<GateSyncTask>()
                .eq(GateSyncTask::getDeviceId, device.getId())
                .eq(GateSyncTask::getUserId, user.getId())
                .eq(GateSyncTask::getStatus, "OK")
                .ne(GateSyncTask::getId, task.getId()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", user.getMemberNo());
        data.put("name", user.getNickname() == null || user.getNickname().isBlank() ? user.getMemberNo() : user.getNickname());
        data.put("id_valid", validUntil(user.getId(), device.getStoreId()).format(MINUTE));
        data.put("face_template", template);
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            data.put("phone", user.getPhone());
        }
        if (synced > 0) {
            data.put("cmd", "editUser");
            data.put("edit_mode", 1);
        } else {
            data.put("cmd", "addUser");
            data.put("user_type", 0);
            data.put("mode", 0);
        }
        task.setCmd(String.valueOf(data.get("cmd")));
        return new Built(data, null);
    }

    private LocalDateTime validUntil(long userId, long storeId) {
        List<Membership> rows = membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, userId)
                .eq(Membership::getStoreId, storeId)
                .ne(Membership::getStatus, "REVOKED")
                .gt(Membership::getEndAt, timeProvider.now()));
        return rows.stream().map(Membership::getEndAt).filter(Objects::nonNull).max(LocalDateTime::compareTo)
                .orElseGet(() -> timeProvider.now().minusMinutes(1));
    }

    private void enqueue(long userId, long deviceId) {
        if (enrolled(userId) == null) {
            return;
        }
        GateSyncTask open = taskMapper.selectOne(new LambdaQueryWrapper<GateSyncTask>()
                .eq(GateSyncTask::getDeviceId, deviceId)
                .eq(GateSyncTask::getUserId, userId)
                .in(GateSyncTask::getStatus, "PENDING", "SENT")
                .last("LIMIT 1"));
        if (open != null) {
            return;
        }
        GateSyncTask failed = taskMapper.selectOne(new LambdaQueryWrapper<GateSyncTask>()
                .eq(GateSyncTask::getDeviceId, deviceId)
                .eq(GateSyncTask::getUserId, userId)
                .eq(GateSyncTask::getStatus, "FAILED")
                .orderByDesc(GateSyncTask::getId)
                .last("LIMIT 1"));
        LocalDateTime now = timeProvider.now();
        if (failed != null) {
            failed.setStatus("PENDING");
            failed.setAttempts(0);
            failed.setLastError(null);
            failed.setUpdatedAt(now);
            taskMapper.updateById(failed);
            return;
        }
        GateSyncTask task = new GateSyncTask();
        task.setId(snowflake.next());
        task.setDeviceId(deviceId);
        task.setUserId(userId);
        task.setCmd("addUser");
        task.setStatus("PENDING");
        task.setAttempts(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
    }

    private void recoverStale(long deviceId) {
        LocalDateTime deadline = timeProvider.now().minusMinutes(2);
        List<GateSyncTask> stale = taskMapper.selectList(new LambdaQueryWrapper<GateSyncTask>()
                .eq(GateSyncTask::getDeviceId, deviceId)
                .eq(GateSyncTask::getStatus, "SENT")
                .lt(GateSyncTask::getUpdatedAt, deadline));
        for (GateSyncTask task : stale) {
            task.setStatus("PENDING");
            task.setUpdatedAt(timeProvider.now());
            taskMapper.updateById(task);
        }
    }

    private void fail(GateSyncTask task, String message) {
        task.setStatus("FAILED");
        task.setAttempts(task.getAttempts() == null ? 1 : task.getAttempts() + 1);
        task.setLastError(message == null ? "下发失败" : message.substring(0, Math.min(200, message.length())));
        task.setUpdatedAt(timeProvider.now());
        taskMapper.updateById(task);
    }

    private UserFace enrolled(long userId) {
        return userFaceMapper.selectOne(new LambdaQueryWrapper<UserFace>()
                .eq(UserFace::getUserId, userId)
                .eq(UserFace::getStatus, "ENROLLED")
                .orderByDesc(UserFace::getId)
                .last("LIMIT 1"));
    }

    private List<GateDevice> enabled(long storeId) {
        return gateDeviceMapper.selectList(new LambdaQueryWrapper<GateDevice>()
                .eq(GateDevice::getStoreId, storeId)
                .eq(GateDevice::getStatus, "ENABLED"));
    }

    private GateDevice findSn(String sn) {
        return gateDeviceMapper.selectOne(new LambdaQueryWrapper<GateDevice>().eq(GateDevice::getDeviceSn, sn));
    }

    private Object lock(String sn) {
        return locks.computeIfAbsent(sn, key -> new Object());
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private record Built(Map<String, Object> data, String error) {
        static Built error(String message) {
            return new Built(null, message);
        }
    }
}
