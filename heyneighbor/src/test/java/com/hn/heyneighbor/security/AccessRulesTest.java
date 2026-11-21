package com.hn.heyneighbor.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessRulesTest {

    @Test
    void eachRoleAreaNeedsItsOwnPermission() {
        assertEquals("user.list", AccessRules.requiredPermission("/admin/users").orElseThrow());
        assertEquals("user.list", AccessRules.requiredPermission("/admin").orElseThrow());
        assertEquals("listing.browse", AccessRules.requiredPermission("/community/listings").orElseThrow());
        assertEquals("request.view_org", AccessRules.requiredPermission("/partner/requests").orElseThrow());
        assertEquals("report.view_listing", AccessRules.requiredPermission("/moderator/reports").orElseThrow());
        assertEquals("support.respond", AccessRules.requiredPermission("/support/requests").orElseThrow());
    }

    @Test
    void administratorScreensHaveTheirOwnPermissions() {
        assertEquals("category.manage", AccessRules.requiredPermission("/admin/categories").orElseThrow());
        assertEquals("category.manage", AccessRules.requiredPermission("/admin/categories/5/deactivate").orElseThrow());
        assertEquals("report.generate", AccessRules.requiredPermission("/admin/reports").orElseThrow());
        assertEquals("user.list", AccessRules.requiredPermission("/admin/users").orElseThrow());
        assertTrue(AccessRules.requiredPermission("/admin/categoriesx").map("user.list"::equals).orElse(false));
    }

    @Test
    void similarLookingPathsAreNotProtectedByAPrefixRule() {
        assertTrue(AccessRules.requiredPermission("/administrator").isEmpty());
        assertTrue(AccessRules.requiredPermission("/supporters").isEmpty());
        assertFalse(AccessRules.isCommon("/administrator"));
    }

    @Test
    void onlyDashboardAndLogoutAreOpenToEveryoneSignedIn() {
        assertTrue(AccessRules.isCommon("/dashboard"));
        assertTrue(AccessRules.isCommon("/logout"));
        assertFalse(AccessRules.isCommon("/admin"));
    }

    @Test
    void trickyPathsAreRefused() {
        assertTrue(AccessRules.isSuspicious("/admin;x=1/users"));
        assertTrue(AccessRules.isSuspicious("/admin/%2e%2e/users"));
        assertTrue(AccessRules.isSuspicious("//admin/users"));
        assertTrue(AccessRules.isSuspicious("/community/../admin"));
        assertFalse(AccessRules.isSuspicious("/admin/users"));
    }

    @Test
    void everyRoleHasAHomePage() {
        assertEquals("/admin", RoleHome.homeFor("administrator"));
        assertEquals("/community", RoleHome.homeFor("community_user"));
        assertEquals("/partner", RoleHome.homeFor("community_partner"));
        assertEquals("/moderator", RoleHome.homeFor("moderator"));
        assertEquals("/support", RoleHome.homeFor("support_coordinator"));
    }

    @Test
    void changingAccountStatusNeedsItsOwnPermission() {
        assertEquals("account.change_status", AccessRules.requiredPermission("/admin/account-status/5").orElseThrow());
        assertEquals("user.list", AccessRules.requiredPermission("/admin/users/5").orElseThrow());
    }

    @Test
    void moderatorReviewPagesNeedTheModeratorPermission() {
        assertEquals("report.view_listing", AccessRules.requiredPermission("/moderator/reports/3").orElseThrow());
        assertEquals("report.view_listing", AccessRules.requiredPermission("/moderator/reports/3/decision").orElseThrow());
    }
}