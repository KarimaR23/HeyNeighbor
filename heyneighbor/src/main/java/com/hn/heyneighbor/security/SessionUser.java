package com.hn.heyneighbor.security;

import java.io.Serializable;

/** The signed-in user, kept in the HTTP session after a successful sign-in. */
public record SessionUser(long userId, String displayName, String email, String roleName)
        implements Serializable {

    /** Role name shown in the page header, for example "Community Partner". */
    public String roleLabel() {
        return RoleHome.labelFor(roleName);
    }

    // JavaBean-style getters so Thymeleaf expressions such as ${currentUser.displayName} always resolve
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getRoleName() { return roleName; }
    public String getRoleLabel() { return roleLabel(); }
}
