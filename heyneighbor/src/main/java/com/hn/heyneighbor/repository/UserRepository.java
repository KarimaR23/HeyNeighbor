package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("ALL")
@Repository
public class UserRepository {

    /** What sign-in needs from the user_account and role tables. */
    public record LoginRecord(long userId, String displayName, String email,
                              String passwordHash, String accountStatus, String roleName) {}

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Looks the account up by email, ignoring capitalization (BR-01). */
    public Optional<LoginRecord> findForLogin(String email) {
        List<LoginRecord> rows = jdbc.query("""
                SELECT u.user_id, u.display_name, u.email, u.password_hash, u.account_status, r.role_name
                FROM user_account u
                JOIN role r ON r.role_id = u.role_id
                WHERE lower(u.email) = lower(?)
                """,
                (rs, i) -> new LoginRecord(
                        rs.getLong("user_id"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("account_status"),
                        rs.getString("role_name")),
                email);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void touchLastLogin(long userId) {
        jdbc.update("UPDATE user_account SET last_login_at = now(), updated_at = now() WHERE user_id = ?", userId);
    }

    /** Only active accounts may use the site (BR-05). */
    public boolean isActive(long userId) {
        Boolean active = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_account WHERE user_id = ? AND account_status = 'active')",
                Boolean.class, userId);
        return Boolean.TRUE.equals(active);
    }

    /** Permissions come only from the account's single role (BR-72). */
    public boolean hasPermission(long userId, String permissionCode) {
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM user_account u
                    JOIN role_permission rp ON rp.role_id = u.role_id
                    JOIN permission p       ON p.permission_id = rp.permission_id
                    WHERE u.user_id = ?
                      AND u.account_status = 'active'
                      AND p.permission_code = ?)
                """, Boolean.class, userId, permissionCode);
        return Boolean.TRUE.equals(allowed);
    }

    public List<String> roleNames() {
        return jdbc.queryForList("SELECT role_name FROM role ORDER BY role_name", String.class);
    }

    /** Administrator view: search by name or email, filter by role and status. The password hash is never selected. */
    public List<Map<String, Object>> searchUsers(String q, String role, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.user_id, u.display_name, u.email, r.role_name, u.account_status,
                       u.email_verified, u.city, u.state, u.created_at, u.last_login_at
                FROM user_account u
                JOIN role r ON r.role_id = u.role_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null) {
            sql.append(" AND (u.display_name ILIKE ? OR u.email ILIKE ?)");
            args.add(Like.contains(q));
            args.add(Like.contains(q));
        }
        if (role != null) {
            sql.append(" AND r.role_name = ?");
            args.add(role);
        }
        if (status != null) {
            sql.append(" AND u.account_status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY r.role_name, u.display_name LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}