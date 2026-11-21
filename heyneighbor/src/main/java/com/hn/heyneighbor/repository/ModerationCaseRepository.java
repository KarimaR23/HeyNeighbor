package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Community moderator: review one reported listing and record the moderation decision. */
@Repository
public class ModerationCaseRepository {

    private final JdbcTemplate jdbc;

    public ModerationCaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One listing report with the listing, its owner, the reporter and the moderation case (if any). */
    public Optional<Map<String, Object>> findReport(long reportId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT rp.report_id, rp.reason, rp.description, rp.report_status, rp.created_at, rp.resolved_at,
                       rp.case_id, rp.reporter_user_id,
                       l.listing_id, l.title AS listing_title, l.description AS listing_description,
                       l.listing_status, l.listing_type, l.offering_kind, l.city AS listing_city,
                       l.postal_code AS listing_postal_code, l.created_at AS listing_created_at,
                       l.owner_user_id,
                       cat.name AS category_name,
                       owner.display_name AS listing_owner, owner.email AS owner_email,
                       reporter.display_name AS reporter_name,
                       mc.case_status, mc.title AS case_title, mc.findings, mc.resolution, mc.action_type,
                       mc.action_taken_at, mc.closed_at, moderator.display_name AS moderator_name
                FROM report rp
                JOIN listing l             ON l.listing_id = rp.reported_listing_id
                JOIN category cat          ON cat.category_id = l.category_id
                JOIN user_account owner    ON owner.user_id = l.owner_user_id
                JOIN user_account reporter ON reporter.user_id = rp.reporter_user_id
                LEFT JOIN moderation_case mc  ON mc.case_id = rp.case_id
                LEFT JOIN user_account moderator ON moderator.user_id = mc.assigned_moderator_id
                WHERE rp.report_id = ? AND rp.report_type = 'listing'
                """, reportId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Other reports filed against the same listing, so the moderator sees the whole picture. */
    public List<Map<String, Object>> otherReportsOnListing(long listingId, long exceptReportId) {
        return jdbc.queryForList("""
                SELECT rp.report_id, rp.reason, rp.report_status, rp.created_at, reporter.display_name AS reporter_name
                FROM report rp
                JOIN user_account reporter ON reporter.user_id = rp.reporter_user_id
                WHERE rp.reported_listing_id = ? AND rp.report_id <> ?
                ORDER BY rp.created_at DESC
                """, listingId, exceptReportId);
    }

    /** Opens the moderation case for a report. Returns the new case_id. */
    public long createCase(long moderatorId, long subjectUserId, String title, long listingId,
                           String status, String findings, String resolution, String actionType, boolean closed) {
        Long id = jdbc.queryForObject("""
                INSERT INTO moderation_case
                    (assigned_moderator_id, subject_user_id, title, case_status, findings, resolution,
                     action_type, target_listing_id, action_taken_at, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                        CASE WHEN ? THEN now() END,
                        CASE WHEN ? THEN now() END)
                RETURNING case_id
                """, Long.class, moderatorId, subjectUserId, title, status, findings, resolution,
                actionType, listingId, closed, closed);
        return id == null ? 0 : id;
    }

    /** Updates an open case. Returns 0 when the case was already closed. */
    public int updateCase(long caseId, String status, String findings, String resolution,
                          String actionType, boolean closed) {
        return jdbc.update("""
                UPDATE moderation_case
                SET case_status     = ?,
                    findings        = ?,
                    resolution      = ?,
                    action_type     = ?,
                    action_taken_at = CASE WHEN ? THEN now() ELSE action_taken_at END,
                    closed_at       = CASE WHEN ? THEN now() ELSE closed_at END,
                    updated_at      = now()
                WHERE case_id = ? AND case_status <> 'closed'
                """, status, findings, resolution, actionType, closed, closed, caseId);
    }

    /** Links the report to its case and moves the report along. Returns 0 when the report was already finished. */
    public int updateReport(long reportId, long caseId, String reportStatus, boolean closed) {
        return jdbc.update("""
                UPDATE report
                SET case_id = ?,
                    report_status = ?,
                    resolved_at = CASE WHEN ? THEN now() ELSE resolved_at END
                WHERE report_id = ? AND report_status IN ('open', 'in_review')
                """, caseId, reportStatus, closed, reportId);
    }

    /** Hides or removes the listing. Does nothing if it was already in that state (or already removed). */
    public void setListingStatus(long listingId, String newStatus) {
        jdbc.update("""
            UPDATE listing
            SET listing_status = ?, updated_at = now()
            WHERE listing_id = ? AND listing_status NOT IN (?, 'removed')
            """, newStatus, listingId, newStatus);
    }

    public void notifyUser(long userId, String title, String body, long reportId) {
        jdbc.update("""
                INSERT INTO notification (user_id, notification_type, title, body, related_entity_type, related_entity_id)
                VALUES (?, 'report_update', ?, ?, 'report', ?)
                """, userId, title, body, reportId);
    }
}