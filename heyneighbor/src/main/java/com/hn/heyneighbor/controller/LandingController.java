package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.RoleHome;
import com.hn.heyneighbor.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Objects;

/**
 * Landing screen for each role. AuthInterceptor only lets a role reach its own landing path,
 * so the page content always matches the signed-in user's role.
 */
@SuppressWarnings("ALL")
@Controller
public class LandingController {

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request) {
        SessionUser user = CurrentUser.get(request);
        return "redirect:" + RoleHome.homeFor(Objects.requireNonNull(user).roleName());
    }

    @GetMapping({"/admin", "/community", "/partner", "/moderator", "/support"})
    public String landing(HttpServletRequest request, Model model) {
        SessionUser user = CurrentUser.get(request);
        model.addAttribute("pageTitle", Objects.requireNonNull(user).roleLabel() + " Home");
        return "Landing";
    }
}
