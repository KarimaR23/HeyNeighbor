package com.hn.heyneighbor.service;

import com.hn.heyneighbor.repository.SignupRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Creates a new account.

 * The role chosen here is the account's only role: it is stored once in user_account.role_id and is
 * not changed afterward except by an administrator (for example after a role_change support request).
 * Community users, community partners and support coordinators can sign up; moderators and
 * administrators are created by an administrator.
 */
@Service
public class SignupService {

    public static final String COMMUNITY_USER = "community_user";
    public static final String COMMUNITY_PARTNER = "community_partner";
    public static final String SUPPORT_COORDINATOR = "support_coordinator";
    public static final List<String> ROLES = List.of(COMMUNITY_USER, COMMUNITY_PARTNER, SUPPORT_COORDINATOR);
    public static final List<String> ORG_TYPES =
            List.of("business", "nonprofit", "church", "food_pantry", "shelter", "community_org", "other");

    private static final int PASSWORD_MIN = 8;
    private static final int PASSWORD_MAX_BYTES = 72;   // bcrypt only reads the first 72 bytes
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern ZIP = Pattern.compile("^\\d{5}(-\\d{4})?$");

    /** What the sign-up form sends. Blank text is already null. */
    public record Request(String role, String displayName, String email, String password, String confirmPassword,
                          String postalCode, String city, String state,
                          String orgName, String orgType, String streetAddress) {}

    /** Whether the account was created, or the messages to show the person. */
    public record Result(boolean created, List<String> errors) {
        public boolean isCreated() { return created; }
        public List<String> getErrors() { return errors; }
    }

    private final SignupRepository signups;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transaction;

    public SignupService(SignupRepository signups, PasswordEncoder encoder, TransactionTemplate transaction) {
        this.signups = signups;
        this.encoder = encoder;
        this.transaction = transaction;
    }

    public Result signUp(Request r) {
        List<String> errors = validate(r);
        if (errors.isEmpty() && signups.emailTaken(r.email())) {
            errors.add("An account with that email already exists. Try signing in instead.");
        }
        if (!errors.isEmpty()) {
            return new Result(false, errors);
        }
        try {
            // The user and the organization are saved together or not at all
            transaction.executeWithoutResult(status -> create(r));
        } catch (DuplicateKeyException e) {
            // Two people used the same email at the same moment
            return new Result(false, List.of("An account with that email already exists. Try signing in instead."));
        }
        return new Result(true, List.of());
    }

    private void create(Request r) {
        int roleId = signups.roleId(r.role())
                .orElseThrow(() -> new IllegalStateException("Role not found: " + r.role()));
        int userId = signups.createUser(roleId, r.email(), encoder.encode(r.password()), statusFor(r.role()),
                r.displayName(), r.city(), r.state(), r.postalCode());
        if (COMMUNITY_PARTNER.equals(r.role())) {
            signups.createOrganization(userId, r.orgName(), r.orgType(), r.streetAddress(),
                    r.city(), r.state(), r.postalCode());
        }
    }

    /** Support coordinators see the support queue, so an administrator must activate them first. */
    public static String statusFor(String role) {
        return SUPPORT_COORDINATOR.equals(role) ? "pending" : "active";
    }

    /** Every rule that can be checked without the database. */
    static List<String> validate(Request r) {
        List<String> errors = new ArrayList<>();

        if (r.role() == null || !ROLES.contains(r.role())) {
            errors.add("Choose how you are joining HeyNeighbor.");
            return errors;
        }
        boolean partner = COMMUNITY_PARTNER.equals(r.role());

        if (r.displayName() == null) {
            errors.add("Enter your name.");
        } else if (r.displayName().length() > 100 || hasControlCharacters(r.displayName())) {
            errors.add("Your name must be 100 characters or fewer.");
        }

        if (r.email() == null || !EMAIL.matcher(r.email()).matches()) {
            errors.add("Enter a valid email address.");
        } else if (r.email().length() > 255) {
            errors.add("Your email address is too long.");
        }

        if (r.password() == null || r.password().length() < PASSWORD_MIN) {
            errors.add("Your password must be at least " + PASSWORD_MIN + " characters.");
        } else if (r.password().getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            errors.add("Your password is too long. Use 72 bytes or fewer.");
        } else if (!r.password().equals(r.confirmPassword())) {
            errors.add("The two passwords do not match.");
        }

        if (r.postalCode() == null || !ZIP.matcher(r.postalCode()).matches()) {
            errors.add("Enter a valid ZIP code, for example 30303.");
        }

        if (r.city() != null && r.city().length() > 100) {
            errors.add("City must be 100 characters or fewer.");
        }
        if (r.state() != null && r.state().length() > 50) {
            errors.add("State must be 50 characters or fewer.");
        }

        if (partner) {
            if (r.orgName() == null || r.orgName().length() > 150) {
                errors.add("Enter your organization's name (150 characters or fewer).");
            }
            if (r.orgType() == null || !ORG_TYPES.contains(r.orgType())) {
                errors.add("Choose your organization's type.");
            }
            if (r.streetAddress() == null || r.streetAddress().length() > 200) {
                errors.add("Enter your organization's street address (200 characters or fewer).");
            }
            if (r.city() == null) {
                errors.add("Enter your organization's city.");
            }
            if (r.state() == null) {
                errors.add("Enter your organization's state.");
            }
        }
        return errors;
    }

    private static boolean hasControlCharacters(String text) {
        return text.chars().anyMatch(Character::isISOControl);
    }
}