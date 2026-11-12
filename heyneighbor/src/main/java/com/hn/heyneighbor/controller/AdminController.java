package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class AdminController {

    private final UserRepository users;

    public AdminController(UserRepository users) {
        this.users = users;
    }

    /** Administrator: search, browse and list users. */
    @GetMapping("/admin/users")
    public String users(@RequestParam(name = "q", required = false) String q,
                        @RequestParam(name = "role", required = false) String role,
                        @RequestParam(name = "status", required = false) String status,
                        Model model) {
        List<String> roles = users.roleNames();
        String query = Options.text(q);
        String roleFilter = Options.oneOf(role, roles);
        String statusFilter = Options.oneOf(status, Options.ACCOUNT_STATUSES);

        List<Map<String, Object>> rows = users.searchUsers(query, roleFilter, statusFilter);

        model.addAttribute("pageTitle", "Manage Users");
        model.addAttribute("rows", rows);
        model.addAttribute("roles", roles);
        model.addAttribute("statuses", Options.ACCOUNT_STATUSES);
        model.addAttribute("q", query == null ? "" : query);
        model.addAttribute("role", roleFilter);
        model.addAttribute("status", statusFilter);
        return "AdminUsers";
    }
}
