package com.foodlife.trade.infrastructure.payment;

import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.constant.RefundOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.PaymentCallbackCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentOrderEntity;
import com.foodlife.trade.domain.order.payment.model.PaymentPrepareCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentRefundCommand;
import com.foodlife.trade.domain.order.payment.model.PaymentRefundResult;
import com.foodlife.trade.domain.order.payment.provider.IPaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class LocalPaymentProvider implements IPaymentProvider {

    private final String callbackSecret;

    public LocalPaymentProvider(@Value("${food.payment.local.callback-secret:local-payment-secret}") String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }

    @Override
    public String channel() {
        return PaymentChannelConstants.LOCAL_PAY;
    }

    @Override
    public void validatePrepare(PaymentPrepareCommand command) {
        if (command == null || command.getOrderId() == null || command.getUserId() == null) {
            throw new IllegalArgumentException("payment prepare command invalid");
        }
    }

    @Override
    public void verifyPaySuccessCallback(PaymentCallbackCommand command, PaymentOrderEntity paymentOrder) {
        if (paymentOrder == null) {
            throw new IllegalArgumentException("payment order not found");
        }
        if (!paymentOrder.getPayAmount().equals(command.getPayAmount())) {
            throw new IllegalArgumentException("pay amount mismatch");
        }
        verifySignature(command);
    }

    @Override
    public PaymentRefundResult refund(PaymentRefundCommand command, PaymentOrderEntity paymentOrder) {
        if (command == null || paymentOrder == null) {
            throw new IllegalArgumentException("payment refund command invalid");
        }
        if (!paymentOrder.getPayOrderNo().equals(command.getPayOrderNo())) {
            throw new IllegalArgumentException("payment refund order not matched");
        }
        if (!paymentOrder.getPayAmount().equals(command.getRefundAmount())) {
            throw new IllegalArgumentException("refund amount mismatch");
        }

        PaymentRefundResult result = new PaymentRefundResult();
        result.setRefundOrderNo(command.getRefundOrderNo());
        result.setOutRefundNo("LOCAL_REFUND_" + command.getRefundOrderNo());
        result.setRefundStatus(RefundOrderStatusConstants.SUCCESS);
        result.setRefundTime(LocalDateTime.now());
        result.setRawResult("local payment refund accepted");
        return result;
    }

    private void verifySignature(PaymentCallbackCommand command) {
        if (command.getSignature() == null || command.getSignature().trim().isEmpty()) {
            throw new IllegalArgumentException("payment callback signature required");
        }
        if (command.getSignType() != null && !"SHA256".equals(command.getSignType().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("payment callback signType not supported");
        }
        String expectedSignature = sign(command.getPayOrderNo(), command.getOutTradeNo(), command.getPayAmount());
        if (!expectedSignature.equalsIgnoreCase(command.getSignature().trim())) {
            throw new IllegalArgumentException("payment callback signature invalid");
        }
    }

    private String sign(String payOrderNo, String outTradeNo, Long payAmount) {
        String plainText = payOrderNo + "|" + outTradeNo + "|" + payAmount + "|" + PaymentChannelConstants.LOCAL_PAY + "|" + callbackSecret;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
