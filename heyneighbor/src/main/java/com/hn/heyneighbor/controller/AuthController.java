package com.hn.heyneighbor.controller;

import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.RoleHome;
import com.hn.heyneighbor.security.SessionUser;
import com.hn.heyneighbor.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@SuppressWarnings("ALL")
@Controller
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(name = "logout", required = false) String logout,
                            HttpServletRequest request, Model model) {
        SessionUser user = CurrentUser.get(request);
        if (user != null) {
            return "redirect:" + RoleHome.homeFor(user.roleName());
        }
        model.addAttribute("loggedOut", logout != null);
        return "Login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(name = "email", required = false) String email,
                        @RequestParam(name = "password", required = false) String password,
                        HttpServletRequest request, Model model) {
        Optional<SessionUser> user = auth.signIn(email, password);

        if (user.isEmpty()) {
            // One message for every failure: unknown email, wrong password, or inactive account
            model.addAttribute("error", "Invalid email or password.");
            model.addAttribute("email", email == null ? "" : email.trim());
            return "Login";
        }

        // Start a fresh session so an old session id cannot be reused after sign-in
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(CurrentUser.SESSION_KEY, user.get());

        return "redirect:" + RoleHome.homeFor(user.get().roleName());
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?logout";
    }
}