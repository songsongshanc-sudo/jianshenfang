package com.gym.self.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.application.AdminOpLog;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.card.domain.CardProduct;
import com.gym.self.modules.card.domain.CardProductMapper;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.gate.GateDevice;
import com.gym.self.modules.gate.GateDeviceMapper;
import com.gym.self.modules.gate.WsGateCommandPort;
import com.gym.self.modules.order.domain.Payment;
import com.gym.self.modules.order.domain.PaymentMapper;
import com.gym.self.modules.order.domain.TradeOrder;
import com.gym.self.modules.order.domain.TradeOrderMapper;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.gym.self.modules.user.domain.UserFace;
import com.gym.self.modules.user.domain.UserFaceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GateMemberImportService {

    private static final int MAX_PEOPLE = 500;
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final LocalDateTime FOREVER = LocalDateTime.of(2099, 12, 31, 23, 59);

    private final WsGateCommandPort gatePort;
    private final GateDeviceMapper gateDeviceMapper;
    private final CardProductMapper cardProductMapper;
    private final GymUserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final MembershipMapper membershipMapper;
    private final TradeOrderMapper tradeOrderMapper;
    private final PaymentMapper paymentMapper;
    private final FaceFileService faceFileService;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;
    private final AdminOpLog adminOpLog;
    private final TransactionTemplate transactions;
    private final AdminMemberService adminMemberService;

    public GateMemberImportService(WsGateCommandPort gatePort, GateDeviceMapper gateDeviceMapper,
                                   CardProductMapper cardProductMapper, GymUserMapper userMapper,
                                   UserFaceMapper userFaceMapper, MembershipMapper membershipMapper,
                                   TradeOrderMapper tradeOrderMapper, PaymentMapper paymentMapper,
                                   FaceFileService faceFileService, Snowflake snowflake, TimeProvider timeProvider,
                                   AdminOpLog adminOpLog, PlatformTransactionManager transactionManager,
                                   AdminMemberService adminMemberService) {
        this.gatePort = gatePort;
        this.gateDeviceMapper = gateDeviceMapper;
        this.cardProductMapper = cardProductMapper;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.membershipMapper = membershipMapper;
        this.tradeOrderMapper = tradeOrderMapper;
        this.paymentMapper = paymentMapper;
        this.faceFileService = faceFileService;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.adminOpLog = adminOpLog;
        this.transactions = new TransactionTemplate(transactionManager);
        this.adminMemberService = adminMemberService;
    }

    public Map<String, Object> importFromGate(AdminPrincipal actor, long deviceId) {
        GateDevice device = gateDeviceMapper.selectById(deviceId);
        if (device == null || !"ENABLED".equals(device.getStatus())) {
            throw BizException.badRequest("闸机不存在或已停用");
        }
        StoreScope.requireStoreAccess(actor, device.getStoreId());
        if (!gatePort.online(device.getDeviceSn())) {
            throw BizException.rejected("闸机不在线");
        }
        CardProduct card = cardProductMapper.selectOne(new LambdaQueryWrapper<CardProduct>()
                .eq(CardProduct::getStoreId, device.getStoreId())
                .eq(CardProduct::getStatus, "ON")
                .orderByAsc(CardProduct::getId)
                .last("LIMIT 1"));
        if (card == null) {
            throw BizException.rejected("请先在这家门店上架一个卡种，导入的到期时间要挂在这张卡上");
        }
        List<String> ids = listUserIds(device.getDeviceSn());
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        List<String> notes = new ArrayList<>();
        for (String rawId : ids) {
            String memberNo = rawId == null ? "" : rawId.trim();
            if (memberNo.isEmpty() || "F1000001".equals(memberNo)) {
                continue;
            }
            if (memberNo.regionMatches(true, 0, "DL", 0, 2)) {
                skipped++;
                note(notes, memberNo + " 是闸机本机录入，未导入");
                continue;
            }
            if (memberNo.length() > 32) {
                skipped++;
                note(notes, memberNo.substring(0, 8) + "… 编号过长，未导入");
                continue;
            }
            try {
                String outcome = transactions.execute(status -> pullOne(device, card, memberNo));
                if ("imported".equals(outcome)) {
                    imported++;
                } else if ("updated".equals(outcome)) {
                    updated++;
                } else {
                    skipped++;
                    if (outcome != null && !"skipped".equals(outcome)) {
                        note(notes, outcome);
                    }
                }
            } catch (RuntimeException exception) {
                skipped++;
                note(notes, memberNo + " 导入失败");
            }
        }
        adminOpLog.record(actor, device.getStoreId(), null, device.getId(), "导入旧会员",
                "闸机 " + device.getName() + "（" + device.getDeviceSn() + "）新增 " + imported
                        + " 人，补全 " + updated + " 人，跳过 " + skipped + " 人。未重新下发人脸");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("notes", notes);
        return result;
    }

    private String pullOne(GateDevice device, CardProduct card, String memberNo) {
        JsonNode detail = gatePort.exchange(device.getDeviceSn(), Map.of(
                "cmd", "getUserDetails",
                "user_id", memberNo,
                "value", 1), 20);
        if (detail == null || detail.path("code").asInt(-1) != 0) {
            return memberNo + " 闸机没有返回资料";
        }
        if (detail.path("user_type").asInt(0) == 10) {
            return memberNo + " 是黑名单，未导入";
        }
        byte[] photo = decodePhoto(text(detail, "face_template"));
        if (photo == null) {
            return memberNo + " 没有照片，未导入";
        }
        LocalDateTime end = parseValid(text(detail, "id_valid"));
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getMemberNo, memberNo));
        boolean created = false;
        if (user == null) {
            user = createUser(device.getStoreId(), memberNo, text(detail, "name"), text(detail, "phone"));
            created = true;
        }
        boolean faceAdded = ensureFace(user, photo);
        boolean cardAdded = ensureCard(user.getId(), device.getStoreId(), card, end);
        if (created) {
            return "imported";
        }
        if (faceAdded || cardAdded) {
            return "updated";
        }
        return "skipped";
    }

    private GymUser createUser(long storeId, String memberNo, String name, String phone) {
        LocalDateTime now = timeProvider.now();
        GymUser user = new GymUser();
        user.setId(snowflake.next());
        user.setNickname(clipName(name));
        user.setPhone(freePhone(phone));
        user.setMemberNo(memberNo);
        user.setRegisterStatus("ACTIVE");
        user.setRegisteredAt(now);
        user.setStatus("ACTIVE");
        user.setCreatedStoreId(storeId);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return user;
    }

    private boolean ensureFace(GymUser user, byte[] photo) {
        Long existing = userFaceMapper.selectCount(new LambdaQueryWrapper<UserFace>()
                .eq(UserFace::getUserId, user.getId())
                .eq(UserFace::getStatus, "ENROLLED"));
        if (existing != null && existing > 0) {
            return false;
        }
        String objectKey = faceFileService.presign(user.getId(), "image/jpeg");
        faceFileService.save(user.getId(), objectKey, photo);
        UserFace face = new UserFace();
        face.setId(snowflake.next());
        face.setUserId(user.getId());
        face.setObjectKey(objectKey);
        face.setStatus("ENROLLED");
        face.setVendorFaceId(user.getMemberNo());
        face.setCreatedAt(timeProvider.now());
        userFaceMapper.insert(face);
        return true;
    }

    private boolean ensureCard(long userId, long storeId, CardProduct card, LocalDateTime end) {
        Membership covered = membershipMapper.selectOne(new LambdaQueryWrapper<Membership>()
                .eq(Membership::getUserId, userId)
                .eq(Membership::getStoreId, storeId)
                .ne(Membership::getStatus, "REVOKED")
                .ge(Membership::getEndAt, end)
                .last("LIMIT 1"));
        if (covered != null) {
            return false;
        }
        LocalDateTime now = timeProvider.now();
        TradeOrder order = new TradeOrder();
        order.setId(snowflake.next());
        order.setOrderNo(String.valueOf(order.getId()));
        order.setUserId(userId);
        order.setStoreId(storeId);
        order.setBizType("CARD");
        order.setProductId(card.getId());
        order.setProductName("闸机原有会员");
        order.setAmountFen(0L);
        order.setStatus("PAID");
        order.setPaidAt(now);
        order.setExpireAt(now);
        order.setIdempotencyKey("gate-import-" + order.getId());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        tradeOrderMapper.insert(order);
        Payment payment = new Payment();
        payment.setId(snowflake.next());
        payment.setOrderId(order.getId());
        payment.setMchId("gate-import");
        payment.setAmountFen(0L);
        payment.setStatus("SUCCESS");
        payment.setTransactionId("gate-import-" + order.getId());
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);
        paymentMapper.insert(payment);
        Membership membership = new Membership();
        membership.setId(snowflake.next());
        membership.setUserId(userId);
        membership.setStoreId(storeId);
        membership.setOrderId(order.getId());
        membership.setCardProductId(card.getId());
        membership.setSource("GATE_IMPORT");
        membership.setStartAt(now);
        membership.setEndAt(end);
        membership.setStatus("ACTIVE");
        membership.setCrossStore(0);
        membership.setCreatedAt(now);
        membershipMapper.insert(membership);
        return true;
    }

    private List<String> listUserIds(String sn) {
        Set<String> ids = new LinkedHashSet<>();
        for (int page = 0; page < 100; page++) {
            JsonNode reply = gatePort.exchange(sn, Map.of("cmd", "getUserInfo", "value", page), 15);
            if (reply == null) {
                throw BizException.rejected(page == 0 ? "闸机没有返回人员名单" : "人员名单没有读完");
            }
            if (reply.path("code").asInt(-1) != 0) {
                if (page == 0) {
                    String msg = reply.path("msg").asText("");
                    throw BizException.rejected(msg.isBlank() ? "闸机拒绝读取人员" : msg);
                }
                break;
            }
            int before = ids.size();
            collectIds(reply, ids);
            if (ids.size() == before) {
                break;
            }
            int total = reply.path("total").asInt(ids.size());
            if (ids.size() >= total || ids.size() >= MAX_PEOPLE) {
                break;
            }
        }
        if (ids.size() > MAX_PEOPLE) {
            throw BizException.rejected("这台闸机人员超过 " + MAX_PEOPLE + "，请先减少后再导入");
        }
        return List.copyOf(ids);
    }

    private static void collectIds(JsonNode reply, Set<String> ids) {
        for (String field : List.of("user_ids", "userIds", "ids")) {
            JsonNode node = reply.get(field);
            if (node != null && node.isArray()) {
                node.forEach(item -> ids.add(item.asText("").trim()));
            }
        }
    }

    private String freePhone(String phone) {
        if (phone == null || !phone.matches("1\\d{10}")) {
            return null;
        }
        Long taken = userMapper.selectCount(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, phone));
        return taken != null && taken > 0 ? null : phone;
    }

    private static String clipName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() || "微信用户".equals(value)) {
            return "";
        }
        return value.length() <= 32 ? value : value.substring(0, 32);
    }

    private static LocalDateTime parseValid(String text) {
        if (text == null || text.isBlank()) {
            return FOREVER;
        }
        String value = text.trim();
        try {
            if (value.length() >= 16) {
                return LocalDateTime.parse(value.substring(0, 16), MINUTE);
            }
            return LocalDate.parse(value.substring(0, 10), DAY).atTime(23, 59);
        } catch (DateTimeParseException exception) {
            return FOREVER;
        }
    }

    static byte[] decodePhoto(String raw) {
        if (raw == null || raw.isBlank() || raw.startsWith("http://") || raw.startsWith("https://")) {
            return null;
        }
        String text = raw.trim();
        try {
            text = URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // 本来就不是 URL 编码
        }
        int comma = text.indexOf(',');
        if (text.startsWith("data:") && comma > 0) {
            text = text.substring(comma + 1);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            return bytes.length == 0 || bytes.length > 5 * 1024 * 1024 ? null : bytes;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static void note(List<String> notes, String line) {
        if (notes.size() < 20) {
            notes.add(line);
        }
    }
}
