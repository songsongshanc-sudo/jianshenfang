package com.gym.self.modules.adminuser.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gym.self.common.api.BizException;
import com.gym.self.common.api.ErrorCode;
import com.gym.self.common.id.Snowflake;
import com.gym.self.modules.adminuser.domain.AdminUser;
import com.gym.self.modules.adminuser.domain.AdminUserMapper;
import com.gym.self.modules.store.domain.Store;
import com.gym.self.modules.store.domain.StoreMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final StoreMapper storeMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwt adminJwt;
    private final TokenStore tokenStore;
    private final LoginLock loginLock;
    private final Snowflake snowflake;

    public AdminAuthService(AdminUserMapper adminUserMapper, StoreMapper storeMapper, PasswordEncoder passwordEncoder,
                            AdminJwt adminJwt, TokenStore tokenStore, LoginLock loginLock, Snowflake snowflake) {
        this.adminUserMapper = adminUserMapper;
        this.storeMapper = storeMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminJwt = adminJwt;
        this.tokenStore = tokenStore;
        this.loginLock = loginLock;
        this.snowflake = snowflake;
    }

    public LoginResult login(String username, String password) {
        if (loginLock.locked(username)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "登录过于频繁，请稍后再试");
        }
        AdminUser admin = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUsername, username));
        if (admin == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            loginLock.recordFailure(username);
            throw new BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        if (!"ACTIVE".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "账号已停用");
        }
        loginLock.clear(username);
        AdminPrincipal principal = new AdminPrincipal(admin.getId(), admin.getRole(), admin.getStoreId());
        AdminJwt.Issued access = adminJwt.access(principal);
        AdminJwt.Issued refresh = adminJwt.refresh(principal);
        tokenStore.save(admin.getId(), access.jti(), "access", access.ttlSeconds());
        tokenStore.save(admin.getId(), refresh.jti(), "refresh", refresh.ttlSeconds());
        return new LoginResult(access.token(), refresh.token(), admin.getRole(), admin.getStoreId());
    }

    public String createStoreAccount(AdminPrincipal actor, String username, String password, long storeId) {
        if (!actor.master()) {
            throw BizException.forbidden("只有总账号可以创建门店账号");
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null || store.getDeleted() != null && store.getDeleted() == 1) {
            throw new BizException(ErrorCode.PARAM, HttpStatus.BAD_REQUEST, "门店不存在");
        }
        Long existing = adminUserMapper.selectCount(new LambdaQueryWrapper<AdminUser>()
                .eq(AdminUser::getUsername, username));
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.PARAM, HttpStatus.BAD_REQUEST, "账号已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        AdminUser account = new AdminUser();
        account.setId(snowflake.next());
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole("STORE");
        account.setStoreId(storeId);
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        adminUserMapper.insert(account);
        return String.valueOf(account.getId());
    }

    public void disable(AdminPrincipal actor, long accountId) {
        if (!actor.master()) {
            throw BizException.forbidden("只有总账号可以停用账号");
        }
        if (actor.id() == accountId) {
            throw BizException.forbidden("不能停用当前登录账号");
        }
        AdminUser account = adminUserMapper.selectById(accountId);
        if (account == null) {
            throw new BizException(ErrorCode.PARAM, HttpStatus.BAD_REQUEST, "账号不存在");
        }
        account.setStatus("DISABLED");
        account.setUpdatedAt(LocalDateTime.now());
        adminUserMapper.updateById(account);
        tokenStore.revokeAdmin(accountId);
    }

    public record LoginResult(String accessToken, String refreshToken, String role, Long storeId) {
    }
}
