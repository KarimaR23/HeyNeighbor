package com.hn.heyneighbor.security;

import com.hn.heyneighbor.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Runs before every page except the public ones (see WebConfig).
 *
 * 1. No signed-in user -> redirect to the sign-in page.
 * 2. Account no longer active (suspended, banned, ...) -> end the session.
 * 3. Path needs a permission -> the database must show the user's role holds it, otherwise 403.
 * 4. Any other path must be on the common list, otherwise 403.
 */
@SuppressWarnings("ALL")
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserRepository users;

    public AuthInterceptor(UserRepository users) {
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        SessionUser user = CurrentUser.get(request);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (!users.isActive(user.userId())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (!"GET".equals(request.getMethod()) && !isSameOrigin(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        if (AccessRules.isSuspicious(path)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        Optional<String> permission = AccessRules.requiredPermission(path);
        boolean allowed;
        if (permission.isPresent()) {
            allowed = users.hasPermission(user.userId(), permission.get());
        } else {
            allowed = AccessRules.isCommon(path);
        }

        if (!allowed) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    /**
     * Pages that change data (POST) must be sent from this site. Browsers attach an Origin header
     * to every POST, so a form on another website cannot make a signed-in administrator change anything.
     * Requests with neither Origin nor Referer (tools such as curl) are not browser form posts and are allowed.
     */
    private static boolean isSameOrigin(HttpServletRequest request) {
        String sent = request.getHeader("Origin");
        if (sent == null) {
            sent = request.getHeader("Referer");
        }
        if (sent == null) {
            return true;
        }
        int port = request.getServerPort();
        boolean standard = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        String own = request.getScheme() + "://" + request.getServerName() + (standard ? "" : ":" + port);
        return sent.equals(own) || sent.startsWith(own + "/");
    }
}
