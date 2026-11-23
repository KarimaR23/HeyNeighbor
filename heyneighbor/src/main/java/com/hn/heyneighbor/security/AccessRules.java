package com.hn.heyneighbor.security;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which permission each part of the site requires.
 * A signed-in user may open a path under a protected prefix only if their role holds the
 * matching permission in the database (account -> role -> role_permission -> permission).
 * Anything that is neither protected nor on the short "common" list is refused.
 */
public final class AccessRules {

    public record Rule(String prefix, String permission) {}

    private static final List<Rule> RULES = List.of(
            // more specific paths first: the first matching rule wins
            new Rule("/admin/categories", "category.manage"),
            new Rule("/admin/reports", "report.generate"),
            new Rule("/admin/account-status", "account.change_status"),
            new Rule("/admin", "user.list"),
            new Rule("/community/listings/new", "listing.create"),
            new Rule("/community", "listing.browse"),
            new Rule("/partner", "request.view_org"),
            new Rule("/moderator", "report.view_listing"),
            new Rule("/support", "support.respond"));

    /** Pages every signed-in, active user may open. */
    private static final Set<String> COMMON = Set.of("/dashboard", "/logout");

    private AccessRules() {}

    /** The permission needed for this path, or empty when the path is not protected by a rule. */
    public static Optional<String> requiredPermission(String path) {
        for (Rule rule : RULES) {
            if (path.equals(rule.prefix()) || path.startsWith(rule.prefix() + "/")) {
                return Optional.of(rule.permission());
            }
        }
        return Optional.empty();
    }

    public static boolean isCommon(String path) {
        return COMMON.contains(path);
    }

    /** Paths with path parameters, encoded characters or doubled slashes are never allowed. */
    public static boolean isSuspicious(String path) {
        return path.contains(";") || path.contains("%") || path.contains("//") || path.contains("..");
    }
}