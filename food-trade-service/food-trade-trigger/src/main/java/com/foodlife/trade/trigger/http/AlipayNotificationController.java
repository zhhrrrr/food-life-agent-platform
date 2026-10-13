package com.foodlife.trade.trigger.http;

import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.provider.PaymentProviderRouter;
import com.foodlife.trade.domain.order.payment.service.ExternalPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@ConditionalOnProperty(prefix = "food.payment.alipay", name = "enabled", havingValue = "true")
public class AlipayNotificationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlipayNotificationController.class);
    private final PaymentProviderRouter providers;
    private final ExternalPaymentService payments;

    public AlipayNotificationController(PaymentProviderRouter providers, ExternalPaymentService payments) {
        this.providers = providers;
        this.payments = payments;
    }

    @PostMapping(value = "/api/trade/pay/callback/alipay", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notifyPayment(@RequestParam Map<String, String> parameters) {
        try {
            providers.route(PaymentChannelConstants.ALIPAY_SANDBOX).verifyNotification(parameters);
            String status = parameters.get("trade_status");
            if (!"TRADE_SUCCESS".equals(status) && !"TRADE_FINISHED".equals(status)) { return "success"; }
            PaymentCallbackCommand command = new PaymentCallbackCommand();
            command.setChannel(PaymentChannelConstants.ALIPAY_SANDBOX);
            // 1. 获取商户支付单号（你的 Java 系统生成）
            command.setPayOrderNo(parameters.get("out_trade_no"));
            // 2. 获取支付宝交易流水号（支付宝生成）
            command.setOutTradeNo(parameters.get("trade_no"));
            command.setPayAmount(new BigDecimal(parameters.get("total_amount")).movePointRight(2).longValueExact());
            command.setPayTime(parameters.containsKey("gmt_payment")
                    ? LocalDateTime.parse(parameters.get("gmt_payment"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
            command.setProviderParameters(parameters);
            payments.acceptPaidPayment(command);
            return "success";
        } catch (Exception e) {
            // Do not log the notification or signature. Alipay retries failures.
            LOGGER.warn("Alipay notification not accepted, payOrderNo={}, reason={}",
                    parameters.get("out_trade_no"), e.getMessage());
            return "failure";
        }
    }
}
