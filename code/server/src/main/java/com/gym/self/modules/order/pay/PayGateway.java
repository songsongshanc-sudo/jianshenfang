package com.gym.self.modules.order.pay;

import com.wechat.pay.java.service.payments.model.Transaction;

public interface PayGateway {

    boolean mockEnabled();

    Prepared prepare(String orderNo, String description, long amountFen, String openid);

    Prepared resign(String prepayId);

    void close(String orderNo);

    void refund(String orderNo, String transactionId, long amountFen);

    Transaction parseNotify(String body, String serial, String nonce, String signature, String timestamp);

    record Prepared(String mchId, String prepayId, String timeStamp, String nonceStr, String payPackage,
                    String signType, String paySign) {
    }
}
