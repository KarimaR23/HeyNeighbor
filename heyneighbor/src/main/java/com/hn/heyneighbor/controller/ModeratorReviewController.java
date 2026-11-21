package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.ModerationCaseRepository;
import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.SessionUser;
import com.hn.heyneighbor.service.ModerationService;
import com.hn.heyneighbor.service.ModerationService.Decision;
import com.hn.heyneighbor.service.ModerationService.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Community moderator: review one reported listing and record a decision. All paths need report.view_listing. */
@Controller
public class ModeratorReviewController {

    private final ModerationCaseRepository moderation;
    private final ModerationService moderationService;

    public ModeratorReviewController(ModerationCaseRepository moderation, ModerationService moderationService) {
        this.moderation = moderation;
        this.moderationService = moderationService;
    }

    /** Review one reported listing: the report, the listing, and the decision form. */
    @GetMapping("/moderator/reports/{reportId}")
    public String review(@PathVariable long reportId, RedirectAttributes redirect, Model model) {
        Optional<Map<String, Object>> found = moderation.findReport(reportId);
        if (found.isEmpty()) {
            redirect.addFlashAttribute("error", "That report does not exist.");
            return "redirect:/moderator/reports";
        }
        Map<String, Object> report = found.get();

        // After a mistake the page comes back with what the moderator typed; otherwise show the saved case
        if (!model.containsAttribute("form")) {
            Map<String, String> form = new HashMap<>();
            form.put("findings", text(report.get("findings")));
            form.put("resolution", text(report.get("resolution")));
            form.put("action", text(report.get("action_type")));
            model.addAttribute("form", form);
        }

        String reportStatus = (String) report.get("report_status");
        boolean decided = !"open".equals(reportStatus) && !"in_review".equals(reportStatus);

        model.addAttribute("pageTitle", "Review report");
        model.addAttribute("report", report);
        model.addAttribute("decided", decided);
        model.addAttribute("others", moderation.otherReportsOnListing(
                ((Number) report.get("listing_id")).longValue(), reportId));
        model.addAttribute("actions", ModerationService.ACTIONS);
        return "ModeratorReportDetail";
    }

    /** Save the moderator's findings, or close the case with an action. */
    @PostMapping("/moderator/reports/{reportId}/decision")
    public String decide(@PathVariable long reportId,
                         @RequestParam(name = "findings", required = false) String findings,
                         @RequestParam(name = "action", required = false) String action,
                         @RequestParam(name = "resolution", required = false) String resolution,
                         @RequestParam(name = "close", required = false) String close,
                         HttpServletRequest request, RedirectAttributes redirect) {
        SessionUser me = CurrentUser.get(request);
        if (me == null) {
            return "redirect:/login";
        }

        Decision decision = new Decision(
                Options.text(findings),
                Options.oneOf(action, ModerationService.ACTIONS),
                Options.text(resolution),
                close != null);

        Result result = moderationService.review(me.userId(), reportId, decision);
        if (result.ok()) {
            redirect.addFlashAttribute("message", result.message());
        } else {
            redirect.addFlashAttribute("errors", result.errors().isEmpty() ? List.of(result.message()) : result.errors());
            Map<String, String> form = new HashMap<>();
            form.put("findings", text(decision.findings()));
            form.put("resolution", text(decision.resolution()));
            form.put("action", text(decision.action()));
            redirect.addFlashAttribute("form", form);
        }
        return "redirect:/moderator/reports/" + reportId;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
