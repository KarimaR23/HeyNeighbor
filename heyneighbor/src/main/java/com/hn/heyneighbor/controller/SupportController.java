package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.SupportRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SupportController {

    private final SupportRepository support;

    public SupportController(SupportRepository support) {
        this.support = support;
    }

    @GetMapping("/support/requests")
    public String requests(
            @RequestParam(required = false) String search,
            Model model) {

        model.addAttribute(
                "pageTitle",
                "Open Support Requests"
        );

        model.addAttribute(
                "search",
                search
        );

        model.addAttribute(
                "rows",
                support.searchOpenSupportRequests(search)
        );

        return "SupportRequests";
    }

    @PostMapping("/support/update-status")
    public String updateStatus(
            @RequestParam int supportRequestId,
            @RequestParam String status) {

        support.updateStatus(
                supportRequestId,
                status
        );

        return "redirect:/support/requests";
    }
}