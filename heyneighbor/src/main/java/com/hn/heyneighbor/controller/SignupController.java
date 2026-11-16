package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.RoleHome;
import com.hn.heyneighbor.security.SessionUser;
import com.hn.heyneighbor.service.SignupService;
import com.hn.heyneighbor.service.SignupService.Request;
import com.hn.heyneighbor.service.SignupService.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public sign-up page (see WebConfig: no sign-in needed to open it).
 * The role picked here stays with the account. Passwords are never sent back to the page.
 */
@SuppressWarnings("SpringMVCViewInspection")
@Controller
public class SignupController {

    private final SignupService signups;

    public SignupController(SignupService signups) {
        this.signups = signups;
    }

    @GetMapping("/signup")
    public String form(HttpServletRequest request, Model model) {
        SessionUser user = CurrentUser.get(request);
        if (user != null) {
            return "redirect:" + RoleHome.homeFor(user.roleName());
        }
        Map<String, String> form = new HashMap<>();
        form.put("role", SignupService.COMMUNITY_USER);
        return render(model, form, null);
    }

    @PostMapping("/signup")
    public String submit(@RequestParam(name = "role", required = false) String role,
                         @RequestParam(name = "displayName", required = false) String displayName,
                         @RequestParam(name = "email", required = false) String email,
                         @RequestParam(name = "password", required = false) String password,
                         @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                         @RequestParam(name = "postalCode", required = false) String postalCode,
                         @RequestParam(name = "city", required = false) String city,
                         @RequestParam(name = "state", required = false) String state,
                         @RequestParam(name = "orgName", required = false) String orgName,
                         @RequestParam(name = "orgType", required = false) String orgType,
                         @RequestParam(name = "streetAddress", required = false) String streetAddress,
                         HttpServletRequest request, Model model) {

        if (CurrentUser.get(request) != null) {
            return "redirect:/dashboard";
        }

        Request signUp = new Request(
                Options.oneOf(role, SignupService.ROLES),
                Options.text(displayName),
                Options.text(email),
                password,
                confirmPassword,
                Options.text(postalCode),
                Options.text(city),
                Options.text(state),
                Options.text(orgName),
                Options.oneOf(orgType, SignupService.ORG_TYPES),
                Options.text(streetAddress));

        Result result = signups.signUp(signUp);
        if (result.created()) {
            return "active".equals(SignupService.statusFor(signUp.role()))
                    ? "redirect:/login?registered"
                    : "redirect:/login?pending";
        }

        // Show the form again with what they typed (but never the passwords)
        Map<String, String> form = new HashMap<>();
        form.put("role", signUp.role() == null ? SignupService.COMMUNITY_USER : signUp.role());
        put(form, "displayName", signUp.displayName());
        put(form, "email", signUp.email());
        put(form, "postalCode", signUp.postalCode());
        put(form, "city", signUp.city());
        put(form, "state", signUp.state());
        put(form, "orgName", signUp.orgName());
        put(form, "orgType", signUp.orgType());
        put(form, "streetAddress", signUp.streetAddress());
        return render(model, form, result.errors());
    }

    private String render(Model model, Map<String, String> form, List<String> errors) {
        model.addAttribute("pageTitle", "Sign up");
        model.addAttribute("form", form);
        model.addAttribute("orgTypes", SignupService.ORG_TYPES);
        model.addAttribute("errors", errors);
        return "SignUp";
    }

    private static void put(Map<String, String> form, String key, String value) {
        form.put(key, value == null ? "" : value);
    }
}
