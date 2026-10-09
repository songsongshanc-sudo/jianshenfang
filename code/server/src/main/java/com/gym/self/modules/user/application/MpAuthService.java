package com.gym.self.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.id.Snowflake;
import com.gym.self.common.time.TimeProvider;
import com.gym.self.modules.card.application.CardService;
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

    public MpAuthService(WxAuthPort wxAuthPort, FaceVendorPort faceVendorPort, GymUserMapper userMapper,
                         UserFaceMapper userFaceMapper, MemberNoService memberNoService, FaceFileService faceFileService,
                         MpJwt mpJwt, MpTokenStore tokenStore, Snowflake snowflake, TimeProvider timeProvider,
                         CardService cardService) {
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
    }

    public SessionView session(String code) {
        WxAuthPort.Session session = wxAuthPort.exchange(code);
        GymUser user = userMapper.selectOne(new LambdaQueryWrapper<GymUser>().eq(GymUser::getOpenid, session.openid()));
        if (user == null) {
            LocalDateTime now = LocalDateTime.now();
            user = new GymUser();
            user.setId(snowflake.next());
            user.setOpenid(session.openid());
            user.setNickname("微信用户");
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
        GymUser user = merge(sessionUser, phoneUser, phone);
        if ("ACTIVE".equals(user.getRegisterStatus())) {
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
            if (!"ACTIVE".equals(user.getRegisterStatus())) {
                throw BizException.badRequest("当前不能更换人脸");
            }
            return faceFileService.presign(user.getId(), contentType);
        }
        if (!principal.faceEnroll() || !"NEED_FACE".equals(user.getRegisterStatus())) {
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
        MpPrincipal principal = CurrentMp.formal();
        GymUser user = mustUser(principal.userId());
        if (!"ACTIVE".equals(user.getRegisterStatus())) {
            throw BizException.badRequest("当前不能更换人脸");
        }
        UserFace current = userFaceMapper.selectOne(new LambdaQueryWrapper<UserFace>()
                .eq(UserFace::getUserId, user.getId())
                .eq(UserFace::getStatus, "ENROLLED")
                .orderByDesc(UserFace::getId)
                .last("LIMIT 1"));
        long size = faceFileService.size(user.getId(), objectKey);
        FaceVendorPort.Result result = faceVendorPort.enroll(user.getId(), objectKey, size);
        if (!result.enrolled()) {
            throw BizException.badRequest(result.reason() == null ? "人脸入库失败，请重新拍摄" : result.reason());
        }
        if (current != null && current.getVendorFaceId() != null) {
            faceVendorPort.revoke(current.getVendorFaceId());
            current.setStatus("REPLACED");
            userFaceMapper.updateById(current);
        }
        UserFace face = new UserFace();
        face.setId(snowflake.next());
        face.setUserId(user.getId());
        face.setObjectKey(objectKey);
        face.setStatus("ENROLLED");
        face.setVendorFaceId(result.vendorFaceId());
        face.setCreatedAt(LocalDateTime.now());
        userFaceMapper.insert(face);
    }

    public LoginView enroll(String objectKey) {
        MpPrincipal principal = CurrentMp.get();
        if (!principal.faceEnroll()) {
            throw BizException.unauthorized();
        }
        GymUser user = mustUser(principal.userId());
        if (!"NEED_FACE".equals(user.getRegisterStatus())) {
            throw BizException.badRequest("当前不需要采集人脸");
        }
        long size = faceFileService.size(user.getId(), objectKey);
        FaceVendorPort.Result result = faceVendorPort.enroll(user.getId(), objectKey, size);
        UserFace face = new UserFace();
        face.setId(snowflake.next());
        face.setUserId(user.getId());
        face.setObjectKey(objectKey);
        face.setCreatedAt(LocalDateTime.now());
        if (!result.enrolled()) {
            face.setStatus("FAILED");
            face.setFailReason(result.reason());
            userFaceMapper.insert(face);
            throw BizException.badRequest(result.reason() == null ? "人脸入库失败，请重新拍摄" : result.reason());
        }
        face.setStatus("ENROLLED");
        face.setVendorFaceId(result.vendorFaceId());
        userFaceMapper.insert(face);
        LocalDateTime now = LocalDateTime.now();
        user.setRegisterStatus("ACTIVE");
        user.setRegisteredAt(now);
        user.setMemberNo(memberNoService.next());
        user.setUpdatedAt(now);
        userMapper.updateById(user);
        return formal(user);
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

    public MeView me(Long storeId) {
        GymUser user = mustUser(CurrentMp.formal().userId());
        LocalDate today = timeProvider.today();
        int companionDays = 0;
        if (user.getRegisteredAt() != null) {
            companionDays = (int) ChronoUnit.DAYS.between(user.getRegisteredAt().toLocalDate(), today);
        }
        CardService.ProgressView progress = cardService.progress(user.getId(), storeId);
        return new MeView(user.getRegisterStatus(), user.getNickname(), user.getMemberNo(), companionDays,
                progress.consecutiveDays(), progress.cumulativeDays(), progress.consecutiveRemain(),
                progress.storeMember());
    }

    private LoginView formal(GymUser user) {
        MpJwt.Issued access = mpJwt.access(user.getId());
        MpJwt.Issued refresh = mpJwt.refresh(user.getId());
        tokenStore.save(user.getId(), access.jti(), access.ttlSeconds());
        tokenStore.save(user.getId(), refresh.jti(), refresh.ttlSeconds());
        return new LoginView("ACCESS", access.token(), refresh.token(), "ACTIVE", user.getMemberNo());
    }

    private GymUser merge(GymUser sessionUser, GymUser phoneUser, String phone) {
        if (phoneUser == null || phoneUser.getId().equals(sessionUser.getId())) {
            sessionUser.setPhone(phone);
            return sessionUser;
        }
        if (sessionUser.getPhone() != null && !sessionUser.getPhone().equals(phone)) {
            throw BizException.badRequest("手机号已注册");
        }
        userMapper.deleteById(sessionUser.getId());
        phoneUser.setOpenid(sessionUser.getOpenid());
        phoneUser.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(phoneUser);
        return phoneUser;
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

    public record MeView(String registerStatus, String nickname, String memberNo, int companionDays, int consecutiveDays,
                         int cumulativeDays, Integer consecutiveRemain, boolean storeMember) {
    }
}
