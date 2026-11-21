package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.repository.AccountAdminRepository;
import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.SessionUser;
import com.hn.heyneighbor.service.AccountStatusService;
import com.hn.heyneighbor.service.AccountStatusService.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;

/**
 * Administrator: open one account and activate or deactivate it.
 *   GET  /admin/users/{id}            needs user. list            (see AccessRules)
 *   POST /admin/account-status/{id}   needs account.change_status
 */
@Controller
public class AdminAccountController {

    private final AccountAdminRepository accounts;
    private final AccountStatusService statusService;

    public AdminAccountController(AccountAdminRepository accounts, AccountStatusService statusService) {
        this.accounts = accounts;
        this.statusService = statusService;
    }

    @GetMapping("/admin/users/{id}")
    public String detail(@PathVariable long id, HttpServletRequest request,
                         RedirectAttributes redirect, Model model) {
        Optional<Map<String, Object>> account = accounts.find(id);
        if (account.isEmpty()) {
            redirect.addFlashAttribute("error", "That account does not exist.");
            return "redirect:/admin/users";
        }
        SessionUser me = CurrentUser.get(request);
        model.addAttribute("pageTitle", "Manage account");
        model.addAttribute("account", account.get());
        model.addAttribute("isSelf", me != null && me.userId() == id);
        return "AdminUserDetail";
    }

    @PostMapping("/admin/account-status/{id}")
    public String change(@PathVariable long id,
                         @RequestParam(name = "action", required = false) String action,
                         @RequestParam(name = "reason", required = false) String reason,
                         HttpServletRequest request, RedirectAttributes redirect) {
        SessionUser me = CurrentUser.get(request);
        if (me == null) {
            return "redirect:/login";
        }
        Result result = statusService.change(me.userId(), id, Options.text(action), Options.text(reason));
        redirect.addFlashAttribute(result.ok() ? "message" : "error", result.message());
        return "redirect:/admin/users/" + id;
    }
}
