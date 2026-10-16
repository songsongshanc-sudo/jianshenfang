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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MpAuthService {

    private static final int INVITE_LIMIT = 5;
    private final Map<String, Integer> unknownInviteFails = new ConcurrentHashMap<>();

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

    public EnterView enter(String code) {
        WxAuthPort.Session session = wxAuthPort.exchange(code);
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getOpenid, session.openid()));
        if (user != null && "ACTIVE".equals(user.getRegisterStatus()) && user.getPhone() != null && !user.getPhone().isBlank()) {
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

    public SessionView session(String code) {
        WxAuthPort.Session session = wxAuthPort.exchange(code);
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getOpenid, session.openid()));
        if (user == null) {
            LocalDateTime now = LocalDateTime.now();
            user = new GymUser();
            user.setId(snowflake.next());
            user.setOpenid(session.openid());
            user.setNickname("");
            user.setRegisterStatus("NEED_PHONE");
            user.setStatus("ACTIVE");
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            userMapper.insert(user);
        }
        MpJwt.Issued issued = mpJwt.session(user.getId());
        tokenStore.save(user.getId(), issued.jti(), issued.ttlSeconds());
        return new SessionView(issued.token());
    }

    @Transactional
    public LoginView phone(String phoneCode) {
        MpPrincipal principal = CurrentMp.get();
        if (!"wx_session".equals(principal.kind())) {
            throw BizException.unauthorized();
        }
        GymUser sessionUser = mustUser(principal.userId());
        String phone = wxAuthPort.phone(phoneCode);
        GymUser phoneUser = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, phone));
        GymUser user = bindPhone(sessionUser, phoneUser, phone);
        if ("ACTIVE".equals(user.getRegisterStatus())) {
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            clearInvite(user.getId());
            return formal(user);
        }
        user.setPhone(phone);
        user.setRegisterStatus("NEED_FACE");
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        MpJwt.Issued face = mpJwt.face(user.getId());
        tokenStore.save(user.getId(), face.jti(), face.ttlSeconds());
        return new LoginView("FACE", face.token(), null, "NEED_FACE", null);
    }

    public String presign(String contentType) {
        MpPrincipal principal = CurrentMp.get();
        GymUser user = mustUser(principal.userId());
        if (principal.formal()) {
            throw BizException.rejected("人脸不能修改");
        }
        if (!principal.faceEnroll() || (!registering(user.getRegisterStatus()) && !"NEED_FACE".equals(user.getRegisterStatus()))) {
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

    public LoginView enroll(String objectKey) {
        MpPrincipal principal = CurrentMp.get();
        if (!principal.faceEnroll()) {
            throw BizException.unauthorized();
        }
        GymUser user = mustUser(principal.userId());
        if (!registering(user.getRegisterStatus()) && !"NEED_FACE".equals(user.getRegisterStatus())) {
            throw BizException.badRequest("当前不需要采集人脸");
        }
        if (faceReady(user.getId()) && user.getMemberNo() != null && !user.getMemberNo().isBlank()) {
            if (!"FACE_OK".equals(user.getRegisterStatus()) && !"ACTIVE".equals(user.getRegisterStatus()) && !"NEED_FACE".equals(user.getRegisterStatus())) {
                user.setRegisterStatus("FACE_OK");
                user.setUpdatedAt(timeProvider.now());
                userMapper.updateById(user);
            }
            if ("NEED_FACE".equals(user.getRegisterStatus()) || "ACTIVE".equals(user.getRegisterStatus())) {
                if ("NEED_FACE".equals(user.getRegisterStatus())) {
                    user.setRegisterStatus("ACTIVE");
                    user.setRegisteredAt(timeProvider.now());
                    user.setUpdatedAt(timeProvider.now());
                    userMapper.updateById(user);
                }
                return formal(user);
            }
            MpJwt.Issued issued = mpJwt.face(user.getId());
            tokenStore.save(user.getId(), issued.jti(), issued.ttlSeconds());
            return new LoginView("REGISTER", issued.token(), null, "FACE_OK", user.getMemberNo());
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
        LocalDateTime now = timeProvider.now();
        if (user.getMemberNo() == null || user.getMemberNo().isBlank()) {
            user.setMemberNo(memberNoService.next());
        }
        face.setVendorFaceId(user.getMemberNo());
        userFaceMapper.insert(face);
        if ("NEED_FACE".equals(user.getRegisterStatus())) {
            user.setRegisterStatus("ACTIVE");
            user.setRegisteredAt(now);
            user.setUpdatedAt(now);
            userMapper.updateById(user);
            return formal(user);
        }
        user.setRegisterStatus("FACE_OK");
        user.setUpdatedAt(now);
        userMapper.updateById(user);
        MpJwt.Issued issued = mpJwt.face(user.getId());
        tokenStore.save(user.getId(), issued.jti(), issued.ttlSeconds());
        return new LoginView("REGISTER", issued.token(), null, "FACE_OK", user.getMemberNo());
    }

    public void sendSms(String phone) {
        requireRegistering();
        smsCodeService.send(phone.trim());
    }

    @Transactional
    public LoginView register(String name, String phone, String smsCode) {
        MpPrincipal principal = CurrentMp.get();
        if (!principal.faceEnroll()) {
            throw BizException.unauthorized();
        }
        GymUser user = mustUser(principal.userId());
        if (!"FACE_OK".equals(user.getRegisterStatus()) || !faceReady(user.getId())) {
            throw BizException.rejected("请先完成人脸核验");
        }
        String realName = name == null ? "" : name.trim();
        if (realName.isEmpty() || realName.length() > 32) {
            throw BizException.badRequest("请填写姓名");
        }
        String mobile = phone == null ? "" : phone.trim();
        smsCodeService.verify(mobile, smsCode);
        GymUser phoneUser = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, mobile));
        boolean kept = phoneUser != null && !phoneUser.getId().equals(user.getId())
                && "ACTIVE".equals(phoneUser.getRegisterStatus());
        GymUser target = bindPhone(user, phoneUser, mobile);
        LocalDateTime now = timeProvider.now();
        if (!kept) {
            target.setNickname(realName);
            target.setRegisterStatus("ACTIVE");
            if (target.getRegisteredAt() == null) {
                target.setRegisteredAt(now);
            }
        }
        target.setPhone(mobile);
        target.setUpdatedAt(now);
        userMapper.updateById(target);
        clearInvite(target.getId());
        return formal(target);
    }

    @Transactional
    public LoginView claim(String phone, String inviteCode) {
        MpPrincipal principal = CurrentMp.get();
        if (!principal.faceEnroll()) {
            throw BizException.unauthorized();
        }
        GymUser shell = mustUser(principal.userId());
        String mobile = phone == null ? "" : phone.trim();
        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase().replace(" ", "");
        if (!mobile.matches("1\\d{10}") || !code.matches("[A-Z2-9]{8}")) {
            throw BizException.badRequest("请填写手机号和 8 位邀请码");
        }
        if (unknownInviteFails.getOrDefault(mobile, 0) >= INVITE_LIMIT) {
            throw BizException.rejected("邀请码已锁定，请联系门店重新生成");
        }
        GymUser target = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getPhone, mobile));
        if (target == null || !"ACTIVE".equals(target.getRegisterStatus())) {
            unknownInviteFails.merge(mobile, 1, Integer::sum);
            throw BizException.rejected("手机号或邀请码不正确");
        }
        int fails = target.getInviteFail() == null ? 0 : target.getInviteFail();
        if (fails >= INVITE_LIMIT) {
            throw BizException.rejected("邀请码已锁定，请联系门店重新生成");
        }
        if (target.getInviteCode() == null || !target.getInviteCode().equals(code)) {
            target.setInviteFail(fails + 1);
            userMapper.updateById(target);
            if (fails + 1 >= INVITE_LIMIT) {
                throw BizException.rejected("邀请码已锁定，请联系门店重新生成");
            }
            throw BizException.rejected("手机号或邀请码不正确");
        }
        if (target.getOpenid() != null && !target.getOpenid().isBlank()
                && !target.getOpenid().equals(shell.getOpenid())) {
            throw BizException.rejected("该手机号已绑定其他微信");
        }
        if (!shell.getId().equals(target.getId()) && occupied(shell)) {
            throw BizException.rejected("当前微信已是其他会员");
        }
        String openid = shell.getOpenid();
        if (!shell.getId().equals(target.getId())) {
            userFaceMapper.delete(new LambdaQueryWrapper<UserFace>().eq(UserFace::getUserId, shell.getId()));
            userMapper.deleteById(shell.getId());
        }
        int updated = userMapper.update(null, new LambdaUpdateWrapper<GymUser>()
                .eq(GymUser::getId, target.getId())
                .eq(GymUser::getInviteCode, code)
                .and(wrapper -> wrapper.isNull(GymUser::getOpenid).or().eq(GymUser::getOpenid, ""))
                .set(GymUser::getOpenid, openid)
                .set(GymUser::getInviteCode, null)
                .set(GymUser::getInviteFail, 0)
                .set(GymUser::getUpdatedAt, timeProvider.now()));
        if (updated == 0 && (target.getOpenid() == null || !target.getOpenid().equals(openid))) {
            throw BizException.rejected("邀请码已失效，请联系门店重新生成");
        }
        if (updated == 0) {
            clearInvite(target.getId());
        }
        GymUser bound = mustUser(target.getId());
        return formal(bound);
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
        phoneUser.setInviteCode(null);
        phoneUser.setInviteFail(0);
        phoneUser.setUpdatedAt(timeProvider.now());
        if ((phoneUser.getMemberNo() == null || phoneUser.getMemberNo().isBlank()) && sessionUser.getMemberNo() != null) {
            phoneUser.setMemberNo(sessionUser.getMemberNo());
        }
        return phoneUser;
    }

    private void clearInvite(long userId) {
        userMapper.update(null, new LambdaUpdateWrapper<GymUser>()
                .eq(GymUser::getId, userId)
                .set(GymUser::getInviteCode, null)
                .set(GymUser::getInviteFail, 0));
    }

    private static boolean occupied(GymUser user) {
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            return true;
        }
        return "ACTIVE".equals(user.getRegisterStatus());
    }

    private void requireRegistering() {
        MpPrincipal principal = CurrentMp.get();
        if (!principal.faceEnroll()) {
            throw BizException.unauthorized();
        }
        GymUser user = mustUser(principal.userId());
        if (!registering(user.getRegisterStatus())) {
            throw BizException.rejected("当前不能发送验证码");
        }
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

    private static boolean registering(String status) {
        return "NEED_REGISTER".equals(status) || "FACE_OK".equals(status);
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

    public record SessionView(String sessionToken) {
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
