# Updates

Prepared: 2026-05-16

## Implemented Features

- Fixed the test environment so `./gradlew test` runs against in-memory H2 instead of requiring a local MySQL server.
- Added event date-range validation so `eventEndDate` must be after `eventDate` on create and update.
- Added a public filtered event search endpoint: `GET /api/events/filter`.
- Added filtering by keyword, city/location, event type, organiser, start date, and end date.
- Added organiser/admin participant list endpoint: `GET /api/events/{id}/participants`.
- Added organiser/admin participant CSV export endpoint: `GET /api/events/{id}/participants.csv`.
- Added participant export fields for user id, username, email, participant name, and joined timestamp.
- Added single-event iCalendar export endpoint: `GET /api/events/{id}/calendar.ics`.
- Added public organiser iCalendar export endpoint: `GET /api/events/organiser/{organiserId}/calendar.ics`.
- Added joined-events iCalendar export endpoint: `GET /api/events/joined/calendar.ics`.
- Added organiser dashboard endpoint: `GET /api/events/organiser/{organiserId}/dashboard`.
- Added dashboard metrics for follower count, total created events, upcoming events, draft events, total participants, and per-event counts.
- Added DTOs for participant exports and organiser dashboard responses.
- Tightened security rules for authenticated event endpoints that previously sat behind broad public `GET /api/events/**` access.
- Verified the backend with `./gradlew test`.

## Yet To Be Implemented Features I Cannot Complete Alone

- Real push notifications, because Firebase/APNs project credentials and Android client integration are required.
- Production email delivery, because SMTP/provider credentials, sender domain setup, and deliverability decisions are required.
- Real billing, paid subscriptions, invoices, taxes, refunds, and payouts, because a payment provider account and business/legal decisions are required.
- App-store release work, because store accounts, signing credentials, screenshots, privacy declarations, and the Android app project are required.
- Android UI changes for the new endpoints, because the Android client code is not in this backend workspace.
- Real pilot customer validation, because clubs/organisations must be contacted and onboarded outside the codebase.
- Real production website scraping guarantees, because target websites, permission/legal review, and parser-quality validation are required.
- Branded production copy, logos, public marketing pages, and final pricing decisions, because those require founder/product direction.
- Live vendor marketplace or paid vendor leads, because vendor relationships, quality control, and commercial terms are required.
- Municipality, university, or umbrella-organisation integrations, because external stakeholder access and data-sharing agreements are required.
