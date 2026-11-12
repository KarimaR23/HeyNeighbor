package com.hn.heyneighbor.repository;

/** Builds a safe LIKE pattern from what the user typed. */
final class Like {

    private Like() {}

    /** "%text%" with the LIKE wildcards in the text escaped. */
    static String contains(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}