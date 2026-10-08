package com.gym.self.modules.store.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.geo.GeoDistance;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.store.domain.Store;
import com.gym.self.modules.store.domain.StoreGuide;
import com.gym.self.modules.store.domain.StoreGuideMapper;
import com.gym.self.modules.store.domain.StoreMapper;
import com.gym.self.modules.store.domain.StorePhone;
import com.gym.self.modules.store.domain.StorePhoneMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class StoreAdminService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<String> PHONE_TYPES = Set.of("DAY", "NIGHT", "LOGISTICS", "COMPLAINT");
    private static final Set<String> WINDOW_REQUIRED = Set.of("DAY", "NIGHT");

    private final StoreMapper storeMapper;
    private final StorePhoneMapper storePhoneMapper;
    private final StoreGuideMapper storeGuideMapper;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;

    public StoreAdminService(StoreMapper storeMapper, StorePhoneMapper storePhoneMapper,
                             StoreGuideMapper storeGuideMapper, Snowflake snowflake, TimeProvider timeProvider) {
        this.storeMapper = storeMapper;
        this.storePhoneMapper = storePhoneMapper;
        this.storeGuideMapper = storeGuideMapper;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
    }

    public String create(AdminPrincipal actor, String code, String name, String province, String city,
                         String address, BigDecimal longitude, BigDecimal latitude) {
        StoreScope.requireMaster(actor);
        LocalDateTime now = LocalDateTime.now();
        Store store = new Store();
        store.setId(snowflake.next());
        store.setCode(code.trim());
        store.setName(name.trim());
        store.setProvince(province.trim());
        store.setCity(city.trim());
        store.setAddress(address.trim());
        store.setLongitude(longitude);
        store.setLatitude(latitude);
        store.setBusinessHours("24h");
        store.setStatus("OPEN");
        store.setDeleted(0);
        store.setCreatedAt(now);
        store.setUpdatedAt(now);
        try {
            storeMapper.insert(store);
        } catch (DuplicateKeyException exception) {
            throw BizException.badRequest("门店编号已存在");
        }
        return String.valueOf(store.getId());
    }

    public List<StoreView> list(AdminPrincipal actor, Long requestedStoreId) {
        Long storeId = StoreScope.requiredStore(actor, requestedStoreId);
        LambdaQueryWrapper<Store> query = new LambdaQueryWrapper<Store>()
                .eq(Store::getDeleted, 0)
                .orderByAsc(Store::getId);
        if (storeId != null) {
            query.eq(Store::getId, storeId);
        }
        return storeMapper.selectList(query).stream().map(this::toView).toList();
    }

    public void update(AdminPrincipal actor, long storeId, StoreUpdate update) {
        StoreScope.requireMaster(actor);
        Store store = mustExist(storeId);
        if (!"OPEN".equals(update.status()) && !"CLOSED".equals(update.status())) {
            throw BizException.badRequest("营业状态不正确");
        }
        store.setName(update.name().trim());
        store.setProvince(update.province().trim());
        store.setCity(update.city().trim());
        store.setAddress(update.address().trim());
        store.setLongitude(update.longitude());
        store.setLatitude(update.latitude());
        store.setCoverUrl(blankToNull(update.coverUrl()));
        store.setBusinessHours(blankToNull(update.businessHours()) == null ? "24h" : update.businessHours().trim());
        store.setStatus(update.status());
        store.setWifiSsid(blankToNull(update.wifiSsid()));
        store.setWifiPassword(blankToNull(update.wifiPassword()));
        store.setUpdatedAt(LocalDateTime.now());
        storeMapper.updateById(store);
    }

    public List<PhoneView> phones(AdminPrincipal actor, long storeId) {
        visible(actor, storeId);
        return storePhoneMapper.selectList(new LambdaQueryWrapper<StorePhone>()
                        .eq(StorePhone::getStoreId, storeId)
                        .orderByAsc(StorePhone::getSortNo)
                        .orderByAsc(StorePhone::getId))
                .stream()
                .map(phone -> new PhoneView(phone.getPhoneType(), phone.getPhone(), format(phone.getTimeStart()),
                        format(phone.getTimeEnd()), phone.getSortNo()))
                .toList();
    }

    @Transactional
    public void replacePhones(AdminPrincipal actor, long storeId, List<PhoneInput> items) {
        writable(actor, storeId);
        List<StorePhone> rows = new ArrayList<>();
        int index = 0;
        for (PhoneInput item : items) {
            rows.add(parsePhone(storeId, item, index++));
        }
        storePhoneMapper.delete(new LambdaQueryWrapper<StorePhone>().eq(StorePhone::getStoreId, storeId));
        for (StorePhone row : rows) {
            storePhoneMapper.insert(row);
        }
    }

    public List<GuideView> guides(AdminPrincipal actor, long storeId) {
        visible(actor, storeId);
        return loadGuides(storeId);
    }

    @Transactional
    public void replaceGuides(AdminPrincipal actor, long storeId, List<GuideInput> items) {
        writable(actor, storeId);
        storeGuideMapper.delete(new LambdaQueryWrapper<StoreGuide>().eq(StoreGuide::getStoreId, storeId));
        LocalDateTime now = LocalDateTime.now();
        int index = 0;
        for (GuideInput item : items) {
            StoreGuide guide = new StoreGuide();
            guide.setId(snowflake.next());
            guide.setStoreId(storeId);
            guide.setImageUrl(item.imageUrl().trim());
            guide.setCaption(item.caption().trim());
            guide.setSortNo(item.sortNo() == null ? index : item.sortNo());
            guide.setCreatedAt(now);
            guide.setUpdatedAt(now);
            storeGuideMapper.insert(guide);
            index++;
        }
    }

    public List<PublicStore> publicList(String province, String city, BigDecimal longitude, BigDecimal latitude) {
        LambdaQueryWrapper<Store> query = new LambdaQueryWrapper<Store>()
                .eq(Store::getDeleted, 0)
                .orderByAsc(Store::getId);
        if (province != null && !province.isBlank()) {
            query.eq(Store::getProvince, province.trim());
        }
        if (city != null && !city.isBlank()) {
            query.eq(Store::getCity, city.trim());
        }
        List<PublicStore> rows = storeMapper.selectList(query).stream()
                .map(store -> toPublic(store, longitude, latitude))
                .toList();
        if (longitude != null && latitude != null) {
            rows = rows.stream()
                    .sorted(Comparator.comparing(PublicStore::distanceMeters, Comparator.nullsLast(Long::compareTo)))
                    .toList();
        }
        return rows;
    }

    public PublicStore publicDetail(long storeId, BigDecimal longitude, BigDecimal latitude) {
        return toPublic(mustExist(storeId), longitude, latitude);
    }

    public List<GuideView> publicGuides(long storeId) {
        mustExist(storeId);
        return loadGuides(storeId);
    }

    public ContactsView contacts(long storeId) {
        mustExist(storeId);
        List<StorePhone> phones = storePhoneMapper.selectList(new LambdaQueryWrapper<StorePhone>()
                .eq(StorePhone::getStoreId, storeId)
                .orderByAsc(StorePhone::getSortNo)
                .orderByAsc(StorePhone::getId));
        LocalTime now = timeProvider.localTime();
        boolean inNight = phones.stream().anyMatch(phone -> "NIGHT".equals(phone.getPhoneType())
                && PhoneWindow.contains(now, phone.getTimeStart(), phone.getTimeEnd()));
        List<String> nightPhones = numbers(phones, "NIGHT", now);
        List<String> dayPhones = numbers(phones, "DAY", now);
        List<String> servicePhones;
        boolean nightAvailable;
        String nightHint;
        String shift;
        if (inNight && !nightPhones.isEmpty()) {
            shift = "NIGHT";
            nightAvailable = true;
            nightHint = null;
            servicePhones = nightPhones;
        } else if (inNight) {
            shift = "NIGHT";
            nightAvailable = false;
            nightHint = "夜班电话未配置";
            servicePhones = dayPhones;
        } else {
            shift = "DAY";
            nightAvailable = false;
            nightHint = "不在夜班时间，请联系白班客服";
            servicePhones = dayPhones;
        }
        List<String> complaintPhones = numbers(phones, "COMPLAINT", now);
        boolean complaintConfigured = phones.stream().anyMatch(phone -> "COMPLAINT".equals(phone.getPhoneType()));
        String complaintHint = complaintConfigured && complaintPhones.isEmpty() ? "不在投诉受理时间" : null;
        return new ContactsView(shift, nightAvailable, nightHint, servicePhones,
                numbers(phones, "LOGISTICS", now), complaintPhones, complaintHint);
    }

    private List<GuideView> loadGuides(long storeId) {
        return storeGuideMapper.selectList(new LambdaQueryWrapper<StoreGuide>()
                        .eq(StoreGuide::getStoreId, storeId)
                        .orderByAsc(StoreGuide::getSortNo)
                        .orderByAsc(StoreGuide::getId))
                .stream()
                .map(guide -> new GuideView(String.valueOf(guide.getId()), guide.getImageUrl(), guide.getCaption(), guide.getSortNo()))
                .toList();
    }

    private List<String> numbers(List<StorePhone> phones, String type, LocalTime now) {
        return phones.stream()
                .filter(phone -> type.equals(phone.getPhoneType()))
                .filter(phone -> PhoneWindow.contains(now, phone.getTimeStart(), phone.getTimeEnd()))
                .map(StorePhone::getPhone)
                .toList();
    }

    private StorePhone parsePhone(long storeId, PhoneInput item, int index) {
        if (!PHONE_TYPES.contains(item.phoneType())) {
            throw BizException.badRequest("电话类型不正确");
        }
        LocalTime start = parseTime(item.timeStart());
        LocalTime end = parseTime(item.timeEnd());
        if (WINDOW_REQUIRED.contains(item.phoneType()) && (start == null || end == null)) {
            throw BizException.badRequest("白班和夜班必须填写时段");
        }
        if ((start == null) != (end == null)) {
            throw BizException.badRequest("时段需要同时填写开始和结束");
        }
        if (start != null && start.equals(end)) {
            throw BizException.badRequest("开始和结束不能相同");
        }
        StorePhone phone = new StorePhone();
        phone.setId(snowflake.next());
        phone.setStoreId(storeId);
        phone.setPhoneType(item.phoneType());
        phone.setPhone(item.phone().trim());
        phone.setTimeStart(start);
        phone.setTimeEnd(end);
        phone.setSortNo(item.sortNo() == null ? index : item.sortNo());
        return phone;
    }

    private Store visible(AdminPrincipal actor, long storeId) {
        if (!actor.master() && !Long.valueOf(storeId).equals(actor.storeId())) {
            throw BizException.forbidden("不能查看其他门店");
        }
        return mustExist(storeId);
    }

    private Store writable(AdminPrincipal actor, long storeId) {
        if (!actor.master() && !Long.valueOf(storeId).equals(actor.storeId())) {
            throw BizException.forbidden("不能修改其他门店");
        }
        return mustExist(storeId);
    }

    private Store mustExist(long storeId) {
        Store store = storeMapper.selectById(storeId);
        if (store == null || Integer.valueOf(1).equals(store.getDeleted())) {
            throw BizException.badRequest("门店不存在");
        }
        return store;
    }

    private StoreView toView(Store store) {
        return new StoreView(String.valueOf(store.getId()), store.getCode(), store.getName(), store.getProvince(),
                store.getCity(), store.getAddress(), store.getLongitude(), store.getLatitude(), store.getCoverUrl(),
                store.getBusinessHours(), store.getStatus(), store.getWifiSsid(), store.getWifiPassword());
    }

    private PublicStore toPublic(Store store, BigDecimal longitude, BigDecimal latitude) {
        return new PublicStore(String.valueOf(store.getId()), store.getName(), store.getProvince(), store.getCity(),
                store.getAddress(), store.getLongitude(), store.getLatitude(), store.getCoverUrl(),
                store.getBusinessHours(), store.getStatus(),
                GeoDistance.meters(latitude, longitude, store.getLatitude(), store.getLongitude()),
                true, "开通会员后查看");
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), HM);
        } catch (DateTimeParseException exception) {
            throw BizException.badRequest("时段格式应为 HH:mm");
        }
    }

    private static String format(LocalTime time) {
        return time == null ? null : time.format(HM);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record StoreUpdate(String name, String province, String city, String address, BigDecimal longitude,
                              BigDecimal latitude, String coverUrl, String businessHours, String status,
                              String wifiSsid, String wifiPassword) {
    }

    public record StoreView(String id, String code, String name, String province, String city, String address,
                            BigDecimal longitude, BigDecimal latitude, String coverUrl, String businessHours,
                            String status, String wifiSsid, String wifiPassword) {
    }

    public record PublicStore(String id, String name, String province, String city, String address,
                              BigDecimal longitude, BigDecimal latitude, String coverUrl, String businessHours,
                              String status, Long distanceMeters, boolean onlineLocked, String onlineText) {
    }

    public record PhoneInput(String phoneType, String phone, String timeStart, String timeEnd, Integer sortNo) {
    }

    public record PhoneView(String phoneType, String phone, String timeStart, String timeEnd, Integer sortNo) {
    }

    public record GuideInput(String imageUrl, String caption, Integer sortNo) {
    }

    public record GuideView(String id, String imageUrl, String caption, Integer sortNo) {
    }

    public record ContactsView(String shift, boolean nightAvailable, String nightHint, List<String> servicePhones,
                               List<String> logisticsPhones, List<String> complaintPhones, String complaintHint) {
    }
}
