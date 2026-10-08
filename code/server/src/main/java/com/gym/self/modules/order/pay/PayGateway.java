package com.gym.self.modules.order.pay;

public interface PayGateway {

    boolean mockEnabled();

    Prepared prepare(String orderNo, String description, long amountFen);

    void close(String orderNo);

    void refund(String orderNo, String transactionId, long amountFen);

    record Prepared(String mchId, String prepayId, String timeStamp, String nonceStr, String payPackage,
                    String signType, String paySign) {
    }
}
