package com.gym.self.modules.shop;

import com.gym.self.common.api.BizException;
import com.gym.self.common.html.RichHtml;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.gate.GateFaceSync;
import com.gym.self.modules.order.application.CourseGrant;
import com.gym.self.modules.order.application.OrderService;
import com.gym.self.modules.order.domain.TradeOrder;
import com.gym.self.modules.user.application.AdminMemberService;
import com.gym.self.modules.user.application.MemberNoService;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShopService implements CourseGrant {

    private final JdbcTemplate jdbc;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;
    private final ContentSafety contentSafety;
    private final OrderService orderService;
    private final GymUserMapper userMapper;
    private final MembershipMapper membershipMapper;
    private final AdminMemberService adminMemberService;
    private final MemberNoService memberNoService;
    private final GateFaceSync gateFaceSync;
    private static final DateTimeFormatter GATE_UNTIL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Map<Long, ArrayDeque<Instant>> grouponHits = new ConcurrentHashMap<>();
    private final Map<String, LessonToken> lessonTokens = new ConcurrentHashMap<>();

    public ShopService(JdbcTemplate jdbc, Snowflake snowflake, TimeProvider timeProvider, ContentSafety contentSafety,
                       OrderService orderService, GymUserMapper userMapper, MembershipMapper membershipMapper,
                       AdminMemberService adminMemberService, MemberNoService memberNoService, GateFaceSync gateFaceSync) {
        this.jdbc = jdbc;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.contentSafety = contentSafety;
        this.orderService = orderService;
        this.userMapper = userMapper;
        this.membershipMapper = membershipMapper;
        this.adminMemberService = adminMemberService;
        this.memberNoService = memberNoService;
        this.gateFaceSync = gateFaceSync;
    }

    public String repair(long userId, long storeId, String equipmentCode, String content, String imageUrl) {
        requireText(content, "请填写报修说明");
        contentSafety.check(userId, content);
        return insertTicket("repair_ticket", userId, storeId, blank(equipmentCode), content.trim(), imageUrl);
    }

    public String complaint(long userId, long storeId, String category, String content, String imageUrl) {
        if (!List.of("EQUIPMENT", "CLEAN", "COACH", "OTHER").contains(category)) {
            throw BizException.badRequest("投诉类型不正确");
        }
        requireText(content, "请填写投诉内容");
        contentSafety.check(userId, content);
        long id = snowflake.next();
        LocalDateTime now = timeProvider.now();
        jdbc.update("""
                INSERT INTO complaint_ticket (id, user_id, store_id, category, content, image_url, status, created_at, updated_at)
                VALUES (?,?,?,?,?,?, 'PENDING', ?, ?)
                """, id, userId, storeId, category, content.trim(), blank(imageUrl), now, now);
        return String.valueOf(id);
    }

    public String lost(long userId, long storeId, String kind, String name, String content, String imageUrl, boolean visible) {
        if (!List.of("LOST", "FOUND").contains(kind)) {
            throw BizException.badRequest("请选择丢失或拾到");
        }
        requireText(name, "请填写名称");
        requireText(content, "请填写说明");
        contentSafety.check(userId, name + "\n" + content);
        long id = snowflake.next();
        LocalDateTime now = timeProvider.now();
        jdbc.update("""
                INSERT INTO lost_item (id, user_id, store_id, kind, name, content, image_url, visible, resolved, created_at, updated_at)
                VALUES (?,?,?,?,?,?,?,?,0,?,?)
                """, id, userId, storeId, kind, name.trim(), content.trim(), blank(imageUrl), visible ? 1 : 0, now, now);
        return String.valueOf(id);
    }

    public List<Map<String, Object>> myRepairs(long userId) {
        return jdbc.queryForList("SELECT * FROM repair_ticket WHERE user_id = ? ORDER BY id DESC", userId);
    }

    public List<Map<String, Object>> myComplaints(long userId) {
        return jdbc.queryForList("SELECT * FROM complaint_ticket WHERE user_id = ? ORDER BY id DESC", userId);
    }

    public List<Map<String, Object>> lostFeed(long userId, long storeId, String tab) {
        if ("MINE_LOST".equals(tab)) {
            return jdbc.queryForList("SELECT * FROM lost_item WHERE user_id = ? AND kind = 'LOST' ORDER BY id DESC", userId);
        }
        if ("MINE_FOUND".equals(tab)) {
            return jdbc.queryForList("SELECT * FROM lost_item WHERE user_id = ? AND kind = 'FOUND' ORDER BY id DESC", userId);
        }
        return jdbc.queryForList("""
                SELECT id, store_id, kind, name, content, image_url, created_at
                FROM lost_item WHERE store_id = ? AND visible = 1 AND resolved = 0 ORDER BY id DESC
                """, storeId);
    }

    public List<Map<String, Object>> adminRepairs(AdminPrincipal actor, Long storeId) {
        return scoped(actor, storeId, "repair_ticket");
    }

    public List<Map<String, Object>> adminComplaints(AdminPrincipal actor, Long storeId) {
        return scoped(actor, storeId, "complaint_ticket");
    }

    public List<Map<String, Object>> adminLost(AdminPrincipal actor, Long storeId) {
        return scoped(actor, storeId, "lost_item");
    }

    public void updateTicket(AdminPrincipal actor, String table, long id, String status) {
        if (!List.of("PENDING", "DOING", "DONE").contains(status)) {
            throw BizException.badRequest("状态不正确");
        }
        if (!List.of("repair_ticket", "complaint_ticket", "lost_item").contains(table)) {
            throw BizException.badRequest("工单不存在");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT store_id FROM " + table + " WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw BizException.badRequest("工单不存在");
        }
        long storeId = ((Number) rows.get(0).get("store_id")).longValue();
        StoreScope.requiredStore(actor, storeId);
        if ("lost_item".equals(table)) {
            jdbc.update("UPDATE lost_item SET resolved = ?, updated_at = ? WHERE id = ?",
                    "DONE".equals(status) ? 1 : 0, timeProvider.now(), id);
            return;
        }
        jdbc.update("UPDATE " + table + " SET status = ?, updated_at = ? WHERE id = ?", status, timeProvider.now(), id);
    }

    public String saveEquipment(AdminPrincipal actor, Long id, long storeId, String code, String name, String intro, String imageUrl, String feedId, String coverUrl) {
        StoreScope.requireStoreAccess(actor, storeId);
        requireText(code, "请填写器械编号");
        requireText(name, "请填写器械名称");
        String feed = normalizeFeed(feedId);
        String cover = coverUrl(coverUrl);
        LocalDateTime now = timeProvider.now();
        String html = RichHtml.clean(intro);
        long equipmentId = id == null ? snowflake.next() : id;
        if (id == null) {
            jdbc.update("""
                    INSERT INTO equipment (id, store_id, code, name, intro, feed_id, cover_url, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, equipmentId, storeId, code.trim(), name.trim(), html, feed, cover, now, now);
        } else {
            List<Map<String, Object>> existing = jdbc.queryForList("SELECT store_id FROM equipment WHERE id = ?", equipmentId);
            if (existing.isEmpty()) {
                throw BizException.badRequest("器械不存在");
            }
            StoreScope.requireStoreAccess(actor, ((Number) existing.get(0).get("store_id")).longValue());
            int updated = jdbc.update("UPDATE equipment SET store_id=?, code=?, name=?, intro=?, feed_id=?, cover_url=?, updated_at=? WHERE id=?",
                    storeId, code.trim(), name.trim(), html, feed, cover, now, equipmentId);
            if (updated != 1) {
                throw BizException.badRequest("器械不存在");
            }
        }
        jdbc.update("DELETE FROM equipment_media WHERE equipment_id = ?", equipmentId);
        if (imageUrl != null && !imageUrl.isBlank()) {
            jdbc.update("INSERT INTO equipment_media (id, equipment_id, media_type, url, sort_no) VALUES (?,?, 'IMAGE', ?, 0)",
                    snowflake.next(), equipmentId, imageUrl.trim());
        }
        return String.valueOf(equipmentId);
    }

    public void deleteEquipment(AdminPrincipal actor, long id) {
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT store_id FROM equipment WHERE id = ?", id);
        if (existing.isEmpty()) {
            throw BizException.badRequest("器械不存在");
        }
        StoreScope.requireStoreAccess(actor, ((Number) existing.get(0).get("store_id")).longValue());
        jdbc.update("DELETE FROM equipment_media WHERE equipment_id = ?", id);
        jdbc.update("DELETE FROM equipment WHERE id = ?", id);
    }

    public String channelsFinderDefault() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT config_value FROM app_config WHERE config_key = 'channels.finder.id'");
        if (rows.isEmpty() || rows.get(0).get("config_value") == null) {
            return "";
        }
        return String.valueOf(rows.get(0).get("config_value"));
    }

    public void saveChannelsFinderDefault(AdminPrincipal actor, String finderId) {
        StoreScope.requireMaster(actor);
        String value = finderId == null || finderId.isBlank() ? "" : requireFinder(finderId);
        int updated = jdbc.update(
                "UPDATE app_config SET config_value = ?, updated_at = ? WHERE config_key = 'channels.finder.id'",
                value, timeProvider.now());
        if (updated != 1) {
            throw BizException.badRequest("配置不存在");
        }
    }

    public List<Map<String, Object>> equipmentOf(long storeId) {
        return jdbc.queryForList("""
                SELECT e.id, s.name AS store_name, e.code, e.name, e.intro, e.feed_id, e.cover_url,
                    (SELECT m.url FROM equipment_media m WHERE m.equipment_id = e.id AND m.media_type = 'IMAGE' ORDER BY m.sort_no LIMIT 1) AS image_url,
                    COALESCE(NULLIF(s.channels_finder_id, ''), NULLIF(cfg.config_value, '')) AS finder_user_name
                FROM equipment e
                JOIN store s ON s.id = e.store_id
                LEFT JOIN app_config cfg ON cfg.config_key = 'channels.finder.id'
                WHERE e.store_id = ? ORDER BY e.id
                """, storeId).stream().map(this::fitIntroField).toList();
    }

    public Map<String, Object> equipmentByCode(long storeId, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.id, e.code, e.name, e.intro, e.feed_id, e.cover_url,
                    (SELECT m.url FROM equipment_media m WHERE m.equipment_id = e.id AND m.media_type = 'IMAGE' ORDER BY m.sort_no LIMIT 1) AS image_url,
                    COALESCE(NULLIF(s.channels_finder_id, ''), NULLIF(cfg.config_value, '')) AS finder_user_name
                FROM equipment e
                JOIN store s ON s.id = e.store_id
                LEFT JOIN app_config cfg ON cfg.config_key = 'channels.finder.id'
                WHERE e.store_id = ? AND e.code = ?
                """, storeId, code);
        if (rows.isEmpty()) {
            throw BizException.badRequest("没有这台器械的教程");
        }
        return fitIntroField(rows.get(0));
    }

    public Map<String, Object> equipmentVideos(Long storeId, String keyword, int page) {
        int size = 10;
        int safePage = Math.max(page, 1);
        String word = keyword == null ? "" : keyword.trim();
        if (word.length() > 32) {
            throw BizException.badRequest("搜索内容过长");
        }
        boolean cross = !word.isEmpty();
        if (!cross && storeId == null) {
            throw BizException.badRequest("请先选择门店");
        }
        StringBuilder where = new StringBuilder("""
                FROM equipment e
                JOIN store s ON s.id = e.store_id AND s.deleted = 0
                LEFT JOIN app_config cfg ON cfg.config_key = 'channels.finder.id'
                WHERE e.feed_id IS NOT NULL AND e.feed_id <> ''
                """);
        List<Object> args = new ArrayList<>();
        if (cross) {
            String like = "%" + word.replace("%", "").replace("_", "").replace("\\", "") + "%";
            where.append(" AND (e.name LIKE ? OR e.code LIKE ?)");
            args.add(like);
            args.add(like);
        } else {
            where.append(" AND e.store_id = ?");
            args.add(storeId);
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) " + where, Integer.class, args.toArray());
        int count = total == null ? 0 : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((safePage - 1L) * size);
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT e.id, e.code, e.name, e.intro, e.cover_url, e.feed_id, s.name AS store_name,
                    COALESCE(NULLIF(s.channels_finder_id, ''), NULLIF(cfg.config_value, '')) AS finder_user_name
                """ + where + " ORDER BY e.updated_at DESC, e.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        list.forEach(row -> {
            row.put("id", String.valueOf(row.get("id")));
            fitIntroField(row);
        });
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("list", list);
        body.put("page", safePage);
        body.put("size", size);
        body.put("total", count);
        body.put("hasMore", (long) safePage * size < count);
        return body;
    }

    public void addGrouponRule(AdminPrincipal actor, long storeId, String platform, String code, long cardProductId) {
        StoreScope.requireStoreAccess(actor, storeId);
        if (!List.of("MEITUAN", "DOUYIN").contains(platform)) {
            throw BizException.badRequest("平台不正确");
        }
        requireText(code, "请填写券码");
        jdbc.update("""
                INSERT INTO groupon_rule (id, store_id, platform, code_hash, card_product_id, created_at)
                VALUES (?,?,?,?,?,?)
                """, snowflake.next(), storeId, platform, hash(code), cardProductId, timeProvider.now());
    }

    public List<Map<String, Object>> grouponRules(AdminPrincipal actor, Long storeId) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        if (scoped == null) {
            return jdbc.queryForList("SELECT id, store_id, platform, card_product_id, created_at FROM groupon_rule ORDER BY id DESC");
        }
        return jdbc.queryForList("SELECT id, store_id, platform, card_product_id, created_at FROM groupon_rule WHERE store_id = ? ORDER BY id DESC", scoped);
    }

    public void updateGrouponRule(AdminPrincipal actor, long id, long storeId, String platform, long cardProductId) {
        StoreScope.requireStoreAccess(actor, storeId);
        if (!List.of("MEITUAN", "DOUYIN").contains(platform)) {
            throw BizException.badRequest("平台不正确");
        }
        int updated = jdbc.update("UPDATE groupon_rule SET store_id=?, platform=?, card_product_id=? WHERE id=?",
                storeId, platform, cardProductId, id);
        if (updated != 1) {
            throw BizException.badRequest("团购券不存在");
        }
    }

    public void deleteGrouponRule(AdminPrincipal actor, long id) {
        Long storeId = jdbc.query("""
                SELECT store_id FROM groupon_rule WHERE id = ?
                """, rs -> rs.next() ? rs.getLong(1) : null, id);
        if (storeId == null) {
            throw BizException.badRequest("团购券不存在");
        }
        StoreScope.requireStoreAccess(actor, storeId);
        Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM groupon_redeem WHERE rule_id = ?", Integer.class, id);
        if (used != null && used > 0) {
            throw BizException.badRequest("券已核销，不能删除");
        }
        int updated = jdbc.update("DELETE FROM groupon_rule WHERE id = ?", id);
        if (updated != 1) {
            throw BizException.badRequest("团购券不存在");
        }
    }

    @Transactional
    public String redeemGroupon(long userId, long storeId, String platform, String code) {
        limitGroupon(userId);
        List<Map<String, Object>> rules = jdbc.queryForList(
                "SELECT id, store_id, card_product_id FROM groupon_rule WHERE platform = ? AND code_hash = ?",
                platform, hash(code));
        if (rules.isEmpty()) {
            throw BizException.rejected("没有匹配的团购券");
        }
        Map<String, Object> rule = rules.get(0);
        if (((Number) rule.get("store_id")).longValue() != storeId) {
            throw BizException.rejected("没有匹配的团购券");
        }
        long ruleId = ((Number) rule.get("id")).longValue();
        Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM groupon_redeem WHERE rule_id = ?", Integer.class, ruleId);
        if (used != null && used > 0) {
            throw BizException.rejected("券已使用");
        }
        long orderId = orderService.grantGroupon(userId, storeId, ((Number) rule.get("card_product_id")).longValue());
        jdbc.update("INSERT INTO groupon_redeem (id, rule_id, user_id, store_id, order_id, created_at) VALUES (?,?,?,?,?,?)",
                snowflake.next(), ruleId, userId, storeId, orderId, timeProvider.now());
        return String.valueOf(orderId);
    }

    @Transactional
    public String saveCoach(AdminPrincipal actor, long storeId, String name, String phone, String intro,
                            String avatarUrl, String photoUrl, String specialty, Double rating, Integer lessonTaught,
                            String certificateUrls, String gateUntil) {
        StoreScope.requireStoreAccess(actor, storeId);
        requireText(name, "请填写教练姓名");
        requireText(phone, "请填写手机号");
        long id = snowflake.next();
        String mobile = requireMobile(phone);
        LocalDateTime until = parseGateUntil(gateUntil);
        jdbc.update("""
                INSERT INTO coach (id, store_id, name, phone, intro, avatar_url, photo_url, specialty, rating, lesson_taught, certificate_urls, gate_until, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, storeId, name.trim(), mobile, RichHtml.clean(intro), blank(avatarUrl), blank(photoUrl),
                blank(specialty), rating == null ? 5.0 : rating, lessonTaught == null ? 0 : Math.max(0, lessonTaught),
                blank(certificateUrls), until == null ? null : Timestamp.valueOf(until), timeProvider.now());
        ensureCoachUser(storeId, name.trim(), mobile);
        syncCoachGate(storeId, mobile);
        return String.valueOf(id);
    }

    @Transactional
    public void updateCoach(AdminPrincipal actor, long id, String name, String phone, String intro,
                            String avatarUrl, String photoUrl, String specialty, Double rating, Integer lessonTaught,
                            String certificateUrls, String gateUntil) {
        long storeId = coachStoreId(id);
        StoreScope.requireStoreAccess(actor, storeId);
        requireText(name, "请填写教练姓名");
        String mobile = requireMobile(phone);
        List<Map<String, Object>> before = jdbc.queryForList("SELECT phone FROM coach WHERE id = ?", id);
        String previousPhone = before.isEmpty() || before.get(0).get("phone") == null ? "" : String.valueOf(before.get(0).get("phone"));
        LocalDateTime until = parseGateUntil(gateUntil);
        int updated = jdbc.update("""
                UPDATE coach SET name=?, phone=?, intro=?, avatar_url=?, photo_url=?, specialty=?, rating=?, lesson_taught=?, certificate_urls=?, gate_until=?
                WHERE id=?
                """, name.trim(), mobile, RichHtml.clean(intro), blank(avatarUrl), blank(photoUrl), blank(specialty),
                rating == null ? 5.0 : rating, lessonTaught == null ? 0 : Math.max(0, lessonTaught), blank(certificateUrls),
                until == null ? null : Timestamp.valueOf(until), id);
        if (updated != 1) {
            throw BizException.badRequest("教练不存在");
        }
        ensureCoachUser(storeId, name.trim(), mobile);
        syncCoachGate(storeId, mobile);
        if (!previousPhone.isBlank() && !previousPhone.equals(mobile)) {
            syncCoachGate(storeId, previousPhone);
        }
    }

    @Transactional
    public void deleteCoach(AdminPrincipal actor, long id) {
        long storeId = coachStoreId(id);
        StoreScope.requireStoreAccess(actor, storeId);
        Integer lessons = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_account a
                JOIN course_pack p ON p.id = a.pack_id
                WHERE p.coach_id = ?
                """, Integer.class, id);
        if (lessons != null && lessons > 0) {
            throw BizException.badRequest("已有学员课时，不能删除");
        }
        List<Map<String, Object>> coachRows = jdbc.queryForList("SELECT phone FROM coach WHERE id = ?", id);
        String coachPhone = coachRows.isEmpty() || coachRows.get(0).get("phone") == null ? "" : String.valueOf(coachRows.get(0).get("phone"));
        jdbc.update("DELETE FROM course_pack WHERE coach_id = ?", id);
        int updated = jdbc.update("DELETE FROM coach WHERE id = ?", id);
        if (updated != 1) {
            throw BizException.badRequest("教练不存在");
        }
        if (!coachPhone.isBlank()) {
            syncCoachGate(storeId, coachPhone);
        }
    }

    public String savePack(AdminPrincipal actor, long coachId, String name, long priceFen, Long earlyRenewFen, int lessonCount,
                           String content, String audience, String coverUrl, Integer minutesPerLesson,
                           Integer roomCols, Integer roomRows) {
        long storeId = coachStoreId(coachId);
        StoreScope.requireStoreAccess(actor, storeId);
        requireText(name, "请填写课程名称");
        if (lessonCount < 1) {
            throw BizException.badRequest("课时至少 1 节");
        }
        long id = snowflake.next();
        int minutes = minutesPerLesson == null || minutesPerLesson < 1 ? 60 : minutesPerLesson;
        int cols = normalizeRoom(roomCols);
        int rows = normalizeRoom(roomRows);
        jdbc.update("""
                INSERT INTO course_pack (id, coach_id, store_id, name, price_fen, early_renew_fen, lesson_count, content, audience, cover_url, minutes_per_lesson, room_cols, room_rows, status, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, 'ON', ?)
                """, id, coachId, storeId, name.trim(), priceFen, normalizeEarly(earlyRenewFen), lessonCount, blank(content), blank(audience),
                blank(coverUrl), minutes, cols, rows, timeProvider.now());
        return String.valueOf(id);
    }

    public void updatePack(AdminPrincipal actor, long id, String name, long priceFen, Long earlyRenewFen, int lessonCount,
                           String content, String audience, String coverUrl, Integer minutesPerLesson, String status,
                           Integer roomCols, Integer roomRows) {
        long storeId = packStoreId(id);
        StoreScope.requireStoreAccess(actor, storeId);
        requireText(name, "请填写课程名称");
        if (lessonCount < 1) {
            throw BizException.badRequest("课时至少 1 节");
        }
        String nextStatus = "OFF".equals(status) ? "OFF" : "ON";
        int minutes = minutesPerLesson == null || minutesPerLesson < 1 ? 60 : minutesPerLesson;
        int cols = normalizeRoom(roomCols);
        int rows = normalizeRoom(roomRows);
        int updated = jdbc.update("""
                UPDATE course_pack SET name=?, price_fen=?, early_renew_fen=?, lesson_count=?, content=?, audience=?, cover_url=?, minutes_per_lesson=?, room_cols=?, room_rows=?, status=?
                WHERE id=?
                """, name.trim(), priceFen, normalizeEarly(earlyRenewFen), lessonCount, blank(content), blank(audience), blank(coverUrl), minutes, cols, rows,
                nextStatus, id);
        if (updated != 1) {
            throw BizException.badRequest("课程不存在");
        }
    }

    public void deletePack(AdminPrincipal actor, long id) {
        long storeId = packStoreId(id);
        StoreScope.requireStoreAccess(actor, storeId);
        Integer lessons = jdbc.queryForObject("SELECT COUNT(*) FROM lesson_account WHERE pack_id = ?", Integer.class, id);
        if (lessons != null && lessons > 0) {
            throw BizException.badRequest("已有学员课时，不能删除");
        }
        int updated = jdbc.update("DELETE FROM course_pack WHERE id = ?", id);
        if (updated != 1) {
            throw BizException.badRequest("课程不存在");
        }
    }

    public List<Map<String, Object>> adminCoaches(long storeId) {
        List<Map<String, Object>> rows = coaches(storeId);
        for (Map<String, Object> row : rows) {
            String phone = row.get("phone") == null ? "" : String.valueOf(row.get("phone"));
            String name = row.get("name") == null ? "" : String.valueOf(row.get("name"));
            String mobile = mobileDigits(phone);
            if (!mobile.matches("1\\d{10}")) {
                row.put("wechatBound", false);
                continue;
            }
            row.put("wechatBound", ensureCoachUser(storeId, name, mobile));
        }
        return rows;
    }

    /** 确保教练存在可登录的 user 记录，返回是否已绑定微信。 */
    private boolean ensureCoachUser(long storeId, String name, String phone) {
        String mobile = requireMobile(phone);
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, mobile));
        LocalDateTime now = timeProvider.now();
        if (user == null) {
            user = new GymUser();
            user.setId(snowflake.next());
            user.setNickname(name.trim());
            user.setPhone(mobile);
            user.setMemberNo(memberNoService.next());
            user.setRegisterStatus("ACTIVE");
            user.setRegisteredAt(now);
            user.setStatus("ACTIVE");
            user.setCreatedStoreId(storeId);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            userMapper.insert(user);
            return false;
        }
        boolean bound = user.getOpenid() != null && !user.getOpenid().isBlank();
        if (!"ACTIVE".equals(user.getRegisterStatus())) {
            user.setRegisterStatus("ACTIVE");
            if (user.getNickname() == null || user.getNickname().isBlank()) {
                user.setNickname(name.trim());
            }
            if (user.getMemberNo() == null || user.getMemberNo().isBlank()) {
                user.setMemberNo(memberNoService.next());
            }
            if (user.getRegisteredAt() == null) {
                user.setRegisteredAt(now);
            }
            if (user.getCreatedStoreId() == null) {
                user.setCreatedStoreId(storeId);
            }
            user.setUpdatedAt(now);
            userMapper.updateById(user);
        }
        return bound;
    }

    public List<Map<String, Object>> coaches(long storeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, s.name AS store_name, s.cover_url AS store_cover_url, c.name, c.phone, c.intro,
                       c.avatar_url, c.photo_url, c.specialty, c.rating, c.lesson_taught, c.certificate_urls, c.gate_until,
                       (SELECT MIN(p.price_fen) FROM course_pack p WHERE p.coach_id = c.id AND p.status = 'ON') AS min_price_fen,
                       (SELECT COUNT(*) FROM course_pack p WHERE p.coach_id = c.id AND p.status = 'ON') AS pack_count
                FROM coach c JOIN store s ON s.id = c.store_id
                WHERE c.store_id = ? ORDER BY c.id
                """, storeId);
        for (Map<String, Object> row : rows) {
            row.put("gateUntil", formatGateUntil(row.remove("gate_until")));
        }
        return rows;
    }

    private void syncCoachGate(long storeId, String phone) {
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, phone));
        if (user != null) {
            gateFaceSync.syncPerson(user.getId(), storeId, "设置教练闸机");
        }
    }

    private LocalDateTime parseGateUntil(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim().replace('T', ' ');
        if (text.length() == 16) {
            text = text + ":00";
        }
        LocalDateTime until;
        try {
            until = LocalDateTime.parse(text, GATE_UNTIL);
        } catch (RuntimeException ex) {
            throw BizException.badRequest("闸机到期时间不正确");
        }
        if (!until.isAfter(timeProvider.now())) {
            throw BizException.badRequest("闸机到期时间要晚于现在");
        }
        return until;
    }

    private static String formatGateUntil(Object value) {
        if (value instanceof LocalDateTime time) {
            return time.format(GATE_UNTIL);
        }
        if (value instanceof Timestamp stamp) {
            return stamp.toLocalDateTime().format(GATE_UNTIL);
        }
        return "";
    }

    public Map<String, Object> coachDetail(long coachId, Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, c.store_id, s.name AS store_name, s.cover_url AS store_cover_url, c.name, c.phone, c.intro,
                       c.avatar_url, c.photo_url, c.specialty, c.rating, c.lesson_taught, c.certificate_urls,
                       (SELECT COUNT(*) FROM course_pack p WHERE p.coach_id = c.id AND p.status = 'ON') AS pack_count
                FROM coach c JOIN store s ON s.id = c.store_id
                WHERE c.id = ?
                """, coachId);
        if (rows.isEmpty()) {
            throw BizException.badRequest("教练不存在");
        }
        Map<String, Object> coach = rows.get(0);
        List<Map<String, Object>> packs = packsByCoach(coachId, true);
        applyEarly(packs, userId);
        coach.put("packs", packs);
        return coach;
    }

    public List<Map<String, Object>> packs(long storeId, Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, coach_id, store_id, name, price_fen, early_renew_fen, lesson_count, content, audience, cover_url, minutes_per_lesson, status
                FROM course_pack WHERE store_id = ? AND status = 'ON' ORDER BY id
                """, storeId);
        applyEarly(rows, userId);
        return rows;
    }

    public List<Map<String, Object>> adminPacks(AdminPrincipal actor, long storeId) {
        StoreScope.requireStoreAccess(actor, storeId);
        return jdbc.queryForList("""
                SELECT p.id, p.coach_id, c.name AS coach_name, p.store_id, p.name, p.price_fen, p.early_renew_fen, p.lesson_count,
                       p.content, p.audience, p.cover_url, p.minutes_per_lesson, p.status
                FROM course_pack p JOIN coach c ON c.id = p.coach_id
                WHERE p.store_id = ? ORDER BY p.id DESC
                """, storeId);
    }

    public Map<String, Object> packDetail(long packId, Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.coach_id, p.store_id, p.name, p.price_fen, p.early_renew_fen, p.lesson_count, p.content, p.audience,
                       p.cover_url, p.minutes_per_lesson, p.status,
                       c.name AS coach_name, c.photo_url AS coach_photo_url, c.avatar_url AS coach_avatar_url
                FROM course_pack p JOIN coach c ON c.id = p.coach_id
                WHERE p.id = ?
                """, packId);
        if (rows.isEmpty() || !"ON".equals(rows.get(0).get("status"))) {
            throw BizException.badRequest("课程不存在");
        }
        applyEarly(rows, userId);
        return rows.get(0);
    }

    private List<Map<String, Object>> packsByCoach(long coachId, boolean onlyOn) {
        if (onlyOn) {
            return jdbc.queryForList("""
                    SELECT id, coach_id, store_id, name, price_fen, early_renew_fen, lesson_count, content, audience, cover_url, minutes_per_lesson, status
                    FROM course_pack WHERE coach_id = ? AND status = 'ON' ORDER BY id
                    """, coachId);
        }
        return jdbc.queryForList("""
                SELECT id, coach_id, store_id, name, price_fen, early_renew_fen, lesson_count, content, audience, cover_url, minutes_per_lesson, status
                FROM course_pack WHERE coach_id = ? ORDER BY id
                """, coachId);
    }

    public void requireCoach(AdminPrincipal actor, long coachId) {
        StoreScope.requireStoreAccess(actor, coachStoreId(coachId));
    }

    private long coachStoreId(long coachId) {
        Long storeId = jdbc.query("SELECT store_id FROM coach WHERE id = ?", rs -> rs.next() ? rs.getLong(1) : null, coachId);
        if (storeId == null) {
            throw BizException.badRequest("教练不存在");
        }
        return storeId;
    }

    private long packStoreId(long packId) {
        Long storeId = jdbc.query("SELECT store_id FROM course_pack WHERE id = ?", rs -> rs.next() ? rs.getLong(1) : null, packId);
        if (storeId == null) {
            throw BizException.badRequest("课程不存在");
        }
        return storeId;
    }

    public OrderService.Created buyPack(long userId, long packId, long agreementId, int agreementVersion, String idempotencyKey) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT store_id, name, price_fen, early_renew_fen, status FROM course_pack WHERE id = ?", packId);
        if (rows.isEmpty() || !"ON".equals(rows.get(0).get("status"))) {
            throw BizException.rejected("课程不存在");
        }
        Map<String, Object> pack = rows.get(0);
        long price = ((Number) pack.get("price_fen")).longValue();
        Number early = (Number) pack.get("early_renew_fen");
        if (early != null && early.longValue() > 0) {
            Integer left = jdbc.queryForObject(
                    "SELECT remaining FROM lesson_account WHERE user_id = ? AND pack_id = ?",
                    Integer.class, userId, packId);
            if (left != null && left > 0) {
                price = early.longValue();
            }
        }
        return orderService.createCourse(userId, ((Number) pack.get("store_id")).longValue(), packId,
                String.valueOf(pack.get("name")), price,
                agreementId, agreementVersion, idempotencyKey);
    }

    @Override
    @Transactional
    public void onPaid(TradeOrder order) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT lesson_count FROM course_pack WHERE id = ?", order.getProductId());
        if (rows.isEmpty()) {
            return;
        }
        int count = ((Number) rows.get(0).get("lesson_count")).intValue();
        LocalDateTime now = timeProvider.now();
        List<Map<String, Object>> accounts = jdbc.queryForList(
                "SELECT id, remaining FROM lesson_account WHERE user_id = ? AND pack_id = ?", order.getUserId(), order.getProductId());
        if (accounts.isEmpty()) {
            jdbc.update("INSERT INTO lesson_account (id, user_id, pack_id, remaining, updated_at) VALUES (?,?,?,?,?)",
                    snowflake.next(), order.getUserId(), order.getProductId(), count, now);
        } else {
            jdbc.update("UPDATE lesson_account SET remaining = remaining + ?, updated_at = ? WHERE id = ?",
                    count, now, accounts.get(0).get("id"));
        }
        jdbc.update("INSERT INTO lesson_ledger (id, user_id, pack_id, delta, reason, created_at) VALUES (?,?,?,?, 'PAY', ?)",
                snowflake.next(), order.getUserId(), order.getProductId(), count, now);
    }

    public List<Map<String, Object>> myLessons(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.pack_id, a.remaining, p.name, p.content, p.cover_url
                FROM lesson_account a JOIN course_pack p ON p.id = a.pack_id
                WHERE a.user_id = ? ORDER BY a.id
                """, userId);
        rows.forEach(row -> {
            if (row.get("pack_id") != null) {
                row.put("pack_id", String.valueOf(row.get("pack_id")));
            }
        });
        return rows;
    }

    public String lessonQr(long userId, long packId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT remaining FROM lesson_account WHERE user_id = ? AND pack_id = ?", userId, packId);
        if (rows.isEmpty()) {
            throw BizException.rejected("没有剩余课时");
        }
        int remaining = ((Number) rows.get(0).get("remaining")).intValue();
        if (remaining < 1) {
            throw BizException.rejected("没有剩余课时");
        }
        String token = "lesson:" + UUID.randomUUID();
        lessonTokens.put(token, new LessonToken(userId, packId, Instant.now().plusSeconds(120)));
        return token;
    }

    public Map<String, Object> coachStatus(long userId) {
        GymUser user = userMapper.selectById(userId);
        if (user == null || user.getPhone() == null || user.getPhone().isBlank()) {
            return Map.of("coach", false);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, store_id FROM coach WHERE phone = ? ORDER BY id LIMIT 1", user.getPhone());
        if (rows.isEmpty()) {
            return Map.of("coach", false);
        }
        Map<String, Object> row = rows.get(0);
        return Map.of(
                "coach", true,
                "id", String.valueOf(row.get("id")),
                "name", row.get("name") == null ? "" : String.valueOf(row.get("name")),
                "storeId", String.valueOf(row.get("store_id")));
    }

    @Transactional
    public void redeemLesson(long coachUserId, String token) {
        GymUser coachUser = userMapper.selectById(coachUserId);
        if (coachUser == null || coachUser.getPhone() == null) {
            throw BizException.forbidden("只有教练可以核销课时");
        }
        Integer coaches = jdbc.queryForObject("SELECT COUNT(*) FROM coach WHERE phone = ?", Integer.class, coachUser.getPhone());
        if (coaches == null || coaches == 0) {
            throw BizException.forbidden("只有教练可以核销课时");
        }
        LessonToken ticket = lessonTokens.remove(token);
        if (ticket == null || ticket.expire.isBefore(Instant.now())) {
            throw BizException.rejected("课时码已失效");
        }
        int updated = jdbc.update("""
                UPDATE lesson_account SET remaining = remaining - 1, updated_at = ?
                WHERE user_id = ? AND pack_id = ? AND remaining > 0
                """, timeProvider.now(), ticket.userId, ticket.packId);
        if (updated != 1) {
            throw BizException.rejected("没有剩余课时");
        }
        jdbc.update("INSERT INTO lesson_ledger (id, user_id, pack_id, delta, reason, created_at) VALUES (?,?,?,-1,'REDEEM',?)",
                snowflake.next(), ticket.userId, ticket.packId, timeProvider.now());
    }

    public Map<String, Object> franchisePage() {
        Map<String, Object> page = jdbc.queryForMap("SELECT intro, hotline, points, support, steps FROM franchise_page WHERE id = 1");
        for (String key : List.of("intro", "points", "support", "steps")) {
            Object value = page.get(key);
            if (value != null) {
                page.put(key, RichHtml.forDisplay(String.valueOf(value)));
            }
        }
        return page;
    }

    public void saveFranchise(AdminPrincipal actor, String intro, String hotline, String points, String support, String steps) {
        StoreScope.requireMaster(actor);
        jdbc.update("UPDATE franchise_page SET intro=?, hotline=?, points=?, support=?, steps=?, updated_at=? WHERE id=1",
                RichHtml.clean(intro), blank(hotline), RichHtml.clean(points), RichHtml.clean(support), RichHtml.clean(steps), timeProvider.now());
    }

    public void updateLead(AdminPrincipal actor, long id, String name, String phone, String budget, String province, String city) {
        StoreScope.requireMaster(actor);
        requireText(name, "请填写姓名");
        requireText(phone, "请填写电话");
        requireText(province, "请填写省份");
        requireText(city, "请填写城市");
        int updated = jdbc.update("UPDATE franchise_lead SET name=?, phone=?, budget=?, province=?, city=? WHERE id=?",
                name.trim(), phone.trim(), blank(budget), province.trim(), city.trim(), id);
        if (updated != 1) {
            throw BizException.badRequest("申请不存在");
        }
    }

    public void deleteLead(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        int updated = jdbc.update("DELETE FROM franchise_lead WHERE id = ?", id);
        if (updated != 1) {
            throw BizException.badRequest("申请不存在");
        }
    }

    public String franchiseLead(String name, String phone, String budget, String province, String city) {
        requireText(name, "请填写姓名");
        requireText(phone, "请填写电话");
        requireText(province, "请填写省份");
        requireText(city, "请填写城市");
        long id = snowflake.next();
        jdbc.update("INSERT INTO franchise_lead (id, name, phone, budget, province, city, created_at) VALUES (?,?,?,?,?,?,?)",
                id, name.trim(), phone.trim(), blank(budget), province.trim(), city.trim(), timeProvider.now());
        return String.valueOf(id);
    }

    public List<Map<String, Object>> leads(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        return jdbc.queryForList("SELECT * FROM franchise_lead ORDER BY id DESC");
    }

    public void sendMessage(AdminPrincipal actor, long userId, String title, String content) {
        StoreScope.requireMaster(actor);
        jdbc.update("INSERT INTO inbox_message (id, user_id, title, content, created_at) VALUES (?,?,?,?,?)",
                snowflake.next(), userId, title.trim(), content.trim(), timeProvider.now());
    }

    public List<Map<String, Object>> messages(long userId) {
        return jdbc.queryForList("""
                SELECT id, title, content, read_at, created_at, biz_type, biz_id, store_id, coach_id
                FROM inbox_message WHERE user_id = ? ORDER BY id DESC
                """, userId).stream().map(row -> {
            Map<String, Object> m = new java.util.HashMap<>(row);
            if (m.get("id") != null) m.put("id", String.valueOf(m.get("id")));
            if (m.get("biz_id") != null) m.put("biz_id", String.valueOf(m.get("biz_id")));
            if (m.get("store_id") != null) m.put("store_id", String.valueOf(m.get("store_id")));
            if (m.get("coach_id") != null) m.put("coach_id", String.valueOf(m.get("coach_id")));
            return m;
        }).toList();
    }

    public int unreadMessageCount(long userId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inbox_message WHERE user_id = ? AND read_at IS NULL", Integer.class, userId);
        return n == null ? 0 : n;
    }

    public void readMessage(long userId, long id) {
        jdbc.update("UPDATE inbox_message SET read_at = ? WHERE id = ? AND user_id = ? AND read_at IS NULL",
                timeProvider.now(), id, userId);
    }

    @Transactional
    public void violation(AdminPrincipal actor, long membershipId, int deltaDays, boolean revoke, String reason) {
        StoreScope.requireMaster(actor);
        requireText(reason, "请填写原因");
        Membership membership = membershipMapper.selectById(membershipId);
        if (membership == null) {
            throw BizException.badRequest("会员不存在");
        }
        if (revoke) {
            membership.setStatus("REVOKED");
            membership.setEndAt(timeProvider.now());
        } else {
            membership.setEndAt(membership.getEndAt().plusDays(deltaDays));
        }
        membershipMapper.updateById(membership);
        jdbc.update("""
                INSERT INTO membership_day_adjust (id, membership_id, user_id, delta_days, reason, admin_id, created_at)
                VALUES (?,?,?,?,?,?,?)
                """, snowflake.next(), membershipId, membership.getUserId(), revoke ? 0 : deltaDays, reason.trim(),
                actor.id(), timeProvider.now());
    }

    public Map<String, Object> faceRecord(AdminPrincipal actor, long userId) {
        StoreScope.requireMaster(actor);
        jdbc.update("INSERT INTO admin_audit (id, admin_id, action, target_type, target_id, created_at) VALUES (?,?, 'VIEW_FACE', 'USER', ?, ?)",
                snowflake.next(), actor.id(), String.valueOf(userId), timeProvider.now());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT object_key, status FROM user_face WHERE user_id = ? AND status = 'ENROLLED' ORDER BY id DESC", userId);
        if (rows.isEmpty()) {
            return Map.of("enrolled", false, "photo", "未采集");
        }
        return Map.of("enrolled", true, "photo", "已采集", "objectKey", String.valueOf(rows.get(0).get("object_key")));
    }

    public String studentStatus(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status FROM student_cert WHERE user_id = ?", userId);
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO student_cert (user_id, status, updated_at) VALUES (?, 'UNVERIFIED', ?)", userId, timeProvider.now());
            return "UNVERIFIED";
        }
        return String.valueOf(rows.get(0).get("status"));
    }

    private String insertTicket(String table, long userId, long storeId, String extra, String content, String imageUrl) {
        long id = snowflake.next();
        LocalDateTime now = timeProvider.now();
        jdbc.update("""
                INSERT INTO repair_ticket (id, user_id, store_id, equipment_code, content, image_url, status, created_at, updated_at)
                VALUES (?,?,?,?,?,?, 'PENDING', ?, ?)
                """, id, userId, storeId, extra, content, blank(imageUrl), now, now);
        return String.valueOf(id);
    }

    private List<Map<String, Object>> scoped(AdminPrincipal actor, Long storeId, String table) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        if (scoped == null) {
            return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id DESC");
        }
        return jdbc.queryForList("SELECT * FROM " + table + " WHERE store_id = ? ORDER BY id DESC", scoped);
    }

    private void limitGroupon(long userId) {
        Instant now = Instant.now();
        ArrayDeque<Instant> hits = grouponHits.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst().isBefore(now.minusSeconds(60))) {
                hits.removeFirst();
            }
            if (hits.size() >= 5) {
                throw BizException.rejected("提交过于频繁");
            }
            hits.addLast(now);
        }
    }

    private static String hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String normalizeFeed(String feedId) {
        if (feedId == null || feedId.isBlank()) {
            return null;
        }
        String value = feedId.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.length() < 8 || value.length() > 256) {
            throw BizException.badRequest("请填写视频号动态管理里复制的 feedId");
        }
        return value;
    }

    private static String coverUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > 512 || !(value.startsWith("http://") || value.startsWith("https://"))) {
            throw BizException.badRequest("预览图地址不正确");
        }
        return value;
    }

    private static String requireFinder(String finderId) {
        String finder = finderId.trim();
        if (!finder.matches("sph[A-Za-z0-9]{4,60}")) {
            throw BizException.badRequest("视频号编号以 sph 开头，在视频号助手首页复制");
        }
        return finder;
    }

    private static String requireMobile(String phone) {
        String mobile = mobileDigits(phone);
        if (!mobile.matches("1\\d{10}")) {
            throw BizException.badRequest("请填写 11 位手机号");
        }
        return mobile;
    }

    /** 去掉空格、横线和 +86，只留数字。13 位且以 86 开头时去掉国家码。 */
    private static String mobileDigits(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BizException.badRequest(message);
        }
    }

    private void applyEarly(List<Map<String, Object>> rows, Long userId) {
        for (Map<String, Object> row : rows) {
            long price = ((Number) row.get("price_fen")).longValue();
            Number early = (Number) row.get("early_renew_fen");
            int left = 0;
            if (userId != null && row.get("id") != null) {
                Integer remaining = jdbc.queryForObject(
                        "SELECT remaining FROM lesson_account WHERE user_id = ? AND pack_id = ?",
                        Integer.class, userId, ((Number) row.get("id")).longValue());
                left = remaining == null ? 0 : remaining;
            }
            boolean renew = early != null && early.longValue() > 0 && left > 0;
            row.put("early_renew", renew);
            row.put("pay_fen", renew ? early.longValue() : price);
        }
    }

    private static Long normalizeEarly(Long fen) {
        return fen == null || fen < 1 ? null : fen;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int normalizeRoom(Integer value) {
        int n = value == null ? 5 : value;
        if (n < 1 || n > 20) {
            throw BizException.badRequest("教室长宽须在 1～20");
        }
        return n;
    }

    private Map<String, Object> fitIntroField(Map<String, Object> row) {
        Object intro = row.get("intro");
        if (intro != null) {
            row.put("intro", RichHtml.forDisplay(String.valueOf(intro)));
        }
        return row;
    }

    private record LessonToken(long userId, long packId, Instant expire) {
    }
}
