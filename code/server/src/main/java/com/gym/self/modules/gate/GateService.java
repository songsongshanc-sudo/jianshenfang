package com.gym.self.modules.gate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.content.domain.AppConfigMapper;
import com.gym.self.modules.store.domain.Store;
import com.gym.self.modules.store.domain.StoreMapper;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.gym.self.modules.user.domain.UserFace;
import com.gym.self.modules.user.domain.UserFaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class GateService {

    private final GateDeviceMapper gateDeviceMapper;
    private final DoorLogMapper doorLogMapper;
    private final GymUserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final MembershipMapper membershipMapper;
    private final StoreMapper storeMapper;
    private final AppConfigMapper appConfigMapper;
    private final GateState gateState;
    private final GateSyncTaskMapper taskMapper;
    private final GateFaceSync gateFaceSync;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;

    public GateService(GateDeviceMapper gateDeviceMapper, DoorLogMapper doorLogMapper, GymUserMapper userMapper,
                       UserFaceMapper userFaceMapper, MembershipMapper membershipMapper, StoreMapper storeMapper,
                       AppConfigMapper appConfigMapper, GateState gateState, GateSyncTaskMapper taskMapper,
                       GateFaceSync gateFaceSync, Snowflake snowflake, TimeProvider timeProvider) {
        this.gateDeviceMapper = gateDeviceMapper;
        this.doorLogMapper = doorLogMapper;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.membershipMapper = membershipMapper;
        this.storeMapper = storeMapper;
        this.appConfigMapper = appConfigMapper;
        this.gateState = gateState;
        this.taskMapper = taskMapper;
        this.gateFaceSync = gateFaceSync;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
    }

    public String createDevice(AdminPrincipal actor, long storeId, String name, String deviceSn) {
        StoreScope.requireMaster(actor);
        mustStore(storeId);
        GateDevice device = new GateDevice();
        device.setId(snowflake.next());
        device.setStoreId(storeId);
        String sn = deviceSn == null ? "" : deviceSn.trim();
        if (sn.isEmpty()) {
            sn = "SN" + device.getId();
        }
        if (sn.length() > 64) {
            throw BizException.badRequest("设备号不正确");
        }
        Long taken = gateDeviceMapper.selectCount(new LambdaQueryWrapper<GateDevice>().eq(GateDevice::getDeviceSn, sn));
        if (taken != null && taken > 0) {
            throw BizException.badRequest("设备号已存在");
        }
        device.setDeviceSn(sn);
        device.setSecret(HexFormat.of().formatHex(String.valueOf(snowflake.next()).getBytes(StandardCharsets.UTF_8)).substring(0, 32));
        device.setName(name.trim());
        device.setStatus("ENABLED");
        device.setLastBeatAt(null);
        gateDeviceMapper.insert(device);
        gateFaceSync.onDeviceReady(device.getId());
        return String.valueOf(device.getId());
    }

    public String resetSecret(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        GateDevice device = mustDevice(id);
        device.setSecret(HexFormat.of().formatHex((String.valueOf(snowflake.next()) + "secret").getBytes(StandardCharsets.UTF_8)).substring(0, 32));
        gateDeviceMapper.updateById(device);
        return device.getSecret();
    }

    public void setStatus(AdminPrincipal actor, long id, String status) {
        StoreScope.requireMaster(actor);
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw BizException.badRequest("设备状态不正确");
        }
        GateDevice device = mustDevice(id);
        device.setStatus(status);
        gateDeviceMapper.updateById(device);
        if ("ENABLED".equals(status)) {
            gateFaceSync.onDeviceReady(device.getId());
        }
    }

    public List<DeviceView> devices(AdminPrincipal actor, Long storeId) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        LambdaQueryWrapper<GateDevice> query = new LambdaQueryWrapper<GateDevice>().orderByAsc(GateDevice::getId);
        if (scoped != null) {
            query.eq(GateDevice::getStoreId, scoped);
        }
        LocalDateTime onlineAfter = timeProvider.now().minusMinutes(3);
        return gateDeviceMapper.selectList(query).stream().map(device -> {
            Store store = storeMapper.selectById(device.getStoreId());
            boolean online = device.getLastBeatAt() != null && device.getLastBeatAt().isAfter(onlineAfter);
            String secret = actor.master() ? device.getSecret() : null;
            int pending = countTasks(device.getId(), "PENDING") + countTasks(device.getId(), "SENT");
            int failed = countTasks(device.getId(), "FAILED");
            GateSyncTask error = taskMapper.selectOne(new LambdaQueryWrapper<GateSyncTask>()
                    .eq(GateSyncTask::getDeviceId, device.getId())
                    .eq(GateSyncTask::getStatus, "FAILED")
                    .orderByDesc(GateSyncTask::getUpdatedAt)
                    .last("LIMIT 1"));
            return new DeviceView(String.valueOf(device.getId()), String.valueOf(device.getStoreId()),
                    store == null ? "" : store.getName(), device.getDeviceSn(), device.getName(), device.getStatus(),
                    online, secret, device.getFirmware() == null ? "" : device.getFirmware(), pending, failed,
                    error == null || error.getLastError() == null ? "" : error.getLastError(),
                    device.getToken() != null && !device.getToken().isBlank());
        }).toList();
    }

    public String rotateToken(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        GateDevice device = mustDevice(id);
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        device.setToken(HexFormat.of().formatHex(bytes));
        gateDeviceMapper.updateById(device);
        gateFaceSync.pushToken(device.getDeviceSn());
        return device.getToken();
    }

    public void retryFailed(AdminPrincipal actor, long id) {
        GateDevice device = mustDevice(id);
        if (!actor.master() && (actor.storeId() == null || !actor.storeId().equals(device.getStoreId()))) {
            throw BizException.forbidden("不能修改其他门店");
        }
        gateFaceSync.retryFailed(device.getId(), device.getDeviceSn());
    }

    public GateDevice deviceBySn(String sn) {
        if (sn == null || sn.isBlank()) {
            return null;
        }
        return gateDeviceMapper.selectOne(new LambdaQueryWrapper<GateDevice>().eq(GateDevice::getDeviceSn, sn.trim()));
    }

    public VendorReply vendorVerify(String sn, String userId, LocalDateTime recogTime) {
        GateDevice device = deviceBySn(sn);
        if (device == null) {
            return VendorReply.missing();
        }
        if (userId != null && userId.startsWith("lesson:")) {
            return VendorReply.of(3, "未注册", Map.of());
        }
        if (!"ENABLED".equals(device.getStatus())) {
            return VendorReply.of(3, "设备已停用", Map.of());
        }
        GymUser user = findUser(userId, null);
        Outcome outcome = decide(device, user, recogTime, userId);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("is_upload_record", 0);
        if (user != null && user.getMemberNo() != null) {
            content.put("user_id", user.getMemberNo());
            content.put("user_name", user.getNickname() == null || user.getNickname().isBlank() ? user.getMemberNo() : user.getNickname());
        }
        return switch (outcome) {
            case OPEN -> VendorReply.of(0, "开门", content);
            case EXPIRED -> VendorReply.of(1, "已过期", content);
            case OTHER_STORE -> VendorReply.of(3, "非本店会员", content);
            case UNREGISTERED -> VendorReply.of(3, "未注册", Map.of());
            case DISABLED -> VendorReply.of(3, "设备已停用", Map.of());
        };
    }

    public void vendorRecord(String sn, String userId, LocalDateTime recogTime, int passStatus) {
        GateDevice device = deviceBySn(sn);
        if (device == null || already(device.getDeviceSn(), recogTime, userId)) {
            return;
        }
        GymUser user = findUser(userId, null);
        if (user == null) {
            log(null, device, null, "REJECTED", timeProvider.now(), recogTime, passStatus, userId);
            return;
        }
        if (passStatus == 0 && gateState.withinDebounce(user.getId(), device.getDeviceSn(), Duration.ofSeconds(debounceSeconds()))) {
            return;
        }
        Membership pass = passStatus == 0 ? activePass(user.getId(), device.getStoreId(), timeProvider.now()) : null;
        log(user.getId(), device, pass == null ? null : pass.getId(), passStatus == 0 && pass != null ? "SUCCESS" : "REJECTED",
                timeProvider.now(), recogTime, passStatus, user.getMemberNo());
    }

    public void vendorStranger(String sn, String userId, String realUserId, LocalDateTime recogTime, int passStatus) {
        GateDevice device = deviceBySn(sn);
        String key = realUserId == null || realUserId.isBlank() ? userId : realUserId;
        if (device == null || already(device.getDeviceSn(), recogTime, key)) {
            return;
        }
        GymUser user = findUser(key, null);
        if (user != null && "F1000001".equals(key)) {
            user = null;
        }
        log(user == null ? null : user.getId(), device, null, "REJECTED", timeProvider.now(), recogTime, passStatus, key);
    }

    public void heartbeat(String sn, String timestamp, String nonce, String signature, String body) {
        GateDevice device = signed(sn, timestamp, nonce, signature, body);
        if (device == null) {
            return;
        }
        device.setLastBeatAt(timeProvider.now());
        gateDeviceMapper.updateById(device);
    }

    @Transactional
    public VerifyResult verify(String sn, String timestamp, String nonce, String signature, String body,
                               String memberNo, String vendorFaceId) {
        if (memberNo != null && memberNo.startsWith("lesson:")) {
            return new VerifyResult(false, "未注册");
        }
        GateDevice device = signed(sn, timestamp, nonce, signature, body);
        if (device == null) {
            return new VerifyResult(false, "签名无效");
        }
        if (!"ENABLED".equals(device.getStatus())) {
            return new VerifyResult(false, "设备已停用");
        }
        GymUser user = findUser(memberNo, vendorFaceId);
        if (user == null || !"ACTIVE".equals(user.getRegisterStatus())) {
            return new VerifyResult(false, "未注册");
        }
        UserFace face = userFaceMapper.selectOne(new LambdaQueryWrapper<UserFace>()
                .eq(UserFace::getUserId, user.getId())
                .eq(UserFace::getStatus, "ENROLLED")
                .orderByDesc(UserFace::getId)
                .last("LIMIT 1"));
        if (face == null) {
            return new VerifyResult(false, "未注册");
        }
        LocalDateTime now = timeProvider.now();
        Membership pass = activePass(user.getId(), device.getStoreId(), now);
        if (pass == null) {
            log(user.getId(), device, null, "REJECTED", now);
            Membership any = anyOtherStore(user.getId(), device.getStoreId(), now);
            return new VerifyResult(false, any == null ? "会员无效" : "非本店会员");
        }
        int window = debounceSeconds();
        if (gateState.withinDebounce(user.getId(), device.getDeviceSn(), Duration.ofSeconds(window))) {
            return new VerifyResult(true, "开门");
        }
        log(user.getId(), device, pass.getId(), "SUCCESS", now);
        return new VerifyResult(true, "开门");
    }

    public List<DoorView> doors(AdminPrincipal actor, Long storeId) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        LambdaQueryWrapper<DoorLog> query = new LambdaQueryWrapper<DoorLog>()
                .orderByDesc(DoorLog::getCreatedAt).orderByDesc(DoorLog::getId);
        if (scoped != null) {
            query.eq(DoorLog::getStoreId, scoped);
        }
        return doorLogMapper.selectList(query).stream().map(row -> {
            GymUser user = row.getUserId() == null ? null : userMapper.selectById(row.getUserId());
            UserFace face = user == null ? null : userFaceMapper.selectOne(new LambdaQueryWrapper<UserFace>()
                    .eq(UserFace::getUserId, user.getId()).eq(UserFace::getStatus, "ENROLLED")
                    .orderByDesc(UserFace::getId).last("LIMIT 1"));
            String memberNo = user == null ? strangerName(row.getMemberKey()) : user.getMemberNo();
            return new DoorView(String.valueOf(row.getId()), String.valueOf(row.getStoreId()), row.getDeviceSn(),
                    user == null ? "" : String.valueOf(user.getId()),
                    memberNo, face == null ? "未采集" : "已采集", row.getResult(),
                    row.getCreatedAt());
        }).toList();
    }

    public int onlineCount(long storeId) {
        LocalDateTime from = timeProvider.now().minusMinutes(onlineMinutes());
        return doorLogMapper.selectList(new LambdaQueryWrapper<DoorLog>()
                        .eq(DoorLog::getStoreId, storeId)
                        .eq(DoorLog::getResult, "SUCCESS")
                        .ge(DoorLog::getCreatedAt, from))
                .stream().map(DoorLog::getUserId).filter(Objects::nonNull).distinct().toList().size();
    }

    public boolean memberNow(Long userId, long storeId) {
        if (userId == null) {
            return false;
        }
        return activePass(userId, storeId, timeProvider.now()) != null;
    }

    private Membership activePass(long userId, long storeId, LocalDateTime now) {
        List<Membership> rows = membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, userId)
                .ne(Membership::getStatus, "REVOKED")
                .gt(Membership::getEndAt, now));
        Membership cross = null;
        Membership here = null;
        for (Membership row : rows) {
            if (row.getCrossStore() != null && row.getCrossStore() == 1) {
                cross = row;
            }
            if (row.getStoreId().equals(storeId)) {
                here = row;
            }
        }
        return cross != null ? cross : here;
    }

    private Membership anyOtherStore(long userId, long storeId, LocalDateTime now) {
        return membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, userId)
                .ne(Membership::getStoreId, storeId)
                .ne(Membership::getStatus, "REVOKED")
                .gt(Membership::getEndAt, now)
                .last("LIMIT 1"));
    }

    private GymUser findUser(String memberNo, String vendorFaceId) {
        if (memberNo != null && !memberNo.isBlank()) {
            return userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getMemberNo, memberNo.trim()));
        }
        if (vendorFaceId != null && !vendorFaceId.isBlank()) {
            UserFace face = userFaceMapper.selectOne(new LambdaQueryWrapper<UserFace>()
                    .eq(UserFace::getVendorFaceId, vendorFaceId.trim())
                    .eq(UserFace::getStatus, "ENROLLED")
                    .last("LIMIT 1"));
            return face == null ? null : userMapper.selectById(face.getUserId());
        }
        return null;
    }

    private void log(long userId, GateDevice device, Long membershipId, String result, LocalDateTime now) {
        log(userId, device, membershipId, result, now, null, null, null);
    }

    private void log(Long userId, GateDevice device, Long membershipId, String result, LocalDateTime now,
                     LocalDateTime recogTime, Integer passStatus, String memberKey) {
        DoorLog row = new DoorLog();
        row.setId(snowflake.next());
        row.setUserId(userId);
        row.setStoreId(device.getStoreId());
        row.setDeviceSn(device.getDeviceSn());
        row.setMembershipId(membershipId);
        row.setChannel("FACE");
        row.setResult(result);
        row.setCreatedAt(now);
        row.setRecogTime(recogTime);
        row.setPassStatus(passStatus);
        row.setMemberKey(memberKey);
        doorLogMapper.insert(row);
    }

    private Outcome decide(GateDevice device, GymUser user, LocalDateTime recogTime, String memberKey) {
        if (!"ENABLED".equals(device.getStatus())) {
            return Outcome.DISABLED;
        }
        if (user == null || !"ACTIVE".equals(user.getRegisterStatus())) {
            return Outcome.UNREGISTERED;
        }
        UserFace face = userFaceMapper.selectOne(new LambdaQueryWrapper<UserFace>()
                .eq(UserFace::getUserId, user.getId())
                .eq(UserFace::getStatus, "ENROLLED")
                .orderByDesc(UserFace::getId)
                .last("LIMIT 1"));
        if (face == null) {
            return Outcome.UNREGISTERED;
        }
        LocalDateTime now = timeProvider.now();
        Membership pass = activePass(user.getId(), device.getStoreId(), now);
        if (pass == null) {
            log(user.getId(), device, null, "REJECTED", now, recogTime, 1, memberKey);
            return anyOtherStore(user.getId(), device.getStoreId(), now) == null ? Outcome.EXPIRED : Outcome.OTHER_STORE;
        }
        if (gateState.withinDebounce(user.getId(), device.getDeviceSn(), Duration.ofSeconds(debounceSeconds()))) {
            return Outcome.OPEN;
        }
        log(user.getId(), device, pass.getId(), "SUCCESS", now, recogTime, 0, user.getMemberNo());
        return Outcome.OPEN;
    }

    private boolean already(String deviceSn, LocalDateTime recogTime, String memberKey) {
        if (recogTime == null || memberKey == null || memberKey.isBlank()) {
            return false;
        }
        Long count = doorLogMapper.selectCount(new LambdaQueryWrapper<DoorLog>()
                .eq(DoorLog::getDeviceSn, deviceSn)
                .eq(DoorLog::getRecogTime, recogTime)
                .eq(DoorLog::getMemberKey, memberKey));
        return count != null && count > 0;
    }

    private static String strangerName(String memberKey) {
        if (memberKey == null || memberKey.isBlank() || "F1000001".equals(memberKey)) {
            return "陌生人";
        }
        return memberKey;
    }

    private int countTasks(long deviceId, String status) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<GateSyncTask>()
                .eq(GateSyncTask::getDeviceId, deviceId)
                .eq(GateSyncTask::getStatus, status));
        return count == null ? 0 : count.intValue();
    }

    private enum Outcome {
        OPEN, EXPIRED, OTHER_STORE, UNREGISTERED, DISABLED
    }

    private GateDevice signed(String sn, String timestamp, String nonce, String signature, String body) {
        if (sn == null || timestamp == null || nonce == null || signature == null) {
            return null;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return null;
        }
        long now = timeProvider.now().atZone(TimeProvider.ZONE).toInstant().toEpochMilli();
        if (Math.abs(now - ts) > Duration.ofMinutes(5).toMillis()) {
            return null;
        }
        GateDevice device = gateDeviceMapper.selectOne(new LambdaQueryWrapper<GateDevice>().eq(GateDevice::getDeviceSn, sn));
        if (device == null) {
            return null;
        }
        String expect = sign(device.getSecret(), sn, timestamp, nonce, body == null ? "" : body);
        if (!expect.equalsIgnoreCase(signature)) {
            return null;
        }
        if (!gateState.firstNonce(sn, nonce, Duration.ofMinutes(5))) {
            return null;
        }
        return device;
    }

    public static String sign(String secret, String sn, String timestamp, String nonce, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = sn + "\n" + timestamp + "\n" + nonce + "\n" + body;
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int debounceSeconds() {
        var config = appConfigMapper.selectById("entry.debounce.seconds");
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return 15;
        }
        return Integer.parseInt(config.getConfigValue());
    }

    private int onlineMinutes() {
        var config = appConfigMapper.selectById("online.window.minutes");
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return 90;
        }
        return Integer.parseInt(config.getConfigValue());
    }

    private void mustStore(long storeId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null || Integer.valueOf(1).equals(store.getDeleted())) {
            throw BizException.badRequest("门店不存在");
        }
    }

    private GateDevice mustDevice(long id) {
        GateDevice device = gateDeviceMapper.selectById(id);
        if (device == null) {
            throw BizException.badRequest("设备不存在");
        }
        return device;
    }

    public record DeviceView(String id, String storeId, String storeName, String deviceSn, String name, String status,
                             boolean online, String secret, String firmware, int pendingCount, int failedCount,
                             String lastError, boolean signed) {
    }

    public record DoorView(String id, String storeId, String deviceSn, String userId, String memberNo, String face,
                           String result, LocalDateTime createdAt) {
    }

    public record VerifyResult(boolean open, String reason) {
    }
}
