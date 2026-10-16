package com.gym.self.modules.lesson.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.lesson.domain.CoachSchedule;
import com.gym.self.modules.lesson.domain.CoachScheduleMapper;
import com.gym.self.modules.lesson.domain.LessonAppeal;
import com.gym.self.modules.lesson.domain.LessonAppealMapper;
import com.gym.self.modules.lesson.domain.LessonBooking;
import com.gym.self.modules.lesson.domain.LessonBookingMapper;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LessonBookingService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> ACTIVE = Set.of("PENDING_COACH", "CONFIRMED", "APPEALING");

    private final CoachScheduleMapper scheduleMapper;
    private final LessonBookingMapper bookingMapper;
    private final LessonAppealMapper appealMapper;
    private final GymUserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final LessonNotify notify;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;

    public LessonBookingService(CoachScheduleMapper scheduleMapper, LessonBookingMapper bookingMapper,
                                LessonAppealMapper appealMapper, GymUserMapper userMapper, JdbcTemplate jdbc,
                                LessonNotify notify, Snowflake snowflake, TimeProvider timeProvider) {
        this.scheduleMapper = scheduleMapper;
        this.bookingMapper = bookingMapper;
        this.appealMapper = appealMapper;
        this.userMapper = userMapper;
        this.jdbc = jdbc;
        this.notify = notify;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
    }

    // ---------- 排班 ----------

    public List<Map<String, Object>> schedules(long coachId) {
        return scheduleMapper.selectList(new LambdaQueryWrapper<CoachSchedule>()
                        .eq(CoachSchedule::getCoachId, coachId)
                        .orderByAsc(CoachSchedule::getSortNo)
                        .orderByAsc(CoachSchedule::getStartHm))
                .stream()
                .map(row -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", String.valueOf(row.getId()));
                    m.put("coachId", String.valueOf(row.getCoachId()));
                    m.put("startHm", row.getStartHm());
                    m.put("sortNo", row.getSortNo());
                    return m;
                })
                .toList();
    }

    @Transactional
    public void replaceSchedules(AdminPrincipal actor, long coachId, List<String> startHms) {
        long storeId = coachStoreId(coachId);
        StoreScope.requireStoreAccess(actor, storeId);
        List<String> normalized = normalizeStarts(startHms);
        if (normalized.size() > 3) {
            throw BizException.badRequest("排班最多 3 个时段");
        }
        int maxMinutes = maxPackMinutes(coachId);
        for (int i = 0; i < normalized.size(); i++) {
            for (int j = i + 1; j < normalized.size(); j++) {
                int gap = minutesOf(normalized.get(j)) - minutesOf(normalized.get(i));
                if (Math.abs(gap) < maxMinutes) {
                    throw BizException.badRequest("排班时段间隔须不少于 " + maxMinutes + " 分钟");
                }
            }
        }
        List<CoachSchedule> old = scheduleMapper.selectList(new LambdaQueryWrapper<CoachSchedule>()
                .eq(CoachSchedule::getCoachId, coachId));
        Set<String> next = new HashSet<>(normalized);
        LocalDateTime now = timeProvider.now();
        for (CoachSchedule row : old) {
            if (!next.contains(row.getStartHm())) {
                Long future = bookingMapper.selectCount(new LambdaQueryWrapper<LessonBooking>()
                        .eq(LessonBooking::getCoachId, coachId)
                        .eq(LessonBooking::getStartHm, row.getStartHm())
                        .in(LessonBooking::getStatus, ACTIVE)
                        .gt(LessonBooking::getStartAt, now));
                if (future != null && future > 0) {
                    throw BizException.badRequest("时段 " + row.getStartHm() + " 已有未来预约，不能删除");
                }
            }
        }
        scheduleMapper.delete(new LambdaQueryWrapper<CoachSchedule>().eq(CoachSchedule::getCoachId, coachId));
        int sort = 0;
        for (String hm : normalized) {
            CoachSchedule row = new CoachSchedule();
            row.setId(snowflake.next());
            row.setCoachId(coachId);
            row.setStartHm(hm);
            row.setSortNo(sort++);
            row.setCreatedAt(now);
            scheduleMapper.insert(row);
        }
    }

    // ---------- 预约页数据 ----------

    public Map<String, Object> bookingOffer(long userId, long packId) {
        Map<String, Object> pack = mustPack(packId);
        if (!"ON".equals(String.valueOf(pack.get("status")))) {
            throw BizException.rejected("课程已下架");
        }
        int remaining = remaining(userId, packId);
        long coachId = ((Number) pack.get("coach_id")).longValue();
        Map<String, Object> out = new HashMap<>();
        out.put("packId", String.valueOf(packId));
        out.put("packName", pack.get("name"));
        out.put("coachId", String.valueOf(coachId));
        out.put("coachName", coachName(coachId));
        out.put("storeId", String.valueOf(pack.get("store_id")));
        out.put("roomCols", pack.get("room_cols"));
        out.put("roomRows", pack.get("room_rows"));
        out.put("minutesPerLesson", pack.get("minutes_per_lesson"));
        out.put("remaining", remaining);
        out.put("startHms", defaultStartHms());
        out.put("minDate", LocalDate.now(TimeProvider.ZONE).plusDays(1).toString());
        out.put("maxDate", LocalDate.now(TimeProvider.ZONE).plusDays(7).toString());
        return out;
    }

    /** 学员自选开始时刻（半小时一档），不再依赖管理端教练排班 */
    private static List<String> defaultStartHms() {
        List<String> list = new ArrayList<>();
        for (int minutes = 6 * 60; minutes <= 22 * 60; minutes += 30) {
            list.add(String.format("%02d:%02d", minutes / 60, minutes % 60));
        }
        return list;
    }

    public List<String> occupiedSeats(long packId, LocalDate date, String startHm) {
        String hm = normalizeHm(startHm);
        return bookingMapper.selectList(new LambdaQueryWrapper<LessonBooking>()
                        .eq(LessonBooking::getPackId, packId)
                        .eq(LessonBooking::getClassDate, date)
                        .eq(LessonBooking::getStartHm, hm)
                        .in(LessonBooking::getStatus, ACTIVE))
                .stream()
                .map(LessonBooking::getSeatNo)
                .toList();
    }

    @Transactional
    public Map<String, Object> createBooking(long userId, long packId, LocalDate classDate, String startHm,
                                             int seatRow, int seatCol) {
        Map<String, Object> pack = mustPack(packId);
        if (!"ON".equals(String.valueOf(pack.get("status")))) {
            throw BizException.rejected("课程已下架");
        }
        if (remaining(userId, packId) < 1) {
            throw BizException.rejected("剩余课时不足");
        }
        LocalDate today = LocalDate.now(TimeProvider.ZONE);
        if (classDate.isBefore(today.plusDays(1)) || classDate.isAfter(today.plusDays(7))) {
            throw BizException.badRequest("只能预约明天起 7 天内的课程");
        }
        String hm = normalizeHm(startHm);
        long coachId = ((Number) pack.get("coach_id")).longValue();
        long storeId = ((Number) pack.get("store_id")).longValue();
        int minutes = ((Number) pack.get("minutes_per_lesson")).intValue();
        int cols = ((Number) pack.get("room_cols")).intValue();
        int rows = ((Number) pack.get("room_rows")).intValue();
        if (!defaultStartHms().contains(hm)) {
            throw BizException.badRequest("请选择有效的上课时间");
        }
        if (seatRow < 1 || seatCol < 1 || seatRow > rows || seatCol > cols) {
            throw BizException.badRequest("座位号不正确");
        }
        String seatNo = seatRow + "-" + seatCol;
        LocalDateTime startAt = LocalDateTime.of(classDate, LocalTime.parse(hm, HM));
        LocalDateTime endAt = startAt.plusMinutes(minutes);
        LocalDateTime now = timeProvider.now();
        if (!startAt.isAfter(now)) {
            throw BizException.badRequest("上课时间已过");
        }
        Long pendingPack = bookingMapper.selectCount(new LambdaQueryWrapper<LessonBooking>()
                .eq(LessonBooking::getUserId, userId)
                .eq(LessonBooking::getPackId, packId)
                .eq(LessonBooking::getStatus, "PENDING_COACH"));
        if (pendingPack != null && pendingPack > 0) {
            throw BizException.rejected("该课程已有待确认预约");
        }
        Long sameSlot = bookingMapper.selectCount(new LambdaQueryWrapper<LessonBooking>()
                .eq(LessonBooking::getUserId, userId)
                .eq(LessonBooking::getPackId, packId)
                .eq(LessonBooking::getClassDate, classDate)
                .eq(LessonBooking::getStartHm, hm)
                .in(LessonBooking::getStatus, ACTIVE));
        if (sameSlot != null && sameSlot > 0) {
            throw BizException.rejected("同一时段只能预约一个座位");
        }
        assertNoOverlap(userId, startAt, endAt, null);
        Long seatTaken = bookingMapper.selectCount(new LambdaQueryWrapper<LessonBooking>()
                .eq(LessonBooking::getPackId, packId)
                .eq(LessonBooking::getClassDate, classDate)
                .eq(LessonBooking::getStartHm, hm)
                .eq(LessonBooking::getSeatNo, seatNo)
                .in(LessonBooking::getStatus, ACTIVE));
        if (seatTaken != null && seatTaken > 0) {
            throw BizException.rejected("该座位已被占用");
        }
        LessonBooking booking = new LessonBooking();
        booking.setId(snowflake.next());
        booking.setUserId(userId);
        booking.setPackId(packId);
        booking.setCoachId(coachId);
        booking.setStoreId(storeId);
        booking.setClassDate(classDate);
        booking.setStartHm(hm);
        booking.setStartAt(startAt);
        booking.setEndAt(endAt);
        booking.setSeatRow(seatRow);
        booking.setSeatCol(seatCol);
        booking.setSeatNo(seatNo);
        booking.setStatus("PENDING_COACH");
        LocalDateTime pendingUntil = now.plusHours(48);
        if (pendingUntil.isAfter(startAt)) {
            pendingUntil = startAt;
        }
        booking.setPendingUntil(pendingUntil);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        bookingMapper.insert(booking);
        String packName = String.valueOf(pack.get("name"));
        notify.send(coachUserId(coachId), "有学员发起预约",
                userLabel(userId) + " 预约「" + packName + "」" + classDate + " " + hm + " 座位" + seatNo,
                "LESSON_BOOKING", booking.getId(), storeId, coachId);
        return detailMap(booking, true);
    }

    @Transactional
    public Map<String, Object> cancelByUser(long userId, long bookingId) {
        LessonBooking booking = mustBooking(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw BizException.badRequest("预约不存在");
        }
        LocalDateTime now = timeProvider.now();
        if ("PENDING_COACH".equals(booking.getStatus())) {
            cancelBooking(booking, now, "USER_PENDING", "学员取消待确认预约");
        } else if ("CONFIRMED".equals(booking.getStatus())) {
            if (now.plusHours(24).isAfter(booking.getStartAt())) {
                throw BizException.rejected("距上课不足 24 小时，不能取消，可发起申诉");
            }
            refundLesson(booking.getUserId(), booking.getPackId(), booking.getId());
            cancelBooking(booking, now, "USER_CONFIRMED", "学员取消已确认预约并退回课时");
        } else {
            throw BizException.rejected("当前状态不能取消");
        }
        return detailMap(mustBooking(bookingId), false);
    }

    @Transactional
    public Map<String, Object> coachApprove(long coachUserId, long bookingId) {
        return doApprove(mustOwnedByCoach(coachUserId, bookingId), "COACH");
    }

    @Transactional
    public Map<String, Object> adminApprove(AdminPrincipal actor, long bookingId) {
        return doApprove(mustAdminBooking(actor, bookingId), "STORE");
    }

    private Map<String, Object> doApprove(LessonBooking booking, String actedBy) {
        if (!"PENDING_COACH".equals(booking.getStatus())) {
            throw BizException.rejected("当前状态不能同意");
        }
        LocalDateTime now = timeProvider.now();
        if (!now.isBefore(booking.getStartAt())) {
            cancelBooking(booking, now, "PAST_START", "已过开课时间，系统取消");
            throw BizException.rejected("已过开课时间，预约已取消");
        }
        if (remaining(booking.getUserId(), booking.getPackId()) < 1) {
            cancelBooking(booking, now, "NO_LESSON", "学员课时不足，系统取消");
            throw BizException.rejected("学员课时不足，预约已取消");
        }
        deductLesson(booking.getUserId(), booking.getPackId(), booking.getId());
        booking.setStatus("CONFIRMED");
        booking.setConfirmedAt(now);
        booking.setActedBy(actedBy);
        booking.setUpdatedAt(now);
        bookingMapper.updateById(booking);
        notify.send(booking.getUserId(), "预约成功",
                "「" + packName(booking.getPackId()) + "」" + booking.getClassDate() + " " + booking.getStartHm()
                        + " 座位" + booking.getSeatNo() + " 已确认（操作端：" + actorLabel(actedBy) + "）",
                "LESSON_BOOKING", booking.getId(), booking.getStoreId(), booking.getCoachId());
        return detailMap(booking, true);
    }

    @Transactional
    public Map<String, Object> coachReject(long coachUserId, long bookingId, String reason) {
        return doReject(mustOwnedByCoach(coachUserId, bookingId), reason, "COACH");
    }

    @Transactional
    public Map<String, Object> adminReject(AdminPrincipal actor, long bookingId, String reason) {
        return doReject(mustAdminBooking(actor, bookingId), reason, "STORE");
    }

    private Map<String, Object> doReject(LessonBooking booking, String reason, String actedBy) {
        if (!"PENDING_COACH".equals(booking.getStatus())) {
            throw BizException.rejected("当前状态不能拒绝");
        }
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("请填写拒绝原因");
        }
        LocalDateTime now = timeProvider.now();
        booking.setStatus("REJECTED");
        booking.setRejectReason(reason.trim());
        booking.setActedBy(actedBy);
        booking.setCancelledAt(now);
        booking.setUpdatedAt(now);
        bookingMapper.updateById(booking);
        notify.send(booking.getUserId(), "预约被拒绝",
                "「" + packName(booking.getPackId()) + "」" + booking.getClassDate() + " " + booking.getStartHm()
                        + " 原因：" + reason.trim() + "（操作端：" + actorLabel(actedBy) + "）",
                "LESSON_BOOKING", booking.getId(), booking.getStoreId(), booking.getCoachId());
        return detailMap(booking, true);
    }

    @Transactional
    public Map<String, Object> createAppeal(long userId, long bookingId, String reason) {
        LessonBooking booking = mustBooking(bookingId);
        if (!booking.getUserId().equals(userId)) {
            throw BizException.badRequest("预约不存在");
        }
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw BizException.rejected("当前状态不能申诉");
        }
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("请填写申诉原因");
        }
        Long open = appealMapper.selectCount(new LambdaQueryWrapper<LessonAppeal>()
                .eq(LessonAppeal::getBookingId, bookingId)
                .eq(LessonAppeal::getStatus, "OPEN"));
        if (open != null && open > 0) {
            throw BizException.rejected("已有进行中的申诉");
        }
        LocalDateTime now = timeProvider.now();
        LessonAppeal appeal = new LessonAppeal();
        appeal.setId(snowflake.next());
        appeal.setBookingId(bookingId);
        appeal.setUserId(userId);
        appeal.setReason(reason.trim());
        appeal.setStatus("OPEN");
        appeal.setCreatedAt(now);
        appealMapper.insert(appeal);
        booking.setStatus("APPEALING");
        booking.setUpdatedAt(now);
        bookingMapper.updateById(booking);
        notify.send(coachUserId(booking.getCoachId()), "学员发起申诉",
                userLabel(userId) + " 申诉「" + packName(booking.getPackId()) + "」座位" + booking.getSeatNo()
                        + "：" + reason.trim(),
                "LESSON_APPEAL", booking.getId(), booking.getStoreId(), booking.getCoachId());
        return detailMap(booking, false);
    }

    @Transactional
    public Map<String, Object> coachRefund(long coachUserId, long bookingId) {
        return doRefund(mustOwnedByCoach(coachUserId, bookingId), "COACH");
    }

    @Transactional
    public Map<String, Object> adminRefund(AdminPrincipal actor, long bookingId) {
        return doRefund(mustAdminBooking(actor, bookingId), "STORE");
    }

    private Map<String, Object> doRefund(LessonBooking booking, String actedBy) {
        if (!"APPEALING".equals(booking.getStatus()) && !"CONFIRMED".equals(booking.getStatus())) {
            throw BizException.rejected("当前状态不能退课");
        }
        if (booking.getRefundedAt() != null) {
            throw BizException.rejected("该预约已退过课时");
        }
        LocalDateTime now = timeProvider.now();
        refundLesson(booking.getUserId(), booking.getPackId(), booking.getId());
        LessonAppeal open = appealMapper.selectOne(new LambdaQueryWrapper<LessonAppeal>()
                .eq(LessonAppeal::getBookingId, booking.getId())
                .eq(LessonAppeal::getStatus, "OPEN")
                .orderByDesc(LessonAppeal::getId)
                .last("LIMIT 1"));
        if (open != null) {
            open.setStatus("APPROVED");
            open.setResolvedAt(now);
            appealMapper.updateById(open);
        }
        booking.setStatus("REFUNDED");
        booking.setRefundedAt(now);
        booking.setActedBy(actedBy);
        booking.setUpdatedAt(now);
        bookingMapper.updateById(booking);
        notify.send(booking.getUserId(), "退课成功",
                "「" + packName(booking.getPackId()) + "」" + booking.getClassDate() + " " + booking.getStartHm()
                        + " 已退回 1 节课时（操作端：" + actorLabel(actedBy) + "）",
                "LESSON_BOOKING", booking.getId(), booking.getStoreId(), booking.getCoachId());
        return detailMap(booking, true);
    }

    @Transactional
    public Map<String, Object> coachRejectAppeal(long coachUserId, long bookingId, String reason) {
        return doRejectAppeal(mustOwnedByCoach(coachUserId, bookingId), reason, "COACH");
    }

    @Transactional
    public Map<String, Object> adminRejectAppeal(AdminPrincipal actor, long bookingId, String reason) {
        return doRejectAppeal(mustAdminBooking(actor, bookingId), reason, "STORE");
    }

    private Map<String, Object> doRejectAppeal(LessonBooking booking, String reason, String actedBy) {
        if (!"APPEALING".equals(booking.getStatus())) {
            throw BizException.rejected("当前没有待处理申诉");
        }
        if (reason == null || reason.isBlank()) {
            throw BizException.badRequest("请填写驳回原因");
        }
        LocalDateTime now = timeProvider.now();
        LessonAppeal open = appealMapper.selectOne(new LambdaQueryWrapper<LessonAppeal>()
                .eq(LessonAppeal::getBookingId, booking.getId())
                .eq(LessonAppeal::getStatus, "OPEN")
                .orderByDesc(LessonAppeal::getId)
                .last("LIMIT 1"));
        if (open == null) {
            throw BizException.rejected("当前没有待处理申诉");
        }
        open.setStatus("REJECTED");
        open.setRejectReason(reason.trim());
        open.setResolvedAt(now);
        appealMapper.updateById(open);
        booking.setStatus("CONFIRMED");
        booking.setActedBy(actedBy);
        booking.setUpdatedAt(now);
        bookingMapper.updateById(booking);
        notify.send(booking.getUserId(), "申诉被驳回",
                "「" + packName(booking.getPackId()) + "」原因：" + reason.trim()
                        + "（操作端：" + actorLabel(actedBy) + "）",
                "LESSON_APPEAL", booking.getId(), booking.getStoreId(), booking.getCoachId());
        return detailMap(booking, true);
    }

    public Map<String, Object> adminBookingDetail(AdminPrincipal actor, long id) {
        LessonBooking booking = resolveAdminBooking(id);
        StoreScope.requireStoreAccess(actor, booking.getStoreId());
        return detailMap(booking, true);
    }

    public List<Map<String, Object>> myBookings(long userId) {
        return bookingMapper.selectList(new LambdaQueryWrapper<LessonBooking>()
                        .eq(LessonBooking::getUserId, userId)
                        .orderByDesc(LessonBooking::getId)
                        .last("LIMIT 100"))
                .stream()
                .map(b -> detailMap(b, false))
                .toList();
    }

    public List<Map<String, Object>> coachBookings(long coachUserId) {
        Long coachId = coachIdByUser(coachUserId);
        if (coachId == null) {
            return List.of();
        }
        return bookingMapper.selectList(new LambdaQueryWrapper<LessonBooking>()
                        .eq(LessonBooking::getCoachId, coachId)
                        .in(LessonBooking::getStatus, List.of("PENDING_COACH", "CONFIRMED", "APPEALING"))
                        .orderByAsc(LessonBooking::getStartAt)
                        .last("LIMIT 200"))
                .stream()
                .map(b -> detailMap(b, true))
                .toList();
    }

    public Map<String, Object> bookingDetail(long viewerUserId, long bookingId, boolean asCoach) {
        LessonBooking booking = mustBooking(bookingId);
        if (asCoach) {
            mustOwnedByCoach(viewerUserId, bookingId);
            return detailMap(booking, true);
        }
        if (!booking.getUserId().equals(viewerUserId)) {
            Long coachId = coachIdByUser(viewerUserId);
            if (coachId == null || !coachId.equals(booking.getCoachId())) {
                throw BizException.badRequest("预约不存在");
            }
            return detailMap(booking, true);
        }
        return detailMap(booking, false);
    }

    public int sweepExpiredPending() {
        LocalDateTime now = timeProvider.now();
        int n = 0;
        for (LessonBooking booking : bookingMapper.selectList(new LambdaQueryWrapper<LessonBooking>()
                .eq(LessonBooking::getStatus, "PENDING_COACH")
                .le(LessonBooking::getPendingUntil, now))) {
            cancelBooking(booking, now, "TIMEOUT", "待确认超时，系统取消");
            n++;
        }
        return n;
    }

    public List<Map<String, Object>> adminAudits(AdminPrincipal actor, Long storeId, Long coachId, String bizType) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.title, a.content, a.biz_type, a.biz_id, a.store_id, a.coach_id, a.user_id, a.created_at,
                       s.name AS store_name, c.name AS coach_name, u.member_no, u.phone, u.nickname
                FROM message_audit a
                LEFT JOIN store s ON s.id = a.store_id
                LEFT JOIN coach c ON c.id = a.coach_id
                LEFT JOIN `user` u ON u.id = a.user_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (scoped != null) {
            sql.append(" AND a.store_id = ?");
            args.add(scoped);
        }
        if (coachId != null) {
            sql.append(" AND a.coach_id = ?");
            args.add(coachId);
        }
        if (bizType != null && !bizType.isBlank()) {
            sql.append(" AND a.biz_type = ?");
            args.add(bizType.trim());
        }
        sql.append(" ORDER BY a.id DESC LIMIT 500");
        return jdbc.queryForList(sql.toString(), args.toArray()).stream().map(row -> {
            Map<String, Object> m = new HashMap<>(row);
            if (m.get("id") != null) m.put("id", String.valueOf(m.get("id")));
            if (m.get("biz_id") != null) m.put("biz_id", String.valueOf(m.get("biz_id")));
            if (m.get("store_id") != null) m.put("store_id", String.valueOf(m.get("store_id")));
            if (m.get("coach_id") != null) m.put("coach_id", String.valueOf(m.get("coach_id")));
            if (m.get("user_id") != null) m.put("user_id", String.valueOf(m.get("user_id")));
            Object nick = m.get("nickname");
            if (nick == null || String.valueOf(nick).isBlank() || "微信用户".equals(String.valueOf(nick))) {
                m.put("nickname", "");
            }
            return m;
        }).toList();
    }

    // ---------- helpers ----------

    private void cancelBooking(LessonBooking booking, LocalDateTime now, String code, String message) {
        booking.setStatus("CANCELLED");
        booking.setCancelReason(code);
        booking.setCancelledAt(now);
        booking.setUpdatedAt(now);
        bookingMapper.updateById(booking);
        String body = "「" + packName(booking.getPackId()) + "」" + booking.getClassDate() + " " + booking.getStartHm()
                + " " + message;
        notify.send(booking.getUserId(), "预约已取消", body, "LESSON_BOOKING", booking.getId(),
                booking.getStoreId(), booking.getCoachId());
        Long coachUid = coachUserId(booking.getCoachId());
        if (coachUid != null && !coachUid.equals(booking.getUserId())) {
            notify.send(coachUid, "预约已取消", body, "LESSON_BOOKING", booking.getId(),
                    booking.getStoreId(), booking.getCoachId());
        } else if (coachUid == null) {
            notify.send(null, "预约已取消", body, "LESSON_BOOKING", booking.getId(),
                    booking.getStoreId(), booking.getCoachId());
        }
    }

    private void assertNoOverlap(long userId, LocalDateTime startAt, LocalDateTime endAt, Long excludeId) {
        List<LessonBooking> rows = bookingMapper.selectList(new LambdaQueryWrapper<LessonBooking>()
                .eq(LessonBooking::getUserId, userId)
                .in(LessonBooking::getStatus, ACTIVE));
        for (LessonBooking row : rows) {
            if (excludeId != null && excludeId.equals(row.getId())) {
                continue;
            }
            if (startAt.isBefore(row.getEndAt()) && endAt.isAfter(row.getStartAt())) {
                throw BizException.rejected("与已有预约时间冲突");
            }
        }
    }

    private void deductLesson(long userId, long packId, long bookingId) {
        int updated = jdbc.update("""
                UPDATE lesson_account SET remaining = remaining - 1, updated_at = ?
                WHERE user_id = ? AND pack_id = ? AND remaining > 0
                """, timeProvider.now(), userId, packId);
        if (updated != 1) {
            throw BizException.rejected("剩余课时不足");
        }
        jdbc.update("INSERT INTO lesson_ledger (id, user_id, pack_id, delta, reason, created_at) VALUES (?,?,?,-1,?,?)",
                snowflake.next(), userId, packId, ledgerReason("BK", bookingId), timeProvider.now());
    }

    private void refundLesson(long userId, long packId, long bookingId) {
        String reason = ledgerReason("BK_RF", bookingId);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lesson_ledger WHERE reason = ?", Integer.class, reason);
        if (exists != null && exists > 0) {
            return;
        }
        // 兼容旧 reason 前缀
        Integer legacy = jdbc.queryForObject(
                "SELECT COUNT(*) FROM lesson_ledger WHERE reason = ?", Integer.class, "BOOKING_REFUND:" + bookingId);
        if (legacy != null && legacy > 0) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, remaining FROM lesson_account WHERE user_id = ? AND pack_id = ?", userId, packId);
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO lesson_account (id, user_id, pack_id, remaining, updated_at) VALUES (?,?,?,?,?)",
                    snowflake.next(), userId, packId, 1, timeProvider.now());
        } else {
            jdbc.update("UPDATE lesson_account SET remaining = remaining + 1, updated_at = ? WHERE user_id = ? AND pack_id = ?",
                    timeProvider.now(), userId, packId);
        }
        jdbc.update("INSERT INTO lesson_ledger (id, user_id, pack_id, delta, reason, created_at) VALUES (?,?,?,1,?,?)",
                snowflake.next(), userId, packId, reason, timeProvider.now());
    }

    private static String ledgerReason(String prefix, long bookingId) {
        return prefix + ":" + bookingId;
    }

    private int remaining(long userId, long packId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT remaining FROM lesson_account WHERE user_id = ? AND pack_id = ?", userId, packId);
        if (rows.isEmpty()) {
            return 0;
        }
        return ((Number) rows.get(0).get("remaining")).intValue();
    }

    private Map<String, Object> detailMap(LessonBooking booking, boolean coachView) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(booking.getId()));
        m.put("packId", String.valueOf(booking.getPackId()));
        m.put("packName", packName(booking.getPackId()));
        m.put("coachId", String.valueOf(booking.getCoachId()));
        m.put("coachName", coachName(booking.getCoachId()));
        m.put("storeId", String.valueOf(booking.getStoreId()));
        m.put("classDate", booking.getClassDate().toString());
        m.put("startHm", booking.getStartHm());
        m.put("startAt", booking.getStartAt());
        m.put("endAt", booking.getEndAt());
        m.put("seatNo", booking.getSeatNo());
        m.put("seatRow", booking.getSeatRow());
        m.put("seatCol", booking.getSeatCol());
        m.put("status", booking.getStatus());
        m.put("rejectReason", booking.getRejectReason());
        m.put("cancelReason", booking.getCancelReason());
        m.put("pendingUntil", booking.getPendingUntil());
        m.put("actedBy", booking.getActedBy());
        m.put("actedByLabel", actorLabel(booking.getActedBy()));
        m.put("pastStart", !timeProvider.now().isBefore(booking.getStartAt()));
        GymUser user = userMapper.selectById(booking.getUserId());
        m.put("userId", String.valueOf(booking.getUserId()));
        m.put("userName", memberName(user));
        m.put("memberNo", user == null ? "" : user.getMemberNo());
        m.put("avatarUrl", user == null ? null : user.getAvatarUrl());
        if (coachView) {
            m.put("phone", user == null ? null : user.getPhone());
        }
        List<Map<String, Object>> appeals = appealMapper.selectList(new LambdaQueryWrapper<LessonAppeal>()
                        .eq(LessonAppeal::getBookingId, booking.getId())
                        .orderByDesc(LessonAppeal::getId))
                .stream()
                .map(a -> {
                    Map<String, Object> x = new HashMap<>();
                    x.put("id", String.valueOf(a.getId()));
                    x.put("reason", a.getReason());
                    x.put("status", a.getStatus());
                    x.put("rejectReason", a.getRejectReason());
                    x.put("createdAt", a.getCreatedAt());
                    x.put("resolvedAt", a.getResolvedAt());
                    return x;
                })
                .toList();
        m.put("appeals", appeals);
        return m;
    }

    private LessonBooking mustOwnedByCoach(long coachUserId, long bookingId) {
        LessonBooking booking = mustBooking(bookingId);
        Long coachId = coachIdByUser(coachUserId);
        if (coachId == null || !coachId.equals(booking.getCoachId())) {
            throw BizException.badRequest("预约不存在");
        }
        return booking;
    }

    private LessonBooking mustAdminBooking(AdminPrincipal actor, long bookingId) {
        LessonBooking booking = mustBooking(bookingId);
        StoreScope.requireStoreAccess(actor, booking.getStoreId());
        return booking;
    }

    /** 支持预约 ID；历史申诉消息可能带申诉 ID，顺带兼容 */
    private LessonBooking resolveAdminBooking(long id) {
        LessonBooking booking = bookingMapper.selectById(id);
        if (booking != null) {
            return booking;
        }
        LessonAppeal appeal = appealMapper.selectById(id);
        if (appeal != null) {
            return mustBooking(appeal.getBookingId());
        }
        throw BizException.badRequest("预约不存在");
    }

    private LessonBooking mustBooking(long id) {
        LessonBooking booking = bookingMapper.selectById(id);
        if (booking == null) {
            throw BizException.badRequest("预约不存在");
        }
        return booking;
    }

    private static String actorLabel(String actedBy) {
        if ("STORE".equals(actedBy)) {
            return "门店";
        }
        if ("COACH".equals(actedBy)) {
            return "教练";
        }
        return "";
    }

    private Map<String, Object> mustPack(long packId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, coach_id, store_id, name, status, minutes_per_lesson, room_cols, room_rows
                FROM course_pack WHERE id = ?
                """, packId);
        if (rows.isEmpty()) {
            throw BizException.badRequest("课程不存在");
        }
        return rows.get(0);
    }

    private long coachStoreId(long coachId) {
        Long storeId = jdbc.query("SELECT store_id FROM coach WHERE id = ?", rs -> rs.next() ? rs.getLong(1) : null, coachId);
        if (storeId == null) {
            throw BizException.badRequest("教练不存在");
        }
        return storeId;
    }

    private Long coachIdByUser(long userId) {
        GymUser user = userMapper.selectById(userId);
        if (user == null || user.getPhone() == null || user.getPhone().isBlank()) {
            return null;
        }
        return jdbc.query("SELECT id FROM coach WHERE phone = ? ORDER BY id LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null, user.getPhone());
    }

    private Long coachUserId(long coachId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT phone FROM coach WHERE id = ?", coachId);
        if (rows.isEmpty() || rows.get(0).get("phone") == null) {
            return null;
        }
        String phone = String.valueOf(rows.get(0).get("phone"));
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, phone).last("LIMIT 1"));
        return user == null ? null : user.getId();
    }

    private String coachName(long coachId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT name FROM coach WHERE id = ?", coachId);
        return rows.isEmpty() ? "" : String.valueOf(rows.get(0).get("name"));
    }

    private String packName(long packId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT name FROM course_pack WHERE id = ?", packId);
        return rows.isEmpty() ? "" : String.valueOf(rows.get(0).get("name"));
    }

    private String userLabel(long userId) {
        GymUser user = userMapper.selectById(userId);
        if (user == null) {
            return "学员";
        }
        return memberName(user);
    }

    private static String memberName(GymUser user) {
        if (user == null) {
            return "学员";
        }
        String name = user.getNickname();
        if (name != null && !name.isBlank() && !"微信用户".equals(name)) {
            return name;
        }
        return user.getMemberNo() == null || user.getMemberNo().isBlank() ? "学员" : user.getMemberNo();
    }

    private int maxPackMinutes(long coachId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(minutes_per_lesson), 60) FROM course_pack WHERE coach_id = ?", Integer.class, coachId);
        return max == null || max < 1 ? 60 : max;
    }

    private static List<String> normalizeStarts(List<String> startHms) {
        if (startHms == null) {
            return List.of();
        }
        return startHms.stream()
                .filter(Objects::nonNull)
                .map(LessonBookingService::normalizeHm)
                .distinct()
                .sorted(Comparator.comparingInt(LessonBookingService::minutesOf))
                .collect(Collectors.toList());
    }

    private static String normalizeHm(String hm) {
        try {
            return LocalTime.parse(hm.trim(), HM).format(HM);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw BizException.badRequest("时间格式应为 HH:mm");
        }
    }

    private static int minutesOf(String hm) {
        LocalTime t = LocalTime.parse(hm, HM);
        return t.getHour() * 60 + t.getMinute();
    }
}
