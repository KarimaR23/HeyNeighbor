# HeyNeighbor 🏘️

## Team Members 👥

- Kenyce N Holloman
- Trinity Banks
- Karima Dewdney
- Christopher Rashard Miller
- Professor Allen

## Project Description  📖 

HeyNeighbor is a community-focused application that allows users to offer goods and services to people in their local area at no cost.

## Technology 💻

The project is built using a modern, scalable full-stack architecture designed for performance, maintainability, and developer efficiency.

- Programming Languages: Java & JavaScript 

- Frameworks & Libraries: React & JUnit 

- Database Technology: Neon 

- Development Environment: IntelliJ IDEA  


## Setup Instructions ⚙️

1. Clone the repository.
2. Open the project in the development environment.
3. Install any required dependencies.
4. Run the application.



Sprint Cycle III: Database Implementation 🗄️

Sprint III implements the database designed in Sprint II. The full application database has 20 tables, built in PostgreSQL on Neon.

Schema and create scripts

The create script builds all 20 tables with their primary keys, foreign keys, constraints and indexes.

The tables are grouped by who built them:

Tables	Built by
role, permission, role_permission, user_account, organization	Karima Dewdney
category, fulfillment_method, listing, request, exchange	Kenyce N Holloman
support_request, conversation, moderation_case, report, user_restriction	Christopher Rashard Miller
review, favorite, recent_view, notification, system_setting	Trinity Banks
Sample-data scripts
Script	Tables filled
Access and partner data	role, permission, role_permission, user_account, organization
Catalog and exchange data	fulfillment_method, category, listing, request, exchange, support_request
Remaining tables data	review, favorite, recent_view, notification, conversation, moderation_case, report, user_restriction
Settings data	system_setting

Sample data uses realistic people, organizations and listings (for example a banana bread gift exchange and a food pantry meal-box dispute), so relationships can be followed across tables.

Query file with rule tests
Query set	What it shows
Users, roles and organizations	Search, related data, add data, modify data, plus rule tests
Reviews, messages and moderation	Reviews, notifications, JSONB messages, moderation cases and reports, plus rule tests

The rule tests are statements that must fail. Examples:

Rule	Enforced by
BR-01 Email is unique (case-insensitive)	Database: UNIQUE on email and a unique index on lower(email)
BR-10 One organization per owner	Database: UNIQUE on organization.owner_user_id
BR-45 One review per exchange and reviewer	Database: UNIQUE on (exchange_id, reviewer_user_id)
BR-46 Ratings are 1 to 5	Database: CHECK constraint
BR-54 A listing is favorited once per user	Database: composite primary key
BR-62 A restriction ends after it starts	Database: CHECK constraint
BR-73 Admin-only permissions go only to the administrator role	Application (Sprint IV)
BR-76 System roles cannot be deleted	Application (Sprint IV)

Rules about who may do what are checked by the application in Sprint IV, because the database does not know who is signed in.

Setup and recreation steps

Use a new, empty Neon database. The order matters because later tables point to earlier ones.

In the Neon console, open the project and pick the branch and database at the top of SQL Editor.
Run the create script to build every table.
Run the access and partner data. It inserts the five roles first, then permissions, users and organizations.
Run the catalog and exchange data, then the settings data.
Run the remaining tables data.
Run the query sets one statement at a time to demonstrate the database.
Check the row counts:
sql
SELECT 'user_account' AS table_name, count(*) AS rows FROM user_account UNION ALL
SELECT 'listing', count(*) FROM listing UNION ALL
SELECT 'exchange', count(*) FROM exchange UNION ALL
SELECT 'review', count(*) FROM review UNION ALL
SELECT 'report', count(*) FROM report;

Each sample-data script is meant to run once. To start over, create a fresh Neon branch, or reset the schema:

sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
Inspecting the structure
sql
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
ORDER BY table_name, ordinal_position;

SELECT conrelid::regclass AS tbl, conname, pg_get_constraintdef(oid) AS def
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
ORDER BY 1, 2;
Design changes since Sprint II
Removed: audit_log, organization_hours, listing_status, listing_category, escalation.
Merged into other tables: user_profile, dispute, support_note, trade_proposal, listing_photo, message, conversation_participant, moderation_action.
Kept as its own table: user_restriction.
Role changes use a support_request of type role_change. Only an administrator approves it.
