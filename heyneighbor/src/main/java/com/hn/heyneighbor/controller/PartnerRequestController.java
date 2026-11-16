package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.PartnerRequestRepository;
import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Community Partner: view requests for their own organization's listings. */
@Controller
public class PartnerRequestController {
    private final PartnerRequestRepository repository;

    public PartnerRequestController(PartnerRequestRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/partner/requests")
    public String requests(HttpServletRequest request, Model model) {
        SessionUser user = CurrentUser.get(request);
        // Additional role check: do not rely on hiding navigation links.
        if (user == null || !"community_partner".equals(user.roleName())) {
            return "error/403";
        }

        var organizations = repository.organizationForOwner(user.userId());
        model.addAttribute("pageTitle", "Organization Requests");
        model.addAttribute("organization", organizations.isEmpty() ? null : organizations.get(0));
        model.addAttribute("requests", repository.requestsForOwner(user.userId()));
        return "PartnerRequests";
    }
}
