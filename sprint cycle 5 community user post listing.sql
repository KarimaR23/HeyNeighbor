-- ============================================================
-- HeyNeighbor - Sprint Cycle V - Community User
-- Use case: The Community User creates a new listing for an item, service, or trade.
-- Safe to run more than once.
-- ============================================================

-- 1. Permissions ------------------------------------------------------------------------
-- /community pages need listing.browse; /community/listings/new needs listing.create (AccessRules.java)
INSERT INTO permission (permission_code, permission_area, is_admin_only, description) VALUES
    ('listing.browse', 'listings', false, 'Browse and search listings'),
    ('listing.create', 'listings', false, 'Create a new listing')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r, permission p
WHERE r.role_name = 'community_user'
  AND p.permission_code IN ('listing.browse', 'listing.create')
ON CONFLICT DO NOTHING;


-- 2. Before / after checks for the demo video --------------------------------------------

-- 2a. Tom's listings (run before posting, then again after: the new one appears at the top)
SELECT l.listing_id, l.title, l.listing_type, l.offering_kind, c.name AS category,
       fm.method_name AS fulfillment, l.trade_wanted, l.quantity_available, l.quantity_unit,
       l.listing_status, l.city, l.postal_code, l.listing_images, l.created_at
FROM listing l
JOIN category c            ON c.category_id = l.category_id
JOIN fulfillment_method fm ON fm.method_id = l.fulfillment_method_id
WHERE l.owner_user_id = (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com')
ORDER BY l.created_at DESC;

-- 2b. How many listings each user has (count goes up by one after posting)
SELECT u.email, count(l.listing_id) AS listings
FROM user_account u
LEFT JOIN listing l ON l.owner_user_id = u.user_id
JOIN role r ON r.role_id = u.role_id
WHERE r.role_name = 'community_user'
GROUP BY u.email
ORDER BY u.email;


-- 3. Optional: undo a test listing (replace 123 with the listing_id you want to remove)
-- DELETE FROM listing WHERE listing_id = 123
--   AND owner_user_id = (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com');
