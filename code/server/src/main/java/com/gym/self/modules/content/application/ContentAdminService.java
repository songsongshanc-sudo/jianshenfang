package com.gym.self.modules.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;
import com.gym.self.modules.content.domain.Agreement;
import com.gym.self.modules.content.domain.AgreementMapper;
import com.gym.self.modules.content.domain.AppConfig;
import com.gym.self.modules.content.domain.AppConfigMapper;
import com.gym.self.modules.content.domain.Banner;
import com.gym.self.modules.content.domain.BannerMapper;
import com.gym.self.modules.content.domain.Notice;
import com.gym.self.modules.content.domain.NoticeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContentAdminService {

    private final BannerMapper bannerMapper;
    private final NoticeMapper noticeMapper;
    private final AgreementMapper agreementMapper;
    private final AppConfigMapper appConfigMapper;
    private final Snowflake snowflake;

    public ContentAdminService(BannerMapper bannerMapper, NoticeMapper noticeMapper, AgreementMapper agreementMapper,
                               AppConfigMapper appConfigMapper, Snowflake snowflake) {
        this.bannerMapper = bannerMapper;
        this.noticeMapper = noticeMapper;
        this.agreementMapper = agreementMapper;
        this.appConfigMapper = appConfigMapper;
        this.snowflake = snowflake;
    }

    public List<BannerView> adminBanners(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        return banners(false);
    }

    public List<BannerView> banners(boolean onlyOn) {
        LambdaQueryWrapper<Banner> query = new LambdaQueryWrapper<Banner>()
                .eq(Banner::getDeleted, 0)
                .orderByAsc(Banner::getSortNo)
                .orderByAsc(Banner::getId);
        if (onlyOn) {
            query.eq(Banner::getStatus, "ON");
        }
        return bannerMapper.selectList(query).stream().map(this::toBanner).toList();
    }

    public String createBanner(AdminPrincipal actor, String title, String imageUrl, String linkUrl, Integer sortNo, String status) {
        StoreScope.requireMaster(actor);
        LocalDateTime now = LocalDateTime.now();
        Banner banner = new Banner();
        banner.setId(snowflake.next());
        banner.setTitle(title.trim());
        banner.setImageUrl(imageUrl.trim());
        banner.setLinkUrl(blankToNull(linkUrl));
        banner.setSortNo(sortNo == null ? 0 : sortNo);
        banner.setStatus(normalizeStatus(status));
        banner.setDeleted(0);
        banner.setCreatedAt(now);
        banner.setUpdatedAt(now);
        bannerMapper.insert(banner);
        return String.valueOf(banner.getId());
    }

    public void updateBanner(AdminPrincipal actor, long id, String title, String imageUrl, String linkUrl, Integer sortNo, String status) {
        StoreScope.requireMaster(actor);
        Banner banner = mustBanner(id);
        banner.setTitle(title.trim());
        banner.setImageUrl(imageUrl.trim());
        banner.setLinkUrl(blankToNull(linkUrl));
        banner.setSortNo(sortNo == null ? 0 : sortNo);
        banner.setStatus(normalizeStatus(status));
        banner.setUpdatedAt(LocalDateTime.now());
        bannerMapper.updateById(banner);
    }

    public void deleteBanner(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        Banner banner = mustBanner(id);
        banner.setDeleted(1);
        banner.setUpdatedAt(LocalDateTime.now());
        bannerMapper.updateById(banner);
    }

    public List<NoticeView> adminNotices(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        return notices(false);
    }

    public List<NoticeView> notices(boolean onlyOn) {
        LambdaQueryWrapper<Notice> query = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getDeleted, 0)
                .orderByAsc(Notice::getSortNo)
                .orderByAsc(Notice::getId);
        if (onlyOn) {
            query.eq(Notice::getStatus, "ON");
        }
        return noticeMapper.selectList(query).stream()
                .map(notice -> new NoticeView(String.valueOf(notice.getId()), notice.getContent(), notice.getStatus(), notice.getSortNo()))
                .toList();
    }

    public String createNotice(AdminPrincipal actor, String content, Integer sortNo, String status) {
        StoreScope.requireMaster(actor);
        LocalDateTime now = LocalDateTime.now();
        Notice notice = new Notice();
        notice.setId(snowflake.next());
        notice.setContent(content.trim());
        notice.setSortNo(sortNo == null ? 0 : sortNo);
        notice.setStatus(normalizeStatus(status));
        notice.setDeleted(0);
        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        noticeMapper.insert(notice);
        return String.valueOf(notice.getId());
    }

    public void updateNotice(AdminPrincipal actor, long id, String content, Integer sortNo, String status) {
        StoreScope.requireMaster(actor);
        Notice notice = mustNotice(id);
        notice.setContent(content.trim());
        notice.setSortNo(sortNo == null ? 0 : sortNo);
        notice.setStatus(normalizeStatus(status));
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    public void deleteNotice(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        Notice notice = mustNotice(id);
        notice.setDeleted(1);
        notice.setUpdatedAt(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    public List<AgreementView> agreements(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        return agreementMapper.selectList(new LambdaQueryWrapper<Agreement>().orderByDesc(Agreement::getVersionNo))
                .stream()
                .map(this::toAgreement)
                .toList();
    }

    public AgreementView updateDraft(AdminPrincipal actor, long id, String title, String content) {
        StoreScope.requireMaster(actor);
        Agreement agreement = mustAgreement(id);
        if (!"DRAFT".equals(agreement.getStatus())) {
            throw BizException.badRequest("已发布的协议不能修改");
        }
        agreement.setTitle(title.trim());
        agreement.setContent(content);
        agreement.setUpdatedAt(LocalDateTime.now());
        agreementMapper.updateById(agreement);
        return toAgreement(agreement);
    }

    public AgreementView publish(AdminPrincipal actor, long id) {
        StoreScope.requireMaster(actor);
        Agreement agreement = mustAgreement(id);
        if (!"DRAFT".equals(agreement.getStatus())) {
            throw BizException.badRequest("只有草稿可以发布");
        }
        if (agreement.getContent() == null || agreement.getContent().isBlank()) {
            throw BizException.badRequest("协议内容不能为空");
        }
        agreement.setStatus("PUBLISHED");
        agreement.setUpdatedAt(LocalDateTime.now());
        agreementMapper.updateById(agreement);
        return toAgreement(agreement);
    }

    public AgreementView createDraft(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        Long drafts = agreementMapper.selectCount(new LambdaQueryWrapper<Agreement>().eq(Agreement::getStatus, "DRAFT"));
        if (drafts != null && drafts > 0) {
            throw BizException.badRequest("已有未发布草稿");
        }
        Integer maxVersion = agreementMapper.selectList(new LambdaQueryWrapper<Agreement>().orderByDesc(Agreement::getVersionNo))
                .stream()
                .map(Agreement::getVersionNo)
                .findFirst()
                .orElse(0);
        LocalDateTime now = LocalDateTime.now();
        Agreement agreement = new Agreement();
        agreement.setId(snowflake.next());
        agreement.setTitle("会员协议");
        agreement.setVersionNo(maxVersion + 1);
        agreement.setContent("");
        agreement.setStatus("DRAFT");
        agreement.setCreatedAt(now);
        agreement.setUpdatedAt(now);
        agreementMapper.insert(agreement);
        return toAgreement(agreement);
    }

    public AgreementView currentAgreement() {
        List<Agreement> published = agreementMapper.selectList(new LambdaQueryWrapper<Agreement>()
                .eq(Agreement::getStatus, "PUBLISHED")
                .orderByDesc(Agreement::getVersionNo)
                .last("LIMIT 1"));
        if (published.isEmpty()) {
            return null;
        }
        return toAgreement(published.get(0));
    }

    public ConfigView config(AdminPrincipal actor) {
        StoreScope.requireMaster(actor);
        return readConfig();
    }

    public ConfigView updateConfig(AdminPrincipal actor, int entryDebounceSeconds, int onlineWindowMinutes, int orderExpireMinutes) {
        StoreScope.requireMaster(actor);
        requireRange(entryDebounceSeconds, 1, 600, "进店防抖秒数");
        requireRange(onlineWindowMinutes, 1, 1440, "在线人数窗口");
        requireRange(orderExpireMinutes, 1, 1440, "未支付超时");
        writeConfig("entry.debounce.seconds", String.valueOf(entryDebounceSeconds));
        writeConfig("online.window.minutes", String.valueOf(onlineWindowMinutes));
        writeConfig("order.expire.minutes", String.valueOf(orderExpireMinutes));
        return readConfig();
    }

    private ConfigView readConfig() {
        return new ConfigView(
                Integer.parseInt(requiredConfig("entry.debounce.seconds")),
                Integer.parseInt(requiredConfig("online.window.minutes")),
                Integer.parseInt(requiredConfig("order.expire.minutes")),
                configValue("refund.daily.deduct.fen"));
    }

    private void writeConfig(String key, String value) {
        AppConfig config = appConfigMapper.selectById(key);
        if (config == null) {
            throw BizException.badRequest("配置不存在");
        }
        config.setConfigValue(value);
        config.setUpdatedAt(LocalDateTime.now());
        appConfigMapper.updateById(config);
    }

    private String requiredConfig(String key) {
        String value = configValue(key);
        if (value.isBlank()) {
            throw BizException.badRequest("配置不存在");
        }
        return value;
    }

    private String configValue(String key) {
        AppConfig config = appConfigMapper.selectById(key);
        return config == null || config.getConfigValue() == null ? "" : config.getConfigValue();
    }

    private Banner mustBanner(long id) {
        Banner banner = bannerMapper.selectById(id);
        if (banner == null || Integer.valueOf(1).equals(banner.getDeleted())) {
            throw BizException.badRequest("Banner 不存在");
        }
        return banner;
    }

    private Notice mustNotice(long id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null || Integer.valueOf(1).equals(notice.getDeleted())) {
            throw BizException.badRequest("公告不存在");
        }
        return notice;
    }

    private Agreement mustAgreement(long id) {
        Agreement agreement = agreementMapper.selectById(id);
        if (agreement == null) {
            throw BizException.badRequest("协议不存在");
        }
        return agreement;
    }

    private BannerView toBanner(Banner banner) {
        return new BannerView(String.valueOf(banner.getId()), banner.getTitle(), banner.getImageUrl(),
                banner.getLinkUrl(), banner.getSortNo(), banner.getStatus());
    }

    private AgreementView toAgreement(Agreement agreement) {
        return new AgreementView(String.valueOf(agreement.getId()), agreement.getTitle(), agreement.getVersionNo(),
                agreement.getContent(), agreement.getStatus());
    }

    private static String normalizeStatus(String status) {
        if (!"ON".equals(status) && !"OFF".equals(status)) {
            throw BizException.badRequest("状态不正确");
        }
        return status;
    }

    private static void requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw BizException.badRequest(label + "超出范围");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record BannerView(String id, String title, String imageUrl, String linkUrl, Integer sortNo, String status) {
    }

    public record NoticeView(String id, String content, String status, Integer sortNo) {
    }

    public record AgreementView(String id, String title, int versionNo, String content, String status) {
    }

    public record ConfigView(int entryDebounceSeconds, int onlineWindowMinutes, int orderExpireMinutes,
                             String refundDailyDeductFen) {
    }
}
