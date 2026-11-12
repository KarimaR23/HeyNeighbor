package com.hn.heyneighbor.controller;

import java.util.List;

/** The values each filter accepts. Anything else typed into the address bar is ignored. */
final class Options {

    static final List<String> ACCOUNT_STATUSES = List.of("pending", "active", "deactivated", "suspended", "banned");
    static final List<String> REQUEST_STATUSES = List.of("pending", "approved", "declined", "withdrawn", "fulfilled", "expired");
    static final List<String> REPORT_STATUSES = List.of("open", "in_review", "resolved", "dismissed");
    static final List<String> REPORT_REASONS = List.of("harassment", "fraud", "unsafe", "inappropriate", "spam", "no_show", "other");
    static final List<String> SUPPORT_STATUSES = List.of("open", "in_progress", "waiting_on_user", "escalated", "resolved", "closed");
    static final List<String> SUPPORT_UNRESOLVED = List.of("open", "in_progress", "waiting_on_user", "escalated");
    static final List<String> SUPPORT_PRIORITIES = List.of("urgent", "high", "normal", "low");
    static final List<String> SUPPORT_TYPES = List.of("exchange_problem", "account_problem", "partner_assistance", "dispute", "role_change", "other");
    static final List<String> LISTING_TYPES = List.of("gift", "trade");
    static final List<String> LISTING_KINDS = List.of("good", "service");

    private Options() {}

    /** Trimmed text, or null if blank. */
    static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The value if it is one of the allowed ones, otherwise null. */
    static String oneOf(String value, List<String> allowed) {
        String trimmed = text(value);
        return trimmed != null && allowed.contains(trimmed) ? trimmed : null;
    }
}