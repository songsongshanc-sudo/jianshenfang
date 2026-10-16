package com.gym.self.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.application.AdminOpLog;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.content.domain.AppConfig;
import com.gym.self.modules.content.domain.AppConfigMapper;
import com.gym.self.modules.order.application.OrderService;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.gym.self.modules.user.domain.UserFace;
import com.gym.self.modules.user.domain.UserFaceMapper;
import com.gym.self.modules.user.face.FaceVendorPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminMemberService {

    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final GymUserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final FaceFileService faceFileService;
    private final FaceVendorPort faceVendorPort;
    private final MemberNoService memberNoService;
    private final OrderService orderService;
    private final AppConfigMapper appConfigMapper;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;
    private final AdminOpLog adminOpLog;

    public AdminMemberService(JdbcTemplate jdbc, GymUserMapper userMapper, UserFaceMapper userFaceMapper,
                              FaceFileService faceFileService, FaceVendorPort faceVendorPort,
                              MemberNoService memberNoService, OrderService orderService,
                              AppConfigMapper appConfigMapper, Snowflake snowflake, TimeProvider timeProvider,
                              AdminOpLog adminOpLog) {
        this.jdbc = jdbc;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.faceFileService = faceFileService;
        this.faceVendorPort = faceVendorPort;
        this.memberNoService = memberNoService;
        this.orderService = orderService;
        this.appConfigMapper = appConfigMapper;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.adminOpLog = adminOpLog;
    }

    public List<Map<String, Object>> list(AdminPrincipal actor, Long storeId, String name, String phone) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.nickname, u.phone, u.member_no, u.created_store_id, u.openid, u.invite_code, s.name AS store_name
                FROM `user` u
                LEFT JOIN store s ON s.id = u.created_store_id
                WHERE u.phone IS NOT NULL AND u.phone <> '' AND u.register_status = 'ACTIVE'
                """);
        List<Object> args = new ArrayList<>();
        if (scoped != null) {
            sql.append("""
                     AND (u.created_store_id = ? OR EXISTS (
                        SELECT 1 FROM trade_order o WHERE o.user_id = u.id AND o.store_id = ? AND o.status = 'PAID')
                     OR EXISTS (
                        SELECT 1 FROM membership m WHERE m.user_id = u.id AND m.store_id = ?))
                    """);
            args.add(scoped);
            args.add(scoped);
            args.add(scoped);
        }
        if (name != null && !name.isBlank()) {
            sql.append(" AND u.nickname LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        if (phone != null && !phone.isBlank()) {
            sql.append(" AND u.phone LIKE ?");
            args.add("%" + phone.trim() + "%");
        }
        sql.append(" ORDER BY u.id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(row.get("id")));
            m.put("name", row.get("nickname"));
            m.put("phone", row.get("phone"));
            m.put("memberNo", row.get("member_no"));
            Object store = row.get("created_store_id");
            m.put("storeId", store == null ? null : String.valueOf(store));
            m.put("storeName", row.get("store_name"));
            boolean bound = row.get("openid") != null && !String.valueOf(row.get("openid")).isBlank();
            m.put("wechatBound", bound);
            m.put("inviteCode", bound ? "" : String.valueOf(row.get("invite_code") == null ? "" : row.get("invite_code")));
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, String> create(AdminPrincipal actor, long storeId, String name, String phone, String faceBase64,
                                     List<Long> cardIds, List<Long> packIds) {
        StoreScope.requireStoreAccess(actor, storeId);
        String realName = requireName(name);
        String mobile = requirePhone(phone);
        if (userMapper.selectCount(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, mobile)) > 0) {
            throw BizException.rejected("该手机号已是会员，请在列表中找到后编辑");
        }
        byte[] photo = decodeFace(faceBase64);
        LocalDateTime now = timeProvider.now();
        GymUser user = new GymUser();
        user.setId(snowflake.next());
        user.setNickname(realName);
        user.setPhone(mobile);
        user.setMemberNo(memberNoService.next());
        user.setRegisterStatus("ACTIVE");
        user.setRegisteredAt(now);
        user.setStatus("ACTIVE");
        user.setCreatedStoreId(storeId);
        user.setInviteCode(nextInviteCode());
        user.setInviteFail(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        enrollFace(user, photo);
        grant(storeId, user.getId(), cardIds, packIds);
        adminOpLog.record(actor, storeId, user.getId(), user.getId(), "新增会员",
                "姓名 " + realName + "，手机号 " + mobile + "，会员编号 " + user.getMemberNo() + "，已生成邀请码");
        return Map.of("id", String.valueOf(user.getId()), "inviteCode", user.getInviteCode());
    }

    @Transactional
    public String renewInvite(AdminPrincipal actor, long userId) {
        GymUser user = mustVisible(actor, userId);
        boolean bound = user.getOpenid() != null && !user.getOpenid().isBlank();
        String code = nextInviteCode();
        jdbc.update("UPDATE `user` SET openid = NULL, invite_code = ?, invite_fail = 0, updated_at = ? WHERE id = ?",
                code, timeProvider.now(), userId);
        Long storeId = actor.storeId() != null ? actor.storeId() : user.getCreatedStoreId();
        String detail = bound
                ? "会员 " + display(user) + " 已解除原微信，需用新邀请码重新绑定"
                : "会员 " + display(user) + " 已更换邀请码，旧码失效";
        adminOpLog.record(actor, storeId, userId, userId, "重新生成邀请码", detail);
        return code;
    }

    @Transactional
    public void changeName(AdminPrincipal actor, long userId, String name) {
        GymUser user = mustVisible(actor, userId);
        String realName = requireName(name);
        String previous = user.getNickname() == null || user.getNickname().isBlank() ? "未填姓名" : user.getNickname();
        if (realName.equals(previous)) {
            return;
        }
        user.setNickname(realName);
        user.setUpdatedAt(timeProvider.now());
        userMapper.updateById(user);
        Long storeId = actor.storeId() != null ? actor.storeId() : user.getCreatedStoreId();
        adminOpLog.record(actor, storeId, userId, userId, "更新会员姓名",
                "会员编号 " + (user.getMemberNo() == null ? "" : user.getMemberNo()) + " 姓名由 " + previous + " 改为 " + realName);
    }

    @Transactional
    public void changePhone(AdminPrincipal actor, long userId, String phone) {
        GymUser user = mustVisible(actor, userId);
        String mobile = requirePhone(phone);
        if (mobile.equals(user.getPhone())) {
            return;
        }
        Long taken = userMapper.selectCount(new LambdaQueryWrapper<GymUser>()
                .eq(GymUser::getPhone, mobile)
                .ne(GymUser::getId, userId));
        if (taken != null && taken > 0) {
            throw BizException.rejected("该手机号已是其他会员");
        }
        String previous = user.getPhone();
        user.setPhone(mobile);
        user.setUpdatedAt(timeProvider.now());
        userMapper.updateById(user);
        Long storeId = actor.storeId() != null ? actor.storeId() : user.getCreatedStoreId();
        adminOpLog.record(actor, storeId, userId, userId, "更新会员手机号",
                "会员 " + display(user) + " 手机号由 " + previous + " 改为 " + mobile);
    }

    @Transactional
    public void grantMore(AdminPrincipal actor, long userId, long storeId, List<Long> cardIds, List<Long> packIds) {
        StoreScope.requireStoreAccess(actor, storeId);
        GymUser user = userMapper.selectById(userId);
        if (user == null || user.getPhone() == null) {
            throw BizException.badRequest("会员不存在");
        }
        if ((cardIds == null || cardIds.isEmpty()) && (packIds == null || packIds.isEmpty())) {
            throw BizException.badRequest("请选择会员卡或课程");
        }
        grant(storeId, userId, cardIds, packIds);
        int cards = cardIds == null ? 0 : cardIds.size();
        int packs = packIds == null ? 0 : packIds.size();
        Long logStore = actor.storeId() != null ? actor.storeId() : storeId;
        adminOpLog.record(actor, logStore, userId, userId, "补开会员卡或课程",
                "会员 " + display(user) + "，会员卡 " + cards + " 张，课程 " + packs + " 个");
    }

    public String privacyUrl() {
        AppConfig config = appConfigMapper.selectById("privacy.guide.url");
        return config == null || config.getConfigValue() == null ? "" : config.getConfigValue();
    }

    public void savePrivacyUrl(AdminPrincipal actor, String url) {
        StoreScope.requireMaster(actor);
        String value = url == null ? "" : url.trim();
        if (!value.isEmpty() && !value.startsWith("http://") && !value.startsWith("https://")) {
            throw BizException.badRequest("请填写 http 或 https 链接");
        }
        if (value.length() > 500) {
            throw BizException.badRequest("链接过长");
        }
        AppConfig config = appConfigMapper.selectById("privacy.guide.url");
        if (config == null) {
            config = new AppConfig();
            config.setConfigKey("privacy.guide.url");
            config.setConfigValue(value);
            config.setUpdatedAt(timeProvider.now());
            appConfigMapper.insert(config);
            return;
        }
        config.setConfigValue(value);
        config.setUpdatedAt(timeProvider.now());
        appConfigMapper.updateById(config);
    }

    private void grant(long storeId, long userId, List<Long> cardIds, List<Long> packIds) {
        if (cardIds != null) {
            for (Long cardId : cardIds) {
                if (cardId != null) {
                    orderService.grantStaffCard(userId, storeId, cardId);
                }
            }
        }
        if (packIds != null) {
            for (Long packId : packIds) {
                if (packId == null) {
                    continue;
                }
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT store_id, name, price_fen, status FROM course_pack WHERE id = ?", packId);
                if (rows.isEmpty() || !"ON".equals(String.valueOf(rows.get(0).get("status")))
                        || ((Number) rows.get(0).get("store_id")).longValue() != storeId) {
                    throw BizException.badRequest("课程不存在");
                }
                orderService.grantStaffCourse(userId, storeId, packId, String.valueOf(rows.get(0).get("name")),
                        ((Number) rows.get(0).get("price_fen")).longValue());
            }
        }
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

    private GymUser mustVisible(AdminPrincipal actor, long userId) {
        GymUser user = userMapper.selectById(userId);
        if (user == null || user.getPhone() == null || !"ACTIVE".equals(user.getRegisterStatus())) {
            throw BizException.badRequest("会员不存在");
        }
        if (actor.master()) {
            return user;
        }
        Long storeId = actor.storeId();
        Integer hit = jdbc.queryForObject("""
                SELECT COUNT(*) FROM `user` u
                WHERE u.id = ? AND (u.created_store_id = ? OR EXISTS (
                    SELECT 1 FROM trade_order o WHERE o.user_id = u.id AND o.store_id = ? AND o.status = 'PAID')
                 OR EXISTS (
                    SELECT 1 FROM membership m WHERE m.user_id = u.id AND m.store_id = ?))
                """, Integer.class, userId, storeId, storeId, storeId);
        if (hit == null || hit == 0) {
            throw BizException.badRequest("会员不存在");
        }
        return user;
    }

    private static String display(GymUser user) {
        String name = user.getNickname() == null || user.getNickname().isBlank() ? "未填姓名" : user.getNickname();
        String no = user.getMemberNo() == null ? "" : "（" + user.getMemberNo() + "）";
        return name + no;
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

    private String nextInviteCode() {
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder code = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                code.append(INVITE_ALPHABET.charAt(RANDOM.nextInt(INVITE_ALPHABET.length())));
            }
            String value = code.toString();
            Long taken = userMapper.selectCount(new LambdaQueryWrapper<GymUser>().eq(GymUser::getInviteCode, value));
            if (taken == null || taken == 0) {
                return value;
            }
        }
        throw BizException.badRequest("邀请码生成失败，请重试");
    }

    private static String requireName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || value.length() > 32) {
            throw BizException.badRequest("请填写姓名");
        }
        return value;
    }

    private static String requirePhone(String phone) {
        String value = phone == null ? "" : phone.trim();
        if (!value.matches("1\\d{10}")) {
            throw BizException.badRequest("请填写 11 位手机号");
        }
        return value;
    }
}
