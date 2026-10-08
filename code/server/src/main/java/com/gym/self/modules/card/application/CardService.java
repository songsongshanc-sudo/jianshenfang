package com.gym.self.modules.card.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.card.MembershipProgress;
import com.gym.self.modules.card.domain.CardProduct;
import com.gym.self.modules.card.domain.CardProductMapper;
import com.gym.self.modules.card.domain.Membership;
import com.gym.self.modules.card.domain.MembershipMapper;
import com.gym.self.modules.store.domain.Store;
import com.gym.self.modules.store.domain.StoreMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class CardService {

    private static final Set<String> DURATION_MODES = Set.of("HOURS_24", "NATURAL_DAY");
    private static final Set<String> UNLOCK_TYPES = Set.of("NONE", "CUMULATIVE_DAYS", "CONSECUTIVE_DAYS");

    private final CardProductMapper cardProductMapper;
    private final MembershipMapper membershipMapper;
    private final StoreMapper storeMapper;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;

    public CardService(CardProductMapper cardProductMapper, MembershipMapper membershipMapper, StoreMapper storeMapper,
                       Snowflake snowflake, TimeProvider timeProvider) {
        this.cardProductMapper = cardProductMapper;
        this.membershipMapper = membershipMapper;
        this.storeMapper = storeMapper;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
    }

    public String create(AdminPrincipal actor, CardInput input) {
        long storeId = scopedStore(actor, input.storeId());
        validate(input);
        mustStore(storeId);
        LocalDateTime now = timeProvider.now();
        CardProduct card = new CardProduct();
        card.setId(snowflake.next());
        card.setStoreId(storeId);
        fill(card, input);
        card.setStockSold(0);
        card.setStockReserved(0);
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        card.setDeleted(0);
        cardProductMapper.insert(card);
        return String.valueOf(card.getId());
    }

    public void update(AdminPrincipal actor, long id, CardInput input) {
        CardProduct card = mustCard(id);
        scopedStore(actor, card.getStoreId());
        validate(input);
        fill(card, input);
        card.setUpdatedAt(timeProvider.now());
        cardProductMapper.updateById(card);
    }

    public List<AdminCard> adminList(AdminPrincipal actor, Long storeId) {
        Long scoped = StoreScope.requiredStore(actor, storeId);
        LambdaQueryWrapper<CardProduct> query = new LambdaQueryWrapper<CardProduct>()
                .eq(CardProduct::getDeleted, 0)
                .orderByAsc(CardProduct::getSortNo)
                .orderByAsc(CardProduct::getId);
        if (scoped != null) {
            query.eq(CardProduct::getStoreId, scoped);
        }
        return cardProductMapper.selectList(query).stream().map(this::toAdmin).toList();
    }

    public List<MpCard> mpList(Long storeId, String placement, Long userId) {
        if (storeId == null || storeMapper.selectById(storeId) == null) {
            throw BizException.badRequest("门店不存在");
        }
        boolean home = "HOME".equalsIgnoreCase(placement);
        LambdaQueryWrapper<CardProduct> query = new LambdaQueryWrapper<CardProduct>()
                .eq(CardProduct::getStoreId, storeId)
                .eq(CardProduct::getStatus, "ON")
                .eq(CardProduct::getDeleted, 0)
                .orderByAsc(CardProduct::getSortNo)
                .orderByAsc(CardProduct::getId);
        if (home) {
            query.eq(CardProduct::getHomeVisible, 1);
        }
        MembershipProgress.Result progress = userId == null
                ? new MembershipProgress.Result(0, 0, false)
                : days(userId, storeId);
        return cardProductMapper.selectList(query).stream().map(card -> toMp(card, progress)).toList();
    }

    public ProgressView progress(Long userId, Long storeId) {
        if (userId == null) {
            return new ProgressView(0, 0, false, consecutiveRemain(storeId, 0));
        }
        MembershipProgress.Result result = days(userId, storeId);
        return new ProgressView(result.cumulativeDays(), result.consecutiveDays(), result.memberToday(),
                consecutiveRemain(storeId, result.consecutiveDays()));
    }

    private MembershipProgress.Result days(long userId, Long storeId) {
        List<MembershipProgress.Span> spans = membershipMapper.selectList(new LambdaQueryWrapper<Membership>()
                        .eq(Membership::getUserId, userId)
                        .ne(Membership::getStatus, "REVOKED"))
                .stream()
                .map(item -> new MembershipProgress.Span(item.getStoreId(), item.getStartAt(), item.getEndAt()))
                .toList();
        return MembershipProgress.of(spans, timeProvider.today(), storeId);
    }

    private Integer consecutiveRemain(Long storeId, int consecutiveDays) {
        if (storeId == null) {
            return null;
        }
        CardProduct card = cardProductMapper.selectList(new LambdaQueryWrapper<CardProduct>()
                        .eq(CardProduct::getStoreId, storeId)
                        .eq(CardProduct::getStatus, "ON")
                        .eq(CardProduct::getDeleted, 0)
                        .eq(CardProduct::getUnlockType, "CONSECUTIVE_DAYS")
                        .orderByAsc(CardProduct::getSortNo)
                        .orderByAsc(CardProduct::getId))
                .stream()
                .findFirst()
                .orElse(null);
        if (card == null || card.getUnlockDays() == null) {
            return null;
        }
        return Math.max(0, card.getUnlockDays() - consecutiveDays);
    }

    public String blockReason(long userId, CardProduct card) {
        if (card.getDeleted() != null && card.getDeleted() == 1 || !"ON".equals(card.getStatus())) {
            return "卡种已下架";
        }
        MpCard view = toMp(card, days(userId, card.getStoreId()));
        if (view.purchasable()) {
            return null;
        }
        return view.lockText() == null ? "暂时不能购买" : view.lockText();
    }

    private MpCard toMp(CardProduct card, MembershipProgress.Result progress) {
        Integer remaining = remaining(card);
        int owned = switch (card.getUnlockType()) {
            case "CUMULATIVE_DAYS" -> progress.cumulativeDays();
            case "CONSECUTIVE_DAYS" -> progress.consecutiveDays();
            default -> Integer.MAX_VALUE;
        };
        int need = card.getUnlockDays() == null ? 0 : card.getUnlockDays();
        boolean unlocked = "NONE".equals(card.getUnlockType()) || owned >= need;
        String lockText = null;
        if (!unlocked && "CUMULATIVE_DAYS".equals(card.getUnlockType())) {
            lockText = "累计满 " + need + " 天后激活此卡";
        } else if (!unlocked) {
            lockText = "连续满 " + need + " 天后激活此卡";
        } else if (remaining != null && remaining <= 0) {
            lockText = "已售罄";
        }
        boolean purchasable = lockText == null;
        return new MpCard(String.valueOf(card.getId()), card.getName(), card.getPriceFen(), card.getDisplayText(),
                card.getValidDays(), card.getDurationMode(), card.getUnlockType(), card.getUnlockDays(),
                flag(card.getCrossStore()), remaining, purchasable, lockText);
    }

    private AdminCard toAdmin(CardProduct card) {
        return new AdminCard(String.valueOf(card.getId()), String.valueOf(card.getStoreId()), card.getName(),
                card.getPriceFen(), card.getDisplayText(), card.getValidDays(), card.getDurationMode(),
                card.getStockTotal(), sold(card.getStockSold()), sold(card.getStockReserved()), remaining(card),
                card.getUnlockType(), card.getUnlockDays(), flag(card.getCrossStore()), flag(card.getHomeVisible()),
                card.getSortNo(), card.getStatus());
    }

    private static Integer remaining(CardProduct card) {
        if (card.getStockTotal() == null) {
            return null;
        }
        int sold = card.getStockSold() == null ? 0 : card.getStockSold();
        int reserved = card.getStockReserved() == null ? 0 : card.getStockReserved();
        return card.getStockTotal() - sold - reserved;
    }

    private void fill(CardProduct card, CardInput input) {
        card.setName(input.name().trim());
        card.setPriceFen(input.priceFen());
        card.setDisplayText(blankToNull(input.displayText()));
        card.setValidDays(input.validDays());
        card.setDurationMode(input.durationMode());
        card.setStockTotal(input.stockTotal());
        card.setUnlockType(input.unlockType());
        card.setUnlockDays("NONE".equals(input.unlockType()) ? null : input.unlockDays());
        card.setCrossStore(input.crossStore());
        card.setHomeVisible(input.homeVisible());
        card.setSortNo(input.sortNo());
        card.setStatus(input.status());
    }

    private void validate(CardInput input) {
        if (input.priceFen() == null || input.priceFen() < 1) {
            throw BizException.badRequest("价格不正确");
        }
        if (input.validDays() == null || input.validDays() < 1) {
            throw BizException.badRequest("有效天数不正确");
        }
        if (!DURATION_MODES.contains(input.durationMode())) {
            throw BizException.badRequest("有效期算法不正确");
        }
        if (!UNLOCK_TYPES.contains(input.unlockType())) {
            throw BizException.badRequest("解锁条件不正确");
        }
        if (!"NONE".equals(input.unlockType()) && (input.unlockDays() == null || input.unlockDays() < 1)) {
            throw BizException.badRequest("请填写解锁天数");
        }
        if (input.stockTotal() != null && input.stockTotal() < 0) {
            throw BizException.badRequest("库存不正确");
        }
        if (input.crossStore() == null || (input.crossStore() != 0 && input.crossStore() != 1)) {
            throw BizException.badRequest("通卡设置不正确");
        }
        if (input.homeVisible() == null || (input.homeVisible() != 0 && input.homeVisible() != 1)) {
            throw BizException.badRequest("首页展示设置不正确");
        }
        if (!"ON".equals(input.status()) && !"OFF".equals(input.status())) {
            throw BizException.badRequest("上下架状态不正确");
        }
    }

    private long scopedStore(AdminPrincipal actor, Long storeId) {
        if (actor.master()) {
            if (storeId == null) {
                throw BizException.badRequest("门店不存在");
            }
            return storeId;
        }
        if (storeId != null && !storeId.equals(actor.storeId())) {
            throw BizException.forbidden("不能修改其他门店");
        }
        if (actor.storeId() == null) {
            throw BizException.forbidden("不能修改其他门店");
        }
        return actor.storeId();
    }

    private Store mustStore(long storeId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null || (store.getDeleted() != null && store.getDeleted() == 1)) {
            throw BizException.badRequest("门店不存在");
        }
        return store;
    }

    private CardProduct mustCard(long id) {
        CardProduct card = cardProductMapper.selectById(id);
        if (card == null || (card.getDeleted() != null && card.getDeleted() == 1)) {
            throw BizException.badRequest("卡种不存在");
        }
        return card;
    }

    private static int sold(Integer value) {
        return value == null ? 0 : value;
    }

    private static int flag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CardInput(Long storeId, String name, Long priceFen, String displayText, Integer validDays,
                            String durationMode, Integer stockTotal, String unlockType, Integer unlockDays,
                            Integer crossStore, Integer homeVisible, Integer sortNo, String status) {
    }

    public record AdminCard(String id, String storeId, String name, long priceFen, String displayText, int validDays,
                            String durationMode, Integer stockTotal, int stockSold, int stockReserved, Integer remaining,
                            String unlockType, Integer unlockDays, int crossStore, int homeVisible, int sortNo,
                            String status) {
    }

    public record MpCard(String id, String name, long priceFen, String displayText, int validDays, String durationMode,
                         String unlockType, Integer unlockDays, int crossStore, Integer remaining, boolean purchasable,
                         String lockText) {
    }

    public record ProgressView(int cumulativeDays, int consecutiveDays, boolean storeMember, Integer consecutiveRemain) {
    }
}
