package com.gym.self.modules.adminuser.application;

import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.domain.AdminUser;
import com.gym.self.modules.adminuser.domain.AdminUserMapper;
import com.gym.self.modules.lesson.application.LessonNotify;
import org.springframework.stereotype.Service;

@Service
public class AdminOpLog {

    public static final String BIZ = "SYSTEM_OP";

    private final LessonNotify notify;
    private final AdminUserMapper adminUserMapper;

    public AdminOpLog(LessonNotify notify, AdminUserMapper adminUserMapper) {
        this.notify = notify;
        this.adminUserMapper = adminUserMapper;
    }

    public void record(AdminPrincipal actor, Long storeId, Long userId, Long bizId, String title, String detail) {
        AdminUser account = adminUserMapper.selectById(actor.id());
        String name = account == null || account.getUsername() == null ? String.valueOf(actor.id()) : account.getUsername();
        String role = actor.master() ? "总账号" : "门店账号";
        String content = "操作人 " + name + "（" + role + "）。" + (detail == null ? "" : detail);
        notify.send(userId, title, content, BIZ, bizId, storeId, null);
    }
}
