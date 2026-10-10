package com.gym.self.modules.order.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.card.application.CardService;
import com.gym.self.modules.gate.GateFaceSync;
import com.gym.self.modules.card.domain.CardProduct;
import com.gym.self.modules.card.domain.CardProductMapper;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.content.domain.Agreement;
import com.gym.self.modules.content.domain.AgreementMapper;
import com.gym.self.modules.content.domain.AppConfig;
import com.gym.self.modules.content.domain.AppConfigMapper;
import com.gym.self.modules.order.domain.Payment;
import com.gym.self.modules.order.domain.PaymentMapper;
import com.gym.self.modules.order.domain.TradeOrder;
import com.gym.self.modules.order.domain.TradeOrderMapper;
import com.gym.self.modules.order.pay.PayGateway;
import com.gym.self.modules.store.domain.Store;
import com.gym.self.modules.store.domain.StoreMapper;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private final TradeOrderMapper tradeOrderMapper;
    private final PaymentMapper paymentMapper;
    private final CardProductMapper cardProductMapper;
    private final MembershipMapper membershipMapper;
    private final AgreementMapper agreementMapper;
    private final AppConfigMapper appConfigMapper;
    private final StoreMapper storeMapper;
    private final GymUserMapper gymUserMapper;
    private final CardService cardService;
    private final PayGateway payGateway;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;
    private final ObjectProvider<CourseGrant> courseGrant;
    private final GateFaceSync gateFaceSync;

    public OrderService(TradeOrderMapper tradeOrderMapper, PaymentMapper paymentMapper,
                        CardProductMapper cardProductMapper, MembershipMapper membershipMapper,
                        AgreementMapper agreementMapper, AppConfigMapper appConfigMapper, StoreMapper storeMapper,
                        GymUserMapper gymUserMapper, CardService cardService, PayGateway payGateway,
                        Snowflake snowflake, TimeProvider timeProvider, ObjectProvider<CourseGrant> courseGrant,
                        GateFaceSync gateFaceSync) {
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.cardProductMapper = cardProductMapper;
        this.membershipMapper = membershipMapper;
        this.agreementMapper = agreementMapper;
        this.appConfigMapper = appConfigMapper;
        this.storeMapper = storeMapper;
        this.gymUserMapper = gymUserMapper;
        this.cardService = cardService;
        this.payGateway = payGateway;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.courseGrant = courseGrant;
        this.gateFaceSync = gateFaceSync;
    }

    @Transactional
    public Created create(long userId, long storeId, long cardProductId, long agreementId, int agreementVersion,
                          String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw BizException.badRequest("请提供 Idempotency-Key");
        }
        TradeOrder existing = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId)
                .eq(TradeOrder::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            return created(existing);
        }
        Agreement agreement = currentAgreement();
        if (agreement == null || !agreement.getId().equals(agreementId)
                || agreement.getVersionNo() == null || agreement.getVersionNo() != agreementVersion) {
            throw BizException.rejected("协议已更新，请重新阅读");
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null || Integer.valueOf(1).equals(store.getDeleted())) {
            throw BizException.badRequest("门店不存在");
        }
        if (!"OPEN".equals(store.getStatus())) {
            throw BizException.rejected("门店已停业");
        }
        CardProduct card = cardProductMapper.selectById(cardProductId);
        if (card == null || !Long.valueOf(storeId).equals(card.getStoreId())) {
            throw BizException.rejected("卡种不存在");
        }
        String block = cardService.blockReason(userId, card);
        if (block != null) {
            throw BizException.rejected(block);
        }
        boolean limited = card.getStockTotal() != null;
        LocalDateTime now = timeProvider.now();
        if (limited && cardProductMapper.reserveOne(card.getId(), now) != 1) {
            throw BizException.rejected("库存不足");
        }
        TradeOrder order = new TradeOrder();
        order.setId(snowflake.next());
        order.setOrderNo(String.valueOf(order.getId()));
        order.setUserId(userId);
        order.setStoreId(storeId);
        order.setBizType("CARD");
        order.setProductId(card.getId());
        order.setProductName(card.getName());
        order.setAmountFen(card.getPriceFen());
        order.setStatus("PENDING");
        order.setAgreementId(agreement.getId());
        order.setAgreementVersion(agreement.getVersionNo());
        order.setIdempotencyKey(idempotencyKey);
        order.setExpireAt(now.plusMinutes(expireMinutes()));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        try {
            tradeOrderMapper.insert(order);
        } catch (DuplicateKeyException exception) {
            TradeOrder raced = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                    .eq(TradeOrder::getUserId, userId)
                    .eq(TradeOrder::getIdempotencyKey, idempotencyKey));
            if (raced != null) {
                if (limited) {
                    cardProductMapper.releaseOne(card.getId(), now);
                }
                return created(raced);
            }
            throw exception;
        }
        PayGateway.Prepared prepared = payGateway.prepare(order.getOrderNo(), store.getName() + "-" + card.getName(),
                order.getAmountFen(), openid(userId));
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
    public Created createCourse(long userId, long storeId, long packId, String name, long priceFen,
                                long agreementId, int agreementVersion, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw BizException.badRequest("请提供 Idempotency-Key");
        }
        TradeOrder existing = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getUserId, userId)
                .eq(TradeOrder::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            return created(existing);
        }
        Agreement agreement = currentAgreement();
        if (agreement == null || !agreement.getId().equals(agreementId)
                || agreement.getVersionNo() == null || agreement.getVersionNo() != agreementVersion) {
            throw BizException.rejected("协议已更新，请重新阅读");
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null || Integer.valueOf(1).equals(store.getDeleted()) || !"OPEN".equals(store.getStatus())) {
            throw BizException.rejected("门店已停业");
        }
        LocalDateTime now = timeProvider.now();
        TradeOrder order = new TradeOrder();
        order.setId(snowflake.next());
        order.setOrderNo(String.valueOf(order.getId()));
        order.setUserId(userId);
        order.setStoreId(storeId);
        order.setBizType("COURSE");
        order.setProductId(packId);
        order.setProductName(name);
        order.setAmountFen(priceFen);
        order.setStatus("PENDING");
        order.setAgreementId(agreement.getId());
        order.setAgreementVersion(agreement.getVersionNo());
        order.setIdempotencyKey(idempotencyKey);
        order.setExpireAt(now.plusMinutes(expireMinutes()));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        tradeOrderMapper.insert(order);
        PayGateway.Prepared prepared = payGateway.prepare(order.getOrderNo(), name, priceFen, openid(userId));
        Payment payment = new Payment();
        payment.setId(snowflake.next());
        payment.setOrderId(order.getId());
        payment.setMchId(prepared.mchId());
        payment.setPrepayId(prepared.prepayId());
        payment.setAmountFen(priceFen);
        payment.setStatus("CREATED");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentMapper.insert(payment);
        return new Created(String.valueOf(order.getId()), order.getOrderNo(), priceFen, order.getStatus(),
                payGateway.mockEnabled(), prepared.timeStamp(), prepared.nonceStr(), prepared.payPackage(),
                prepared.signType(), prepared.paySign());
    }

    public OrderView detail(long userId, long orderId) {
        TradeOrder order = mustOwn(userId, orderId);
        return toView(order);
    }

    public List<OrderView> mine(long userId) {
        return tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getUserId, userId)
                        .orderByDesc(TradeOrder::getCreatedAt)
                        .orderByDesc(TradeOrder::getId))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public OrderView cancel(long userId, long orderId) {
        lock(orderId);
        TradeOrder order = mustOwn(userId, orderId);
        if ("PAID".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) {
            return toView(order);
        }
        if ("PENDING".equals(order.getStatus())) {
            payGateway.close(order.getOrderNo());
        }
        closePending(order, timeProvider.now());
        return toView(reload(orderId));
    }

    @Transactional
    public void fulfillByNotify(String orderNo, String transactionId, long paidFen, String rawNotify) {
        if (orderNo == null || orderNo.isBlank()) {
            throw BizException.badRequest("商户订单号不正确");
        }
        TradeOrder order = tradeOrderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo));
        if (order == null) {
            throw BizException.badRequest("订单不存在");
        }
        if (order.getAmountFen() == null || order.getAmountFen() != paidFen) {
            throw BizException.rejected("支付金额不正确");
        }
        fulfill(order.getId(), transactionId, rawNotify);
    }

    @Transactional
    public OrderView mockPay(long userId, long orderId) {
        if (!payGateway.mockEnabled()) {
            throw BizException.rejected("当前环境不能模拟支付");
        }
        mustOwn(userId, orderId);
        fulfill(orderId, "mock-" + orderId, null);
        return toView(reload(orderId));
    }

    @Transactional
    public void fulfill(long orderId, String transactionId, String rawNotify) {
        lock(orderId);
        TradeOrder order = reload(orderId);
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, orderId));
        if (payment == null) {
            throw BizException.badRequest("订单不存在");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw BizException.badRequest("支付单号不正确");
        }
        if ("PAID".equals(order.getStatus()) || "REFUNDED".equals(order.getStatus())) {
            return;
        }
        if (!"PENDING".equals(order.getStatus()) && !"CLOSED".equals(order.getStatus())) {
            throw BizException.rejected("订单不能支付");
        }
        LocalDateTime now = timeProvider.now();
        boolean course = "COURSE".equals(order.getBizType());
        CardProduct card = course ? null : cardProductMapper.selectById(order.getProductId());
        boolean limited = card != null && card.getStockTotal() != null;
        if (limited) {
            int sold = "PENDING".equals(order.getStatus())
                    ? cardProductMapper.sellReserved(card.getId(), now)
                    : cardProductMapper.sellOne(card.getId(), now);
            if (sold != 1) {
                throw BizException.rejected("库存不足");
            }
        }
        order.setStatus("PAID");
        order.setPaidAt(now);
        order.setUpdatedAt(now);
        tradeOrderMapper.updateById(order);
        payment.setTransactionId(transactionId);
        payment.setStatus("SUCCESS");
        payment.setRawNotify(rawNotify);
        payment.setUpdatedAt(now);
        paymentMapper.updateById(payment);
        if (course) {
            CourseGrant grant = courseGrant.getIfAvailable();
            if (grant != null) {
                grant.onPaid(order);
            }
            return;
        }
        if (membershipMapper.selectCount(new LambdaQueryWrapper<Membership>().eq(Membership::getOrderId, orderId)) == 0) {
            membershipMapper.insert(membership(order, card, now, "PAY"));
            gateFaceSync.onMembership(order.getUserId(), order.getStoreId());
        }
    }

    @Transactional
    public long grantGroupon(long userId, long storeId, long cardProductId) {
        CardProduct card = cardProductMapper.selectById(cardProductId);
        if (card == null || !Long.valueOf(storeId).equals(card.getStoreId()) || !"ON".equals(card.getStatus())) {
            throw BizException.rejected("卡种不存在");
        }
        LocalDateTime now = timeProvider.now();
        if (card.getStockTotal() != null && cardProductMapper.sellOne(card.getId(), now) != 1) {
            throw BizException.rejected("库存不足");
        }
        TradeOrder order = new TradeOrder();
        order.setId(snowflake.next());
        order.setOrderNo(String.valueOf(order.getId()));
        order.setUserId(userId);
        order.setStoreId(storeId);
        order.setBizType("CARD");
        order.setProductId(card.getId());
        order.setProductName(card.getName());
        order.setAmountFen(0L);
        order.setStatus("PAID");
        order.setPaidAt(now);
        order.setExpireAt(now);
        order.setIdempotencyKey("groupon-" + order.getId());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        tradeOrderMapper.insert(order);
        Payment payment = new Payment();
        payment.setId(snowflake.next());
        payment.setOrderId(order.getId());
        payment.setMchId("groupon");
        payment.setAmountFen(0L);
        payment.setStatus("SUCCESS");
        payment.setTransactionId("groupon-" + order.getId());
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentMapper.insert(payment);
        membershipMapper.insert(membership(order, card, now, "GROUPON"));
        gateFaceSync.onMembership(userId, storeId);
        return order.getId();
    }

    public int closeExpired() {
        LocalDateTime now = timeProvider.now();
        List<TradeOrder> due = tradeOrderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getStatus, "PENDING")
                .lt(TradeOrder::getExpireAt, now));
        int closed = 0;
        for (TradeOrder order : due) {
            if (closeIfPending(order.getId(), now)) {
                closed++;
            }
        }
        return closed;
    }

    public List<AdminOrder> adminList(AdminPrincipal actor, Long storeId) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        LambdaQueryWrapper<TradeOrder> query = new LambdaQueryWrapper<TradeOrder>()
                .orderByDesc(TradeOrder::getCreatedAt)
                .orderByDesc(TradeOrder::getId);
        if (scoped != null) {
            query.eq(TradeOrder::getStoreId, scoped);
        }
        return tradeOrderMapper.selectList(query).stream().map(order -> {
            Store store = storeMapper.selectById(order.getStoreId());
            return new AdminOrder(String.valueOf(order.getId()), order.getOrderNo(),
                    store == null ? "" : store.getName(), order.getProductName(), order.getAmountFen(),
                    order.getStatus(), order.getPaidAt());
        }).toList();
    }

    public RefundResult refund(AdminPrincipal actor, long orderId) {
        throw BizException.rejected("购买后不能退款");
    }

    @Transactional
    public boolean closeIfPending(long orderId, LocalDateTime now) {
        lock(orderId);
        TradeOrder order = reload(orderId);
        if (order == null || !"PENDING".equals(order.getStatus())) {
            return false;
        }
        payGateway.close(order.getOrderNo());
        closePending(order, now);
        return true;
    }

    private void closePending(TradeOrder order, LocalDateTime now) {
        if (!"PENDING".equals(order.getStatus())) {
            return;
        }
        order.setStatus("CLOSED");
        order.setUpdatedAt(now);
        tradeOrderMapper.updateById(order);
        if ("CARD".equals(order.getBizType())) {
            cardProductMapper.releaseOne(order.getProductId(), now);
        }
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, order.getId()));
        if (payment != null && "CREATED".equals(payment.getStatus())) {
            payment.setStatus("CLOSED");
            payment.setUpdatedAt(now);
            paymentMapper.updateById(payment);
        }
    }

    private Membership membership(TradeOrder order, CardProduct card, LocalDateTime paidAt, String source) {
        LocalDateTime start = paidAt;
        Membership latest = membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, order.getUserId())
                .eq(Membership::getStoreId, order.getStoreId())
                .ne(Membership::getStatus, "REVOKED")
                .gt(Membership::getEndAt, paidAt)
                .orderByDesc(Membership::getEndAt)
                .last("LIMIT 1"));
        if (latest != null) {
            start = latest.getEndAt();
        }
        int days = card == null || card.getValidDays() == null ? 1 : card.getValidDays();
        LocalDateTime end = card != null && "NATURAL_DAY".equals(card.getDurationMode())
                ? start.toLocalDate().plusDays(days).atStartOfDay()
                : start.plusHours(24L * days);
        Membership membership = new Membership();
        membership.setId(snowflake.next());
        membership.setUserId(order.getUserId());
        membership.setStoreId(order.getStoreId());
        membership.setOrderId(order.getId());
        membership.setCardProductId(order.getProductId());
        membership.setSource(source);
        membership.setStartAt(start);
        membership.setEndAt(end);
        membership.setStatus("ACTIVE");
        membership.setCrossStore(card == null || card.getCrossStore() == null ? 0 : card.getCrossStore());
        membership.setCreatedAt(paidAt);
        return membership;
    }

    private Agreement currentAgreement() {
        return agreementMapper.selectOne(new LambdaQueryWrapper<Agreement>()
                .eq(Agreement::getStatus, "PUBLISHED")
                .orderByDesc(Agreement::getVersionNo)
                .last("LIMIT 1"));
    }

    private int expireMinutes() {
        AppConfig config = appConfigMapper.selectById("order.expire.minutes");
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return 15;
        }
        return Integer.parseInt(config.getConfigValue());
    }

    private void lock(long orderId) {
        if (tradeOrderMapper.lockById(orderId) == null) {
            throw BizException.badRequest("订单不存在");
        }
    }

    private TradeOrder mustOwn(long userId, long orderId) {
        TradeOrder order = reload(orderId);
        if (order == null || !Long.valueOf(userId).equals(order.getUserId())) {
            throw BizException.badRequest("订单不存在");
        }
        return order;
    }

    private TradeOrder reload(long orderId) {
        return tradeOrderMapper.selectById(orderId);
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

    private String openid(long userId) {
        GymUser user = gymUserMapper.selectById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            throw BizException.badRequest("请重新登录后再支付");
        }
        return user.getOpenid();
    }

    private OrderView toView(TradeOrder order) {
        Store store = storeMapper.selectById(order.getStoreId());
        return new OrderView(String.valueOf(order.getId()), order.getOrderNo(),
                store == null ? "" : store.getName(), order.getProductName(), order.getAmountFen(), order.getStatus(),
                order.getExpireAt(), order.getPaidAt());
    }

    public record Created(String orderId, String orderNo, long amountFen, String status, boolean mockPay,
                          String timeStamp, String nonceStr, String payPackage, String signType, String paySign) {
    }

    public record OrderView(String id, String orderNo, String storeName, String productName, long amountFen,
                            String status, LocalDateTime expireAt, LocalDateTime paidAt) {
    }

    public record AdminOrder(String id, String orderNo, String storeName, String productName, long amountFen,
                             String status, LocalDateTime paidAt) {
    }

    public record RefundResult(long refundFen) {
    }
}
