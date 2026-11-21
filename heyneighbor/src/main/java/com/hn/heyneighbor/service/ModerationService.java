package com.hn.heyneighbor.service;

import com.hn.heyneighbor.repository.ModerationCaseRepository;
import com.hn.heyneighbor.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Use case: the community moderator reviews a reported listing and updates the moderation case
 * with a decision or action.
 * Two ways to save:
 *  - "Save progress": the case becomes under_review and the findings are kept.
 *  - "Close case": the moderator must give findings, an action and a resolution. The action is
 *    applied (listing hidden or removed), the case is closed and the report is resolved
 *    (or dismissed when the action is "no action").
 */
@Service
public class ModerationService {

    public static final List<String> ACTIONS = List.of("warning", "listing_hidden", "listing_removed", "no_action");
    static final int TEXT_MAX = 2000;

    /** What the moderator typed. Blank text is already null. */
    public record Decision(String findings, String action, String resolution, boolean close) {}

    /** What happened, in words the page can show. */
    public record Result(boolean ok, String message, List<String> errors) {
        static Result done(String message) { return new Result(true, message, List.of()); }
        static Result failed(String message) { return new Result(false, message, List.of()); }
        static Result invalid(List<String> errors) { return new Result(false, null, errors); }
    }

    /** Thrown inside the transaction so everything is undone. */
    private static class Refused extends RuntimeException {
        Refused(String message) { super(message); }
    }

    private final ModerationCaseRepository moderation;
    private final UserRepository users;
    private final TransactionTemplate transaction;

    public ModerationService(ModerationCaseRepository moderation, UserRepository users, TransactionTemplate transaction) {
        this.moderation = moderation;
        this.users = users;
        this.transaction = transaction;
    }

    public Result review(long moderatorId, long reportId, Decision d) {
        List<String> errors = validate(d);
        if (!errors.isEmpty()) {
            return Result.invalid(errors);
        }
        // Hiding or removing a listing needs its own permission, on top of viewing reports
        if (d.close() && changesListing(d.action()) && !users.hasPermission(moderatorId, "listing.hide")) {
            return Result.failed("Your role is not allowed to hide or remove listings.");
        }
        try {
            String message = transaction.execute(status -> apply(moderatorId, reportId, d));
            return Result.done(message);
        } catch (Refused e) {
            return Result.failed(e.getMessage());
        }
    }

    private String apply(long moderatorId, long reportId, Decision d) {
        Map<String, Object> report = moderation.findReport(reportId)
                .orElseThrow(() -> new Refused("That report no longer exists."));

        String reportStatus = (String) report.get("report_status");
        String caseStatus = (String) report.get("case_status");
        boolean reportFinished = !"open".equals(reportStatus) && !"in_review".equals(reportStatus);
        if (reportFinished || "closed".equals(caseStatus)) {
            throw new Refused("This report has already been decided.");
        }

        long listingId = ((Number) report.get("listing_id")).longValue();
        String listingTitle = (String) report.get("listing_title");
        String listingStatus = (String) report.get("listing_status");
        long ownerId = ((Number) report.get("owner_user_id")).longValue();
        long reporterId = ((Number) report.get("reporter_user_id")).longValue();

        if (d.close()) {
            if ("listing_hidden".equals(d.action()) && ("hidden".equals(listingStatus) || "removed".equals(listingStatus))) {
                throw new Refused("This listing is already " + listingStatus + ".");
            }
            if ("listing_removed".equals(d.action()) && "removed".equals(listingStatus)) {
                throw new Refused("This listing is already removed.");
            }
        }

        String newCaseStatus = d.close() ? "closed" : "under_review";
        String newReportStatus = !d.close() ? "in_review" : "no_action".equals(d.action()) ? "dismissed" : "resolved";

        Object existingCase = report.get("case_id");
        long caseId;
        if (existingCase == null) {
            caseId = moderation.createCase(moderatorId, ownerId, caseTitle(reportId, listingTitle), listingId,
                    newCaseStatus, d.findings(), d.resolution(), d.action(), d.close());
        } else {
            caseId = ((Number) existingCase).longValue();
            if (moderation.updateCase(caseId, newCaseStatus, d.findings(), d.resolution(), d.action(), d.close()) == 0) {
                throw new Refused("This case has already been closed.");
            }
        }

        if (moderation.updateReport(reportId, caseId, newReportStatus, d.close()) == 0) {
            throw new Refused("This report has already been decided.");
        }

        if (!d.close()) {
            return "Saved. The case is under review.";
        }

        if ("listing_hidden".equals(d.action())) {
            moderation.setListingStatus(listingId, "hidden");
        } else if ("listing_removed".equals(d.action())) {
            moderation.setListingStatus(listingId, "removed");
        }

        moderation.notifyUser(reporterId, "Your report was reviewed",
                "A moderator reviewed your report about \"" + listingTitle + "\". Thank you for helping keep HeyNeighbor safe.",
                reportId);
        if (!"no_action".equals(d.action())) {
            moderation.notifyUser(ownerId, "Moderator decision on your listing",
                    "Your listing \"" + listingTitle + "\" was reviewed after a report. Decision: "
                            + actionLabel(d.action()) + ".", reportId);
        }
        return "Case closed. Decision: " + actionLabel(d.action()) + ".";
    }

    static boolean changesListing(String action) {
        return "listing_hidden".equals(action) || "listing_removed".equals(action);
    }

    static String actionLabel(String action) {
        return switch (action == null ? "" : action) {
            case "warning" -> "warning sent to the listing owner";
            case "listing_hidden" -> "listing hidden";
            case "listing_removed" -> "listing removed";
            case "no_action" -> "no action needed";
            default -> "none";
        };
    }

    static String caseTitle(long reportId, String listingTitle) {
        String title = "Report #" + reportId + ": " + listingTitle;
        return title.length() <= 150 ? title : title.substring(0, 147) + "...";
    }

    /** Every rule that can be checked without the database. */
    static List<String> validate(Decision d) {
        List<String> errors = new ArrayList<>();
        if (d.findings() == null) {
            errors.add("Write your findings: what you saw when you reviewed the listing.");
        } else if (d.findings().length() > TEXT_MAX) {
            errors.add("Findings must be " + TEXT_MAX + " characters or fewer.");
        }
        if (d.action() != null && !ACTIONS.contains(d.action())) {
            errors.add("Choose one of the listed actions.");
        }
        if (d.resolution() != null && d.resolution().length() > TEXT_MAX) {
            errors.add("The resolution must be " + TEXT_MAX + " characters or fewer.");
        }
        if (d.close()) {
            if (d.action() == null) {
                errors.add("Choose an action before closing the case.");
            }
            if (d.resolution() == null) {
                errors.add("Write the resolution before closing the case.");
            }
        }
        return errors;
    }
}