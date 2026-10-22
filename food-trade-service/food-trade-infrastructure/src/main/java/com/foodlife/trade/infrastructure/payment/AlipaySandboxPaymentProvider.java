package com.foodlife.trade.infrastructure.payment;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeCloseModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.foodlife.trade.domain.order.payment.constant.PaymentChannelConstants;
import com.foodlife.trade.domain.order.payment.constant.RefundOrderStatusConstants;
import com.foodlife.trade.domain.order.payment.model.*;
import com.foodlife.trade.domain.order.payment.provider.IPaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Date;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "food.payment.alipay", name = "enabled", havingValue = "true")
public class AlipaySandboxPaymentProvider implements IPaymentProvider {
    private final AlipayClient client;
    private final AlipaySandboxProperties properties;
    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AlipaySandboxPaymentProvider(AlipayClient client, AlipaySandboxProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String channel() { return PaymentChannelConstants.ALIPAY_SANDBOX; }

    @Override
    public void validatePrepare(PaymentPrepareCommand command) {
        if (command == null || command.getUserId() == null || command.getOrderId() == null) {
            throw new IllegalArgumentException("payment prepare command invalid");
        }
    }

    @Override
    public String buildPaymentUrl(PaymentOrderEntity order) {
        try {
            return client.pageExecute(pagePayRequest(order), "GET").getBody();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay payment URL creation failed", e);
        }
    }

    @Override
    public String buildPaymentForm(PaymentOrderEntity order) {
        try {
            return client.pageExecute(pagePayRequest(order), "POST").getBody();
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay payment form creation failed", e);
        }
    }

    private AlipayTradePagePayRequest pagePayRequest(PaymentOrderEntity order) {
        if (order.getPayAmount() == null || order.getPayAmount() <= 0) {
            throw new IllegalArgumentException("payment amount must be positive");
        }
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(order.getPayOrderNo());
        model.setTotalAmount(yuan(order.getPayAmount()));
        model.setSubject("美食生活订单 " + order.getOrderNo());
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        ZonedDateTime expires = order.getCreateTime().atZone(ZoneId.systemDefault()).plusMinutes(30);
        if (!expires.toInstant().isAfter(Instant.now())) {
            throw new IllegalArgumentException("payment order expired");
        }
        model.setTimeExpire(expires.withZoneSameInstant(ZoneId.of("Asia/Shanghai")).format(ALIPAY_TIME));
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setBizModel(model);
        request.setNotifyUrl(properties.getNotifyUrl());
        request.setReturnUrl(UriComponentsBuilder.fromUriString(properties.getReturnUrl())
                .queryParam("payOrderNo", order.getPayOrderNo()).build().toUriString());
        return request;
    }

    private static LocalDateTime localTime(Date value) {
        return value == null ? LocalDateTime.now()
                : value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    @Override
    public void verifyNotification(Map<String, String> parameters) {
        try {
            if (!"RSA2".equals(parameters.get("sign_type"))
                    || !AlipaySignature.rsaCheckV1(new java.util.HashMap<>(parameters),
                    AlipaySandboxConfiguration.readKey(properties.getPublicKeyPath()), "UTF-8", "RSA2")) {
                throw new IllegalArgumentException("Alipay notification signature invalid");
            }
            if (!properties.getAppId().equals(parameters.get("app_id"))
                    || !properties.getSellerId().equals(parameters.get("seller_id"))) {
                throw new IllegalArgumentException("Alipay notification application or seller mismatch");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Alipay notification verification failed");
        }
    }

    @Override
    public void verifyPaySuccessCallback(PaymentCallbackCommand command, PaymentOrderEntity order) {
        if (!channel().equals(order.getChannel()) || !channel().equals(command.getChannel())) {
            throw new IllegalArgumentException("Alipay payment channel mismatch");
        }
        if (command.getProviderParameters() != null) {
            Map<String, String> parameters = command.getProviderParameters();
            verifyNotification(parameters);
            if (!paid(parameters.get("trade_status"))
                    || !Objects.equals(order.getPayOrderNo(), parameters.get("out_trade_no"))
                    || !Objects.equals(command.getOutTradeNo(), parameters.get("trade_no"))
                    || cents(parameters.get("total_amount")) != order.getPayAmount()
                    || !Objects.equals(command.getPayAmount(), order.getPayAmount())) {
                throw new IllegalArgumentException("Alipay notification trade or amount mismatch");
            }
        } else {
            PaymentCallbackCommand verified = queryPaidPayment(order);
            if (verified == null || !Objects.equals(verified.getOutTradeNo(), command.getOutTradeNo())
                    || !Objects.equals(verified.getPayAmount(), command.getPayAmount())) {
                throw new IllegalArgumentException("Alipay has not confirmed payment");
            }
        }
        if (order.getOutTradeNo() != null && !order.getOutTradeNo().equals(command.getOutTradeNo())) {
            throw new IllegalArgumentException("Alipay trade number changed");
        }
    }

    @Override
    public PaymentCallbackCommand queryPaidPayment(PaymentOrderEntity order) {
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(order.getPayOrderNo());
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeQueryResponse response = client.execute(request);
            if (!response.isSuccess()) {
                if ("ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) { return null; }
                throw new IllegalArgumentException("Alipay query failed: " + response.getSubCode());
            }
            if (!order.getPayOrderNo().equals(response.getOutTradeNo())
                    || cents(response.getTotalAmount()) != order.getPayAmount()) {
                throw new IllegalArgumentException("Alipay query trade or amount mismatch");
            }
            if (!paid(response.getTradeStatus())) { return null; }
            PaymentCallbackCommand command = new PaymentCallbackCommand();
            command.setChannel(channel());
            command.setPayOrderNo(order.getPayOrderNo());
            command.setOutTradeNo(response.getTradeNo());
            command.setPayAmount(order.getPayAmount());
            command.setPayTime(localTime(response.getSendPayDate()));
            return command;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay query unavailable", e);
        }
    }

    @Override
    public void closePayment(PaymentOrderEntity order) {
        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(order.getPayOrderNo());
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        request.setBizModel(model);
        try {
            AlipayTradeCloseResponse response = client.execute(request);
            if (response.isSuccess() && !order.getPayOrderNo().equals(response.getOutTradeNo())) {
                throw new IllegalArgumentException("Alipay close response trade mismatch");
            }
            if (!response.isSuccess() && !"ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())
                    && !"ACQ.TRADE_HAS_CLOSE".equals(response.getSubCode())) {
                if ("ACQ.TRADE_STATUS_ERROR".equals(response.getSubCode()) && alreadyClosed(order)) { return; }
                throw new IllegalArgumentException("Alipay close failed: " + response.getSubCode());
            }
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay close unavailable", e);
        }
    }

    private boolean alreadyClosed(PaymentOrderEntity order) throws AlipayApiException {
        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(order.getPayOrderNo());
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizModel(model);
        AlipayTradeQueryResponse response = client.execute(request);
        return response != null && response.isSuccess() && "TRADE_CLOSED".equals(response.getTradeStatus())
                && order.getPayOrderNo().equals(response.getOutTradeNo())
                && cents(response.getTotalAmount()) == order.getPayAmount();
    }

    @Override
    public PaymentRefundResult refund(PaymentRefundCommand command, PaymentOrderEntity order) {
        if (!Objects.equals(command.getPayOrderNo(), order.getPayOrderNo())
                || !Objects.equals(command.getRefundAmount(), order.getPayAmount())) {
            throw new IllegalArgumentException("Alipay refund order or amount mismatch");
        }
        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(order.getPayOrderNo());
        model.setOutRequestNo(command.getRefundOrderNo());
        model.setRefundAmount(yuan(command.getRefundAmount()));
        model.setRefundReason(command.getRefundReason());
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizModel(model);
        try {
            AlipayTradeRefundResponse response = client.execute(request);
            if (!response.isSuccess()) {
                throw new IllegalArgumentException("Alipay refund failed: " + response.getSubCode());
            }
            if (!order.getPayOrderNo().equals(response.getOutTradeNo())
                    || !Objects.equals(command.getOutTradeNo() == null ? order.getOutTradeNo() : command.getOutTradeNo(), response.getTradeNo())
                    || cents(response.getRefundFee()) != command.getRefundAmount()) {
                throw new IllegalArgumentException("Alipay refund response mismatch");
            }
            PaymentRefundResult result = new PaymentRefundResult();
            result.setRefundOrderNo(command.getRefundOrderNo());
            result.setOutRefundNo(command.getRefundOrderNo());
            result.setRefundStatus(RefundOrderStatusConstants.SUCCESS);
            result.setRefundTime(localTime(response.getGmtRefundPay()));
            result.setRawResult("Alipay sandbox refund confirmed");
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay refund unavailable; retry the same refund request", e);
        }
    }

    @Override
    public PaymentRefundResult queryRefund(PaymentRefundCommand command, PaymentOrderEntity order) {
        if (!Objects.equals(command.getPayOrderNo(), order.getPayOrderNo())
                || !Objects.equals(command.getRefundAmount(), order.getPayAmount())) {
            throw new IllegalArgumentException("Alipay refund query order or amount mismatch");
        }
        AlipayTradeFastpayRefundQueryModel model = new AlipayTradeFastpayRefundQueryModel();
        model.setOutTradeNo(command.getPayOrderNo());
        model.setOutRequestNo(command.getRefundOrderNo());
        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        request.setBizModel(model);
        try {
            AlipayTradeFastpayRefundQueryResponse response = client.execute(request);
            if (!response.isSuccess()) {
                if ("ACQ.REFUND_NOT_EXIST".equals(response.getSubCode())
                        || "ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())) { return null; }
                throw new IllegalStateException("Alipay refund query failed: " + response.getSubCode());
            }
            // The sandbox returns 10000 with empty fields for a non-existent refund request.
            // Only REFUND_SUCCESS carries a financial confirmation to validate.
            if (!"REFUND_SUCCESS".equals(response.getRefundStatus())) { return null; }
            if (!Objects.equals(command.getPayOrderNo(), response.getOutTradeNo())
                    || !Objects.equals(command.getRefundOrderNo(), response.getOutRequestNo())
                    || !Objects.equals(command.getOutTradeNo() == null ? order.getOutTradeNo() : command.getOutTradeNo(),
                            response.getTradeNo())) {
                throw new IllegalArgumentException("Alipay refund query identity mismatch");
            }
            if (cents(response.getRefundAmount()) != command.getRefundAmount()) {
                throw new IllegalArgumentException("Alipay refund query amount mismatch");
            }
            PaymentRefundResult result = new PaymentRefundResult();
            result.setRefundOrderNo(command.getRefundOrderNo());
            result.setOutRefundNo(command.getRefundOrderNo());
            result.setRefundStatus(RefundOrderStatusConstants.SUCCESS);
            result.setRefundTime(localTime(response.getGmtRefundPay()));
            result.setRawResult("Alipay sandbox refund query confirmed");
            return result;
        } catch (AlipayApiException e) {
            throw new IllegalStateException("Alipay refund query unavailable; do not infer failure", e);
        }
    }

    static String yuan(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
    static long cents(String yuan) { return new BigDecimal(yuan).movePointRight(2).longValueExact(); }
    private static boolean paid(String status) {
        return "TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status);
    }
}
