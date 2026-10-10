package com.gym.self.modules.order.interfaces;

import com.gym.self.common.api.BizException;
import com.gym.self.modules.order.application.OrderService;
import com.gym.self.modules.order.pay.PayGateway;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mp/pay")
public class MpPayNotifyController {

    private static final Logger log = LoggerFactory.getLogger(MpPayNotifyController.class);

    private final PayGateway payGateway;
    private final OrderService orderService;

    public MpPayNotifyController(PayGateway payGateway, OrderService orderService) {
        this.payGateway = payGateway;
        this.orderService = orderService;
    }

    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> notify(
            @RequestBody String body,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp) {
        try {
            Transaction transaction = payGateway.parseNotify(body, serial, nonce, signature, timestamp);
            if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
                return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
            }
            long paidFen = transaction.getAmount() == null || transaction.getAmount().getTotal() == null
                    ? -1L
                    : transaction.getAmount().getTotal().longValue();
            orderService.fulfillByNotify(transaction.getOutTradeNo(), transaction.getTransactionId(), paidFen, body);
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (BizException exception) {
            if (exception.getStatus() == HttpStatus.UNAUTHORIZED) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("code", "FAIL", "message", "签名校验失败"));
            }
            log.warn("wechat pay notify rejected: {}", exception.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "FAIL", "message", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.error("wechat pay notify failed", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", "FAIL", "message", "处理失败"));
        }
    }
}
