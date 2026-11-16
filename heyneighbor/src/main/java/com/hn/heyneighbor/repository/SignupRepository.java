package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Database work for creating a new account at sign-up. */
@SuppressWarnings("ALL")
@Repository
public class SignupRepository {

    private final JdbcTemplate jdbc;

    public SignupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Integer> roleId(String roleName) {
        List<Integer> ids = jdbc.queryForList("SELECT role_id FROM role WHERE role_name = ?", Integer.class, roleName);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    /** Email addresses are unique ignoring capitalization (BR-01). */
    public boolean emailTaken(String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_account WHERE lower(email) = lower(?))", Boolean.class, email));
    }

    /** Creates the account and returns its new user_id. The password is already a bcrypt hash. */
    public int createUser(int roleId, String email, String passwordHash, String status, String displayName,
                          String city, String state, String postalCode) {
        Integer id = jdbc.queryForObject("""
                INSERT INTO user_account
                    (role_id, email, password_hash, account_status, email_verified,
                     display_name, city, state, postal_code)
                VALUES (?, ?, ?, ?, false, ?, ?, ?, ?)
                RETURNING user_id
                """, Integer.class, roleId, email, passwordHash, status, displayName, city, state, postalCode);
        return id == null ? 0 : id;
    }

    /** Creates the partner's organization. It starts as pending until an administrator approves it. */
    public void createOrganization(int ownerUserId, String name, String type,
                                   String street, String city, String state, String postalCode) {
        jdbc.update("""
                INSERT INTO organization
                    (owner_user_id, org_name, org_type, street_address, city, state, postal_code, approval_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')
                """, ownerUserId, name, type, street, city, state, postalCode);
    }
}