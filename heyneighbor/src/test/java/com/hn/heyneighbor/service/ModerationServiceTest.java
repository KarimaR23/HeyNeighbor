package com.hn.heyneighbor.service;

import com.hn.heyneighbor.service.ModerationService.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationServiceTest {

    @Test
    void savingProgressNeedsOnlyFindings() {
        assertTrue(ModerationService.validate(new Decision("Looks like spam", null, null, false)).isEmpty());
    }

    @Test
    void findingsAreAlwaysRequired() {
        assertFalse(ModerationService.validate(new Decision(null, "no_action", "Fine", true)).isEmpty());
        assertFalse(ModerationService.validate(new Decision(null, null, null, false)).isEmpty());
    }

    @Test
    void closingNeedsAnActionAndAResolution() {
        assertEquals(2, ModerationService.validate(new Decision("Seen it", null, null, true)).size());
        assertTrue(ModerationService.validate(new Decision("Seen it", "listing_hidden", "Hidden for review", true)).isEmpty());
    }

    @Test
    void unknownActionIsRefused() {
        assertFalse(ModerationService.validate(new Decision("Seen it", "delete_everything", "x", true)).isEmpty());
    }

    @Test
    void overlongTextIsRefused() {
        assertFalse(ModerationService.validate(new Decision("x".repeat(2001), null, null, false)).isEmpty());
    }

    @Test
    void onlyHidingAndRemovingChangeTheListing() {
        assertTrue(ModerationService.changesListing("listing_hidden"));
        assertTrue(ModerationService.changesListing("listing_removed"));
        assertFalse(ModerationService.changesListing("warning"));
        assertFalse(ModerationService.changesListing("no_action"));
    }

    @Test
    void caseTitleFitsTheColumn() {
        assertTrue(ModerationService.caseTitle(7, "y".repeat(400)).length() <= 150);
        assertEquals("Report #7: Bike", ModerationService.caseTitle(7, "Bike"));
    }
}
