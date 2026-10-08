package com.gym.self.modules.order.pay;

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
    public Prepared prepare(String orderNo, String description, long amountFen) {
        return new Prepared("mock", "mock-" + orderNo, "", "", "", "", "");
    }

    @Override
    public void close(String orderNo) {
    }

    @Override
    public void refund(String orderNo, String transactionId, long amountFen) {
    }
}
