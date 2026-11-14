package com.mosbyboys.dishesbecrazy.application.prc;

import com.mosbyboys.dishesbecrazy.application.ord.PermissionDeniedException;
import com.mosbyboys.dishesbecrazy.domain.ord.OrderLine;
import com.mosbyboys.dishesbecrazy.domain.prc.PricingBreakdown;
import com.mosbyboys.dishesbecrazy.domain.reg.Role;
import com.mosbyboys.dishesbecrazy.domain.reg.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PRCFacade {

    private final PricingService pricingService;
    private final LedgerService ledgerService;

    public PricingBreakdown getTotal(List<OrderLine> lineItems) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderLine item : lineItems) {
            subtotal = subtotal.add(item.getLineTotal());
        }

        return pricingService.calculateTotal(subtotal);
    }

    // Only the operator reads the full ledger (R-PRC-04, Appendix A: "view all orders and billing ledgers")
    public List<LedgerEntry> getLedger(User user) {
        if (user.getRole() != Role.OPERATOR) {
            throw new PermissionDeniedException("Only an operator can view the ledger.");
        }
        return ledgerService.getEntries();
    }
}
