package com.gym.self.modules.shop;

import com.gym.self.common.api.BizException;
import com.gym.self.common.html.RichHtml;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.order.application.CourseGrant;
import com.gym.self.modules.order.application.OrderService;
import com.gym.self.modules.order.domain.TradeOrder;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.HexFormat;
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
    private final Map<Long, ArrayDeque<Instant>> grouponHits = new ConcurrentHashMap<>();
    private final Map<String, LessonToken> lessonTokens = new ConcurrentHashMap<>();

    public ShopService(JdbcTemplate jdbc, Snowflake snowflake, TimeProvider timeProvider, ContentSafety contentSafety,
                       OrderService orderService, GymUserMapper userMapper, MembershipMapper membershipMapper) {
        this.jdbc = jdbc;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.contentSafety = contentSafety;
        this.orderService = orderService;
        this.userMapper = userMapper;
        this.membershipMapper = membershipMapper;
    }

    public String repair(long userId, long storeId, String equipmentCode, String content, String imageUrl) {
        contentSafety.check(content);
        requireText(content, "请填写报修说明");
        return insertTicket("repair_ticket", userId, storeId, blank(equipmentCode), content.trim(), imageUrl);
    }

    public String complaint(long userId, long storeId, String category, String content, String imageUrl) {
        contentSafety.check(content);
        if (!List.of("EQUIPMENT", "CLEAN", "COACH", "OTHER").contains(category)) {
            throw BizException.badRequest("投诉类型不正确");
        }
        requireText(content, "请填写投诉内容");
        long id = snowflake.next();
        LocalDateTime now = timeProvider.now();
        jdbc.update("""
                INSERT INTO complaint_ticket (id, user_id, store_id, category, content, image_url, status, created_at, updated_at)
                VALUES (?,?,?,?,?,?, 'PENDING', ?, ?)
                """, id, userId, storeId, category, content.trim(), blank(imageUrl), now, now);
        return String.valueOf(id);
    }

    public String lost(long userId, long storeId, String kind, String name, String content, String imageUrl, boolean visible) {
        contentSafety.check(name + content);
        if (!List.of("LOST", "FOUND").contains(kind)) {
            throw BizException.badRequest("请选择丢失或拾到");
        }
        requireText(name, "请填写名称");
        requireText(content, "请填写说明");
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

    public String saveEquipment(AdminPrincipal actor, Long id, long storeId, String code, String name, String intro, String imageUrl, String videoUrl) {
        StoreScope.requireMaster(actor);
        requireText(code, "请填写器械编号");
        requireText(name, "请填写器械名称");
        LocalDateTime now = timeProvider.now();
        String html = RichHtml.clean(intro);
        long equipmentId = id == null ? snowflake.next() : id;
        if (id == null) {
            jdbc.update("""
                    INSERT INTO equipment (id, store_id, code, name, intro, created_at, updated_at)
                    VALUES (?,?,?,?,?,?,?)
                    """, equipmentId, storeId, code.trim(), name.trim(), html, now, now);
        } else {
            int updated = jdbc.update("UPDATE equipment SET store_id=?, code=?, name=?, intro=?, updated_at=? WHERE id=?",
                    storeId, code.trim(), name.trim(), html, now, equipmentId);
            if (updated != 1) {
                throw BizException.badRequest("器械不存在");
            }
        }
        jdbc.update("DELETE FROM equipment_media WHERE equipment_id = ?", equipmentId);
        if (imageUrl != null && !imageUrl.isBlank()) {
            jdbc.update("INSERT INTO equipment_media (id, equipment_id, media_type, url, sort_no) VALUES (?,?, 'IMAGE', ?, 0)",
                    snowflake.next(), equipmentId, imageUrl.trim());
        }
        if (videoUrl != null && !videoUrl.isBlank()) {
            jdbc.update("INSERT INTO equipment_media (id, equipment_id, media_type, url, sort_no) VALUES (?,?, 'VIDEO', ?, 1)",
                    snowflake.next(), equipmentId, videoUrl.trim());
        }
        return String.valueOf(equipmentId);
    }

    public void deleteEquipment(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE id = ?", Integer.class, id);
        if (found == null || found == 0) {
            throw BizException.badRequest("器械不存在");
        }
        jdbc.update("DELETE FROM equipment_media WHERE equipment_id = ?", id);
        jdbc.update("DELETE FROM equipment WHERE id = ?", id);
    }

    public List<Map<String, Object>> equipmentOf(long storeId) {
        return jdbc.queryForList("""
                SELECT e.id, s.name AS store_name, e.code, e.name, e.intro,
                    (SELECT m.url FROM equipment_media m WHERE m.equipment_id = e.id AND m.media_type = 'IMAGE' ORDER BY m.sort_no LIMIT 1) AS image_url,
                    (SELECT m.url FROM equipment_media m WHERE m.equipment_id = e.id AND m.media_type = 'VIDEO' ORDER BY m.sort_no LIMIT 1) AS video_url
                FROM equipment e
                JOIN store s ON s.id = e.store_id
                WHERE e.store_id = ? ORDER BY e.id
                """, storeId);
    }

    public Map<String, Object> equipmentByCode(long storeId, String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.id, e.code, e.name, e.intro,
                    (SELECT m.url FROM equipment_media m WHERE m.equipment_id = e.id AND m.media_type = 'IMAGE' ORDER BY m.sort_no LIMIT 1) AS image_url,
                    (SELECT m.url FROM equipment_media m WHERE m.equipment_id = e.id AND m.media_type = 'VIDEO' ORDER BY m.sort_no LIMIT 1) AS video_url
                FROM equipment e
                WHERE e.store_id = ? AND e.code = ?
                """, storeId, code);
        if (rows.isEmpty()) {
            throw BizException.badRequest("没有这台器械的教程");
        }
        return rows.get(0);
    }

    public void addGrouponRule(AdminPrincipal actor, long storeId, String platform, String code, long cardProductId) {
        StoreScope.requireMaster(actor);
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
        StoreScope.requireMaster(actor);
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
        StoreScope.requireMaster(actor);
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

    public String saveCoach(AdminPrincipal actor, long storeId, String name, String phone, String intro) {
        StoreScope.requireMaster(actor);
        long id = snowflake.next();
        jdbc.update("INSERT INTO coach (id, store_id, name, phone, intro, created_at) VALUES (?,?,?,?,?,?)",
                id, storeId, name.trim(), phone.trim(), RichHtml.clean(intro), timeProvider.now());
        return String.valueOf(id);
    }

    public void updateCoach(AdminPrincipal actor, long id, String name, String phone, String intro) {
        StoreScope.requireMaster(actor);
        requireText(name, "请填写教练姓名");
        requireText(phone, "请填写手机号");
        int updated = jdbc.update("UPDATE coach SET name=?, phone=?, intro=? WHERE id=?",
                name.trim(), phone.trim(), RichHtml.clean(intro), id);
        if (updated != 1) {
            throw BizException.badRequest("教练不存在");
        }
    }

    public void deleteCoach(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        Integer lessons = jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_account a
                JOIN course_pack p ON p.id = a.pack_id
                WHERE p.coach_id = ?
                """, Integer.class, id);
        if (lessons != null && lessons > 0) {
            throw BizException.badRequest("已有学员课时，不能删除");
        }
        jdbc.update("DELETE FROM course_pack WHERE coach_id = ?", id);
        int updated = jdbc.update("DELETE FROM coach WHERE id = ?", id);
        if (updated != 1) {
            throw BizException.badRequest("教练不存在");
        }
    }

    public String savePack(AdminPrincipal actor, long coachId, String name, long priceFen, int lessonCount, String content, String audience) {
        StoreScope.requireMaster(actor);
        List<Map<String, Object>> coaches = jdbc.queryForList("SELECT store_id FROM coach WHERE id = ?", coachId);
        if (coaches.isEmpty()) {
            throw BizException.badRequest("教练不存在");
        }
        long id = snowflake.next();
        jdbc.update("""
                INSERT INTO course_pack (id, coach_id, store_id, name, price_fen, lesson_count, content, audience, status, created_at)
                VALUES (?,?,?,?,?,?,?,?, 'ON', ?)
                """, id, coachId, coaches.get(0).get("store_id"), name.trim(), priceFen, lessonCount, blank(content), blank(audience), timeProvider.now());
        return String.valueOf(id);
    }

    public List<Map<String, Object>> coaches(long storeId) {
        return jdbc.queryForList("""
                SELECT c.id, s.name AS store_name, c.name, c.phone, c.intro
                FROM coach c JOIN store s ON s.id = c.store_id
                WHERE c.store_id = ? ORDER BY c.id
                """, storeId);
    }

    public List<Map<String, Object>> packs(long storeId) {
        return jdbc.queryForList("""
                SELECT id, coach_id, store_id, name, price_fen, lesson_count, content, audience, status
                FROM course_pack WHERE store_id = ? AND status = 'ON' ORDER BY id
                """, storeId);
    }

    public OrderService.Created buyPack(long userId, long packId, long agreementId, int agreementVersion, String idempotencyKey) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT store_id, name, price_fen, status FROM course_pack WHERE id = ?", packId);
        if (rows.isEmpty() || !"ON".equals(rows.get(0).get("status"))) {
            throw BizException.rejected("课程不存在");
        }
        Map<String, Object> pack = rows.get(0);
        return orderService.createCourse(userId, ((Number) pack.get("store_id")).longValue(), packId,
                String.valueOf(pack.get("name")), ((Number) pack.get("price_fen")).longValue(),
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
        return jdbc.queryForList("""
                SELECT a.pack_id, a.remaining, p.name
                FROM lesson_account a JOIN course_pack p ON p.id = a.pack_id
                WHERE a.user_id = ? ORDER BY a.id
                """, userId);
    }

    public String lessonQr(long userId, long packId) {
        Integer remaining = jdbc.queryForObject(
                "SELECT remaining FROM lesson_account WHERE user_id = ? AND pack_id = ?", Integer.class, userId, packId);
        if (remaining == null || remaining < 1) {
            throw BizException.rejected("没有剩余课时");
        }
        String token = "lesson:" + UUID.randomUUID();
        lessonTokens.put(token, new LessonToken(userId, packId, Instant.now().plusSeconds(60)));
        return token;
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
        return jdbc.queryForMap("SELECT intro, hotline, points, support, steps FROM franchise_page WHERE id = 1");
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
        return jdbc.queryForList("SELECT id, title, content, read_at, created_at FROM inbox_message WHERE user_id = ? ORDER BY id DESC", userId);
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

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw BizException.badRequest(message);
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


    private record LessonToken(long userId, long packId, Instant expire) {
    }
}
