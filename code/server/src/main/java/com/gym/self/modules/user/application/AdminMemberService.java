package com.gym.self.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.application.AdminOpLog;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.gate.GateFaceSync;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminMemberService {

    private final JdbcTemplate jdbc;
    private final GymUserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final FaceFileService faceFileService;
    private final FaceVendorPort faceVendorPort;
    private final MemberNoService memberNoService;
    private final OrderService orderService;
    private final GateFaceSync gateFaceSync;
    private final AppConfigMapper appConfigMapper;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;
    private final AdminOpLog adminOpLog;

    public AdminMemberService(JdbcTemplate jdbc, GymUserMapper userMapper, UserFaceMapper userFaceMapper,
                              FaceFileService faceFileService, FaceVendorPort faceVendorPort,
                              MemberNoService memberNoService, OrderService orderService, GateFaceSync gateFaceSync,
                              AppConfigMapper appConfigMapper, Snowflake snowflake, TimeProvider timeProvider,
                              AdminOpLog adminOpLog) {
        this.jdbc = jdbc;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.faceFileService = faceFileService;
        this.faceVendorPort = faceVendorPort;
        this.memberNoService = memberNoService;
        this.orderService = orderService;
        this.gateFaceSync = gateFaceSync;
        this.appConfigMapper = appConfigMapper;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.adminOpLog = adminOpLog;
    }

    public List<Map<String, Object>> list(AdminPrincipal actor, Long storeId, String name, String phone) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.nickname, u.phone, u.member_no, u.register_status, u.status, u.created_store_id, u.openid, s.name AS store_name
                FROM `user` u
                LEFT JOIN store s ON s.id = u.created_store_id
                WHERE ((u.member_no IS NOT NULL AND u.member_no <> '')
                    OR (u.phone IS NOT NULL AND u.phone <> ''))
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
        sql.append(" ORDER BY u.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(row.get("id")));
            m.put("name", row.get("nickname"));
            m.put("phone", row.get("phone"));
            m.put("memberNo", row.get("member_no"));
            m.put("registerStatus", row.get("register_status"));
            m.put("status", row.get("status") == null ? "ACTIVE" : String.valueOf(row.get("status")));
            Object store = row.get("created_store_id");
            m.put("storeId", store == null ? null : String.valueOf(store));
            m.put("storeName", row.get("store_name"));
            boolean bound = row.get("openid") != null && !String.valueOf(row.get("openid")).isBlank();
            m.put("wechatBound", bound);
            return m;
        }).toList();
    }

    @Transactional
    public Map<String, String> create(AdminPrincipal actor, long storeId, String name, String phone, String faceBase64,
                                     List<Long> cardIds, List<Long> packIds) {
        StoreScope.requireStoreAccess(actor, storeId);
        String realName = requireName(name);
        String mobile = requirePhone(phone);
        GymUser existing = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, mobile));
        if (existing != null) {
            if (actor.master() || visibleTo(actor, existing.getId())) {
                throw BizException.rejected("该手机号已是会员，请在列表里搜索这个手机号");
            }
            throw BizException.rejected("该手机号已登记，但不在当前门店的会员列表里");
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
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        enrollFace(user, photo);
        grant(storeId, user.getId(), cardIds, packIds);
        adminOpLog.record(actor, storeId, user.getId(), user.getId(), "新增会员",
                "姓名 " + realName + "，手机号 " + mobile + "，会员编号 " + user.getMemberNo());
        return Map.of("id", String.valueOf(user.getId()));
    }

    @Transactional
    public void unbind(AdminPrincipal actor, long userId) {
        GymUser user = mustVisible(actor, userId);
        jdbc.update("UPDATE `user` SET openid = NULL, updated_at = ? WHERE id = ?", timeProvider.now(), userId);
        Long storeId = actor.storeId() != null ? actor.storeId() : user.getCreatedStoreId();
        adminOpLog.record(actor, storeId, userId, userId, "解绑微信", "会员 " + display(user) + " 已解除微信绑定");
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
        String previous = user.getPhone() == null || user.getPhone().isBlank() ? "未填" : user.getPhone();
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
        GymUser user = mustVisible(actor, userId);
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

    public Map<String, Object> memberGrants(AdminPrincipal actor, long userId) {
        mustVisible(actor, userId);
        LocalDateTime now = timeProvider.now();
        Long storeId = actor.master() ? null : actor.storeId();
        String cardSql = """
                SELECT m.id, c.name, m.end_at, s.name AS store_name
                FROM membership m
                JOIN card_product c ON c.id = m.card_product_id
                JOIN store s ON s.id = m.store_id
                WHERE m.user_id = ? AND m.end_at > ? AND m.status <> 'REVOKED'
                """;
        String packSql = """
                SELECT a.id, p.name, a.remaining, s.name AS store_name
                FROM lesson_account a
                JOIN course_pack p ON p.id = a.pack_id
                JOIN store s ON s.id = p.store_id
                WHERE a.user_id = ? AND a.remaining > 0
                """;
        List<Object> cardArgs = new ArrayList<>();
        cardArgs.add(userId);
        cardArgs.add(now);
        List<Object> packArgs = new ArrayList<>();
        packArgs.add(userId);
        if (storeId != null) {
            cardSql += " AND m.store_id = ?";
            packSql += " AND p.store_id = ?";
            cardArgs.add(storeId);
            packArgs.add(storeId);
        }
        cardSql += " ORDER BY m.id";
        packSql += " ORDER BY a.id";
        List<Map<String, Object>> cards = jdbc.queryForList(cardSql, cardArgs.toArray()).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(row.get("id")));
            item.put("name", row.get("name"));
            item.put("storeName", row.get("store_name"));
            String endAt = row.get("end_at") == null ? "" : String.valueOf(row.get("end_at"));
            item.put("endAt", endAt.length() >= 10 ? endAt.substring(0, 10) : endAt);
            return item;
        }).toList();
        List<Map<String, Object>> packs = jdbc.queryForList(packSql, packArgs.toArray()).stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", String.valueOf(row.get("id")));
            item.put("name", row.get("name"));
            item.put("storeName", row.get("store_name"));
            item.put("remaining", row.get("remaining"));
            return item;
        }).toList();
        return Map.of("cards", cards, "packs", packs);
    }

    @Transactional
    public void removeCard(AdminPrincipal actor, long userId, long membershipId) {
        GymUser user = mustVisible(actor, userId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id, store_id, status FROM membership WHERE id = ?", membershipId);
        if (rows.isEmpty() || ((Number) rows.get(0).get("user_id")).longValue() != userId
                || "REVOKED".equals(String.valueOf(rows.get(0).get("status")))) {
            throw BizException.badRequest("会员卡不存在");
        }
        long storeId = ((Number) rows.get(0).get("store_id")).longValue();
        StoreScope.requireStoreAccess(actor, storeId);
        LocalDateTime now = timeProvider.now();
        jdbc.update("UPDATE membership SET status = 'REVOKED', end_at = ? WHERE id = ?", now, membershipId);
        gateFaceSync.onMembership(userId, storeId);
        gateFaceSync.syncMembership(userId, storeId, "删除会员卡");
        adminOpLog.record(actor, storeId, userId, membershipId, "删除会员卡", "会员 " + display(user) + " 的会员卡已删除");
    }

    @Transactional
    public void removePack(AdminPrincipal actor, long userId, long accountId) {
        GymUser user = mustVisible(actor, userId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.user_id, a.pack_id, a.remaining, p.store_id, p.name
                FROM lesson_account a JOIN course_pack p ON p.id = a.pack_id
                WHERE a.id = ?
                """, accountId);
        if (rows.isEmpty() || ((Number) rows.get(0).get("user_id")).longValue() != userId) {
            throw BizException.badRequest("课程不存在");
        }
        int remaining = ((Number) rows.get(0).get("remaining")).intValue();
        if (remaining <= 0) {
            throw BizException.badRequest("课程不存在");
        }
        long storeId = ((Number) rows.get(0).get("store_id")).longValue();
        StoreScope.requireStoreAccess(actor, storeId);
        long packId = ((Number) rows.get(0).get("pack_id")).longValue();
        LocalDateTime now = timeProvider.now();
        jdbc.update("UPDATE lesson_account SET remaining = 0, updated_at = ? WHERE id = ?", now, accountId);
        jdbc.update("INSERT INTO lesson_ledger (id, user_id, pack_id, delta, reason, created_at) VALUES (?,?,?,?,?,?)",
                snowflake.next(), userId, packId, -remaining, "ADMIN_REMOVE", now);
        adminOpLog.record(actor, storeId, userId, accountId, "删除课程",
                "会员 " + display(user) + " 的课程「" + rows.get(0).get("name") + "」已删除，清掉 " + remaining + " 节");
    }

    @Transactional
    public void suspendMember(AdminPrincipal actor, long userId) {
        GymUser user = mustVisible(actor, userId);
        if ("DISABLED".equals(user.getStatus())) {
            return;
        }
        user.setStatus("DISABLED");
        user.setUpdatedAt(timeProvider.now());
        userMapper.updateById(user);
        gateFaceSync.suspendPerson(user.getId(), user.getMemberNo(), gatesHolding(user));
        Long storeId = actor.storeId() != null ? actor.storeId() : user.getCreatedStoreId();
        adminOpLog.record(actor, storeId, userId, userId, "停用会员", "会员 " + display(user) + " 已停用");
    }

    @Transactional
    public void resumeMember(AdminPrincipal actor, long userId) {
        GymUser user = mustVisible(actor, userId);
        if (!"DISABLED".equals(user.getStatus())) {
            return;
        }
        user.setStatus("ACTIVE");
        user.setUpdatedAt(timeProvider.now());
        userMapper.updateById(user);
        gateFaceSync.resumePerson(user.getId(), gatesHolding(user));
        Long storeId = actor.storeId() != null ? actor.storeId() : user.getCreatedStoreId();
        adminOpLog.record(actor, storeId, userId, userId, "恢复会员", "会员 " + display(user) + " 已恢复");
    }

    private List<com.gym.self.modules.gate.GateDevice> gatesHolding(GymUser user) {
        long createdStoreId = user.getCreatedStoreId() == null ? 0L : user.getCreatedStoreId();
        return jdbc.query("""
                SELECT DISTINCT d.id, d.device_sn, d.name
                FROM gate_device d
                WHERE d.status = 'ENABLED' AND (
                  EXISTS (
                    SELECT 1 FROM gate_sync_task t
                    WHERE t.device_id = d.id AND t.user_id = ?)
                  OR d.store_id IN (SELECT m.store_id FROM membership m WHERE m.user_id = ?)
                  OR d.store_id IN (SELECT v.store_id FROM visit_pass v WHERE v.user_id = ? AND v.store_id IS NOT NULL)
                  OR d.store_id = ?
                )
                """, (rs, row) -> {
            com.gym.self.modules.gate.GateDevice device = new com.gym.self.modules.gate.GateDevice();
            device.setId(rs.getLong("id"));
            device.setDeviceSn(rs.getString("device_sn"));
            device.setName(rs.getString("name"));
            device.setStatus("ENABLED");
            return device;
        }, user.getId(), user.getId(), user.getId(), createdStoreId);
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
        boolean grantedCard = cardIds != null && cardIds.stream().anyMatch(id -> id != null);
        if (grantedCard) {
            gateFaceSync.syncMembership(userId, storeId, "保存会员卡");
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
        if (user == null || !listed(user)) {
            throw BizException.badRequest("会员不存在");
        }
        if (!visibleTo(actor, userId)) {
            throw BizException.badRequest("会员不存在");
        }
        return user;
    }

    private boolean visibleTo(AdminPrincipal actor, long userId) {
        if (actor.master()) {
            return true;
        }
        Long storeId = actor.storeId();
        if (storeId == null) {
            return false;
        }
        Integer hit = jdbc.queryForObject("""
                SELECT COUNT(*) FROM `user` u
                WHERE u.id = ? AND (u.created_store_id = ? OR EXISTS (
                    SELECT 1 FROM trade_order o WHERE o.user_id = u.id AND o.store_id = ? AND o.status = 'PAID')
                 OR EXISTS (
                    SELECT 1 FROM membership m WHERE m.user_id = u.id AND m.store_id = ?))
                """, Integer.class, userId, storeId, storeId, storeId);
        return hit != null && hit > 0;
    }

    private static boolean listed(GymUser user) {
        boolean phone = user.getPhone() != null && !user.getPhone().isBlank();
        boolean memberNo = user.getMemberNo() != null && !user.getMemberNo().isBlank();
        return phone || memberNo;
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
