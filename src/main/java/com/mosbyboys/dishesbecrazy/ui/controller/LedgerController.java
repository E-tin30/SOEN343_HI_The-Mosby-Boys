package com.mosbyboys.dishesbecrazy.ui.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mosbyboys.dishesbecrazy.application.prc.LedgerEntry;
import com.mosbyboys.dishesbecrazy.application.prc.PRCFacade;
import com.mosbyboys.dishesbecrazy.application.reg.REGFacade;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class LedgerController {

    private final PRCFacade prcFacade;
    private final REGFacade regFacade;

    // Every charge and refund, newest first. Operator only (checked in PRCFacade).
    @GetMapping("/api/ledger")
    public List<LedgerEntry> getLedger(@AuthenticationPrincipal Jwt jwt) {
        return prcFacade.getLedger(regFacade.getCurrentUser(UUID.fromString(jwt.getSubject())));
    }
}
