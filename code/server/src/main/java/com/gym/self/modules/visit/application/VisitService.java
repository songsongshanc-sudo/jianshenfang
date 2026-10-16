package com.gym.self.modules.visit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.content.domain.AppConfig;
import com.gym.self.modules.content.domain.AppConfigMapper;
import com.gym.self.modules.gate.GateFaceSync;
import com.gym.self.modules.order.domain.Payment;
import com.gym.self.modules.order.domain.PaymentMapper;
import com.gym.self.modules.order.domain.TradeOrder;
import com.gym.self.modules.order.domain.TradeOrderMapper;
import com.gym.self.modules.order.pay.PayGateway;
import com.gym.self.modules.store.domain.Store;
import com.gym.self.modules.store.domain.StoreMapper;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.gym.self.modules.user.domain.UserFace;
import com.gym.self.modules.user.domain.UserFaceMapper;
import com.gym.self.modules.visit.domain.VisitPass;
import com.gym.self.modules.visit.domain.VisitPassMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class VisitService {

    private static final Logger log = LoggerFactory.getLogger(VisitService.class);
    private static final String BIZ = "VISIT";
    private static final long PRODUCT_ID = 0L;
    private static final int FORFEIT_GRACE_HOURS = 2;
    private static final Set<String> OPEN_STATUSES = Set.of("UNUSED", "IN_STORE", "TIMEOUT_FORFEIT");
    private static final String DEFAULT_NOTICE = "支付金额为参观押金，不是会费。仅可到下单时选中的门店参观一次；请从进店闸机入场、出店闸机离场，按时离店将原路退回押金；超时未离店将扣除全部押金。购买后若从未进店，可申请退款；有效期到期仍未进店将自动退回。";

    private final AppConfigMapper appConfigMapper;
    private final VisitPassMapper visitPassMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final PaymentMapper paymentMapper;
    private final MembershipMapper membershipMapper;
    private final StoreMapper storeMapper;
    private final GymUserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final PayGateway payGateway;
    private final GateFaceSync gateFaceSync;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;

    public VisitService(AppConfigMapper appConfigMapper, VisitPassMapper visitPassMapper,
                        TradeOrderMapper tradeOrderMapper, PaymentMapper paymentMapper, MembershipMapper membershipMapper,
                        StoreMapper storeMapper, GymUserMapper userMapper, UserFaceMapper userFaceMapper,
                        PayGateway payGateway, GateFaceSync gateFaceSync, Snowflake snowflake, TimeProvider timeProvider) {
        this.appConfigMapper = appConfigMapper;
        this.visitPassMapper = visitPassMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.membershipMapper = membershipMapper;
        this.storeMapper = storeMapper;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.payGateway = payGateway;
        this.gateFaceSync = gateFaceSync;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
    }

    public ConfigView adminGet(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        return config();
    }

    @Transactional
    public void adminSave(AdminPrincipal actor, ConfigUpdate update) {
        StoreScope.requireMaster(actor);
        if (update.depositFen() == null || update.depositFen() < 1) {
            throw BizException.badRequest("押金至少 1 分");
        }
        if (update.durationMinutes() == null || update.durationMinutes() < 1 || update.durationMinutes() > 24 * 60) {
            throw BizException.badRequest("可参观时长不正确");
        }
        if (update.validDays() == null || update.validDays() < 1 || update.validDays() > 365) {
            throw BizException.badRequest("有效期不正确");
        }
        if (update.warnMinutes() == null || update.warnMinutes() < 1 || update.warnMinutes() >= update.durationMinutes()) {
            throw BizException.badRequest("提醒时间须小于可参观时长");
        }
        write("visit.enabled", update.enabled() ? "1" : "0");
        write("visit.deposit.fen", String.valueOf(update.depositFen()));
        write("visit.duration.minutes", String.valueOf(update.durationMinutes()));
        write("visit.valid.days", String.valueOf(update.validDays()));
        write("visit.warn.minutes", String.valueOf(update.warnMinutes()));
        write("visit.notice", update.notice() == null || update.notice().isBlank() ? DEFAULT_NOTICE : update.notice().trim());
    }

    public List<AdminVisit> adminList(AdminPrincipal actor, Long storeId) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        LambdaQueryWrapper<VisitPass> query = new LambdaQueryWrapper<VisitPass>().orderByDesc(VisitPass::getId);
        if (scoped != null) {
            if (actor.master()) {
                query.and(w -> w.eq(VisitPass::getStoreId, scoped).or().isNull(VisitPass::getStoreId));
            } else {
                query.eq(VisitPass::getStoreId, scoped);
            }
        }
        return visitPassMapper.selectList(query).stream().map(row -> {
            Store store = row.getStoreId() == null ? null : storeMapper.selectById(row.getStoreId());
            GymUser user = userMapper.selectById(row.getUserId());
            return new AdminVisit(String.valueOf(row.getId()), String.valueOf(row.getOrderId()),
                    user == null || user.getPhone() == null ? "" : user.getPhone(),
                    store == null ? "" : store.getName(), row.getDepositFen(), row.getStatus(),
                    row.getValidUntil(), row.getEnteredAt(), row.getDueExitAt(), row.getExitedAt(), row.getRefundedAt());
        }).toList();
    }

    public OfferView offer(long userId, Long storeId) {
        ConfigView cfg = config();
        boolean member = storeId != null && hasActiveMembership(userId, storeId);
        VisitPass active = activePass(userId);
        MyVisit my = active == null ? null : toMy(active);
        boolean canBuy = cfg.enabled() && storeId != null && !member && active == null && faceReady(userId);
        String block = null;
        if (!cfg.enabled()) {
            block = "参观暂未开放";
        } else if (storeId == null) {
            block = "请先选择门店";
        } else if (member) {
            block = "本店已是会员，无需购买参观卡";
        } else if (active != null) {
            block = "你已有参观卡";
        } else if (!faceReady(userId)) {
            block = "请先完成注册与人脸采集";
        }
        return new OfferView(cfg, canBuy, block, my, member);
    }

    @Transactional
    public Created createOrder(long userId, long storeId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw BizException.badRequest("请提供 Idempotency-Key");
        }
        TradeOrder existing = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId)
                .eq(TradeOrder::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            return created(existing);
        }
        ConfigView cfg = config();
        if (!cfg.enabled()) {
            throw BizException.rejected("参观暂未开放");
        }
        if (hasActiveMembership(userId, storeId)) {
            throw BizException.rejected("本店已是会员，无需购买参观卡");
        }
        if (activePass(userId) != null) {
            throw BizException.rejected("你已有参观卡");
        }
        if (!faceReady(userId)) {
            throw BizException.rejected("请先完成注册与人脸采集");
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null || Integer.valueOf(1).equals(store.getDeleted())) {
            throw BizException.badRequest("门店不存在");
        }
        if (!"OPEN".equals(store.getStatus())) {
            throw BizException.rejected("门店已停业");
        }
        LocalDateTime now = timeProvider.now();
        TradeOrder order = new TradeOrder();
        order.setId(snowflake.next());
        order.setOrderNo(String.valueOf(order.getId()));
        order.setUserId(userId);
        order.setStoreId(storeId);
        order.setBizType(BIZ);
        order.setProductId(PRODUCT_ID);
        order.setProductName("参观押金");
        order.setAmountFen(cfg.depositFen());
        order.setStatus("PENDING");
        order.setIdempotencyKey(idempotencyKey);
        order.setExpireAt(now.plusMinutes(orderExpireMinutes()));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        try {
            tradeOrderMapper.insert(order);
        } catch (DuplicateKeyException exception) {
            TradeOrder again = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getUserId, userId)
                    .eq(TradeOrder::getIdempotencyKey, idempotencyKey));
            if (again != null) {
                return created(again);
            }
            throw exception;
        }
        GymUser user = userMapper.selectById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            throw BizException.badRequest("请重新登录后再支付");
        }
        PayGateway.Prepared prepared = payGateway.prepare(order.getOrderNo(), "参观押金", order.getAmountFen(), user.getOpenid());
        Payment payment = new Payment();
        payment.setId(snowflake.next());
        payment.setOrderId(order.getId());
        payment.setMchId(prepared.mchId());
        payment.setPrepayId(prepared.prepayId());
        payment.setAmountFen(order.getAmountFen());
        payment.setStatus("CREATED");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentMapper.insert(payment);
        return new Created(String.valueOf(order.getId()), order.getOrderNo(), order.getAmountFen(), order.getStatus(),
                payGateway.mockEnabled(), prepared.timeStamp(), prepared.nonceStr(), prepared.payPackage(),
                prepared.signType(), prepared.paySign());
    }

    @Transactional
    public void onPaid(TradeOrder order) {
        if (!BIZ.equals(order.getBizType())) {
            return;
        }
        if (visitPassMapper.selectCount(new LambdaQueryWrapper<VisitPass>().eq(VisitPass::getOrderId, order.getId())) > 0) {
            return;
        }
        ConfigView cfg = config();
        LocalDateTime now = timeProvider.now();
        VisitPass pass = new VisitPass();
        pass.setId(snowflake.next());
        pass.setUserId(order.getUserId());
        pass.setOrderId(order.getId());
        pass.setDepositFen(order.getAmountFen());
        pass.setDurationMinutes(cfg.durationMinutes());
        pass.setWarnMinutes(cfg.warnMinutes());
        pass.setStoreId(order.getStoreId());
        pass.setValidUntil(now.plusDays(cfg.validDays()));
        pass.setStatus("UNUSED");
        pass.setCreatedAt(now);
        pass.setUpdatedAt(now);
        visitPassMapper.insert(pass);
        gateFaceSync.onVisitChanged(order.getUserId(), order.getStoreId());
    }

    public boolean canOpenByVisit(long userId, long storeId, String direction) {
        VisitPass pass = activePass(userId);
        if (pass == null || pass.getStoreId() == null || !pass.getStoreId().equals(storeId)) {
            return false;
        }
        LocalDateTime now = timeProvider.now();
        boolean exit = "EXIT".equalsIgnoreCase(direction == null ? "" : direction);
        return switch (pass.getStatus()) {
            case "UNUSED" -> !exit && !pass.getValidUntil().isBefore(now);
            case "IN_STORE", "TIMEOUT_FORFEIT" -> true;
            default -> false;
        };
    }

    public LocalDateTime accessUntil(long userId, long storeId) {
        VisitPass pass = activePass(userId);
        if (pass == null || pass.getStoreId() == null || !pass.getStoreId().equals(storeId)) {
            return null;
        }
        LocalDateTime now = timeProvider.now();
        return switch (pass.getStatus()) {
            case "UNUSED" -> pass.getValidUntil();
            case "IN_STORE" -> pass.getDueExitAt() == null ? pass.getValidUntil() : pass.getDueExitAt().plusHours(FORFEIT_GRACE_HOURS);
            case "TIMEOUT_FORFEIT" -> pass.getForfeitedAt() == null ? now.plusHours(FORFEIT_GRACE_HOURS)
                    : pass.getForfeitedAt().plusHours(FORFEIT_GRACE_HOURS);
            default -> null;
        };
    }

    public boolean rejectOtherStoreVisit(long userId, long storeId) {
        VisitPass pass = activePass(userId);
        if (pass == null || pass.getStoreId() == null) {
            return false;
        }
        return !pass.getStoreId().equals(storeId)
                && ("UNUSED".equals(pass.getStatus()) || "IN_STORE".equals(pass.getStatus()) || "TIMEOUT_FORFEIT".equals(pass.getStatus()));
    }

    @Transactional
    public GateEffect onGateSuccess(long userId, long storeId, String direction) {
        VisitPass pass = activePass(userId);
        if (pass == null || pass.getStoreId() == null || !pass.getStoreId().equals(storeId)) {
            return GateEffect.NONE;
        }
        LocalDateTime now = timeProvider.now();
        boolean exit = "EXIT".equalsIgnoreCase(direction == null ? "" : direction);
        if (exit) {
            if (!"IN_STORE".equals(pass.getStatus()) && !"TIMEOUT_FORFEIT".equals(pass.getStatus())) {
                return GateEffect.NONE;
            }
            finishExit(pass, now);
            return GateEffect.EXITED;
        }
        if ("UNUSED".equals(pass.getStatus())) {
            if (pass.getValidUntil().isBefore(now)) {
                return GateEffect.NONE;
            }
            pass.setStatus("IN_STORE");
            pass.setEnteredAt(now);
            pass.setDueExitAt(now.plusMinutes(pass.getDurationMinutes()));
            pass.setUpdatedAt(now);
            visitPassMapper.updateById(pass);
            return GateEffect.ENTERED;
        }
        return GateEffect.NONE;
    }

    @Transactional
    public MyVisit refundUnused(long userId) {
        VisitPass pass = activePass(userId);
        if (pass == null || !"UNUSED".equals(pass.getStatus())) {
            throw BizException.rejected("当前不能退款");
        }
        refundPass(pass, "REFUNDED_UNUSED");
        return toMy(visitPassMapper.selectById(pass.getId()));
    }

    public int sweep() {
        LocalDateTime now = timeProvider.now();
        int n = 0;
        for (VisitPass pass : visitPassMapper.selectList(new LambdaQueryWrapper<VisitPass>()
                .eq(VisitPass::getStatus, "UNUSED").lt(VisitPass::getValidUntil, now))) {
            try {
                refundPass(pass, "EXPIRED_REFUNDED");
                n++;
            } catch (RuntimeException exception) {
                log.warn("visit expire refund failed {}", pass.getId(), exception);
            }
        }
        for (VisitPass pass : visitPassMapper.selectList(new LambdaQueryWrapper<VisitPass>()
                .eq(VisitPass::getStatus, "IN_STORE").lt(VisitPass::getDueExitAt, now))) {
            pass.setStatus("TIMEOUT_FORFEIT");
            pass.setForfeitedAt(now);
            pass.setUpdatedAt(now);
            visitPassMapper.updateById(pass);
            n++;
        }
        for (VisitPass pass : visitPassMapper.selectList(new LambdaQueryWrapper<VisitPass>()
                .eq(VisitPass::getStatus, "TIMEOUT_FORFEIT")
                .lt(VisitPass::getForfeitedAt, now.minusHours(FORFEIT_GRACE_HOURS)))) {
            pass.setStatus("TIMEOUT_CLOSED");
            pass.setUpdatedAt(now);
            visitPassMapper.updateById(pass);
            if (pass.getStoreId() != null) {
                gateFaceSync.onVisitChanged(pass.getUserId(), pass.getStoreId());
            }
            n++;
        }
        for (VisitPass pass : visitPassMapper.selectList(new LambdaQueryWrapper<VisitPass>()
                .eq(VisitPass::getStatus, "REFUND_PENDING"))) {
            try {
                String target = pass.getExitedAt() != null ? "EXITED_REFUNDED"
                        : (pass.getValidUntil().isBefore(now) ? "EXPIRED_REFUNDED" : "REFUNDED_UNUSED");
                refundPass(pass, target);
                n++;
            } catch (RuntimeException exception) {
                log.warn("visit refund retry failed {}", pass.getId(), exception);
            }
        }
        return n;
    }

    private void finishExit(VisitPass pass, LocalDateTime now) {
        pass.setExitedAt(now);
        pass.setUpdatedAt(now);
        if ("TIMEOUT_FORFEIT".equals(pass.getStatus())) {
            pass.setStatus("TIMEOUT_CLOSED");
            visitPassMapper.updateById(pass);
            if (pass.getStoreId() != null) {
                gateFaceSync.onVisitChanged(pass.getUserId(), pass.getStoreId());
            }
            return;
        }
        refundPass(pass, "EXITED_REFUNDED");
    }

    private void refundPass(VisitPass pass, String successStatus) {
        LocalDateTime now = timeProvider.now();
        TradeOrder order = tradeOrderMapper.selectById(pass.getOrderId());
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, pass.getOrderId()));
        if (order == null || payment == null || !"PAID".equals(order.getStatus())) {
            throw BizException.rejected("订单状态不正确");
        }
        try {
            payGateway.refund(order.getOrderNo(), payment.getTransactionId(), pass.getDepositFen());
            order.setStatus("REFUNDED");
            order.setUpdatedAt(now);
            tradeOrderMapper.updateById(order);
            payment.setStatus("REFUNDED");
            payment.setUpdatedAt(now);
            paymentMapper.updateById(payment);
            pass.setStatus(successStatus);
            pass.setRefundedAt(now);
            pass.setRefundError(null);
            pass.setUpdatedAt(now);
            visitPassMapper.updateById(pass);
            if (pass.getStoreId() != null) {
                gateFaceSync.onVisitChanged(pass.getUserId(), pass.getStoreId());
            }
        } catch (RuntimeException exception) {
            pass.setStatus("REFUND_PENDING");
            pass.setRefundError(clip(exception.getMessage()));
            pass.setUpdatedAt(now);
            visitPassMapper.updateById(pass);
            throw exception;
        }
    }

    private ConfigView config() {
        String notice = read("visit.notice", DEFAULT_NOTICE);
        if (notice.isBlank() || (!notice.contains("押金") && !notice.contains("参观"))) {
            notice = DEFAULT_NOTICE;
        }
        return new ConfigView(
                "1".equals(read("visit.enabled", "0")),
                Long.parseLong(read("visit.deposit.fen", "990")),
                Integer.parseInt(read("visit.duration.minutes", "60")),
                Integer.parseInt(read("visit.valid.days", "30")),
                Integer.parseInt(read("visit.warn.minutes", "5")),
                notice);
    }

    private VisitPass activePass(long userId) {
        return visitPassMapper.selectOne(new LambdaQueryWrapper<VisitPass>()
                .eq(VisitPass::getUserId, userId)
                .in(VisitPass::getStatus, OPEN_STATUSES)
                .orderByDesc(VisitPass::getId)
                .last("LIMIT 1"));
    }

    private boolean hasActiveMembership(long userId, long storeId) {
        Long count = membershipMapper.selectCount(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, userId)
                .ne(Membership::getStatus, "REVOKED")
                .gt(Membership::getEndAt, timeProvider.now())
                .and(w -> w.eq(Membership::getStoreId, storeId).or().eq(Membership::getCrossStore, 1)));
        return count != null && count > 0;
    }

    private boolean faceReady(long userId) {
        GymUser user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getRegisterStatus())) {
            return false;
        }
        Long faces = userFaceMapper.selectCount(new LambdaQueryWrapper<UserFace>()
                .eq(UserFace::getUserId, userId)
                .eq(UserFace::getStatus, "ENROLLED"));
        return faces != null && faces > 0;
    }

    private MyVisit toMy(VisitPass pass) {
        Store store = pass.getStoreId() == null ? null : storeMapper.selectById(pass.getStoreId());
        long remainMs = 0;
        boolean warn = false;
        if ("IN_STORE".equals(pass.getStatus()) && pass.getDueExitAt() != null) {
            remainMs = Math.max(0, Duration.between(timeProvider.now(), pass.getDueExitAt()).toMillis());
            warn = remainMs <= pass.getWarnMinutes() * 60_000L;
        }
        return new MyVisit(String.valueOf(pass.getId()), String.valueOf(pass.getOrderId()), pass.getStatus(),
                pass.getDepositFen(), pass.getDurationMinutes(), pass.getWarnMinutes(), pass.getValidUntil(),
                pass.getEnteredAt(), pass.getDueExitAt(), pass.getExitedAt(),
                store == null ? null : String.valueOf(store.getId()), store == null ? null : store.getName(),
                remainMs, warn, pass.getRefundError());
    }

    private Created created(TradeOrder order) {
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, order.getId()));
        if (payGateway.mockEnabled() || payment == null || payment.getPrepayId() == null || payment.getPrepayId().isBlank()
                || !"PENDING".equals(order.getStatus())) {
            return new Created(String.valueOf(order.getId()), order.getOrderNo(), order.getAmountFen(), order.getStatus(),
                    payGateway.mockEnabled(), "", "", payment == null ? "" : "prepay_id=" + payment.getPrepayId(),
                    "RSA", "");
        }
        PayGateway.Prepared prepared = payGateway.resign(payment.getPrepayId());
        return new Created(String.valueOf(order.getId()), order.getOrderNo(), order.getAmountFen(), order.getStatus(),
                false, prepared.timeStamp(), prepared.nonceStr(), prepared.payPackage(), prepared.signType(),
                prepared.paySign());
    }

    private int orderExpireMinutes() {
        return Integer.parseInt(read("order.expire.minutes", "15"));
    }

    private String read(String key, String fallback) {
        AppConfig config = appConfigMapper.selectById(key);
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return fallback;
        }
        return config.getConfigValue();
    }

    private void write(String key, String value) {
        AppConfig config = appConfigMapper.selectById(key);
        LocalDateTime now = timeProvider.now();
        if (config == null) {
            config = new AppConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setUpdatedAt(now);
            appConfigMapper.insert(config);
            return;
        }
        config.setConfigValue(value);
        config.setUpdatedAt(now);
        appConfigMapper.updateById(config);
    }

    private static String clip(String message) {
        if (message == null) {
            return "退款失败";
        }
        return message.length() > 250 ? message.substring(0, 250) : message;
    }

    public enum GateEffect { NONE, ENTERED, EXITED }

    public record ConfigView(boolean enabled, long depositFen, int durationMinutes, int validDays, int warnMinutes, String notice) {}
    public record ConfigUpdate(boolean enabled, Long depositFen, Integer durationMinutes, Integer validDays, Integer warnMinutes, String notice) {}
    public record OfferView(ConfigView config, boolean canBuy, String blockReason, MyVisit mine, boolean member) {}
    public record MyVisit(String id, String orderId, String status, long depositFen, int durationMinutes, int warnMinutes,
                          LocalDateTime validUntil, LocalDateTime enteredAt, LocalDateTime dueExitAt, LocalDateTime exitedAt,
                          String storeId, String storeName, long remainMs, boolean warn, String refundError) {}
    public record Created(String orderId, String orderNo, long amountFen, String status, boolean mockPay,
                          String timeStamp, String nonceStr, String payPackage, String signType, String paySign) {}
    public record AdminVisit(String id, String orderId, String phone, String storeName, long depositFen, String status,
                             LocalDateTime validUntil, LocalDateTime enteredAt, LocalDateTime dueExitAt,
                             LocalDateTime exitedAt, LocalDateTime refundedAt) {}
}
