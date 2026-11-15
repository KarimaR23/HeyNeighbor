package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Community Moderator: reports about listings (Sprint IV retrieval use case). */
@SuppressWarnings("ALL")
@Repository
public class ModerationRepository {

    private final JdbcTemplate jdbc;

    public ModerationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reported listings, open reports first, then newest. Every filter is optional.
     *
     * @param q      matches the listing title, the listing owner's name or the reporter's name
     * @param status report_status (open, in_review, resolved, dismissed)
     * @param reason report reason (harassment, fraud, ...)
     */
    public List<Map<String, Object>> searchListingReports(String q, String status, String reason) {
        StringBuilder sql = new StringBuilder("""
                SELECT rp.report_id, rp.reason, rp.description, rp.report_status, rp.created_at,
                       l.title AS listing_title, l.listing_status,
                       c.name AS category_name,
                       owner.display_name AS owner_name,
                       reporter.display_name AS reporter_name
                FROM report rp
                JOIN listing l             ON l.listing_id = rp.reported_listing_id
                LEFT JOIN category c       ON c.category_id = l.category_id
                JOIN user_account owner    ON owner.user_id = l.owner_user_id
                JOIN user_account reporter ON reporter.user_id = rp.reporter_user_id
                WHERE rp.report_type = 'listing'
                """);
        List<Object> args = new ArrayList<>();
        if (q != null) {
            sql.append(" AND (l.title ILIKE ? OR owner.display_name ILIKE ? OR reporter.display_name ILIKE ?)");
            String pattern = Like.contains(q);
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null) {
            sql.append(" AND rp.report_status = ?");
            args.add(status);
        }
        if (reason != null) {
            sql.append(" AND rp.reason = ?");
            args.add(reason);
        }
        sql.append("""
                 ORDER BY CASE rp.report_status WHEN 'open' THEN 0 WHEN 'in_review' THEN 1 ELSE 2 END,
                          rp.created_at DESC
                 LIMIT 200
                """);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}
