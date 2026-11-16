package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

/** Read-only queries restricted to the signed-in partner's own organization. */
@Repository
public class PartnerRequestRepository {
    private final JdbcTemplate jdbc;

    public PartnerRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> organizationForOwner(long ownerUserId) {
        return jdbc.queryForList("""
                SELECT organization_id, org_name, approval_status
                FROM organization
                WHERE owner_user_id = ?
                """, ownerUserId);
    }

    public List<Map<String, Object>> requestsForOwner(long ownerUserId) {
        return jdbc.queryForList("""
                SELECT r.request_id, r.request_status, r.message,
                       r.quantity_requested, r.requested_at, r.responded_at,
                       l.listing_id, l.title AS listing_title,
                       l.listing_type, l.offering_kind,
                       u.display_name AS requester_name,
                       o.org_name AS organization_name
                FROM request r
                JOIN listing l ON l.listing_id = r.listing_id
                JOIN organization o ON o.organization_id = l.organization_id
                JOIN user_account u ON u.user_id = r.requester_user_id
                WHERE o.owner_user_id = ?
                  AND l.owner_user_id = o.owner_user_id
                ORDER BY r.requested_at DESC, r.request_id DESC
                LIMIT 200
                """, ownerUserId);
    }
}
