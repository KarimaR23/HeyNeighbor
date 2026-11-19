package com.hn.heyneighbor.service;

import com.hn.heyneighbor.repository.AccountAdminRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Use case: the administrator activates or deactivates a user account.
 * Rules
 *  - activate:   a pending or deactivated account becomes active
 *  - deactivate: an active account becomes deactivated, and a reason is required
 *  - suspended and banned accounts are handled through moderation, not here
 *  - an administrator cannot deactivate their own account
 *  - a deactivated account cannot sign in: AuthInterceptor ends its session on the next request
 */
@Service
public class AccountStatusService {

    public static final String ACTIVATE = "activate";
    public static final String DEACTIVATE = "deactivate";
    static final int REASON_MAX = 500;

    /** What happened, in words the page can show. */
    public record Result(boolean ok, String message) {}

    private final AccountAdminRepository accounts;

    public AccountStatusService(AccountAdminRepository accounts) {
        this.accounts = accounts;
    }

    public Result change(long adminId, long targetId, String action, String reason) {
        Optional<Map<String, Object>> found = accounts.find(targetId);
        if (found.isEmpty()) {
            return new Result(false, "That account no longer exists.");
        }
        Map<String, Object> account = found.get();
        String current = (String) account.get("account_status");
        String name = (String) account.get("display_name");

        Optional<String> problem = check(adminId, targetId, current, action, reason);
        if (problem.isPresent()) {
            return new Result(false, problem.get());
        }

        String next = targetStatus(action);
        int changed = accounts.changeStatus(targetId, current, next, adminId, reason);
        if (changed == 0) {
            return new Result(false, "Someone else changed this account while you were looking at it. Check its status and try again.");
        }
        return new Result(true, name + ": status changed from " + current + " to " + next + ".");
    }

    /** The status the action leads to, or null for an unknown action. */
    static String targetStatus(String action) {
        if (ACTIVATE.equals(action)) {
            return "active";
        }
        if (DEACTIVATE.equals(action)) {
            return "deactivated";
        }
        return null;
    }

    /** Every rule that can be checked without changing anything. Empty means the change is allowed. */
    static Optional<String> check(long adminId, long targetId, String currentStatus, String action, String reason) {
        if (targetStatus(action) == null) {
            return Optional.of("Choose Activate or Deactivate.");
        }
        if (ACTIVATE.equals(action)) {
            if (!"pending".equals(currentStatus) && !"deactivated".equals(currentStatus)) {
                return Optional.of("Only pending or deactivated accounts can be activated. This account is " + currentStatus + ".");
            }
        } else {
            if (adminId == targetId) {
                return Optional.of("You cannot deactivate your own account.");
            }
            if (!"active".equals(currentStatus)) {
                return Optional.of("Only active accounts can be deactivated. This account is " + currentStatus + ".");
            }
            if (reason == null) {
                return Optional.of("Enter a reason for deactivating this account.");
            }
        }
        if (reason != null && reason.length() > REASON_MAX) {
            return Optional.of("The reason must be " + REASON_MAX + " characters or fewer.");
        }
        return Optional.empty();
    }
}
