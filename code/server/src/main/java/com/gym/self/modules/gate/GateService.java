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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

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
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;

    public GateService(GateDeviceMapper gateDeviceMapper, DoorLogMapper doorLogMapper, GymUserMapper userMapper,
                       UserFaceMapper userFaceMapper, MembershipMapper membershipMapper, StoreMapper storeMapper,
                       AppConfigMapper appConfigMapper, GateState gateState, Snowflake snowflake, TimeProvider timeProvider) {
        this.gateDeviceMapper = gateDeviceMapper;
        this.doorLogMapper = doorLogMapper;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.membershipMapper = membershipMapper;
        this.storeMapper = storeMapper;
        this.appConfigMapper = appConfigMapper;
        this.gateState = gateState;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
    }

    public String createDevice(AdminPrincipal actor, long storeId, String name) {
        StoreScope.requireMaster(actor);
        mustStore(storeId);
        GateDevice device = new GateDevice();
        device.setId(snowflake.next());
        device.setStoreId(storeId);
        device.setDeviceSn("SN" + device.getId());
        device.setSecret(HexFormat.of().formatHex(String.valueOf(snowflake.next()).getBytes(StandardCharsets.UTF_8)).substring(0, 32));
        device.setName(name.trim());
        device.setStatus("ENABLED");
        device.setLastBeatAt(null);
        gateDeviceMapper.insert(device);
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
            return new DeviceView(String.valueOf(device.getId()), String.valueOf(device.getStoreId()),
                    store == null ? "" : store.getName(), device.getDeviceSn(), device.getName(), device.getStatus(),
                    online, secret);
        }).toList();
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
            GymUser user = userMapper.selectById(row.getUserId());
            UserFace face = user == null ? null : userFaceMapper.selectOne(new LambdaQueryWrapper<UserFace>()
                    .eq(UserFace::getUserId, user.getId()).eq(UserFace::getStatus, "ENROLLED")
                    .orderByDesc(UserFace::getId).last("LIMIT 1"));
            return new DoorView(String.valueOf(row.getId()), String.valueOf(row.getStoreId()), row.getDeviceSn(),
                    user == null ? "" : String.valueOf(user.getId()),
                    user == null ? "" : user.getMemberNo(), face == null ? "未采集" : "已采集", row.getResult(),
                    row.getCreatedAt());
        }).toList();
    }

    public int onlineCount(long storeId) {
        LocalDateTime from = timeProvider.now().minusMinutes(onlineMinutes());
        return doorLogMapper.selectList(new LambdaQueryWrapper<DoorLog>()
                        .eq(DoorLog::getStoreId, storeId)
                        .eq(DoorLog::getResult, "SUCCESS")
                        .ge(DoorLog::getCreatedAt, from))
                .stream().map(DoorLog::getUserId).distinct().toList().size();
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
        DoorLog row = new DoorLog();
        row.setId(snowflake.next());
        row.setUserId(userId);
        row.setStoreId(device.getStoreId());
        row.setDeviceSn(device.getDeviceSn());
        row.setMembershipId(membershipId);
        row.setChannel("FACE");
        row.setResult(result);
        row.setCreatedAt(now);
        doorLogMapper.insert(row);
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
                             boolean online, String secret) {
    }

    public record DoorView(String id, String storeId, String deviceSn, String userId, String memberNo, String face,
                           String result, LocalDateTime createdAt) {
    }

    public record VerifyResult(boolean open, String reason) {
    }
}
