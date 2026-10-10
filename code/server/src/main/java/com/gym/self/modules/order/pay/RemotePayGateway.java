package com.gym.self.modules.order.pay;

import com.gym.self.common.api.BizException;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.cipher.Signer;
import com.wechat.pay.java.core.exception.ServiceException;
import com.wechat.pay.java.core.exception.ValidationException;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.core.util.NonceUtil;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.CloseOrderRequest;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "gym.pay.mock-enabled", havingValue = "false", matchIfMissing = true)
public class RemotePayGateway implements PayGateway {

    private static final Logger log = LoggerFactory.getLogger(RemotePayGateway.class);

    private final String appId;
    private final String mchId;
    private final String apiV3Key;
    private final String privateKeyPath;
    private final String mchSerial;
    private final String notifyUrl;

    private volatile JsapiServiceExtension jsapi;
    private volatile NotificationParser notificationParser;
    private volatile Signer signer;

    public RemotePayGateway(
            @Value("${gym.wx.mp-appid:}") String appId,
            @Value("${gym.pay.mch-id:}") String mchId,
            @Value("${gym.pay.api-v3-key:}") String apiV3Key,
            @Value("${gym.pay.private-key-path:}") String privateKeyPath,
            @Value("${gym.pay.mch-serial:}") String mchSerial,
            @Value("${gym.pay.notify-url:}") String notifyUrl) {
        this.appId = trim(appId);
        this.mchId = trim(mchId);
        this.apiV3Key = trim(apiV3Key);
        this.privateKeyPath = trim(privateKeyPath);
        this.mchSerial = trim(mchSerial);
        this.notifyUrl = trim(notifyUrl);
    }

    @Override
    public boolean mockEnabled() {
        return false;
    }

    @Override
    public Prepared prepare(String orderNo, String description, long amountFen, String openid) {
        ensureConfigured();
        if (openid == null || openid.isBlank()) {
            throw BizException.badRequest("请重新登录后再支付");
        }
        if (amountFen <= 0) {
            throw BizException.badRequest("支付金额不正确");
        }
        PrepayRequest request = new PrepayRequest();
        request.setAppid(appId);
        request.setMchid(mchId);
        request.setDescription(clipDescription(description));
        request.setOutTradeNo(orderNo);
        request.setNotifyUrl(notifyUrl);
        Amount amount = new Amount();
        amount.setTotal((int) amountFen);
        amount.setCurrency("CNY");
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);
        try {
            PrepayWithRequestPaymentResponse response = jsapi().prepayWithRequestPayment(request);
            String packageVal = response.getPackageVal();
            String prepayId = packageVal != null && packageVal.startsWith("prepay_id=")
                    ? packageVal.substring("prepay_id=".length())
                    : packageVal;
            return new Prepared(mchId, prepayId, response.getTimeStamp(), response.getNonceStr(), packageVal,
                    response.getSignType(), response.getPaySign());
        } catch (ServiceException exception) {
            log.warn("wechat pay prepay failed: {} {}", exception.getErrorCode(), exception.getErrorMessage());
            throw BizException.rejected("微信支付下单失败，请稍后重试");
        } catch (RuntimeException exception) {
            log.error("wechat pay prepay error", exception);
            throw BizException.rejected("微信支付下单失败，请稍后重试");
        }
    }

    @Override
    public Prepared resign(String prepayId) {
        ensureConfigured();
        if (prepayId == null || prepayId.isBlank()) {
            throw BizException.badRequest("支付参数不正确");
        }
        String packageVal = prepayId.startsWith("prepay_id=") ? prepayId : "prepay_id=" + prepayId;
        String rawPrepayId = packageVal.substring("prepay_id=".length());
        long timestamp = Instant.now().getEpochSecond();
        String nonceStr = NonceUtil.createNonce(32);
        String message = appId + "\n" + timestamp + "\n" + nonceStr + "\n" + packageVal + "\n";
        String paySign = signer().sign(message).getSign();
        return new Prepared(mchId, rawPrepayId, String.valueOf(timestamp), nonceStr, packageVal, "RSA", paySign);
    }

    @Override
    public void close(String orderNo) {
        if (!configured()) {
            return;
        }
        CloseOrderRequest request = new CloseOrderRequest();
        request.setMchid(mchId);
        request.setOutTradeNo(orderNo);
        try {
            jsapi().closeOrder(request);
        } catch (ServiceException exception) {
            log.info("wechat pay close skipped: {} {}", exception.getErrorCode(), exception.getErrorMessage());
        } catch (RuntimeException exception) {
            log.warn("wechat pay close failed for {}", orderNo, exception);
        }
    }

    @Override
    public void refund(String orderNo, String transactionId, long amountFen) {
        throw BizException.rejected("购买后不能退款");
    }

    @Override
    public Transaction parseNotify(String body, String serial, String nonce, String signature, String timestamp) {
        ensureConfigured();
        RequestParam requestParam = new RequestParam.Builder()
                .serialNumber(serial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(body)
                .build();
        try {
            return notificationParser().parse(requestParam, Transaction.class);
        } catch (ValidationException exception) {
            log.warn("wechat pay notify signature invalid", exception);
            throw BizException.unauthorized();
        }
    }

    private void ensureConfigured() {
        if (!configured()) {
            throw BizException.badRequest("微信支付尚未配置");
        }
    }

    private boolean configured() {
        return !appId.isEmpty()
                && !mchId.isEmpty()
                && !apiV3Key.isEmpty()
                && !privateKeyPath.isEmpty()
                && !mchSerial.isEmpty()
                && !notifyUrl.isEmpty();
    }

    private JsapiServiceExtension jsapi() {
        ensureClient();
        return jsapi;
    }

    private NotificationParser notificationParser() {
        ensureClient();
        return notificationParser;
    }

    private Signer signer() {
        ensureClient();
        return signer;
    }

    private void ensureClient() {
        if (jsapi != null) {
            return;
        }
        synchronized (this) {
            if (jsapi != null) {
                return;
            }
            try {
                RSAAutoCertificateConfig built = new RSAAutoCertificateConfig.Builder()
                        .merchantId(mchId)
                        .privateKeyFromPath(privateKeyPath)
                        .merchantSerialNumber(mchSerial)
                        .apiV3Key(apiV3Key)
                        .build();
                this.signer = built.createSigner();
                this.jsapi = new JsapiServiceExtension.Builder().config(built).signType("RSA").build();
                this.notificationParser = new NotificationParser(built);
            } catch (RuntimeException exception) {
                log.error("wechat pay client init failed", exception);
                throw BizException.badRequest("微信支付尚未配置");
            }
        }
    }

    private static String clipDescription(String description) {
        String text = description == null || description.isBlank() ? "健身房订单" : description.trim();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 127) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        int size = 0;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            int next = ch.getBytes(StandardCharsets.UTF_8).length;
            if (size + next > 124) {
                break;
            }
            builder.append(ch);
            size += next;
        }
        return builder + "...";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
