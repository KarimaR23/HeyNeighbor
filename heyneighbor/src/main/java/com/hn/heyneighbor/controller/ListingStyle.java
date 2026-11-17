package com.hn.heyneighbor.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picture and card color for a listing, chosen from its category, matching the cards on the public home page.
 * Used until listings have uploaded photos.
 */
final class ListingStyle {

    private ListingStyle() {
    }

    private record Look(String emoji, String color) {
    }

    private static final Look DEFAULT = new Look("📦", "#e0e7ff");

    private static Look forCategory(Object categoryName) {
        String name = categoryName == null ? "" : categoryName.toString().toLowerCase(Locale.ROOT);
        if (name.contains("food")) return new Look("🥕", "#fde2e2");
        if (name.contains("shelter")) return new Look("🛏️", "#e9ddfb");
        if (name.contains("utilit")) return new Look("💡", "#fdf3c4");
        if (name.contains("service")) return new Look("🛠️", "#d7ecfb");
        if (name.contains("household")) return new Look("🛋️", "#fdf0c4");
        if (name.contains("cloth")) return new Look("🧥", "#d7ecfb");
        return DEFAULT;
    }

    /** Adds "emoji" and "color" to each listing row (rows from JdbcTemplate.queryForList are mutable). */
    static List<Map<String, Object>> decorate(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Look look = forCategory(row.get("category_name"));
            row.put("emoji", look.emoji());
            row.put("color", look.color());
        }
        return rows;
    }

    /** Adds "emoji" to each category row, for the category chips. */
    static List<Map<String, Object>> decorateCategories(List<Map<String, Object>> categories) {
        for (Map<String, Object> c : categories) {
            c.put("emoji", forCategory(c.get("name")).emoji());
        }
        return categories;
    }
}
