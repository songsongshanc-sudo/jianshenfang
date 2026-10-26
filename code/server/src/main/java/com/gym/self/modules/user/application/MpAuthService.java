package com.gym.self.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.card.application.CardService;
import com.gym.self.modules.gate.GateFaceSync;
import com.gym.self.modules.user.auth.CurrentMp;
import com.gym.self.modules.user.auth.MpJwt;
import com.gym.self.modules.user.auth.MpPrincipal;
import com.gym.self.modules.user.auth.MpTokenStore;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.gym.self.modules.user.domain.UserFace;
import com.gym.self.modules.user.domain.UserFaceMapper;
import com.gym.self.modules.user.face.FaceVendorPort;
import com.gym.self.modules.user.wechat.WxAuthPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class MpAuthService {

    private final WxAuthPort wxAuthPort;
    private final FaceVendorPort faceVendorPort;
    private final GymUserMapper userMapper;
    private final UserFaceMapper userFaceMapper;
    private final MemberNoService memberNoService;
    private final FaceFileService faceFileService;
    private final MpJwt mpJwt;
    private final MpTokenStore tokenStore;
    private final Snowflake snowflake;
    private final TimeProvider timeProvider;
    private final CardService cardService;
    private final GateFaceSync gateFaceSync;
    private final SmsCodeService smsCodeService;

    public MpAuthService(WxAuthPort wxAuthPort, FaceVendorPort faceVendorPort, GymUserMapper userMapper,
                         UserFaceMapper userFaceMapper, MemberNoService memberNoService, FaceFileService faceFileService,
                         MpJwt mpJwt, MpTokenStore tokenStore, Snowflake snowflake, TimeProvider timeProvider,
                         CardService cardService, GateFaceSync gateFaceSync, SmsCodeService smsCodeService) {
        this.wxAuthPort = wxAuthPort;
        this.faceVendorPort = faceVendorPort;
        this.userMapper = userMapper;
        this.userFaceMapper = userFaceMapper;
        this.memberNoService = memberNoService;
        this.faceFileService = faceFileService;
        this.mpJwt = mpJwt;
        this.tokenStore = tokenStore;
        this.snowflake = snowflake;
        this.timeProvider = timeProvider;
        this.cardService = cardService;
        this.gateFaceSync = gateFaceSync;
        this.smsCodeService = smsCodeService;
    }

    /** 静默登录：code -> openid。已注册直接登录，否则进入注册阶段。 */
    public EnterView enter(String code) {
        WxAuthPort.Session session = wxAuthPort.exchange(code);
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getOpenid, session.openid()));
        if (user != null && "ACTIVE".equals(user.getRegisterStatus()) && hasPhone(user)) {
            return EnterView.from(formal(user), faceReady(user.getId()));
        }
        if (user == null) {
            LocalDateTime now = timeProvider.now();
            user = new GymUser();
            user.setId(snowflake.next());
            user.setOpenid(session.openid());
            user.setNickname("");
            user.setRegisterStatus("NEED_REGISTER");
            user.setStatus("ACTIVE");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            userMapper.insert(user);
        }
        MpJwt.Issued face = mpJwt.face(user.getId());
        tokenStore.save(user.getId(), face.jti(), face.ttlSeconds());
        return new EnterView("REGISTER", face.token(), null, user.getRegisterStatus(), user.getMemberNo(), faceReady(user.getId()));
    }

    /** 本机号登录：微信 getPhoneNumber 返回的 code。 */
    @Transactional
    public LoginView phone(String phoneCode) {
        MpPrincipal principal = requireFaceToken();
        GymUser shell = mustUser(principal.userId());
        String phone = wxAuthPort.phone(phoneCode);
        return loginByPhone(shell, phone);
    }

    /** 其他号码登录：手机号 + 验证码。 */
    @Transactional
    public LoginView login(String phone, String smsCode) {
        MpPrincipal principal = requireFaceToken();
        GymUser shell = mustUser(principal.userId());
        String mobile = phone == null ? "" : phone.trim();
        smsCodeService.verify(mobile, smsCode);
        return loginByPhone(shell, mobile);
    }

    /** 统一手机号登录：绑定 openid，已注册发 access，未注册进入信息完善阶段。 */
    private LoginView loginByPhone(GymUser shell, String phone) {
        GymUser phoneUser = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, phone));
        GymUser target = bindPhone(shell, phoneUser, phone);
        if ("ACTIVE".equals(target.getRegisterStatus())) {
            target.setUpdatedAt(timeProvider.now());
            userMapper.updateById(target);
            return formal(target);
        }
        target.setPhone(phone);
        target.setRegisterStatus("NEED_PROFILE");
        target.setUpdatedAt(timeProvider.now());
        userMapper.updateById(target);
        MpJwt.Issued face = mpJwt.face(target.getId());
        tokenStore.save(target.getId(), face.jti(), face.ttlSeconds());
        return new LoginView("REGISTER", face.token(), null, "NEED_PROFILE", target.getMemberNo());
    }

    /** 信息完善：提交姓名，激活账号（人脸需已通过 /face 入库）。 */
    @Transactional
    public LoginView complete(String name) {
        MpPrincipal principal = requireFaceToken();
        GymUser user = mustUser(principal.userId());
        if (!hasPhone(user)) {
            throw BizException.badRequest("请先登录手机号");
        }
        if (!faceReady(user.getId())) {
            throw BizException.badRequest("请先完成人脸核验");
        }
        String realName = name == null ? "" : name.trim();
        if (realName.isEmpty() || realName.length() > 32) {
            throw BizException.badRequest("请填写姓名");
        }
        if (user.getMemberNo() == null || user.getMemberNo().isBlank()) {
            user.setMemberNo(memberNoService.next());
        }
        user.setNickname(realName);
        user.setRegisterStatus("ACTIVE");
        if (user.getRegisteredAt() == null) {
            user.setRegisteredAt(timeProvider.now());
        }
        user.setUpdatedAt(timeProvider.now());
        userMapper.updateById(user);
        UserFace enrolled = enrolledFace(user.getId());
        if (enrolled != null) {
            enrolled.setVendorFaceId(user.getMemberNo());
            userFaceMapper.updateById(enrolled);
        }
        return formal(user);
    }

    /** 退出登录：撤销当前 token，并解绑微信（openid 置空），允许下次重新登录。 */
    @Transactional
    public void logout(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }
        try {
            var claims = mpJwt.parse(authorization.substring(7));
            tokenStore.revoke(claims.getId());
            long userId = Long.parseLong(claims.getSubject());
            userMapper.update(null, new LambdaUpdateWrapper<GymUser>()
                    .eq(GymUser::getId, userId)
                    .set(GymUser::getOpenid, null)
                    .set(GymUser::getUpdatedAt, timeProvider.now()));
        } catch (RuntimeException ignored) {
            // token 已失效，无需处理
        }
    }

    public String presign(String contentType) {
        MpPrincipal principal = CurrentMp.get();
        GymUser user = mustUser(principal.userId());
        if (principal.formal()) {
            throw BizException.rejected("人脸不能修改");
        }
        if (!principal.faceEnroll() || !preRegistration(user.getRegisterStatus())) {
            throw BizException.unauthorized();
        }
        return faceFileService.presign(user.getId(), contentType);
    }

    public void savePhoto(String objectKey, byte[] body) {
        MpPrincipal principal = CurrentMp.get();
        if (!principal.faceEnroll() && !principal.formal()) {
            throw BizException.unauthorized();
        }
        faceFileService.save(principal.userId(), objectKey, body);
    }

    @Transactional
    public void replaceFace(String objectKey) {
        throw BizException.rejected("人脸不能修改");
    }

    /** 人脸入库（信息完善阶段调用）。 */
    @Transactional
    public LoginView enroll(String objectKey) {
        MpPrincipal principal = requireFaceToken();
        GymUser user = mustUser(principal.userId());
        if (!hasPhone(user)) {
            throw BizException.badRequest("请先登录手机号");
        }
        long size = faceFileService.size(user.getId(), objectKey);
        FaceVendorPort.Result result = faceVendorPort.enroll(user.getId(), objectKey, size);
        UserFace face = new UserFace();
        face.setId(snowflake.next());
        face.setUserId(user.getId());
        face.setObjectKey(objectKey);
        face.setCreatedAt(timeProvider.now());
        if (!result.enrolled()) {
            face.setStatus("FAILED");
            face.setFailReason(result.reason());
            userFaceMapper.insert(face);
            throw BizException.badRequest(result.reason() == null ? "人脸入库失败，请重新拍摄" : result.reason());
        }
        face.setStatus("ENROLLED");
        face.setVendorFaceId(result.vendorFaceId());
        userFaceMapper.insert(face);
        MpJwt.Issued issued = mpJwt.face(user.getId());
        tokenStore.save(user.getId(), issued.jti(), issued.ttlSeconds());
        return new LoginView("REGISTER", issued.token(), null, user.getRegisterStatus(), user.getMemberNo());
    }

    public void sendSms(String phone) {
        requireFaceToken();
        smsCodeService.send(phone == null ? "" : phone.trim());
    }

    public byte[] facePreview() {
        MpPrincipal principal = CurrentMp.get();
        if (!principal.faceEnroll() && !principal.formal()) {
            throw BizException.unauthorized();
        }
        UserFace face = enrolledFace(principal.userId());
        if (face == null) {
            throw BizException.badRequest("还没有人脸照片");
        }
        return faceFileService.read(principal.userId(), face.getObjectKey());
    }

    public LoginView refresh(String refreshToken) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = mpJwt.parse(refreshToken);
        } catch (RuntimeException exception) {
            throw BizException.unauthorized();
        }
        if (!"refresh".equals(claims.get("kind")) || !tokenStore.valid(claims.getId())) {
            throw BizException.unauthorized();
        }
        GymUser user = mustUser(Long.parseLong(claims.getSubject()));
        if (!"ACTIVE".equals(user.getRegisterStatus())) {
            throw BizException.unauthorized();
        }
        return formal(user);
    }

    public MeView updateProfile(String nickname) {
        throw BizException.rejected("姓名不能修改");
    }

    public MeView me(Long storeId) {
        GymUser user = mustUser(CurrentMp.formal().userId());
        LocalDate today = timeProvider.today();
        int companionDays = 0;
        if (user.getRegisteredAt() != null) {
            companionDays = (int) ChronoUnit.DAYS.between(user.getRegisteredAt().toLocalDate(), today);
        }
        CardService.ProgressView progress = cardService.progress(user.getId(), storeId);
        return new MeView(user.getRegisterStatus(), displayName(user), user.getPhone(), user.getMemberNo(), companionDays,
                progress.consecutiveDays(), progress.cumulativeDays(), progress.consecutiveRemain(),
                progress.storeMember(), gateFaceSync.status(user.getId(), storeId));
    }

    private LoginView formal(GymUser user) {
        MpJwt.Issued access = mpJwt.access(user.getId());
        MpJwt.Issued refresh = mpJwt.refresh(user.getId());
        tokenStore.save(user.getId(), access.jti(), access.ttlSeconds());
        tokenStore.save(user.getId(), refresh.jti(), refresh.ttlSeconds());
        return new LoginView("ACCESS", access.token(), refresh.token(), "ACTIVE", user.getMemberNo());
    }

    /** 把手机号绑到 shell 的 openid 上；若手机号已存在账号，则合并到该账号。 */
    private GymUser bindPhone(GymUser sessionUser, GymUser phoneUser, String phone) {
        if (phoneUser == null || phoneUser.getId().equals(sessionUser.getId())) {
            sessionUser.setPhone(phone);
            return sessionUser;
        }
        if (phoneUser.getOpenid() != null && !phoneUser.getOpenid().isBlank()
                && !phoneUser.getOpenid().equals(sessionUser.getOpenid())) {
            throw BizException.rejected("该手机号已绑定其他微信");
        }
        String openid = sessionUser.getOpenid();
        userMapper.deleteById(sessionUser.getId());
        phoneUser.setOpenid(openid);
        phoneUser.setUpdatedAt(timeProvider.now());
        if ((phoneUser.getMemberNo() == null || phoneUser.getMemberNo().isBlank()) && sessionUser.getMemberNo() != null) {
            phoneUser.setMemberNo(sessionUser.getMemberNo());
        }
        return phoneUser;
    }

    private MpPrincipal requireFaceToken() {
        MpPrincipal principal = CurrentMp.get();
        if (!principal.faceEnroll()) {
            throw BizException.unauthorized();
        }
        return principal;
    }

    private static boolean hasPhone(GymUser user) {
        return user.getPhone() != null && !user.getPhone().isBlank();
    }

    private static boolean preRegistration(String status) {
        return "NEED_REGISTER".equals(status) || "NEED_PROFILE".equals(status);
    }

    private boolean faceReady(long userId) {
        return enrolledFace(userId) != null;
    }

    private UserFace enrolledFace(long userId) {
        return userFaceMapper.selectOne(new LambdaQueryWrapper<UserFace>()
                .eq(UserFace::getUserId, userId)
                .eq(UserFace::getStatus, "ENROLLED")
                .orderByDesc(UserFace::getId)
                .last("LIMIT 1"));
    }

    private static String displayName(GymUser user) {
        if (user.getNickname() == null || user.getNickname().isBlank() || "微信用户".equals(user.getNickname())) {
            return "";
        }
        return user.getNickname();
    }

    private GymUser mustUser(long userId) {
        GymUser user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw BizException.unauthorized();
        }
        return user;
    }

    public record LoginView(String tokenType, String accessToken, String refreshToken, String registerStatus, String memberNo) {
    }

    public record MeView(String registerStatus, String nickname, String phone, String memberNo, int companionDays, int consecutiveDays,
                         int cumulativeDays, Integer consecutiveRemain, boolean storeMember, String faceSync) {
    }

    public record EnterView(String tokenType, String accessToken, String refreshToken, String registerStatus,
                            String memberNo, boolean faceReady) {
        static EnterView from(LoginView login, boolean faceReady) {
            return new EnterView(login.tokenType(), login.accessToken(), login.refreshToken(),
                    login.registerStatus(), login.memberNo(), faceReady);
        }
    }
}
