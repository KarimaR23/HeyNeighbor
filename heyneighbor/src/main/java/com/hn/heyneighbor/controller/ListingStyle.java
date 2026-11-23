package com.hn.heyneighbor.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Circle colors for people without a profile picture. */
    private static final String[] AVATAR_COLORS = {"#1e40af", "#c8102e", "#2f9e44", "#8b5e3c", "#7c3aed", "#0e7490", "#b45309"};

    /**
     * Adds "emoji" and "color" (listing picture) and "poster_initial" and "poster_color"
     * (circle shown when the poster has no profile picture) to each listing row.
     * Rows from JdbcTemplate.queryForList are mutable.
     */
    static List<Map<String, Object>> decorate(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Look look = forCategory(row.get("category_name"));
            row.put("emoji", look.emoji());
            row.put("color", look.color());
            row.put("photo", firstPhoto(row.get("listing_images")));

            String poster = row.get("posted_by") == null ? "" : row.get("posted_by").toString().trim();
            row.put("poster_initial", poster.isEmpty() ? "?" : poster.substring(0, 1).toUpperCase(Locale.ROOT));
            // same person, same color every time
            row.put("poster_color", AVATAR_COLORS[Math.floorMod(poster.hashCode(), AVATAR_COLORS.length)]);
            Object avatar = row.get("poster_avatar");
            if (avatar != null && avatar.toString().isBlank()) {
                row.put("poster_avatar", null);
            }
        }
        return rows;
    }

    private static final Pattern FIRST_STRING = Pattern.compile("\"([^\"]+)\"");

    /**
     * First photo from listing.listing_images (a JSON array of paths), or null.
     * Only our own uploads (/uploads/listings/...) and https links are shown.
     */
    static String firstPhoto(Object listingImages) {
        if (listingImages == null) return null;
        Matcher m = FIRST_STRING.matcher(listingImages.toString());
        if (!m.find()) return null;
        String path = m.group(1);
        boolean ours = path.startsWith("/uploads/listings/") && !path.contains("..");
        return ours || path.startsWith("https://") ? path : null;
    }

    /** Adds "emoji" to each category row, for the category chips. */
    static List<Map<String, Object>> decorateCategories(List<Map<String, Object>> categories) {
        for (Map<String, Object> c : categories) {
            c.put("emoji", forCategory(c.get("name")).emoji());
        }
        return categories;
    }
}
