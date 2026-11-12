package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("ALL")
@Repository
public class AnalyticsRepository {

    public static final List<String> TYPES = List.of("activity", "growth", "category", "partner");
    public static final List<String> GROUPS = List.of("day", "week", "month");
    public static final List<String> ROLES = List.of("community_user", "community_partner");

    public record Column(String key, String label, boolean numeric) {
        public String getKey() { return key; }
        public String getLabel() { return label; }
        public boolean isNumeric() { return numeric; }
    }

    public record Result(String title, String description, List<Column> columns, List<Map<String, Object>> rows) {
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public List<Column> getColumns() { return columns; }
        public List<Map<String, Object>> getRows() { return rows; }
    }

    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Result run(String type, String group, LocalDate from, LocalDate to,
                      List<Integer> categoryIds, List<String> roles) {
        Date start = Date.valueOf(from);
        Date end = Date.valueOf(to.plusDays(1));   // exclusive upper bound
        String unit = GROUPS.contains(group) ? group : "week";
        String format = unit.equals("month") ? "YYYY-MM" : "YYYY-MM-DD";
        String bucketLabel = switch (unit) {
            case "day" -> "Day";
            case "month" -> "Month";
            default -> "Week starting";
        };

        return switch (type) {
            case "growth" -> growth(unit, format, bucketLabel, start, end, roles);
            case "category" -> category(start, end, categoryIds);
            case "partner" -> partner(start, end, categoryIds);
            default -> activity(unit, format, bucketLabel, start, end, categoryIds, roles);
        };
    }

    private Result activity(String unit, String format, String bucketLabel, Date start, Date end,
                            List<Integer> categoryIds, List<String> roles) {
        String catFilter = categoryIds.isEmpty() ? "" : " AND l.category_id IN (" + marks(categoryIds.size()) + ")";
        String roleFilter = roles.isEmpty() ? "" : " AND r.role_name IN (" + marks(roles.size()) + ")";

        String sql = """
                SELECT bucket,
                       SUM(listings)::bigint  AS listings,
                       SUM(exchanges)::bigint AS exchanges,
                       SUM(members)::bigint   AS members
                FROM (
                    SELECT to_char(date_trunc('{unit}', l.created_at), '{format}') AS bucket,
                           1 AS listings, 0 AS exchanges, 0 AS members
                    FROM listing l
                    WHERE l.created_at >= ? AND l.created_at < ? {catFilter}
                  UNION ALL
                    SELECT to_char(date_trunc('{unit}', e.completed_at), '{format}'),
                           0, 1, 0
                    FROM exchange e
                    JOIN listing l ON l.listing_id = e.listing_id
                    WHERE e.exchange_status = 'completed'
                      AND e.completed_at >= ? AND e.completed_at < ? {catFilter}
                  UNION ALL
                    SELECT to_char(date_trunc('{unit}', u.created_at), '{format}'),
                           0, 0, 1
                    FROM user_account u
                    JOIN role r ON r.role_id = u.role_id
                    WHERE u.created_at >= ? AND u.created_at < ? {roleFilter}
                ) x
                GROUP BY bucket
                ORDER BY bucket
                """
                .replace("{unit}", unit).replace("{format}", format)
                .replace("{catFilter}", catFilter).replace("{roleFilter}", roleFilter);

        List<Object> args = new ArrayList<>();
        args.add(start); args.add(end); args.addAll(categoryIds);
        args.add(start); args.add(end); args.addAll(categoryIds);
        args.add(start); args.add(end); args.addAll(roles);

        return new Result("Platform Activity",
                "New listings, completed exchanges and new members for each period.",
                List.of(new Column("bucket", bucketLabel, false),
                        new Column("listings", "New listings", true),
                        new Column("exchanges", "Completed exchanges", true),
                        new Column("members", "New members", true)),
                jdbc.queryForList(sql, args.toArray()));
    }

    private Result growth(String unit, String format, String bucketLabel, Date start, Date end, List<String> roles) {
        String roleFilter = roles.isEmpty() ? "" : " AND r.role_name IN (" + marks(roles.size()) + ")";

        String sql = """
                SELECT to_char(date_trunc('{unit}', u.created_at), '{format}') AS bucket,
                       COUNT(*) FILTER (WHERE r.role_name = 'community_user')    AS community_users,
                       COUNT(*) FILTER (WHERE r.role_name = 'community_partner') AS partners,
                       COUNT(*) FILTER (WHERE r.role_name NOT IN ('community_user', 'community_partner')) AS staff,
                       COUNT(*) AS total
                FROM user_account u
                JOIN role r ON r.role_id = u.role_id
                WHERE u.created_at >= ? AND u.created_at < ? {roleFilter}
                GROUP BY 1
                ORDER BY 1
                """
                .replace("{unit}", unit).replace("{format}", format).replace("{roleFilter}", roleFilter);

        List<Object> args = new ArrayList<>();
        args.add(start); args.add(end); args.addAll(roles);

        return new Result("User Growth",
                "Sign-ups by role for each period.",
                List.of(new Column("bucket", bucketLabel, false),
                        new Column("community_users", "Community users", true),
                        new Column("partners", "Partners", true),
                        new Column("staff", "Staff", true),
                        new Column("total", "Total sign-ups", true)),
                jdbc.queryForList(sql, args.toArray()));
    }

    private Result category(Date start, Date end, List<Integer> categoryIds) {
        String catFilter = categoryIds.isEmpty() ? "" : " WHERE c.category_id IN (" + marks(categoryIds.size()) + ")";

        String sql = """
                SELECT c.name AS category,
                       (SELECT COUNT(*) FROM listing l
                         WHERE l.category_id = c.category_id
                           AND l.created_at >= ? AND l.created_at < ?) AS listings,
                       (SELECT COUNT(*) FROM request rq
                         JOIN listing l ON l.listing_id = rq.listing_id
                         WHERE l.category_id = c.category_id
                           AND rq.requested_at >= ? AND rq.requested_at < ?) AS requests,
                       (SELECT COUNT(*) FROM exchange e
                         JOIN listing l ON l.listing_id = e.listing_id
                         WHERE l.category_id = c.category_id AND e.exchange_status = 'completed'
                           AND e.completed_at >= ? AND e.completed_at < ?) AS completed
                FROM category c
                {catFilter}
                ORDER BY listings DESC, completed DESC, c.name
                """.replace("{catFilter}", catFilter);

        List<Object> args = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            args.add(start); args.add(end);
        }
        args.addAll(categoryIds);

        return new Result("Category Performance",
                "Listings posted, requests made and exchanges completed in each category.",
                List.of(new Column("category", "Category", false),
                        new Column("listings", "New listings", true),
                        new Column("requests", "Requests", true),
                        new Column("completed", "Completed exchanges", true)),
                jdbc.queryForList(sql, args.toArray()));
    }

    private Result partner(Date start, Date end, List<Integer> categoryIds) {
        String catFilter = categoryIds.isEmpty() ? "" : " AND l.category_id IN (" + marks(categoryIds.size()) + ")";

        String sql = """
                SELECT o.org_name AS partner,
                       (SELECT COUNT(*) FROM listing l
                         WHERE l.organization_id = o.organization_id
                           AND l.created_at >= ? AND l.created_at < ? {catFilter}) AS listings,
                       (SELECT COUNT(*) FROM exchange e
                         JOIN listing l ON l.listing_id = e.listing_id
                         WHERE l.organization_id = o.organization_id AND e.exchange_status = 'completed'
                           AND e.completed_at >= ? AND e.completed_at < ? {catFilter}) AS completed,
                       (SELECT COALESCE(SUM(e.quantity), 0) FROM exchange e
                         JOIN listing l ON l.listing_id = e.listing_id
                         WHERE l.organization_id = o.organization_id AND e.exchange_status = 'completed'
                           AND e.completed_at >= ? AND e.completed_at < ? {catFilter}) AS units
                FROM organization o
                WHERE o.approval_status = 'approved'
                ORDER BY completed DESC, listings DESC, o.org_name
                """.replace("{catFilter}", catFilter);

        List<Object> args = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            args.add(start); args.add(end); args.addAll(categoryIds);
        }

        return new Result("Partner Impact",
                "Resources community partners listed and handed over to neighbors.",
                List.of(new Column("partner", "Partner", false),
                        new Column("listings", "New listings", true),
                        new Column("completed", "Completed exchanges", true),
                        new Column("units", "Units provided", true)),
                jdbc.queryForList(sql, args.toArray()));
    }

    private static String marks(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }
}
