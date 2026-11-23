
package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
public class PartnerListingRepository {

    private final JdbcTemplate jdbc;

    public PartnerListingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Retrieve the active listing categories. */
    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("""
                SELECT category_id, name
                FROM category
                WHERE is_active = true
                ORDER BY display_order, name
                """);
    }

    /** Retrieve the available fulfillment methods. */
    public List<Map<String, Object>> fulfillmentMethods() {
        return jdbc.queryForList("""
                SELECT method_id, method_name
                FROM fulfillment_method
                WHERE is_active = true
                ORDER BY method_id
                """);
    }

    /** Find an approved organization owned by the signed-in partner. */
    public List<Map<String, Object>> approvedOrganization(long userId) {
        return jdbc.queryForList("""
                SELECT organization_id, org_name
                FROM organization
                WHERE owner_user_id = ?
                  AND approval_status = 'approved'
                """, userId);
    }

    /** Retrieve listings belonging to the signed-in partner's organization. */
    public List<Map<String, Object>> listingsForOwner(long userId) {
        return jdbc.queryForList("""
            SELECT l.listing_id,
                   l.title,
                   l.description,
                   l.listing_type,
                   l.offering_kind,
                   l.listing_status,
                   l.quantity_available,
                   l.created_at,
                   c.name AS category_name
            FROM listing l
            JOIN organization o
                ON o.organization_id = l.organization_id
            JOIN category c
                ON c.category_id = l.category_id
            WHERE o.owner_user_id = ?
              AND l.owner_user_id = o.owner_user_id
            ORDER BY l.created_at DESC, l.listing_id DESC
            """, userId);
    }

    /** Check whether the partner has a saved location. */
    public boolean hasLocation(long userId) {
        Boolean found = jdbc.queryForObject("""
                SELECT postal_code IS NOT NULL
                   AND latitude IS NOT NULL
                   AND longitude IS NOT NULL
                FROM user_account
                WHERE user_id = ?
                """, Boolean.class, userId);

        return Boolean.TRUE.equals(found);
    }

    /** Publish a listing belonging to the partner's approved organization. */
    public int createListing(
            long userId,
            int categoryId,
            int fulfillmentMethodId,
            String title,
            String description,
            String listingType,
            String offeringKind,
            String tradeWanted,
            Integer quantityAvailable,
            String pickupDropoffNotes) {

        Integer listingId = jdbc.queryForObject("""
                INSERT INTO listing (
                    owner_user_id,
                    organization_id,
                    category_id,
                    fulfillment_method_id,
                    title,
                    description,
                    listing_type,
                    offering_kind,
                    trade_wanted,
                    quantity_available,
                    pickup_dropoff_notes,
                    city,
                    postal_code,
                    latitude,
                    longitude,
                    listing_status,
                    published_at
                )
                SELECT
                    u.user_id,
                    o.organization_id,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    u.city,
                    u.postal_code,
                    u.latitude,
                    u.longitude,
                    'active',
                    CURRENT_TIMESTAMP
                FROM user_account u
                JOIN organization o
                    ON o.owner_user_id = u.user_id
                WHERE u.user_id = ?
                  AND u.account_status = 'active'
                  AND o.approval_status = 'approved'
                  AND u.postal_code IS NOT NULL
                  AND u.latitude IS NOT NULL
                  AND u.longitude IS NOT NULL
                RETURNING listing_id
                """,
                Integer.class,
                categoryId,
                fulfillmentMethodId,
                title,
                description,
                listingType,
                offeringKind,
                tradeWanted,
                quantityAvailable,
                pickupDropoffNotes,
                userId
        );

        return Objects.requireNonNull(listingId);
    }
}
