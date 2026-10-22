package com.foodlife.trade.infrastructure.payment;

import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.foodlife.trade.domain.order.payment.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlipaySandboxPaymentProviderTest {
    @TempDir Path keys;
    private final AlipayClient client = mock(AlipayClient.class);
    private AlipaySandboxPaymentProvider provider;
    private AlipaySandboxProperties properties;
    private KeyPair signingKeys;
    private PaymentOrderEntity order;

    @BeforeEach
    void setup() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKeys = generator.generateKeyPair();
        Path publicFile = keys.resolve("alipay-public.txt");
        Files.writeString(publicFile, Base64.getEncoder().encodeToString(signingKeys.getPublic().getEncoded()));
        properties = new AlipaySandboxProperties();
        properties.setAppId("sandbox-app");
        properties.setSellerId("sandbox-seller");
        properties.setPublicKeyPath(publicFile.toString());
        properties.setNotifyUrl("https://example.test/api/trade/pay/callback/alipay");
        provider = new AlipaySandboxPaymentProvider(client, properties);
        order = new PaymentOrderEntity();
        order.setPayOrderNo("PAY_TEST");
        order.setOrderNo("ORDER_TEST");
        order.setChannel("ALIPAY_SANDBOX");
        order.setPayAmount(16800L);
        order.setCreateTime(LocalDateTime.now());
    }

    private Map<String, String> parameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("app_id", "sandbox-app");
        parameters.put("seller_id", "sandbox-seller");
        parameters.put("sign_type", "RSA2");
        parameters.put("out_trade_no", "PAY_TEST");
        parameters.put("trade_no", "ALIPAY_TEST");
        parameters.put("trade_status", "TRADE_SUCCESS");
        parameters.put("total_amount", "168.00");
        return parameters;
    }

    private void sign(Map<String, String> parameters) throws Exception {
        Map<String, String> content = new HashMap<>(parameters);
        content.remove("sign_type");
        content.remove("sign");
        parameters.put("sign", AlipaySignature.rsaSign(AlipaySignature.getSignContent(content),
                Base64.getEncoder().encodeToString(signingKeys.getPrivate().getEncoded()), "UTF-8", "RSA2"));
    }

    private PaymentCallbackCommand callback(Map<String, String> parameters) {
        PaymentCallbackCommand command = new PaymentCallbackCommand();
        command.setChannel("ALIPAY_SANDBOX");
        command.setPayOrderNo("PAY_TEST");
        command.setOutTradeNo("ALIPAY_TEST");
        command.setPayAmount(16800L);
        command.setProviderParameters(parameters);
        return command;
    }

    @Test void acceptAuthenticMatchingNotification() throws Exception {
        Map<String, String> parameters = parameters();
        sign(parameters);
        provider.verifyPaySuccessCallback(callback(parameters), order);
        provider.verifyPaySuccessCallback(callback(parameters), order);
        assertThat(parameters).containsKeys("sign", "sign_type");
    }

    @Test void rejectTamperedSignature() throws Exception {
        Map<String, String> parameters = parameters();
        sign(parameters);
        parameters.put("total_amount", "1.00");
        assertThatThrownBy(() -> provider.verifyNotification(parameters)).hasMessageContaining("signature invalid");
    }

    @Test void rejectValidSignatureForAnotherSeller() throws Exception {
        Map<String, String> parameters = parameters();
        parameters.put("seller_id", "another-seller");
        sign(parameters);
        assertThatThrownBy(() -> provider.verifyNotification(parameters)).hasMessageContaining("seller mismatch");
    }

    @Test void rejectValidSignatureForAnotherApplication() throws Exception {
        Map<String, String> parameters = parameters();
        parameters.put("app_id", "another-app");
        sign(parameters);
        assertThatThrownBy(() -> provider.verifyNotification(parameters)).hasMessageContaining("application or seller mismatch");
    }

    @Test void rejectSignedWrongAmountOrUnpaidStatus() throws Exception {
        Map<String, String> parameters = parameters();
        parameters.put("total_amount", "1.00");
        sign(parameters);
        assertThatThrownBy(() -> provider.verifyPaySuccessCallback(callback(parameters), order)).hasMessageContaining("amount mismatch");
        parameters.put("total_amount", "168.00");
        parameters.put("trade_status", "WAIT_BUYER_PAY");
        sign(parameters);
        assertThatThrownBy(() -> provider.verifyPaySuccessCallback(callback(parameters), order)).hasMessageContaining("mismatch");
    }

    @Test void rejectCallbackThroughLocalChannel() {
        PaymentCallbackCommand command = callback(parameters());
        command.setChannel("LOCAL_PAY");
        assertThatThrownBy(() -> provider.verifyPaySuccessCallback(command, order)).hasMessageContaining("channel mismatch");
    }

    @Test void createPagePayUsingServerAmountAndReturnOrderNumber() throws Exception {
        AlipayTradePagePayResponse response = new AlipayTradePagePayResponse();
        response.setBody("https://openapi-sandbox.dl.alipaydev.com/gateway.do?sign=test");
        when(client.pageExecute(any(AlipayTradePagePayRequest.class), eq("GET"))).thenReturn(response);
        provider.buildPaymentUrl(order);
        ArgumentCaptor<AlipayTradePagePayRequest> request = ArgumentCaptor.forClass(AlipayTradePagePayRequest.class);
        verify(client).pageExecute(request.capture(), eq("GET"));
        assertThat(((com.alipay.api.domain.AlipayTradePagePayModel) request.getValue().getBizModel()).getOutTradeNo()).isEqualTo("PAY_TEST");
        assertThat(request.getValue().getReturnUrl()).endsWith("?payOrderNo=PAY_TEST");
        assertThat(AlipaySandboxPaymentProvider.yuan(16800)).isEqualTo("168.00");
        assertThatThrownBy(() -> AlipaySandboxPaymentProvider.cents("0.001")).isInstanceOf(ArithmeticException.class);
    }

    @Test void createPostCheckoutWithActualSdkPreservingCharsetAndChineseSubject() {
        String privateKey = Base64.getEncoder().encodeToString(signingKeys.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(signingKeys.getPublic().getEncoded());
        AlipayClient actualClient = new com.alipay.api.DefaultAlipayClient(properties.getGatewayUrl(),
                properties.getAppId(), privateKey, "json", "utf-8", publicKey, "RSA2");
        String html = new AlipaySandboxPaymentProvider(actualClient, properties).buildPaymentForm(order);
        assertThat(html).contains("method=\"post\"", "gateway.do?charset=utf-8", "美食生活订单", "alipay.trade.page.pay", "168.00");
        assertThat(html).doesNotContain(privateKey);
    }

    @Test
    void checkoutDeadlineRepresentsThirtyRealMinutesInAlipayTime() throws Exception {
        order.setCreateTime(LocalDateTime.now());
        AlipayTradePagePayResponse response = new AlipayTradePagePayResponse();
        response.setBody("sandbox-checkout");
        when(client.pageExecute(any(AlipayTradePagePayRequest.class), eq("POST"))).thenReturn(response);
        provider.buildPaymentForm(order);
        ArgumentCaptor<AlipayTradePagePayRequest> request = ArgumentCaptor.forClass(AlipayTradePagePayRequest.class);
        verify(client).pageExecute(request.capture(), eq("POST"));
        String expires = ((com.alipay.api.domain.AlipayTradePagePayModel) request.getValue().getBizModel()).getTimeExpire();
        Instant deadline = LocalDateTime.parse(expires, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        assertThat(Math.abs(Duration.between(Instant.now().plusSeconds(1800), deadline).toSeconds())).isLessThanOrEqualTo(3);
        order.setCreateTime(LocalDateTime.now().minusMinutes(31));
        assertThatThrownBy(() -> provider.buildPaymentForm(order)).hasMessage("payment order expired");
    }

    @Test
    void queriedPaymentTimeUsesTheSameLocalClockAsDomainOrders() throws Exception {
        Instant paidAt = Instant.parse("2026-10-22T07:00:00Z");
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("10000");
        response.setOutTradeNo("PAY_TEST");
        response.setTradeNo("ALIPAY_TEST");
        response.setTradeStatus("TRADE_SUCCESS");
        response.setTotalAmount("168.00");
        response.setSendPayDate(Date.from(paidAt));
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        assertThat(provider.queryPaidPayment(order).getPayTime().atZone(ZoneId.systemDefault()).toInstant()).isEqualTo(paidAt);
    }

    @Test void failClosedWhenProviderCannotClosePaidTrade() throws Exception {
        AlipayTradeCloseResponse response = new AlipayTradeCloseResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.TRADE_STATUS_ERROR");
        when(client.execute(any(AlipayTradeCloseRequest.class))).thenReturn(response);
        assertThatThrownBy(() -> provider.closePayment(order)).hasMessageContaining("close failed");
    }

    @Test void queryNotExistingTradeIsPendingButOtherErrorsFail() throws Exception {
        AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.TRADE_NOT_EXIST");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(response);
        assertThat(provider.queryPaidPayment(order)).isNull();
        response.setSubCode("ACQ.SYSTEM_ERROR");
        assertThatThrownBy(() -> provider.queryPaidPayment(order)).hasMessageContaining("query failed");
    }

    @Test void retryCloseAcceptsVerifiedAlreadyClosedTrade() throws Exception {
        AlipayTradeCloseResponse close = new AlipayTradeCloseResponse();
        close.setCode("40004");
        close.setSubCode("ACQ.TRADE_STATUS_ERROR");
        when(client.execute(any(AlipayTradeCloseRequest.class))).thenReturn(close);
        AlipayTradeQueryResponse query = new AlipayTradeQueryResponse();
        query.setCode("10000");
        query.setOutTradeNo("PAY_TEST");
        query.setTotalAmount("168.00");
        query.setTradeStatus("TRADE_CLOSED");
        when(client.execute(any(AlipayTradeQueryRequest.class))).thenReturn(query);
        provider.closePayment(order);
        query.setTradeStatus("TRADE_SUCCESS");
        assertThatThrownBy(() -> provider.closePayment(order)).hasMessageContaining("close failed");
    }

    private PaymentRefundCommand refundCommand() {
        order.setOutTradeNo("ALIPAY_TEST");
        PaymentRefundCommand command = new PaymentRefundCommand();
        command.setPayOrderNo("PAY_TEST");
        command.setOutTradeNo("ALIPAY_TEST");
        command.setRefundOrderNo("RFPAY_TEST");
        command.setRefundAmount(16800L);
        return command;
    }

    private AlipayTradeFastpayRefundQueryResponse refundQueryResponse() {
        AlipayTradeFastpayRefundQueryResponse response = new AlipayTradeFastpayRefundQueryResponse();
        response.setCode("10000");
        response.setOutTradeNo("PAY_TEST");
        response.setOutRequestNo("RFPAY_TEST");
        response.setTradeNo("ALIPAY_TEST");
        response.setRefundAmount("168.00");
        response.setRefundStatus("REFUND_SUCCESS");
        return response;
    }

    @Test void refundQueryRequiresSuccessfulRefundStatusAndMatchingAmount() throws Exception {
        PaymentRefundCommand command = refundCommand();
        AlipayTradeFastpayRefundQueryResponse response = refundQueryResponse();
        when(client.execute(any(AlipayTradeFastpayRefundQueryRequest.class))).thenReturn(response);
        assertThat(provider.queryRefund(command, order).getRefundStatus()).isEqualTo("SUCCESS");
        response.setRefundStatus(null);
        assertThat(provider.queryRefund(command, order)).isNull();
        response.setRefundStatus("REFUND_SUCCESS");
        response.setRefundAmount("1.00");
        assertThatThrownBy(() -> provider.queryRefund(command, order)).hasMessageContaining("amount mismatch");
    }

    @Test void emptySuccessfulQueryMeansRefundNotYetConfirmed() throws Exception {
        PaymentRefundCommand command = refundCommand();
        AlipayTradeFastpayRefundQueryResponse response = new AlipayTradeFastpayRefundQueryResponse();
        response.setCode("10000");
        when(client.execute(any(AlipayTradeFastpayRefundQueryRequest.class))).thenReturn(response);
        assertThat(provider.queryRefund(command, order)).isNull();
    }

    @Test void refundQueryRejectsAnotherRequestEvenWhenApiCallSucceeded() throws Exception {
        PaymentRefundCommand command = refundCommand();
        AlipayTradeFastpayRefundQueryResponse response = refundQueryResponse();
        response.setOutRequestNo("OTHER_REFUND");
        when(client.execute(any(AlipayTradeFastpayRefundQueryRequest.class))).thenReturn(response);
        assertThatThrownBy(() -> provider.queryRefund(command, order)).hasMessageContaining("identity mismatch");
    }

    @Test void refundQueryOnlyTreatsNotExistingRefundAsSafeToRetry() throws Exception {
        PaymentRefundCommand command = refundCommand();
        AlipayTradeFastpayRefundQueryResponse response = refundQueryResponse();
        response.setCode("40004");
        response.setSubCode("ACQ.REFUND_NOT_EXIST");
        when(client.execute(any(AlipayTradeFastpayRefundQueryRequest.class))).thenReturn(response);
        assertThat(provider.queryRefund(command, order)).isNull();
        response.setSubCode("ACQ.SYSTEM_ERROR");
        assertThatThrownBy(() -> provider.queryRefund(command, order)).hasMessageContaining("query failed");
    }

    @Test void refundUsesSameRequestNumberAndExactAmountOnRetry() throws Exception {
        order.setOutTradeNo("ALIPAY_TEST");
        PaymentRefundCommand command = new PaymentRefundCommand();
        command.setPayOrderNo("PAY_TEST");
        command.setRefundOrderNo("RFPAY_TEST");
        command.setRefundAmount(16800L);
        AlipayTradeRefundResponse response = new AlipayTradeRefundResponse();
        response.setCode("10000");
        response.setOutTradeNo("PAY_TEST");
        response.setTradeNo("ALIPAY_TEST");
        response.setRefundFee("168.00");
        when(client.execute(any(AlipayTradeRefundRequest.class))).thenReturn(response);
        provider.refund(command, order);
        provider.refund(command, order);
        ArgumentCaptor<AlipayTradeRefundRequest> request = ArgumentCaptor.forClass(AlipayTradeRefundRequest.class);
        verify(client, times(2)).execute(request.capture());
        for (AlipayTradeRefundRequest value : request.getAllValues()) {
            com.alipay.api.domain.AlipayTradeRefundModel model = (com.alipay.api.domain.AlipayTradeRefundModel) value.getBizModel();
            assertThat(model.getOutRequestNo()).isEqualTo("RFPAY_TEST");
            assertThat(model.getRefundAmount()).isEqualTo("168.00");
        }
    }
}
