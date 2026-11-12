package com.hn.heyneighbor.service;

import com.hn.heyneighbor.repository.UserRepository;
import com.hn.heyneighbor.repository.UserRepository.LoginRecord;
import com.hn.heyneighbor.security.SessionUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    /**
     * A valid bcrypt hash of a throwaway string. It is checked when the email does not exist so that
     * unknown emails and wrong passwords take about the same time to answer.
     */
    private static final String DUMMY_HASH = "$2b$10$RImAFiUT0hHmW/B1gfqzz.RjI8lfpYCWT1cugbkZHWYPec.dn3l/i";

    /** bcrypt only reads the first 72 bytes of a password. */
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * Checks the email and password against the database.
     * Returns the signed-in user, or empty for any failure. The caller cannot tell which check failed.
     */
    public Optional<SessionUser> signIn(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isEmpty()
                || password.length() > MAX_PASSWORD_LENGTH) {
            return Optional.empty();
        }

        Optional<LoginRecord> found = users.findForLogin(email.trim());
        String hash = found.map(LoginRecord::passwordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = encoder.matches(password, hash);

        if (found.isEmpty() || !passwordMatches) {
            return Optional.empty();
        }
        LoginRecord user = found.get();

        // BR-05: only active accounts can sign in
        if (!"active".equals(user.accountStatus())) {
            return Optional.empty();
        }

        users.touchLastLogin(user.userId());
        return Optional.of(new SessionUser(user.userId(), user.displayName(), user.email(), user.roleName()));
    }
}