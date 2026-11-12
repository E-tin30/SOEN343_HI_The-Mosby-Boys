package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mosbyboys.dishesbecrazy.application.dash.ResetService;
import com.mosbyboys.dishesbecrazy.application.dlv.DispatchQueueLoader;
import com.mosbyboys.dishesbecrazy.application.ord.OrderQueueLoader;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import com.mosbyboys.dishesbecrazy.technical.security.DemoStateAccess;
import com.mosbyboys.dishesbecrazy.technical.security.SecurityConfig;
import com.mosbyboys.dishesbecrazy.technical.seed.DemoDataRestorer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DemoResetController.class)
@Import({SecurityConfig.class, DemoStateAccess.class, ResetService.class})
class DemoResetControllerTest {
    @Autowired MockMvc mvc;
    @Autowired DemoStateAccess access;
    @MockitoBean REGFacade registration;
    @MockitoBean DemoDataRestorer database;
    @MockitoBean OrderQueueLoader queues;
    @MockitoBean DispatchQueueLoader dispatchQueue;
    @MockitoBean JwtDecoder decoder;
    private final UUID operatorId = UUID.randomUUID();

    @BeforeEach
    void prepareOperator() {
        var operator = mock(User.class);
        when(operator.getRole()).thenReturn(Role.OPERATOR);
        when(registration.getCurrentUser(operatorId)).thenReturn(operator);
        clearInvocations(registration, database, queues);
        try (var restoration = access.beginRestoration()) {
            restoration.complete();
        }
    }
    // Real security, controller and coordinator; persistence and runtime rebuilding cannot touch external services.

    @Test
    void unauthenticatedResetCannotReachTheService() throws Exception {
        mvc.perform(post("/api/demo/reset")).andExpect(status().isUnauthorized());
        verifyNoInteractions(registration, database, queues);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "OPERATOR", mode = EnumSource.Mode.EXCLUDE)
    void persistedNonOperatorRolesCannotRestoreEvenWithOperatorClaims(Role role) throws Exception {
        var caller = mock(User.class);
        when(caller.getRole()).thenReturn(role);
        when(registration.getCurrentUser(operatorId)).thenReturn(caller);

        mvc.perform(post("/api/demo/reset").with(jwt().jwt(token -> token.subject(operatorId.toString())
                        .claim("role", "OPERATOR"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(database, queues);
    }
    // Browser claims and a visible button cannot override the role stored by REG.

    @Test
    void operatorResetCommitsAndRebuildsBeforeReturningSuccess() throws Exception {
        mvc.perform(post("/api/demo/reset").with(jwt().jwt(token -> token.subject(operatorId.toString()))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        var ordered = inOrder(database, queues);
        ordered.verify(database).restore();
        ordered.verify(queues).rebuild();
        assertDoesNotThrow(() -> access.beginRequest().close());
    }
    // Passing through the actual request filter also proves Reset is exempt from shared access, avoiding a lock upgrade.

    @Test
    void queueFailureCanBeRetriedThroughTheSameEndpointDuringRecovery() throws Exception {
        doThrow(new IllegalStateException("Queue recovery failed")).doNothing().when(queues).rebuild();

        mvc.perform(post("/api/demo/reset").with(jwt().jwt(token -> token.subject(operatorId.toString()))))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/orders/history").with(jwt())).andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/demo/reset").with(jwt().jwt(token -> token.subject(operatorId.toString()))))
                .andExpect(status().isNoContent());

        assertDoesNotThrow(() -> access.beginRequest().close());
        verify(database, times(2)).restore();
        verify(queues, times(2)).rebuild();
    }
    // A failed rebuild keeps ordinary requests blocked while leaving the authenticated Reset route available for retry.

    @Test
    void overlappingResetReturnsConflictWithoutChangingData() throws Exception {
        try (var restoration = access.beginRestoration()) {
            mvc.perform(post("/api/demo/reset").with(jwt().jwt(token -> token.subject(operatorId.toString()))))
                    .andExpect(status().isConflict());
            verifyNoInteractions(database, queues);
            restoration.complete();
        }
    }

    @Test
    void databaseFailureSkipsQueuesAndReopensOrdinaryAccess() throws Exception {
        doThrow(new IllegalStateException("Baseline unavailable")).when(database).restore();

        mvc.perform(post("/api/demo/reset").with(jwt().jwt(token -> token.subject(operatorId.toString()))))
                .andExpect(status().isConflict());

        verifyNoInteractions(queues);
        assertDoesNotThrow(() -> access.beginRequest().close());
    }
}
