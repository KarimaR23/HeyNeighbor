package com.hn.heyneighbor.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStatusServiceTest {

    @Test
    void pendingAccountCanBeActivated() {
        assertTrue(AccountStatusService.check(1, 5, "pending", "activate", null).isEmpty());
    }

    @Test
    void deactivatedAccountCanBeActivated() {
        assertTrue(AccountStatusService.check(1, 5, "deactivated", "activate", null).isEmpty());
    }

    @Test
    void activeAccountCanBeDeactivatedWithAReason() {
        assertTrue(AccountStatusService.check(1, 5, "active", "deactivate", "Asked to close their account").isEmpty());
    }

    @Test
    void deactivatingNeedsAReason() {
        assertEquals("Enter a reason for deactivating this account.",
                AccountStatusService.check(1, 5, "active", "deactivate", null).orElseThrow());
    }

    @Test
    void administratorCannotDeactivateThemself() {
        assertEquals("You cannot deactivate your own account.",
                AccountStatusService.check(1, 1, "active", "deactivate", "test").orElseThrow());
    }

    @Test
    void suspendedAndBannedAccountsAreNotChangedHere() {
        assertTrue(AccountStatusService.check(1, 5, "suspended", "activate", null).isPresent());
        assertTrue(AccountStatusService.check(1, 5, "banned", "activate", null).isPresent());
        assertTrue(AccountStatusService.check(1, 5, "suspended", "deactivate", "x").isPresent());
    }

    @Test
    void alreadyActiveCannotBeActivatedAndAlreadyDeactivatedCannotBeDeactivated() {
        assertTrue(AccountStatusService.check(1, 5, "active", "activate", null).isPresent());
        assertTrue(AccountStatusService.check(1, 5, "deactivated", "deactivate", "x").isPresent());
    }

    @Test
    void unknownActionAndOverlongReasonAreRefused() {
        assertTrue(AccountStatusService.check(1, 5, "active", "delete", null).isPresent());
        assertTrue(AccountStatusService.check(1, 5, "pending", "activate", "x".repeat(501)).isPresent());
    }
}