package com.mosbyboys.dishesbecrazy.ui.controller;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.mosbyboys.dishesbecrazy.application.ord.ORDFacade;
import com.mosbyboys.dishesbecrazy.application.ord.PermissionDeniedException;
import com.mosbyboys.dishesbecrazy.application.prc.CheckoutService;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.ord.Order;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.reg.Customer;
import com.mosbyboys.dishesbecrazy.technical.payment.StripePayments;
import com.mosbyboys.dishesbecrazy.technical.security.SecurityConfig;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PaymentController.class, OrderController.class})
@Import(SecurityConfig.class)
class CheckoutControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CheckoutService checkouts;
    @MockitoBean StripePayments payments;
    @MockitoBean REGFacade users;
    @MockitoBean ORDFacade orders;
    @MockitoBean JwtDecoder decoder;
    private final UUID customerId = UUID.randomUUID();
    private final UUID checkoutId = UUID.randomUUID();

    @BeforeEach
    void user() {
        when(users.getCurrentUser(customerId)).thenReturn(new Customer(customerId, OffsetDateTime.now(),
                "Demo", "Customer", "demo@example.com", "demo", null, "Montreal", null));
    }

    @Test
    void paymentSetupAndOrderCompletionRequireAuthentication() throws Exception {
        mvc.perform(post("/payments/create-payment-intent").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/placeOrder").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/payments/" + checkoutId + "/cancel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(checkouts, payments);
    }

    @Test
    void rawCartPayloadCannotBypassCheckoutVerification() throws Exception {
        mvc.perform(post("/placeOrder").with(jwt().jwt(token -> token.subject(customerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[],\"restaurantId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(checkouts, orders);
    }

    @Test
    void completionUsesJwtIdentityAndReturnsTheVerifiedOrder() throws Exception {
        var order = new Order(customerId, UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("10.00"), "Soup")), new BigDecimal("10.00"));
        when(checkouts.complete(checkoutId, customerId)).thenReturn(order);
        mvc.perform(post("/placeOrder").with(jwt().jwt(token -> token.subject(customerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"checkoutId\":\"" + checkoutId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(order.getId().toString()));
        verify(checkouts).complete(checkoutId, customerId);
        verifyNoInteractions(orders);
    }

    @Test
    void crossCustomerCompletionIsForbidden() throws Exception {
        when(checkouts.complete(checkoutId, customerId)).thenThrow(new PermissionDeniedException("Another customer's checkout."));
        mvc.perform(post("/placeOrder").with(jwt().jwt(token -> token.subject(customerId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"checkoutId\":\"" + checkoutId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidWebhookCannotInvokeCompletionDespiteBeingOutsideJwtAuthentication() throws Exception {
        when(payments.verifiedCheckout(anyString(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));
        mvc.perform(post("/payments/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(checkouts);
    }

    @Test
    void signedWebhookCompletesWithoutABrowserSession() throws Exception {
        when(payments.verifiedCheckout("signed-payload", "signature")).thenReturn(Optional.of(checkoutId));
        mvc.perform(post("/payments/webhook").header("Stripe-Signature", "signature")
                        .contentType(MediaType.APPLICATION_JSON).content("signed-payload"))
                .andExpect(status().isOk());
        verify(checkouts).completeFromWebhook(checkoutId);
    }
    // HTTP authentication/dispatch is real; separate gateway tests exercise actual HMAC verification without network calls.
}
