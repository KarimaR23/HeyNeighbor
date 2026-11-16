package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.SupportRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SupportController {

    private final SupportRepository support;

    public SupportController(SupportRepository support) {
        this.support = support;
    }

    @GetMapping("/support/requests")
    public String requests(Model model) {

        model.addAttribute(
                "pageTitle",
                "Open Support Requests"
        );

        model.addAttribute(
                "rows",
                support.searchOpenSupportRequests()
        );

        return "SupportRequests";
    }
}