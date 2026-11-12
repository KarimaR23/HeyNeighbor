package com.hn.heyneighbor.nav;

/** One entry in a role's navigation. Entries that are not built yet are shown but not clickable. */
public record NavItem(String label, String href, boolean enabled, String icon, String description) {

    // JavaBean-style getters so Thymeleaf expressions such as ${n.label} always resolve
    public String getLabel() { return label; }
    public String getHref() { return href; }
    public boolean isEnabled() { return enabled; }
    public String getIcon() { return icon; }
    public String getDescription() { return description; }
}
