package com.gym.self.modules.user.application;

import com.gym.self.common.api.BizException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SmsCodeService {

    private static final long RESEND_SECONDS = 60;
    private static final int DAILY_LIMIT = 10;

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Map<String, Integer> dailyCount = new ConcurrentHashMap<>();

    public void send(String phone) {
        requireMobile(phone);
        Instant now = Instant.now();
        Pending row = pending.get(phone);
        if (row != null && now.isBefore(row.sentAt().plusSeconds(RESEND_SECONDS))) {
            throw BizException.rejected("验证码发送太频繁，请稍后重试");
        }
        String day = LocalDate.now().toString();
        int count = dailyCount.merge(phone + ":" + day, 1, Integer::sum);
        if (count > DAILY_LIMIT) {
            dailyCount.put(phone + ":" + day, DAILY_LIMIT);
            throw BizException.rejected("验证码发送次数过多，请明天再试");
        }
        // TODO: 接入阿里云短信（Dysmsapi）
        throw BizException.rejected("短信尚未配置，请先开通阿里云短信");
    }

    public void verify(String phone, String code) {
        requireMobile(phone);
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("请填写验证码");
        }
        Pending row = pending.get(phone);
        if (row == null || Instant.now().isAfter(row.expireAt()) || !row.code().equals(code.trim())) {
            throw BizException.badRequest("验证码不正确");
        }
        pending.remove(phone);
    }

    private static void requireMobile(String phone) {
        if (phone == null || !phone.matches("1\\d{10}")) {
            throw BizException.badRequest("请填写 11 位手机号");
        }
    }

    private record Pending(String code, Instant expireAt, Instant sentAt) {
    }
}
