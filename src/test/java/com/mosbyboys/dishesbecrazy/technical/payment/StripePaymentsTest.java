package com.mosbyboys.dishesbecrazy.technical.payment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StripePaymentsTest {
    private final StripeClient client = mock(StripeClient.class, RETURNS_DEEP_STUBS);
    private final StripePayments payments = new StripePayments(client, "whsec_local_test_only");
    private final UUID checkoutId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Test
    void springSelectsTheConfiguredConstructorWithoutMakingANetworkCall() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "test", Map.of("stripe.secret-key", "sk_test_local_only", "stripe.webhook-secret", "whsec_local_only")));
            context.registerBean(tools.jackson.databind.json.JsonMapper.class,
                    () -> tools.jackson.databind.json.JsonMapper.builder().build());
            context.register(StripePayments.class);
            context.refresh();
            assertNotNull(context.getBean(StripePayments.class));
        }
    }

    @Test
    void createsManualCaptureWithStableKeyAndCheckoutOwnership() throws Exception {
        when(client.v1().paymentIntents().create(any(PaymentIntentCreateParams.class), any(RequestOptions.class)))
                .thenReturn(new PaymentIntent());
        payments.create(checkoutId, customerId, 1234L);
        var params = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
        var options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(client.v1().paymentIntents()).create(params.capture(), options.capture());
        assertEquals(1234L, params.getValue().getAmount());
        assertEquals("cad", params.getValue().getCurrency());
        assertEquals(PaymentIntentCreateParams.CaptureMethod.MANUAL, params.getValue().getCaptureMethod());
        assertEquals(checkoutId.toString(), params.getValue().getMetadata().get("checkout_id"));
        assertEquals(customerId.toString(), params.getValue().getMetadata().get("customer_id"));
        assertEquals("dishes-create-" + checkoutId, options.getValue().getIdempotencyKey());
    }

    @Test
    void captureRetriesReuseAnIdempotencyKey() throws Exception {
        payments.capture("pi_test", checkoutId);
        payments.capture("pi_test", checkoutId);
        var options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(client.v1().paymentIntents(), times(2)).capture(eq("pi_test"), options.capture());
        assertEquals(options.getAllValues().get(0).getIdempotencyKey(), options.getAllValues().get(1).getIdempotencyKey());
    }

    @Test
    void checkoutRequiresWebhookRecoveryConfiguration() {
        var unconfigured = new StripePayments(client, "");
        var error = assertThrows(ResponseStatusException.class, () -> unconfigured.create(checkoutId, customerId, 1234));
        assertEquals(503, error.getStatusCode().value());
        verifyNoInteractions(client);
    }

    @Test
    void providerFailuresExposeNoPaymentDetails() throws Exception {
        when(client.v1().paymentIntents().capture(eq("pi_test"), any(RequestOptions.class)))
                .thenThrow(mock(StripeException.class));
        var error = assertThrows(ResponseStatusException.class, () -> payments.capture("pi_test", checkoutId));
        assertEquals(503, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Retry this checkout"));
    }

    @Test
    void acceptsSignedAuthorizationAndSuccessCallbacks() throws Exception {
        for (String type : new String[] {"payment_intent.amount_capturable_updated", "payment_intent.succeeded"}) {
            String payload = payload(type, "dishes-checkout-v1", Stripe.API_VERSION);
            assertEquals(checkoutId, payments.verifiedCheckout(payload, signature(payload)).orElseThrow());
        }
    }

    @Test
    void rejectsMissingAndIncorrectSignatures() {
        String payload = payload("payment_intent.succeeded", "dishes-checkout-v1", Stripe.API_VERSION);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> payments.verifiedCheckout(payload, null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> payments.verifiedCheckout(payload, "t=1,v1=invalid")).getStatusCode().value());
    }

    @Test
    void rejectsMalformedPayloadsWithoutExposingParserErrors() throws Exception {
        for (String payload : new String[] {"null", "{}", "broken-json"}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> payments.verifiedCheckout(payload, signature(payload))).getStatusCode().value());
        }
    }

    @Test
    void ignoresUnrelatedSignedEventsAndIntegrations() throws Exception {
        String otherIntegration = payload("payment_intent.succeeded", "another-app", Stripe.API_VERSION);
        assertTrue(payments.verifiedCheckout(otherIntegration, signature(otherIntegration)).isEmpty());
        String otherEvent = payload("payment_intent.created", "dishes-checkout-v1", Stripe.API_VERSION);
        assertTrue(payments.verifiedCheckout(otherEvent, signature(otherEvent)).isEmpty());
    }

    @Test
    void olderCallbackVersionsCanRecoverThroughMetadataWithoutUnsafeTypedDeserialization() throws Exception {
        String payload = payload("payment_intent.succeeded", "dishes-checkout-v1", "2020-08-27");
        assertEquals(checkoutId, payments.verifiedCheckout(payload, signature(payload)).orElseThrow());
    }

    private String payload(String type, String integration, String version) {
        return """
                {"id":"evt_local_test","object":"event","type":"%s","api_version":"%s",
                 "data":{"object":{"id":"pi_local_test","object":"payment_intent",
                 "metadata":{"integration":"%s","checkout_id":"%s"}}}}
                """.formatted(type, version, integration, checkoutId);
    }

    private String signature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_local_test_only".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + hash;
    }
    // SDK calls are mocked; signature verification is real and uses a disposable test secret.
}
