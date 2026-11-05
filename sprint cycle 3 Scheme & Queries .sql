-- ============================================================
-- HeyNeighbor - Sprint Cycle III - Database Implementation
-- Tables created by: Trinity Banks
--   1. review
--   2. favorite
--   3. recent_view
--   4. notification
--   5. system_setting
--
-- Depends on tables already in Neon: user_account, listing, exchange
-- Conventions match the existing tables: identity primary keys,
-- timestamptz with now() defaults, lowercase status values in varchar.
-- ============================================================


-- 1. REVIEW ---------------------------------------------------
-- A participant's rating of the other participant after a completed exchange.
CREATE TABLE review (
    review_id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exchange_id          INTEGER      NOT NULL REFERENCES exchange (exchange_id),
    reviewer_user_id     INTEGER      NOT NULL REFERENCES user_account (user_id),
    reviewee_user_id     INTEGER      NOT NULL REFERENCES user_account (user_id),
    overall_rating       INTEGER      NOT NULL,
    reliability_rating   INTEGER,
    friendliness_rating  INTEGER,
    comment              TEXT,
    review_status        VARCHAR(20)  NOT NULL DEFAULT 'visible',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- BR-45: one review per participant per exchange
    CONSTRAINT review_one_per_exchange UNIQUE (exchange_id, reviewer_user_id),
    -- BR-44: you cannot review yourself
    CONSTRAINT review_not_self CHECK (reviewer_user_id <> reviewee_user_id),
    -- BR-46: ratings are whole numbers 1 to 5
    CONSTRAINT review_overall_rating_range      CHECK (overall_rating BETWEEN 1 AND 5),
    CONSTRAINT review_reliability_rating_range  CHECK (reliability_rating  IS NULL OR reliability_rating  BETWEEN 1 AND 5),
    CONSTRAINT review_friendliness_rating_range CHECK (friendliness_rating IS NULL OR friendliness_rating BETWEEN 1 AND 5),
    CONSTRAINT review_status_values CHECK (review_status IN ('visible', 'hidden', 'removed'))
);

CREATE INDEX review_reviewee_idx ON review (reviewee_user_id);


-- 2. FAVORITE -------------------------------------------------
-- A listing a user saved.
CREATE TABLE favorite (
    user_id     INTEGER     NOT NULL REFERENCES user_account (user_id) ON DELETE CASCADE,
    listing_id  INTEGER     NOT NULL REFERENCES listing (listing_id)   ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- BR-54: a user can favorite a listing only once
    PRIMARY KEY (user_id, listing_id)
);


-- 3. RECENT_VIEW ----------------------------------------------
-- One row per user + listing; last_viewed_at is updated on repeat views.
CREATE TABLE recent_view (
    user_id         INTEGER     NOT NULL REFERENCES user_account (user_id) ON DELETE CASCADE,
    listing_id      INTEGER     NOT NULL REFERENCES listing (listing_id)   ON DELETE CASCADE,
    last_viewed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- BR-55: one row per user and listing (app keeps only the newest 50)
    PRIMARY KEY (user_id, listing_id)
);

CREATE INDEX recent_view_user_time_idx ON recent_view (user_id, last_viewed_at DESC);


-- 4. NOTIFICATION ---------------------------------------------
-- An in-app alert for one user (BR-56).
CREATE TABLE notification (
    notification_id      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              INTEGER      NOT NULL REFERENCES user_account (user_id) ON DELETE CASCADE,
    notification_type    VARCHAR(40)  NOT NULL,
    title                VARCHAR(150) NOT NULL,
    body                 TEXT,
    related_entity_type  VARCHAR(40),
    related_entity_id    INTEGER,          -- not a foreign key: may point to any table
    is_read              BOOLEAN      NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at              TIMESTAMPTZ,

    CONSTRAINT notification_type_values CHECK (notification_type IN (
        'new_message', 'request_received', 'request_approved', 'request_declined',
        'trade_proposed', 'exchange_reserved', 'exchange_completed', 'exchange_cancelled',
        'review_received', 'report_update', 'support_update', 'account_update', 'system')),
    CONSTRAINT notification_read_at_when_read CHECK (read_at IS NULL OR is_read)
);

CREATE INDEX notification_user_unread_idx ON notification (user_id, is_read, created_at DESC);


-- 5. SYSTEM_SETTING -------------------------------------------
-- Administrator-configurable platform values.
CREATE TABLE system_setting (
    setting_id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    setting_key         VARCHAR(100) NOT NULL UNIQUE,   -- BR-70: keys are unique
    setting_value       VARCHAR(500) NOT NULL,
    value_type          VARCHAR(20)  NOT NULL,
    description         TEXT,
    updated_by_user_id  INTEGER      REFERENCES user_account (user_id),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT system_setting_value_type_values CHECK (value_type IN ('string', 'integer', 'boolean', 'decimal'))
);


-- ============================================================
-- Sample data for system_setting (needs no other rows to exist).
-- Sample data for the other four tables needs users, listings and
-- exchanges first, so add it after those tables have sample data.
-- ============================================================
INSERT INTO system_setting (setting_key, setting_value, value_type, description) VALUES
    ('max_search_radius_miles',   '50',   'integer', 'Largest search distance a user may choose (BR-27)'),
    ('default_search_radius_miles','10',  'integer', 'Search distance used when the user has not set one'),
    ('max_listing_photos',        '8',    'integer', 'Maximum number of images on one listing (BR-28)'),
    ('recent_views_kept_per_user','50',   'integer', 'How many recently viewed listings are kept per user (BR-55)'),
    ('listing_expiration_days',   '30',   'integer', 'Days before an active listing expires automatically'),
    ('require_email_verification','true', 'boolean', 'Users must verify their email before posting');


-- ============================================================
-- Check: list the five new tables and their constraints
-- ============================================================
-- SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conrelid::regclass::text IN ('review','favorite','recent_view','notification','system_setting')
-- ORDER BY 1, 2;



-- ============================================================
-- HeyNeighbor - Sprint Cycle III - Database Implementation
-- Tables created: Karima Dewdney
CREATE TABLE role (
  role_id        INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  role_name      VARCHAR(50) NOT NULL UNIQUE,
  description    TEXT,
  is_system_role BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE permission (
  permission_id   INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  permission_code VARCHAR(100) NOT NULL UNIQUE,
  permission_area VARCHAR(40) NOT NULL,
  is_admin_only   BOOLEAN NOT NULL DEFAULT false,
  description     TEXT
);

CREATE TABLE role_permission (
  role_id       INTEGER NOT NULL REFERENCES role(role_id),
  permission_id INTEGER NOT NULL REFERENCES permission(permission_id),
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_account (
  user_id                   INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  role_id                   INTEGER NOT NULL REFERENCES role(role_id),
  email                     VARCHAR(255) NOT NULL UNIQUE,
  password_hash             VARCHAR(255) NOT NULL,
  account_status            VARCHAR(20) NOT NULL DEFAULT 'pending'
    CHECK (account_status IN ('pending','active','deactivated','suspended','banned')),
  email_verified            BOOLEAN NOT NULL DEFAULT false,
  display_name              VARCHAR(100) NOT NULL,
  phone                     VARCHAR(20),
  avatar_image              VARCHAR(500),
  bio                       TEXT,
  city                      VARCHAR(100),
  state                     VARCHAR(50),
  postal_code               VARCHAR(10),
  latitude                  NUMERIC(9,6),
  longitude                 NUMERIC(9,6),
  default_search_radius_miles INTEGER,
  status_changed_by_user_id INTEGER REFERENCES user_account(user_id),
  status_changed_at         TIMESTAMPTZ,
  status_reason             TEXT,
  created_by_user_id        INTEGER REFERENCES user_account(user_id),
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_login_at             TIMESTAMPTZ,
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE organization (
  organization_id     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  owner_user_id       INTEGER NOT NULL UNIQUE REFERENCES user_account(user_id),
  org_name            VARCHAR(150) NOT NULL,
  org_type            VARCHAR(20) NOT NULL
    CHECK (org_type IN ('business','nonprofit','church','food_pantry','shelter','community_org','other')),
  description         TEXT,
  services_offered    TEXT,
  operating_hours     TEXT,
  contact_email       VARCHAR(255),
  contact_phone       VARCHAR(20),
  website_url         VARCHAR(255),
  street_address      VARCHAR(200) NOT NULL,
  city                VARCHAR(100) NOT NULL,
  state               VARCHAR(50) NOT NULL,
  postal_code         VARCHAR(10) NOT NULL,
  latitude            NUMERIC(9,6),
  longitude           NUMERIC(9,6),
  registration_number VARCHAR(50),
  approval_status     VARCHAR(20) NOT NULL DEFAULT 'pending'
    CHECK (approval_status IN ('pending','approved','rejected','deactivated')),
  reviewed_by_user_id INTEGER REFERENCES user_account(user_id),
  reviewed_at         TIMESTAMPTZ,
  rejection_reason    TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- ============================================================
-- HeyNeighbor - Sprint Cycle III - Database Implementation
-- Tables Created Kenyce Holloman
SELECT c.table_name, c.column_name, c.data_type, c.character_maximum_length AS len, c.is_nullable, 
    c.column_default, c.is_identity FROM information_schema.columns c WHERE c.table_schema='public' 
    AND c.table_name IN ('exchange','request','user_account',) ORDER BY c.table_name, c.ordinal_position;

SELECT conrelid::regclass AS tbl, conname, pg_get_constraintdef(oid) AS def FROM pg_constraint WHERE connamespace='public'::regnamespace AND 
    conrelid::regclass::text IN ('exchange','request','listing') ORDER BY 1,2;

-- ============================================================
-- HeyNeighbor - Sprint Cycle III - Database Implementation
-- Christopher Miller 5: support_request, conversation,
-- moderation_case, report, user_restriction
-- ============================================================

CREATE TABLE support_request (
  support_request_id      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  requester_user_id       INTEGER NOT NULL REFERENCES user_account(user_id),
  assigned_coordinator_id INTEGER REFERENCES user_account(user_id),
  related_listing_id      INTEGER REFERENCES listing(listing_id),
  related_exchange_id     INTEGER REFERENCES exchange(exchange_id),
  request_type            VARCHAR(30) NOT NULL
    CHECK (request_type IN ('exchange_problem','account_problem','partner_assistance','dispute','role_change','other')),
  requested_role_id       INTEGER REFERENCES role(role_id),
  subject                 VARCHAR(150) NOT NULL,
  description             TEXT NOT NULL,
  status                  VARCHAR(20) NOT NULL DEFAULT 'open'
    CHECK (status IN ('open','in_progress','waiting_on_user','escalated','resolved','closed')),
  priority                VARCHAR(10) NOT NULL DEFAULT 'normal'
    CHECK (priority IN ('low','normal','high','urgent')),
  dispute_type            VARCHAR(30)
    CHECK (dispute_type IN ('no_show','not_as_described','unavailable','communication','other')),
  recommended_resolution  TEXT,
  resolution_summary      TEXT,
  escalated_to_role_id    INTEGER REFERENCES role(role_id),
  escalation_reason       TEXT,
  escalated_at            TIMESTAMPTZ,
  notes                   JSONB,  -- [{author_user_id, note, is_internal, written_at}]
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at             TIMESTAMPTZ,
  closed_at               TIMESTAMPTZ,
  CONSTRAINT role_change_needs_role CHECK (request_type <> 'role_change' OR requested_role_id IS NOT NULL),
  CONSTRAINT exchange_types_need_exchange CHECK (request_type NOT IN ('exchange_problem','dispute') OR related_exchange_id IS NOT NULL)
);

CREATE TABLE conversation (
  conversation_id     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  conversation_type   VARCHAR(10) NOT NULL
    CHECK (conversation_type IN ('inquiry','request','exchange','support')),
  listing_id          INTEGER REFERENCES listing(listing_id),
  request_id          INTEGER REFERENCES request(request_id),
  exchange_id         INTEGER REFERENCES exchange(exchange_id),
  support_request_id  INTEGER REFERENCES support_request(support_request_id),
  is_locked           BOOLEAN NOT NULL DEFAULT false,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_message_at     TIMESTAMPTZ,
  participant_user_ids JSONB NOT NULL,   -- [1, 2, ...]
  participant_status  JSONB NOT NULL,    -- [{user_id, joined_at, last_read_at, archived}]
  messages            JSONB              -- [{number, sender_user_id, text, sent_at, status}]
);

CREATE TABLE moderation_case (
  case_id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  assigned_moderator_id    INTEGER NOT NULL REFERENCES user_account(user_id),
  subject_user_id          INTEGER REFERENCES user_account(user_id),
  title                    VARCHAR(150) NOT NULL,
  case_status              VARCHAR(20) NOT NULL DEFAULT 'open'
    CHECK (case_status IN ('open','under_review','escalated','closed')),
  findings                 TEXT,
  resolution               TEXT,
  action_type              VARCHAR(30)
    CHECK (action_type IN ('warning','listing_hidden','listing_removed','review_removed','message_removed','restriction_applied','no_action')),
  target_listing_id        INTEGER REFERENCES listing(listing_id),
  target_review_id         INTEGER REFERENCES review(review_id),
  target_conversation_id   INTEGER REFERENCES conversation(conversation_id),
  target_message_number    INTEGER,
  action_taken_at          TIMESTAMPTZ,
  escalated_to_role_id     INTEGER REFERENCES role(role_id),
  escalation_reason        TEXT,
  escalated_at             TIMESTAMPTZ,
  opened_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at                TIMESTAMPTZ
);

CREATE TABLE report (
  report_id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reporter_user_id         INTEGER NOT NULL REFERENCES user_account(user_id),
  report_type              VARCHAR(10) NOT NULL
    CHECK (report_type IN ('listing','user','message','review','exchange')),
  reported_listing_id      INTEGER REFERENCES listing(listing_id),
  reported_user_id         INTEGER REFERENCES user_account(user_id),
  reported_conversation_id INTEGER REFERENCES conversation(conversation_id),
  reported_message_number  INTEGER,
  reported_review_id       INTEGER REFERENCES review(review_id),
  reported_exchange_id     INTEGER REFERENCES exchange(exchange_id),
  routed_to_role_id        INTEGER REFERENCES role(role_id),
  case_id                  INTEGER REFERENCES moderation_case(case_id),
  reason                   VARCHAR(20) NOT NULL
    CHECK (reason IN ('harassment','fraud','unsafe','inappropriate','spam','no_show','other')),
  description              TEXT,
  report_status            VARCHAR(20) NOT NULL DEFAULT 'open'
    CHECK (report_status IN ('open','in_review','resolved','dismissed')),
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at              TIMESTAMPTZ
);

CREATE TABLE user_restriction (
  restriction_id     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  case_id            INTEGER NOT NULL REFERENCES moderation_case(case_id),
  user_id            INTEGER NOT NULL REFERENCES user_account(user_id),
  imposed_by_user_id INTEGER NOT NULL REFERENCES user_account(user_id),
  restriction_type   VARCHAR(20) NOT NULL
    CHECK (restriction_type IN ('no_listings','no_messages','no_exchanges')),
  reason             TEXT NOT NULL,
  starts_at          TIMESTAMPTZ NOT NULL,
  ends_at            TIMESTAMPTZ NOT NULL,
  lifted_at          TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at)
);

CREATE INDEX idx_report_status    ON report(report_status);
CREATE INDEX idx_restriction_user ON user_restriction(user_id, ends_at);


-- ============================================================
-- HeyNeighbor - Sprint Cycle III - Database Implementation
-- Sample data
-- ============================================================
INSERT INTO role (role_name, description, is_system_role) VALUES
    ('community_user',      'Posts gift/trade listings, requests items, messages other users, leaves reviews', true),
    ('community_partner',   'Approved organization that posts resources (food, shelter, services) for the community', true),
    ('moderator',           'Reviews reports, hides or removes content, applies temporary restrictions (BR-61)', true),
    ('support_coordinator', 'Handles support requests and disputes and recommends resolutions', true),
    ('administrator',       'Manages accounts, roles, organization approvals, categories and system settings (BR-74)', true);

INSERT INTO user_account
 (role_id, email, password_hash, account_status, email_verified, display_name, phone,
  bio, city, state, postal_code, latitude, longitude, default_search_radius_miles, created_at)
VALUES
 ((SELECT role_id FROM role WHERE role_name='administrator'),
  'dana.whitfield@neighborlink.org', '$2b$12$Qx8kZ1adminplaceholderhashAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
  'active', true, 'Dana Whitfield', '404-555-0101', 'Platform administrator.',
  'Atlanta', 'GA', '30303', 33.753746, -84.386330, 25, now() - interval '120 days');

INSERT INTO user_account
 (role_id, email, password_hash, account_status, email_verified, display_name, phone,
  bio, city, state, postal_code, latitude, longitude, default_search_radius_miles,
  created_by_user_id, created_at)
SELECT r.role_id, v.email, v.pw, 'active', true, v.display_name, v.phone, v.bio,
       'Atlanta', 'GA', v.zip, v.lat, v.lon, 25,
       (SELECT user_id FROM user_account WHERE email='dana.whitfield@neighborlink.org'),
       now() - interval '100 days'
FROM (VALUES
  ('moderator',           'marcus.reyes@neighborlink.org', '$2b$12$modPlaceholderHashBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB', 'Marcus Reyes',  '404-555-0102', 'Community moderator.',          '30308', 33.772700, -84.381400),
  ('support_coordinator', 'priya.nair@neighborlink.org',   '$2b$12$supPlaceholderHashCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC', 'Priya Nair',    '404-555-0103', 'Support coordinator.',          '30312', 33.738000, -84.372000)
) AS v(role_name, email, pw, display_name, phone, bio, zip, lat, lon)
JOIN role r ON r.role_name = v.role_name;

-- Self-registered users (no creator)
INSERT INTO user_account
 (role_id, email, password_hash, account_status, email_verified, display_name, phone,
  bio, city, state, postal_code, latitude, longitude, default_search_radius_miles, created_at, last_login_at)
SELECT r.role_id, v.email, v.pw, v.status, v.verified, v.display_name, v.phone, v.bio,
       v.city, 'GA', v.zip, v.lat, v.lon, v.radius, now() - v.age, now() - interval '2 days'
FROM (VALUES
  ('community_user',    'aaliyah.brooks@gmail.com',  '$2b$12$userPlaceholderHashDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD', 'active',    true,  'Aaliyah Brooks',  '678-555-0111', 'Nursing student who loves baking and gardening.',   'Atlanta',  '30310', 33.725500, -84.425600, 10, interval '90 days'),
  ('community_user',    'tom.nguyen@outlook.com',    '$2b$12$userPlaceholderHashEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE', 'active',    true,  'Tom Nguyen',      '678-555-0112', 'Handy with bikes and small repairs.',               'Decatur',  '30030', 33.774800, -84.296300, 15, interval '75 days'),
  ('community_user',    'rosa.mendez@yahoo.com',     '$2b$12$userPlaceholderHashFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF', 'active',    true,  'Rosa Mendez',     '470-555-0113', 'Retired teacher, offers tutoring.',                 'Atlanta',  '30318', 33.791100, -84.447000, 20, interval '60 days'),
  ('community_user',    'jordan.p@gmail.com',        '$2b$12$userPlaceholderHashGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG', 'pending',   false, 'Jordan Patel',    NULL,           NULL,                                        'Marietta', '30060', 33.952600, -84.549900, 10, interval '1 day'),
  ('community_user',    'kyle.dawson@gmail.com',     '$2b$12$userPlaceholderHashHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH', 'suspended', true,  'Kyle Dawson',     '404-555-0114', 'Suspended after repeated no-shows.',                'Atlanta',  '30316', 33.723900, -84.340700, 10, interval '80 days'),
  ('community_partner', 'info@greaterhopepantry.org','$2b$12$partnerPlaceholderHashIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIIII', 'active',    true,  'Greater Hope Food Pantry', '404-555-0120', 'Community food pantry account.',         'Atlanta',  '30314', 33.756600, -84.413000, 25, interval '70 days'),
  ('community_partner', 'contact@safeharborshelter.org','$2b$12$partnerPlaceholderHashJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ','active',  true,  'Safe Harbor Shelter', '404-555-0121', 'Emergency shelter account.',                  'Atlanta',  '30303', 33.749000, -84.388000, 25, interval '65 days'),
  ('community_partner', 'hello@eastsidefixit.com',   '$2b$12$partnerPlaceholderHashKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK', 'pending',   true,  'Eastside Fix-It Co-op', '404-555-0122', 'Awaiting organization approval.',            'Decatur',  '30032', 33.740300, -84.268800, 25, interval '3 days')
) AS v(role_name, email, pw, status, verified, display_name, phone, bio, city, zip, lat, lon, radius, age)
JOIN role r ON r.role_name = v.role_name;

-- Record who suspended Kyle and why
UPDATE user_account
SET status_changed_by_user_id = (SELECT user_id FROM user_account WHERE email='dana.whitfield@neighborlink.org'),
    status_changed_at = now() - interval '10 days',
    status_reason = 'Three reported no-shows on scheduled pickups.'
WHERE email = 'kyle.dawson@gmail.com';

SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('listing', 'request', 'exchange', 'category', 'fulfillment_method', 'support_request')
ORDER BY table_name, ordinal_position;

-- 1. FULFILLMENT_METHOD ---------------------------------------
INSERT INTO fulfillment_method (method_name, description) VALUES
    ('pickup',   'The receiver picks the item up or attends in person'),
    ('drop-off', 'The provider delivers the item or service'),
    ('both',     'Either pickup or drop-off can be arranged')
ON CONFLICT (method_name) DO NOTHING;

-- 2. CATEGORY -------------------------------------------------
INSERT INTO category (name, description, display_order) VALUES
    ('Food',            'Groceries, meals and baked goods',                1),
    ('Shelter',         'Emergency beds and housing help',                 2),
    ('Utilities',       'Help with bills and utility services',            3),
    ('Services',        'Tutoring, repairs and other help',                4),
    ('Household items', 'Furniture, tools, clothing and other goods',      5)
ON CONFLICT (name) DO NOTHING;

-- 3. LISTING --------------------------------------------------
INSERT INTO listing
    (owner_user_id, organization_id, category_id, listing_status, fulfillment_method_id,
     title, description, listing_type, offering_kind, trade_wanted,
     quantity_available, quantity_unit, eligibility_info, availability_notes,
     city, postal_code, latitude, longitude, published_at, completed_at)
VALUES
    ((SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'), NULL,
     (SELECT category_id FROM category WHERE name = 'Food'), 'completed',
     (SELECT method_id FROM fulfillment_method WHERE method_name = 'pickup'),
     'Homemade banana bread loaves', 'Two fresh loaves, baked this week. Contains walnuts.',
     'gift', 'good', NULL, NULL, NULL, NULL, 'Weekday evenings after 6pm',
     'Atlanta', '30310', 33.725500, -84.425600, now() - interval '12 days', now() - interval '9 days'),

    ((SELECT user_id FROM user_account WHERE email = 'info@greaterhopepantry.org'),
     (SELECT organization_id FROM organization WHERE org_name = 'Greater Hope Food Pantry'),
     (SELECT category_id FROM category WHERE name = 'Food'), 'active',
     (SELECT method_id FROM fulfillment_method WHERE method_name = 'pickup'),
     'Holiday meal boxes', 'Boxes with turkey, sides and dessert for a family of four.',
     'gift', 'good', NULL, 198, 'boxes', 'Open to any household in the Atlanta area',
     'Mon-Fri 9am-5pm; Sat 10am-2pm', 'Atlanta', '30314', 33.756600, -84.413000, now() - interval '10 days', NULL),

    ((SELECT user_id FROM user_account WHERE email = 'rosa.mendez@yahoo.com'), NULL,
     (SELECT category_id FROM category WHERE name = 'Services'), 'active',
     (SELECT method_id FROM fulfillment_method WHERE method_name = 'both'),
     'Free math tutoring for middle schoolers', 'One-hour sessions covering pre-algebra and fractions.',
     'gift', 'service', NULL, NULL, NULL, NULL, 'Tuesday and Thursday afternoons',
     'Atlanta', '30318', 33.791100, -84.447000, now() - interval '6 days', NULL),

    ((SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'), NULL,
     (SELECT category_id FROM category WHERE name = 'Services'), 'active',
     (SELECT method_id FROM fulfillment_method WHERE method_name = 'pickup'),
     'Bike tune-up and repair', 'Brakes, gears and tire repair for most bikes.',
     'trade', 'service', 'Fresh produce or baked goods', NULL, NULL, NULL, 'Saturdays',
     'Decatur', '30030', 33.774800, -84.296300, now() - interval '5 days', NULL);

-- 4. REQUEST --------------------------------------------------
INSERT INTO request
    (listing_id, requester_user_id, request_status, message, quantity_requested,
     offered_description, requested_at, responded_at, responded_by_user_id)
VALUES
    ((SELECT listing_id FROM listing WHERE title = 'Homemade banana bread loaves'),
     (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
     'approved', 'My neighbor is sick and this would cheer her up. Thank you!', NULL, NULL,
     now() - interval '11 days', now() - interval '10 days',
     (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com')),

    ((SELECT listing_id FROM listing WHERE title = 'Holiday meal boxes'),
     (SELECT user_id FROM user_account WHERE email = 'kyle.dawson@gmail.com'),
     'approved', 'Two boxes for my family and my mother.', 2, NULL,
     now() - interval '8 days', now() - interval '8 days',
     (SELECT user_id FROM user_account WHERE email = 'info@greaterhopepantry.org')),

    ((SELECT listing_id FROM listing WHERE title = 'Bike tune-up and repair'),
     (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'),
     'pending', 'My bike needs new brake pads.', NULL, 'Two loaves of banana bread',
     now() - interval '1 day', NULL, NULL);

-- 5. EXCHANGE -------------------------------------------------
INSERT INTO exchange
    (listing_id, provider_user_id, receiver_user_id, request_id, fulfillment_method_id,
     quantity, scheduled_at, meeting_notes, exchange_status, created_at, completed_at, completed_by_user_id)
VALUES
    ((SELECT listing_id FROM listing WHERE title = 'Homemade banana bread loaves'),
     (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'),
     (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
     (SELECT r.request_id FROM request r JOIN listing l ON l.listing_id = r.listing_id
        WHERE l.title = 'Homemade banana bread loaves'),
     (SELECT method_id FROM fulfillment_method WHERE method_name = 'pickup'),
     NULL, now() - interval '9 days', 'Front porch, 6:30pm', 'completed',
     now() - interval '10 days', now() - interval '9 days',
     (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com')),

    ((SELECT listing_id FROM listing WHERE title = 'Holiday meal boxes'),
     (SELECT user_id FROM user_account WHERE email = 'info@greaterhopepantry.org'),
     (SELECT user_id FROM user_account WHERE email = 'kyle.dawson@gmail.com'),
     (SELECT r.request_id FROM request r JOIN listing l ON l.listing_id = r.listing_id
        WHERE l.title = 'Holiday meal boxes'),
     (SELECT method_id FROM fulfillment_method WHERE method_name = 'pickup'),
     2, now() - interval '6 days', 'Pantry front desk, Saturday 11am', 'disputed',
     now() - interval '8 days', NULL, NULL);

-- 6. SUPPORT_REQUEST: exchange problem and dispute ------------
INSERT INTO support_request
    (requester_user_id, assigned_coordinator_id, related_listing_id, related_exchange_id,
     request_type, subject, description, status, priority, dispute_type,
     recommended_resolution, resolution_summary, notes, created_at, resolved_at)
VALUES
    -- Dispute: only a participant of the exchange can open it (BR-66, BR-67)
    ((SELECT user_id FROM user_account WHERE email = 'info@greaterhopepantry.org'),
     (SELECT user_id FROM user_account WHERE email = 'priya.nair@neighborlink.org'),
     (SELECT listing_id FROM listing WHERE title = 'Holiday meal boxes'),
     (SELECT e.exchange_id FROM exchange e JOIN listing l ON l.listing_id = e.listing_id
        WHERE l.title = 'Holiday meal boxes'),
     'dispute', 'Receiver did not show up for reserved meal boxes',
     'Two boxes were held for Saturday at 11am. The receiver never came and did not message us.',
     'in_progress', 'high', 'no_show',
     'Warn the receiver and release the held boxes back to the listing.', NULL,
     '[{"author":"priya.nair@neighborlink.org","note":"Contacted the receiver, waiting for a reply.","is_internal":false}]'::jsonb,
     now() - interval '5 days', NULL),

    -- Exchange problem, already resolved (BR-65)
    ((SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
     (SELECT user_id FROM user_account WHERE email = 'priya.nair@neighborlink.org'),
     (SELECT listing_id FROM listing WHERE title = 'Homemade banana bread loaves'),
     (SELECT e.exchange_id FROM exchange e JOIN listing l ON l.listing_id = e.listing_id
        WHERE l.title = 'Homemade banana bread loaves'),
     'exchange_problem', 'Pickup started 30 minutes late',
     'The handoff was late, but the bread was fine. I wanted to leave a record.',
     'resolved', 'low', NULL, NULL,
     'Logged the feedback. Both users were thanked for completing the exchange.',
     NULL, now() - interval '9 days', now() - interval '8 days');

-- 1. REVIEW ---------------------------------------------------
-- Both people review each other after the completed banana bread exchange.
INSERT INTO review
    (exchange_id, reviewer_user_id, reviewee_user_id, overall_rating,
     reliability_rating, friendliness_rating, comment, review_status, created_at, updated_at)
VALUES
    ((SELECT e.exchange_id FROM exchange e JOIN listing l ON l.listing_id = e.listing_id
        WHERE l.title = 'Homemade banana bread loaves'),
     (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
     (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'),
     5, 5, 5, 'The bread was still warm and she was so kind. My neighbor loved it.',
     'visible', now() - interval '9 days', now() - interval '9 days'),

    ((SELECT e.exchange_id FROM exchange e JOIN listing l ON l.listing_id = e.listing_id
        WHERE l.title = 'Homemade banana bread loaves'),
     (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'),
     (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
     4, 4, 5, 'Friendly and polite. Arrived about 30 minutes late but let me know.',
     'visible', now() - interval '8 days', now() - interval '8 days');

-- 2. FAVORITE -------------------------------------------------
INSERT INTO favorite (user_id, listing_id, created_at)
SELECT u.user_id, l.listing_id, now() - v.age
FROM (VALUES
    ('tom.nguyen@outlook.com',    'Free math tutoring for middle schoolers', interval '5 days'),
    ('aaliyah.brooks@gmail.com',  'Free math tutoring for middle schoolers', interval '4 days'),
    ('aaliyah.brooks@gmail.com',  'Holiday meal boxes',                      interval '3 days'),
    ('rosa.mendez@yahoo.com',     'Bike tune-up and repair',                 interval '2 days')
) AS v(email, title, age)
JOIN user_account u ON u.email = v.email
JOIN listing l      ON l.title = v.title;

-- 3. RECENT_VIEW ----------------------------------------------
INSERT INTO recent_view (user_id, listing_id, last_viewed_at)
SELECT u.user_id, l.listing_id, now() - v.age
FROM (VALUES
    ('tom.nguyen@outlook.com',    'Free math tutoring for middle schoolers', interval '5 days'),
    ('tom.nguyen@outlook.com',    'Holiday meal boxes',                      interval '2 days'),
    ('aaliyah.brooks@gmail.com',  'Bike tune-up and repair',                 interval '1 day'),
    ('aaliyah.brooks@gmail.com',  'Holiday meal boxes',                      interval '3 days'),
    ('rosa.mendez@yahoo.com',     'Bike tune-up and repair',                 interval '2 days'),
    ('kyle.dawson@gmail.com',     'Holiday meal boxes',                      interval '9 days')
) AS v(email, title, age)
JOIN user_account u ON u.email = v.email
JOIN listing l      ON l.title = v.title;

-- 4. CONVERSATION ---------------------------------------------
-- (a) Inquiry about a listing
INSERT INTO conversation
    (conversation_type, listing_id, is_locked, created_at, last_message_at,
     participant_user_ids, participant_status, messages)
VALUES
    ('inquiry',
     (SELECT listing_id FROM listing WHERE title = 'Free math tutoring for middle schoolers'),
     false, now() - interval '5 days', now() - interval '5 days' + interval '2 hours',
     jsonb_build_array(
        (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
        (SELECT user_id FROM user_account WHERE email = 'rosa.mendez@yahoo.com')),
     jsonb_build_array(
        jsonb_build_object('user_id', (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
                           'joined_at', now() - interval '5 days', 'last_read_at', now() - interval '5 days' + interval '3 hours', 'archived', false),
        jsonb_build_object('user_id', (SELECT user_id FROM user_account WHERE email = 'rosa.mendez@yahoo.com'),
                           'joined_at', now() - interval '5 days', 'last_read_at', now() - interval '5 days' + interval '2 hours', 'archived', false)),
     jsonb_build_array(
        jsonb_build_object('number', 1, 'sender_user_id', (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
                           'text', 'Hi Rosa, is there room for my niece on Thursdays? She is in sixth grade.',
                           'sent_at', now() - interval '5 days', 'status', 'visible'),
        jsonb_build_object('number', 2, 'sender_user_id', (SELECT user_id FROM user_account WHERE email = 'rosa.mendez@yahoo.com'),
                           'text', 'Yes, I have a spot at 4pm. Send a request and we can set it up.',
                           'sent_at', now() - interval '5 days' + interval '2 hours', 'status', 'visible')));

-- (b) Exchange conversation about the banana bread handoff
INSERT INTO conversation
    (conversation_type, exchange_id, is_locked, created_at, last_message_at,
     participant_user_ids, participant_status, messages)
VALUES
    ('exchange',
     (SELECT e.exchange_id FROM exchange e JOIN listing l ON l.listing_id = e.listing_id
        WHERE l.title = 'Homemade banana bread loaves'),
     false, now() - interval '10 days', now() - interval '9 days' - interval '2 hours',
     jsonb_build_array(
        (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'),
        (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com')),
     jsonb_build_array(
        jsonb_build_object('user_id', (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'),
                           'joined_at', now() - interval '10 days', 'last_read_at', now() - interval '9 days', 'archived', false),
        jsonb_build_object('user_id', (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
                           'joined_at', now() - interval '10 days', 'last_read_at', now() - interval '9 days', 'archived', true)),
     jsonb_build_array(
        jsonb_build_object('number', 1, 'sender_user_id', (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'),
                           'text', 'Approved! Does 6:30pm tomorrow work for pickup?',
                           'sent_at', now() - interval '10 days', 'status', 'visible'),
        jsonb_build_object('number', 2, 'sender_user_id', (SELECT user_id FROM user_account WHERE email = 'tom.nguyen@outlook.com'),
                           'text', 'Perfect. I might be a few minutes late.',
                           'sent_at', now() - interval '10 days' + interval '1 hour', 'status', 'visible'),
        jsonb_build_object('number', 3, 'sender_user_id', (SELECT user_id FROM user_account WHERE email = 'aaliyah.brooks@gmail.com'),
                           'text', 'No problem, I will leave it on the front porch.',
                           'sent_at', now() - interval '9 days' - interval '2 hours', 'status', 'visible')));

-- (c) Support conversation for the meal box no-show dispute
INSERT INTO conversation
    (conversation_type, support_request_id, is_locked, created_at, last_message_at,
     participant_user_ids, participant_status, messages)
VALUES
    ('support',
     (SELECT support_request_id FROM support_request WHERE subject = 'Receiver did not show up for reserved meal boxes'),
     false, now() - interval '5 days', now() - interval '4 days',
     jsonb_build_array(
        (SELECT user_id FROM user_account WHERE email = 'info@greaterhopepantry.org'),
        (SELECT user_id FROM user_account WHERE email = 'priya.nair@neighborlink.org')),
     jsonb_build_array(
        jsonb_build_object('user_id', (SELECT user_id FROM user_account WHERE email = 'info@greaterhopepantry.org'),
                           'joined_at', now() - interval '5 days', 'last_read_at', now() - interval '4 days', 'archived', false),
        jsonb_build_object('user_id', (SELECT user_id FROM user_account WHERE email = 'priya.nair@neighborlink.org'),
                           'joined_at', now() - interval '5 days', 'last_read_at', now() - interval '4 days', 'archived', false)),
     jsonb_build_array(
        jsonb_build_object('number', 1, 'sender_user_id', (SELECT user_id FROM user_account WHERE email = 'priya.nair@neighborlink.org'),
                           'text', 'Thanks for reporting this. I contacted the receiver and will update you here.',
                           'sent_at', now() - interval '5 days' + interval '1 hour', 'status', 'visible'),
        jsonb_build_object('number', 2, 'sender_user_id', (SELECT user_id FROM)

SELECT display_name, city, account_status
FROM user_account
WHERE city = 'Atlanta' AND account_status = 'active'
ORDER BY display_name;

SELECT l.title, r.request_status, u.display_name AS requester
FROM request r
JOIN listing l      ON l.listing_id = r.listing_id
JOIN user_account u ON u.user_id = r.requester_user_id
WHERE u.email = 'tom.nguyen@outlook.com';
