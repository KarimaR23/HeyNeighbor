-- ============================================================
-- HeyNeighbor - Sprint Cycle IV - Community Moderator
-- Sample data for "Community Moderator views reported listings"
-- Safe to run more than once. Uses the Sprint III sample users and listings.
-- ============================================================

-- The moderator role needs this permission to open /moderator pages
INSERT INTO permission (permission_code, permission_area, is_admin_only, description)
VALUES ('report.view_listing', 'moderation', false, 'View reports about listings')
ON CONFLICT (permission_code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r, permission p
WHERE r.role_name = 'moderator' AND p.permission_code = 'report.view_listing'
ON CONFLICT DO NOTHING;

-- Reports about listings
INSERT INTO report
    (reporter_user_id, report_type, reported_listing_id, reported_user_id,
     routed_to_role_id, reason, description, report_status, created_at, resolved_at)
SELECT reporter.user_id, 'listing', l.listing_id, l.owner_user_id,
       (SELECT role_id FROM role WHERE role_name = 'moderator'),
       v.reason, v.description, v.status, now() - v.age,
       CASE WHEN v.status IN ('resolved', 'dismissed') THEN now() - v.age + interval '1 day' END
FROM (VALUES
  ('Free math tutoring for middle schoolers', 'tom.nguyen@outlook.com', 'unsafe',
   'Listing asks parents to share their child''s school name in the first message.', 'open', interval '2 days'),
  ('Bike tune-up and repair', 'rosa.mendez@yahoo.com', 'spam',
   'Posted three copies of the same listing.', 'in_review', interval '5 days'),
  ('Bike tune-up and repair', 'aaliyah.brooks@gmail.com', 'fraud',
   'Asked me to pay a deposit before the tune-up.', 'open', interval '1 day'),
  ('Holiday meal boxes', 'tom.nguyen@outlook.com', 'other',
   'Listing says 198 boxes are left, but the pantry told me they had run out.', 'dismissed', interval '6 days')
) AS v(title, reporter_email, reason, description, status, age)
JOIN listing l             ON l.title = v.title
JOIN user_account reporter ON reporter.email = v.reporter_email
WHERE NOT EXISTS (
    SELECT 1 FROM report x
    WHERE x.report_type = 'listing'
      AND x.reported_listing_id = l.listing_id
      AND x.reporter_user_id = reporter.user_id
      AND x.reason = v.reason);

-- Check: what the moderator page will show
SELECT rp.report_id, l.title, rp.reason, rp.report_status, u.display_name AS reported_by, rp.created_at
FROM report rp
JOIN listing l      ON l.listing_id = rp.reported_listing_id
JOIN user_account u ON u.user_id = rp.reporter_user_id
WHERE rp.report_type = 'listing'
ORDER BY rp.created_at DESC;
