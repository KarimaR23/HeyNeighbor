package com.hn.heyneighbor.web;

import com.hn.heyneighbor.nav.NavItem;
import com.hn.heyneighbor.nav.NavService;
import com.hn.heyneighbor.security.CurrentUser;
import com.hn.heyneighbor.security.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** Puts the signed-in user and that role's navigation on every page. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final NavService navService;

    public GlobalModelAdvice(NavService navService) {
        this.navService = navService;
    }

    @ModelAttribute("currentUser")
    public SessionUser currentUser(HttpServletRequest request) {
        return CurrentUser.get(request);
    }

    @ModelAttribute("roleNav")
    public List<NavItem> roleNav(HttpServletRequest request) {
        SessionUser user = CurrentUser.get(request);
        return user == null ? List.of() : navService.forRole(user.roleName());
    }
}