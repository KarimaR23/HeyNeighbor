package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Administrator: look at one account and change its status. */
@Repository
public class AccountAdminRepository {

    private final JdbcTemplate jdbc;

    public AccountAdminRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One account with its role and who last changed its status. The password hash is never selected. */
    public Optional<Map<String, Object>> find(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.user_id, u.display_name, u.email, r.role_name, u.account_status,
                       u.email_verified, u.city, u.state, u.postal_code, u.created_at, u.last_login_at,
                       u.status_changed_at, u.status_reason, changer.display_name AS status_changed_by
                FROM user_account u
                JOIN role r ON r.role_id = u.role_id
                LEFT JOIN user_account changer ON changer.user_id = u.status_changed_by_user_id
                WHERE u.user_id = ?
                """, userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Changes the status only if the account still has the status the administrator was looking at.
     * Returns 1 when it changed, 0 when someone else changed it first.
     */
    public int changeStatus(long userId, String fromStatus, String toStatus, long changedByUserId, String reason) {
        return jdbc.update("""
                UPDATE user_account
                SET account_status = ?,
                    status_changed_by_user_id = ?,
                    status_changed_at = now(),
                    status_reason = ?,
                    updated_at = now()
                WHERE user_id = ? AND account_status = ?
                """, toStatus, changedByUserId, reason, userId, fromStatus);
    }
}