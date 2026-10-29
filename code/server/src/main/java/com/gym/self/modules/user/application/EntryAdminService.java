package com.gym.self.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.gate.GateFaceSync;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.gym.self.modules.user.domain.UserFace;
import com.gym.self.modules.user.domain.UserFaceMapper;
import com.gym.self.modules.user.face.FaceVendorPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EntryAdminService {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;
    private final GymUserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final FaceFileService faceFileService;
    private final FaceVendorPort faceVendorPort;
    private final MemberNoService memberNoService;
    private final GateFaceSync gateFaceSync;

    public EntryAdminService(JdbcTemplate jdbc, Snowflake snowflake, TimeProvider timeProvider, GymUserMapper userMapper,
                             UserFaceMapper userFaceMapper, FaceFileService faceFileService, FaceVendorPort faceVendorPort,
                             MemberNoService memberNoService, GateFaceSync gateFaceSync) {
        this.jdbc = jdbc;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.faceFileService = faceFileService;
        this.faceVendorPort = faceVendorPort;
        this.memberNoService = memberNoService;
        this.gateFaceSync = gateFaceSync;
    }

    public List<Map<String, Object>> list(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        return jdbc.queryForList("""
                SELECT d.id, d.name, d.phone, d.store_id, s.name AS store_name, d.gate_until, u.member_no
                FROM door_staff d
                JOIN store s ON s.id = d.store_id
                JOIN `user` u ON u.id = d.user_id
                ORDER BY d.id DESC
                """).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(row.get("id")));
            item.put("name", row.get("name"));
            item.put("phone", row.get("phone"));
            item.put("storeId", String.valueOf(row.get("store_id")));
            item.put("storeName", row.get("store_name"));
            item.put("gateUntil", format(row.get("gate_until")));
            item.put("memberNo", row.get("member_no"));
            return item;
        }).toList();
    }

    @Transactional
    public String create(AdminPrincipal actor, long storeId, String name, String phone, String faceBase64, String gateUntil) {
        StoreScope.requireMaster(actor);
        mustStore(storeId);
        String personName = requireName(name);
        String mobile = requireMobile(phone);
        LocalDateTime until = requireUntil(gateUntil);
        if (count("SELECT COUNT(*) FROM door_staff WHERE phone = ?", mobile) > 0) {
            throw BizException.badRequest("这个手机号已经是管理员");
        }
        GymUser user = ensureUser(storeId, personName, mobile);
        enrollFace(user, decodeFace(faceBase64));
        LocalDateTime now = timeProvider.now();
        long id = snowflake.next();
        jdbc.update("""
                INSERT INTO door_staff (id, store_id, user_id, name, phone, gate_until, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, id, storeId, user.getId(), personName, mobile, Timestamp.valueOf(until), now, now);
        gateFaceSync.syncPerson(user.getId(), storeId, "添加管理员");
        return String.valueOf(id);
    }

    @Transactional
    public void update(AdminPrincipal actor, long id, String name, String phone, String faceBase64, String gateUntil) {
        StoreScope.requireMaster(actor);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT store_id, user_id, phone FROM door_staff WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw BizException.badRequest("管理员不存在");
        }
        long storeId = ((Number) rows.get(0).get("store_id")).longValue();
        long userId = ((Number) rows.get(0).get("user_id")).longValue();
        String personName = requireName(name);
        String mobile = requireMobile(phone);
        LocalDateTime until = requireUntil(gateUntil);
        if (count("SELECT COUNT(*) FROM door_staff WHERE phone = ? AND id <> ?", mobile, id) > 0) {
            throw BizException.badRequest("这个手机号已经是管理员");
        }
        GymUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.badRequest("管理员不存在");
        }
        GymUser occupied = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, mobile));
        if (occupied != null && !occupied.getId().equals(user.getId())) {
            throw BizException.badRequest("手机号已被使用");
        }
        user.setNickname(personName);
        user.setPhone(mobile);
        user.setUpdatedAt(timeProvider.now());
        userMapper.updateById(user);
        if (faceBase64 != null && !faceBase64.isBlank()) {
            enrollFace(user, decodeFace(faceBase64));
        }
        jdbc.update("UPDATE door_staff SET name = ?, phone = ?, gate_until = ?, updated_at = ? WHERE id = ?",
                personName, mobile, Timestamp.valueOf(until), timeProvider.now(), id);
        gateFaceSync.syncPerson(user.getId(), storeId, "编辑管理员");
    }

    @Transactional
    public void delete(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT store_id, user_id, phone FROM door_staff WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw BizException.badRequest("管理员不存在");
        }
        long storeId = ((Number) rows.get(0).get("store_id")).longValue();
        long userId = ((Number) rows.get(0).get("user_id")).longValue();
        jdbc.update("DELETE FROM door_staff WHERE id = ?", id);
        gateFaceSync.syncPerson(userId, storeId, "删除管理员");
        if (count("SELECT COUNT(*) FROM membership WHERE user_id = ?", userId) == 0
                && count("SELECT COUNT(*) FROM lesson_account WHERE user_id = ?", userId) == 0
                && count("SELECT COUNT(*) FROM trade_order WHERE user_id = ?", userId) == 0
                && count("SELECT COUNT(*) FROM coach WHERE phone = ?", rows.get(0).get("phone")) == 0) {
            jdbc.update("DELETE FROM user_face WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM `user` WHERE id = ?", userId);
        }
    }

    private GymUser ensureUser(long storeId, String name, String phone) {
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, phone));
        LocalDateTime now = timeProvider.now();
        if (user != null) {
            user.setNickname(name);
            if (user.getMemberNo() == null || user.getMemberNo().isBlank()) {
                user.setMemberNo(memberNoService.next());
            }
            user.setRegisterStatus("ACTIVE");
            user.setStatus("ACTIVE");
            user.setUpdatedAt(now);
            userMapper.updateById(user);
            return user;
        }
        user = new GymUser();
        user.setId(snowflake.next());
        user.setNickname(name);
        user.setPhone(phone);
        user.setMemberNo(memberNoService.next());
        user.setRegisterStatus("ACTIVE");
        user.setRegisteredAt(now);
        user.setStatus("ACTIVE");
        user.setCreatedStoreId(storeId);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return user;
    }

    private void enrollFace(GymUser user, byte[] photo) {
        String objectKey = faceFileService.presign(user.getId(), "image/jpeg");
        faceFileService.save(user.getId(), objectKey, photo);
        long size = faceFileService.size(user.getId(), objectKey);
        FaceVendorPort.Result result = faceVendorPort.enroll(user.getId(), objectKey, size);
        if (!result.enrolled()) {
            throw BizException.badRequest(result.reason() == null ? "人脸入库失败" : result.reason());
        }
        UserFace face = new UserFace();
        face.setId(snowflake.next());
        face.setUserId(user.getId());
        face.setObjectKey(objectKey);
        face.setStatus("ENROLLED");
        face.setVendorFaceId(user.getMemberNo());
        face.setCreatedAt(timeProvider.now());
        userFaceMapper.insert(face);
    }

    private void mustStore(long storeId) {
        if (count("SELECT COUNT(*) FROM store WHERE id = ?", storeId) == 0) {
            throw BizException.badRequest("门店不存在");
        }
    }

    private int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private LocalDateTime requireUntil(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest("请填写闸机到期时间");
        }
        String text = raw.trim().replace('T', ' ');
        if (text.length() == 16) {
            text = text + ":00";
        }
        LocalDateTime until;
        try {
            until = LocalDateTime.parse(text, MINUTE);
        } catch (RuntimeException ex) {
            throw BizException.badRequest("闸机到期时间不正确");
        }
        if (!until.isAfter(timeProvider.now())) {
            throw BizException.badRequest("闸机到期时间要晚于现在");
        }
        return until;
    }

    private static String requireName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.length() > 32) {
            throw BizException.badRequest("请填写姓名");
        }
        return value;
    }

    private static String requireMobile(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        if (!digits.matches("1\\d{10}")) {
            throw BizException.badRequest("请填写 11 位手机号");
        }
        return digits;
    }

    private static byte[] decodeFace(String faceBase64) {
        if (faceBase64 == null || faceBase64.isBlank()) {
            throw BizException.badRequest("请上传人脸照片");
        }
        String raw = faceBase64;
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma > 0) {
            raw = raw.substring(comma + 1);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(raw);
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) {
                throw BizException.badRequest("照片大小不正确");
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            throw BizException.badRequest("人脸照片不正确");
        }
    }

    private static String format(Object value) {
        if (value instanceof LocalDateTime time) {
            return time.format(MINUTE);
        }
        if (value instanceof Timestamp stamp) {
            return stamp.toLocalDateTime().format(MINUTE);
        }
        return value == null ? "" : String.valueOf(value);
    }
}
