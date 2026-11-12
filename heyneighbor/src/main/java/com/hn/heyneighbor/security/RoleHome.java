package com.hn.heyneighbor.security;

import java.util.Map;

/** Maps each of the five system roles to its landing page and display label. */
public final class RoleHome {

    private static final Map<String, String> HOME = Map.of(
            "administrator", "/admin",
            "community_user", "/community",
            "community_partner", "/partner",
            "moderator", "/moderator",
            "support_coordinator", "/support");

    private static final Map<String, String> LABEL = Map.of(
            "administrator", "Administrator",
            "community_user", "Community User",
            "community_partner", "Community Partner",
            "moderator", "Community Moderator",
            "support_coordinator", "Support Coordinator");

    private RoleHome() {}

    public static String homeFor(String roleName) {
        return HOME.getOrDefault(roleName, "/login");
    }

    public static String labelFor(String roleName) {
        return LABEL.getOrDefault(roleName, roleName);
    }
}