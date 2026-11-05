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
