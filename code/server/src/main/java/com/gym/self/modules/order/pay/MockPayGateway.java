package com.gym.self.modules.order.pay;

import com.gym.self.common.api.BizException;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "gym.pay.mock-enabled", havingValue = "true")
public class MockPayGateway implements PayGateway {

    @Override
    public boolean mockEnabled() {
        return true;
    }

    @Override
    public Prepared prepare(String orderNo, String description, long amountFen, String openid) {
        return new Prepared("mock", "mock-" + orderNo, "", "", "", "", "");
    }

    @Override
    public Prepared resign(String prepayId) {
        return new Prepared("mock", prepayId == null ? "" : prepayId, "", "", "", "", "");
    }

    @Override
    public void close(String orderNo) {
    }

    @Override
    public void refund(String orderNo, String transactionId, long amountFen) {
    }

    @Override
    public Transaction parseNotify(String body, String serial, String nonce, String signature, String timestamp) {
        throw BizException.rejected("当前环境不能接收微信支付回调");
    }
}
