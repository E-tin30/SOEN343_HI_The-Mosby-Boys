package com.mosbyboys.dishesbecrazy.technical.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Event;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StripePayments {
    private final StripeClient client;
    private final String webhookSecret;
    private final JsonMapper json;

    @Autowired
    public StripePayments(@Value("${stripe.secret-key}") String secretKey,
                          @Value("${stripe.webhook-secret:}") String webhookSecret, JsonMapper json) {
        this(new StripeClient(secretKey), webhookSecret, json);
    }

    StripePayments(StripeClient client, String webhookSecret) {
        this(client, webhookSecret, JsonMapper.builder().build());
    }

    private StripePayments(StripeClient client, String webhookSecret, JsonMapper json) {
        this.client = client;
        this.webhookSecret = webhookSecret;
        this.json = json;
    }

    public PaymentIntent create(UUID checkoutId, UUID customerId, long totalMinor) {
        if (webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment recovery is not configured. Set STRIPE_WEBHOOK_SECRET before checkout.");
        }
        var params = PaymentIntentCreateParams.builder().setAmount(totalMinor).setCurrency("cad")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL).addPaymentMethodType("card")
                .putMetadata("integration", "dishes-checkout-v1")
                .putMetadata("checkout_id", checkoutId.toString()).putMetadata("customer_id", customerId.toString()).build();
        try {
            return client.v1().paymentIntents().create(params, options("create", checkoutId));
        } catch (StripeException error) {
            throw unavailable();
        }
    }
    // Authorizes cards first; only the backend captures funds after order validation and persistence preparation.

    public PaymentIntent retrieve(String id) {
        try {
            return client.v1().paymentIntents().retrieve(id, RequestOptions.builder()
                    .setConnectTimeout(5000).setReadTimeout(10000).setMaxNetworkRetries(1).build());
        } catch (StripeException error) {
            throw unavailable();
        }
    }

    public PaymentIntent capture(String id, UUID checkoutId) {
        try {
            return client.v1().paymentIntents().capture(id, options("capture", checkoutId));
        } catch (StripeException error) {
            throw unavailable();
        }
    }

    public void cancel(String id, UUID checkoutId) {
        try {
            client.v1().paymentIntents().cancel(id, options("cancel", checkoutId));
        } catch (StripeException error) {
            throw unavailable();
        }
    }
    // Stable keys allow retries after a timeout without repeating a successful Stripe operation.

    public Optional<UUID> verifiedCheckout(String payload, String signature) {
        if (webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook is not configured.");
        }
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stripe signature is required.");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (com.stripe.exception.SignatureVerificationException | RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook.");
        }
        if (event == null || event.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe webhook event.");
        }
        if (!event.getType().equals("payment_intent.amount_capturable_updated")
                && !event.getType().equals("payment_intent.succeeded")) {
            return Optional.empty();
        }
        try {
            var data = json.readTree(event.getDataObjectDeserializer().getRawJson());
            var metadata = data.path("metadata");
            if (!"dishes-checkout-v1".equals(metadata.path("integration").asString(""))) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(metadata.path("checkout_id").asString("")));
        } catch (RuntimeException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe checkout reference.");
        }
    }
    // Read only signed metadata, then retrieve the authoritative intent through the pinned SDK before completing it.

    private RequestOptions options(String operation, UUID checkoutId) {
        return RequestOptions.builder().setIdempotencyKey("dishes-" + operation + "-" + checkoutId)
                .setConnectTimeout(5000).setReadTimeout(10000).setMaxNetworkRetries(1).build();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Payment status could not be confirmed. Retry this checkout; do not start a second payment.");
    }
    // Keeps provider errors, credentials and payment details out of client-facing responses.
}
