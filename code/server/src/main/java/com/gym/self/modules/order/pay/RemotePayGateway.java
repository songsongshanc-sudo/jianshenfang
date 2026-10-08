package com.gym.self.modules.order.pay;

import com.gym.self.common.api.BizException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "gym.pay.mock-enabled", havingValue = "false", matchIfMissing = true)
public class RemotePayGateway implements PayGateway {

    @Override
    public boolean mockEnabled() {
        return false;
    }

    @Override
    public Prepared prepare(String orderNo, String description, long amountFen) {
        throw BizException.badRequest("微信支付尚未配置");
    }

    @Override
    public void close(String orderNo) {
        throw BizException.badRequest("微信支付尚未配置");
    }

    @Override
    public void refund(String orderNo, String transactionId, long amountFen) {
        throw BizException.badRequest("微信支付尚未配置");
    }
}
