package com.hn.heyneighbor.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Reads the signed-in user from the session without creating a session. */
public final class CurrentUser {

    public static final String SESSION_KEY = "signedInUser";

    private CurrentUser() {}

    /** The signed-in user, or null when nobody is signed in. */
    public static SessionUser get(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof SessionUser user ? user : null;
    }
}
