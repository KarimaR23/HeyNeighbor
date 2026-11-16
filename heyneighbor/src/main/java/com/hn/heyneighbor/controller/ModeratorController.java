package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.ModerationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Community Moderator: view reported listings (Sprint IV).
 * /moderator paths need the report.view_listing permission (AccessRules + AuthInterceptor).
 */
@Controller
public class ModeratorController {

    private final ModerationRepository moderation;

    public ModeratorController(ModerationRepository moderation) {
        this.moderation = moderation;
    }

    @GetMapping("/moderator/reports")
    public String reports(@RequestParam(name = "q", required = false) String q,
                          @RequestParam(name = "status", required = false) String status,
                          @RequestParam(name = "reason", required = false) String reason,
                          Model model) {
        String query = Options.text(q);
        String statusFilter = Options.oneOf(status, Options.REPORT_STATUSES);
        String reasonFilter = Options.oneOf(reason, Options.REPORT_REASONS);

        List<Map<String, Object>> rows = moderation.searchListingReports(query, statusFilter, reasonFilter);

        model.addAttribute("pageTitle", "Reported Listings");
        model.addAttribute("rows", rows);
        model.addAttribute("statuses", Options.REPORT_STATUSES);
        model.addAttribute("reasons", Options.REPORT_REASONS);
        model.addAttribute("q", query == null ? "" : query);
        model.addAttribute("status", statusFilter);
        model.addAttribute("reason", reasonFilter);
        return "ModeratorReports";
    }
}
