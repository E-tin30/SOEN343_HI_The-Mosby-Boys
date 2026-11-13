package com.mosbyboys.dishesbecrazy.technical.security;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.servlet.ServletException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(DemoRequestFilterTest.TestConfig.class)
@WebAppConfiguration
class DemoRequestFilterTest {
    @Autowired private WebApplicationContext context;
    @Autowired private DemoStateAccess access;
    @Autowired private TestEndpoints endpoints;
    private MockMvc mvc;

    @BeforeEach
    void prepareRequests() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        endpoints.work = () -> { };
        endpoints.calls = 0;
        try (var restoration = access.beginRestoration()) {
            restoration.complete();
        }
    }
    // Load the real SecurityConfig and filter with local MVC handlers and a decoder that never contacts Supabase.

    @Test
    void normalRequestsHoldAccessUntilTheHandlerFinishes() throws Exception {
        endpoints.work = () -> assertThrows(IllegalStateException.class, access::beginRestoration);

        mvc.perform(post("/placeOrder").with(jwt())).andExpect(status().isOk());

        assertEquals(1, endpoints.calls);
        try (var restoration = access.beginRestoration()) {
            restoration.complete();
        }
    }

    @Test
    void readsAndCommandsReceiveRetryResponsesDuringRestoration() throws Exception {
        try (var restoration = access.beginRestoration()) {
            mvc.perform(post("/placeOrder").with(jwt()))
                    .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"))
                    .andExpect(content().string("Demo restoration is in progress. Retry shortly."));
            mvc.perform(get("/api/orders/history").with(jwt())).andExpect(status().isServiceUnavailable());
            assertEquals(0, endpoints.calls);
            restoration.complete();
        }
        mvc.perform(post("/placeOrder").with(jwt())).andExpect(status().isOk());
    }

    @Test
    void authenticationStillRunsBeforeTheRequestGuard() throws Exception {
        try (var restoration = access.beginRestoration()) {
            mvc.perform(post("/placeOrder")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/demo/reset")).andExpect(status().isUnauthorized());
            restoration.complete();
        }
        assertEquals(0, endpoints.calls);
    }

    @Test
    void recoveryBlocksNormalRequestsButExemptsLoginAndTheResetPath() throws Exception {
        try (var restoration = access.beginRestoration()) {
            restoration.databaseRestored();
        }

        mvc.perform(post("/placeOrder").with(jwt())).andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Demo restoration is incomplete. Retry Reset before continuing."));
        mvc.perform(get("/api/orders/history").with(jwt())).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/login")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").with(jwt())).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/demo/reset").with(jwt())).andExpect(status().isNotFound());
        mvc.perform(get("/api/demo/reset").with(jwt())).andExpect(status().isServiceUnavailable());
        assertEquals(0, endpoints.calls);

        try (var retry = access.beginRestoration()) {
            retry.complete();
        }
        mvc.perform(post("/placeOrder").with(jwt())).andExpect(status().isOk());
    }
    // This filter-only context has no Reset controller; its 404 proves exemption. Controller tests cover the real route.

    @Test
    void accessIsHeldThroughSynchronousAfterCommitCallbacks() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        boolean[] callbackRan = { false };
        endpoints.work = () -> transaction.executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        assertThrows(IllegalStateException.class, () -> {
                            try (var restoration = access.beginRestoration()) { restoration.complete(); }
                        });
                        callbackRan[0] = true;
                    }
                }));

        mvc.perform(post("/placeOrder").with(jwt())).andExpect(status().isOk());

        assertTrue(callbackRan[0]);
        try (var restoration = access.beginRestoration()) { restoration.complete(); }
    }
    // Dispatch's current AFTER_COMMIT listeners execute synchronously inside this same request lifetime.

    @Test
    void aFailedHandlerStillReleasesRequestAccess() throws Exception {
        var filter = new DemoRequestFilter(access);
        var request = new MockHttpServletRequest("POST", "/placeOrder");
        assertThrows(ServletException.class, () -> filter.doFilter(request, new MockHttpServletResponse(),
                (incoming, response) -> { throw new ServletException("Command failed"); }));

        try (var restoration = access.beginRestoration()) {
            restoration.complete();
        }
    }

    @Test
    void resetExemptionWorksUnderAContextPath() throws Exception {
        var filter = new DemoRequestFilter(access);
        var request = new MockHttpServletRequest("POST", "/demo/api/demo/reset");
        request.setContextPath("/demo");
        boolean[] reachedHandler = { false };
        try (var restoration = access.beginRestoration()) {
            filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> reachedHandler[0] = true);
            assertTrue(reachedHandler[0]);
            restoration.complete();
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean DemoStateAccess access() { return new DemoStateAccess(); }
        @Bean TestEndpoints endpoints() { return new TestEndpoints(); }
        @Bean JwtDecoder decoder() { return token -> { throw new JwtException("Use the test JWT request helper"); }; }
    }

    @RestController
    static class TestEndpoints {
        private Runnable work;
        private int calls;

        @PostMapping("/placeOrder")
        String place() {
            work.run();
            calls++;
            return "ok";
        }

        @GetMapping("/api/orders/history")
        String history() { return "history"; }

        @PostMapping("/login")
        String login() { return "login"; }

        @GetMapping("/api/auth/me")
        String profile() { return "profile"; }
    }
}
