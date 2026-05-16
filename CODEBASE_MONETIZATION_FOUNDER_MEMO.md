# Founder Memo: Event Planner Monetization Analysis

Prepared: 2026-05-16

Method note: this memo intentionally ignores all existing `.md` files in the repository. The codebase analysis is based on `src/main`, `src/test`, Gradle configuration, application properties, and current public competitor research.

## Verdict

The app is monetizable only if it is positioned narrowly. It should not be sold as a generic event planner, a Meetup clone, a ticketing platform, or a full Vereinsverwaltung system. The strongest commercial wedge is:

**A low-friction event reach and RSVP layer for clubs, small groups, volunteer organisations, local venues, and recurring communities.**

The product implemented today is a Spring Boot backend for creating events, sharing invite links, letting users join, supporting guest usage, following organisers, and enriching planning with venue/vendor/weather suggestions. That is a useful foundation, but not yet a product most organisations would pay for. The paid product needs a club/group workspace, real reminder delivery, recurring events, calendar export, organiser dashboard, and import from existing calendars/websites.

Recommended business model:

- Members, guests, and participants use it for free.
- Clubs/groups/organisers pay for workspace capabilities, reminder reach, analytics, import, multi-admin control, branding, and support.
- A setup/import service should be tested early because volunteer-run groups often value saved setup time more than configuration screens.

## What The Product Implements Today

### Actual Product In The Code

The current product is:

> A mobile-client backend for event creation, public/private visibility, invite-based joining, guest onboarding, organiser following, and planning suggestions.

The most important code evidence:

- `src/main/java/event_planer/project/controller/EventController.java`: event CRUD, joining/leaving, invite links, short links, organiser subscriptions, follower count, event reference data, vendor attachment.
- `src/main/java/event_planer/project/service/EventService.java`: core event logic, capacity checks, visibility rules, invite token handling, admin grants, organiser subscriptions, response mapping.
- `src/main/java/event_planer/project/service/UserService.java`: registration/login, guest user creation, guest-to-registered migration, account deletion, expired guest cleanup.
- `src/main/java/event_planer/project/entity/Event.java`: event domain model with venue fields, visibility, status, participants, options, vendors, invite token, admins, capacity, and organiser.
- `src/main/java/event_planer/project/entity/UserOrganizerSubscription.java`: user follows organiser with notification preferences.
- `src/main/java/event_planer/project/service/VenueService.java`: OpenStreetMap/Nominatim/Overpass venue suggestions.
- `src/main/java/event_planer/project/service/VendorService.java`: OpenStreetMap vendor/service suggestions by selected event options.
- `src/main/java/event_planer/project/service/WeatherService.java`: Open-Meteo forecast lookup.
- `src/main/resources/templates/invite.html`: public invite landing page for shared links.

### Implemented Features

Authentication and accounts:

- Register/login with JWT.
- `PRIVATE`, `COMPANY`, `ADMIN`, and `GUEST` roles.
- Guest account creation with a 30-day expiry.
- Guest-to-registered migration for events and participations.
- Account deletion.
- Daily scheduled cleanup of expired guest users.

Event management:

- Create, read, update, delete events.
- Public/private visibility.
- Draft/planned/ongoing/completed/cancelled statuses.
- Start and end date fields.
- Location and venue metadata.
- Event type and event options.
- Capacity limit through `maxParticipants`.
- Participant-name collection toggle.
- Created-events and joined-events endpoints.
- Search by event title.

Participation and sharing:

- Join and leave events.
- Join through invite token.
- Public invite preview.
- Public HTML invite landing page.
- Short invite codes that map to invite tokens.
- Event-level admins who can access invite links.
- Invite token only appears in API responses for organiser/admin.

Organiser following:

- Subscribe/unsubscribe to an organiser.
- List current subscriptions.
- Get future public events from followed organisers.
- Store notification preferences: notifications enabled, email fallback enabled, reminder-before minutes.
- Follower count endpoint.

Planning assistance:

- Venue suggestions by city, radius, location type, and event type.
- Vendor suggestions by city, radius, country, and event option.
- Vendor attachment/removal on events by organiser/admin.
- Weather forecast for one day or a 16-day range.

Platform basics:

- Spring Security with JWT bearer auth.
- Swagger/OpenAPI.
- Public privacy/account-deletion pages.
- Cache headers/ETag support.
- H2 local profile; MySQL default profile; PostgreSQL driver available.

### Planned Or Missing Features

The code implies but does not yet implement several commercially important capabilities:

- No first-class club/group/organisation workspace. Organisers are individual `User` records.
- No billing model, subscription state, invoices, trials, or plan limits.
- No push notification token storage.
- No email delivery provider integration.
- No scheduled reminder sender, even though reminder preferences exist.
- No recurring events or event templates.
- No calendar export/import (`.ics`, Google Calendar, Outlook).
- No website, RSS, iCal, CSV, Instagram, or Facebook event import.
- No organiser dashboard.
- No attendee list export endpoint.
- No departments/groups/teams inside an organisation.
- No member directory or member import.
- No payments, ticketing, donations, membership fees, refunds, or payouts.
- No messaging/chat.
- No waitlist.
- No QR-code follow/join flow.
- No public discovery by location/category beyond all-public-events and title search.
- No moderation/reporting workflow.
- No admin console for customer support.
- No production seed/migration strategy beyond Hibernate `ddl-auto=update`.

## Product Gaps That Matter Commercially

### Gap 1: Buyer Entity Is Missing

The backend has `User`, not `Club`, `Organisation`, `Workspace`, or `Group`. A paying customer should be a workspace with name, logo, public page, admins, billing plan, followers, and import settings. Charging a personal organiser account is weaker because clubs are shared institutions.

### Gap 2: Reminder Promise Is Not Fulfilled

`UserOrganizerSubscription` stores reminder preferences, but the backend does not send push notifications or emails. The monetizable promise is not "you can follow an organiser"; it is "members are reliably reminded."

### Gap 3: Events Are Manual And One-Off

Clubs operate on recurring schedules. Without recurring events, event templates, copy-event, and imports, organisers must repeatedly enter data. That is friction, and friction kills volunteer adoption.

### Gap 4: No Paid Admin Surface

There is no dashboard that makes value visible to the buyer: followers, upcoming events, RSVP counts, unanswered invites, reminder delivery, imported drafts, exports, or activity metrics.

### Gap 5: Planning Suggestions Are Useful But Not The Wedge

Venue, vendor, and weather suggestions are nice, but they are not the main reason clubs will pay. They may become differentiators later, especially for public events and vendor lead generation, but they should not distract from reminders, RSVPs, recurring events, and import.

### Gap 6: Test Environment Is Not Fully Self-Contained

`./gradlew test` currently fails because `ProjectApplicationTests.contextLoads()` uses the default MySQL configuration and cannot connect locally. Most tests run, but this is a readiness issue. The test suite should default to H2/test profile for reliable CI.

## Competitor Landscape

| Competitor | Category | Target User | Pricing / Monetization | Strengths | Implication |
|---|---|---|---|---|---|
| Klubraum | Club/group app | Clubs and groups, especially DACH | Free tier; Plus/Pro per active user/year, currently listed at EUR 2 and EUR 4 per user/year launch pricing | Unlimited members/chats, shared calendar, areas/subgroups, surveys, carpooling, GDPR, event controls, iCal/CSV import, exports | Very dangerous in German club app space. Do not compete as "another club app"; compete as lightweight event reach/import. |
| Vereinsplaner | Full club administration | Clubs needing member/admin/finance workflows | Free; Pro EUR 9.92/mo, Premium EUR 14.92/mo, Ultimate EUR 41.58/mo billed annually | Member management, fees, SEPA, documents, finances, newsletter, chat, surveys, hierarchy, app | Too hard to attack directly. Your app should not attempt full back-office administration first. |
| Spond | Sports team and club management | Teams, coaches, parents, sports clubs | Free platform; monetizes through payment processing fees | Events, invites, reminders, guardians, messaging, availability, payments, fundraising, club management | Strong in sports. Avoid youth-sports head-on unless you build guardian/team/season workflows. |
| TeamSnap | Sports club/team OS | Youth sports teams, clubs, leagues | Free team start; paid single-team upgrades; custom club/league pricing | Rosters, scheduling, availability, communications, assignments, registration, payments, coaching content | Too specialised and mature for sports. Use sports clubs as pilots only if needs are simple event visibility, not roster management. |
| Meetup | Public group/event discovery | Public groups and organisers | Standard organiser starts around USD 29.99/mo; Pro around USD 55/group/mo | Event discovery, unlimited events/attendees, co-hosts, promotion, paid tickets/dues, advanced Pro dashboard/API | Marketplace discovery is hard. Opportunity exists for lower-friction local groups that dislike platform lock-in/pricing. |
| Eventbrite | Ticketing and event marketing | Public event organisers | Free for free tickets; fees on paid ticketing and paid packages | Ticketing, checkout, event pages, marketing, payouts | Do not compete on ticketing first. Use Eventbrite only as a later integration or benchmark for paid public events. |
| Heylo | Community/group platform | Social clubs, volunteer groups, run clubs, communities | Free base group; paid/group scaling options; payment platform fee | Unlimited events/admins/members, RSVPs, chats, directory, payments, discovery, web/mobile | Close to your desired market. Differentiate with "keep your current website/calendar, import events, lightweight reminders." |
| Hobnob | Invitation/RSVP and group events | Party hosts, clubs, social groups, event planners | Free app with in-app purchases/ticketing style upsides | Beautiful invites, text/email sending, RSVP, updates, ticketing, sign-up lists, group spaces, recurring events | Beats you on invitation polish. You can beat it only on club/workspace operations and local recurring community use. |
| Partiful | Social invitations | Party/event hosts | Core platform free; paid ticketing beta | Highly polished invites, RSVP, social sharing, event updates | Too strong for consumer social events. Avoid party/invite-only positioning. |
| Punchbowl | Digital invitations | Consumer celebrations, venues | Invitation/greeting-card monetization and branded venue invitations | Templates, text/email invites, RSVP, nudges, co-hosts, potluck, polls | Competes with polished invitations, not club operations. |
| Mobilizon | Open/federated event platform | Communities wanting decentralized event organisation | Open-source/federated ecosystem | Public events, groups, federation, privacy-respecting alternative to major platforms | Not a direct paid SaaS competitor, but sets expectations for open public event discovery. |

Sources:

- Klubraum pricing: https://klubraum.com/pricing/
- Vereinsplaner pricing/features: https://vereinsplaner.com/en/preise
- Spond club management: https://www.spond.com/club-management/
- Spond payment fees: https://help.spond.com/app/en/articles/118091-payments-costs-in-the-spond-app
- TeamSnap pricing: https://www.teamsnap.com/pricing
- Meetup organiser pricing: https://help.meetup.com/hc/en-us/articles/28677808413197-Organizer-Subscription-prices-overview
- Eventbrite pricing: https://www.eventbrite.com/help/en-us/articles/193833/
- Heylo pricing: https://www.heylo.com/pricing
- ClubExpress pricing: https://clubexpress.com/pricing
- Hobnob: https://hobnob.app/
- Partiful pricing FAQ: https://help.partiful.com/hc/en-us/articles/27354376389403-Does-Partiful-cost-money
- Mobilizon: https://docs.mobilizon.org/about/

## Answers To The Seven Business Questions

### 1. Strongest Commercial Wedge

The strongest wedge is:

**"Follow a club or group once, then never miss its events again; organisers get RSVPs, reminders, and simple reach analytics without replacing their current website, WhatsApp, or member system."**

This wedge fits the current code because organiser subscriptions, invite links, public/private events, guest mode, participant counts, and planning data already exist. It also avoids the worst competitor traps: full club administration, youth sports management, ticketing, and global marketplace discovery.

Best initial customer types:

- small sports clubs with simple recurring events;
- cultural associations and venues;
- university/student groups;
- volunteer/community groups;
- hobby clubs;
- local organisations with a stale website but active calendar needs.

### 2. Who Pays And Who Uses It Free

Free users:

- members;
- guests;
- event participants;
- casual followers;
- parents/friends who only RSVP or receive reminders.

Paid users:

- club/group workspaces;
- venue/event organiser workspaces;
- umbrella organisations with multiple groups;
- municipalities/universities later, if they want local event aggregation.

Why: participants will not pay to receive reminders. Organisers may pay if the app reduces manual chasing, increases attendance, and makes publishing easier.

### 3. Competitor Categories Too Hard To Attack Directly

Do not attack these directly at the start:

- **Full club administration**: Vereinsplaner, ClubExpress. Too much finance, member management, forms, permissions, documents, dues, accounting.
- **Sports operating systems**: Spond, TeamSnap. Too many expectations around rosters, guardians, seasons, availability, payments, safeguarding, coaching workflows.
- **Public event marketplaces**: Meetup, Eventbrite. Marketplace liquidity is expensive and slow.
- **Consumer invitation design**: Partiful, Punchbowl, Hobnob. They are much stronger at polished invite creation, social sharing, and party flows.
- **Ticketing/payment infrastructure**: Eventbrite, Heylo, Spond. Payments create operational and compliance burden.

The safer path is an event-first, club-friendly helper that works alongside existing channels.

### 4. Features Required Before Anyone Pays

Minimum paid product:

- Club/group workspace entity.
- Public workspace profile page.
- Multiple admins per workspace.
- Real reminder delivery through push and/or email.
- Recurring events.
- Calendar export (`.ics`) for event, workspace, and followed feed.
- Organiser dashboard with followers, events, RSVP counts, and reminder status.
- Basic onboarding flow for inviting members via link/QR.
- Reliable test/CI profile and production database migration discipline.

Without these, the app can be a prototype or pilot, but paid conversion will be weak.

### 5. Features That Make It Meaningfully Differentiated

The strongest differentiators would be:

- **Website/calendar import assistant**: a club enters an existing website, iCal, CSV, or Google Calendar URL; the app turns events into reviewable drafts.
- **No-migration positioning**: keep WhatsApp, keep the website, keep email; add a reliable follow/reminder layer.
- **WhatsApp-optimised invite/follow links**: short links, link previews, QR codes, web fallback, app deep links.
- **Volunteer shift slots**: setup crew, cleanup, drivers, snacks, equipment, referee/helper slots.
- **"Did everyone know?" analytics**: followers reached, reminders sent, invite opens, RSVPs, non-responders.
- **Local discovery after supply exists**: public pages and nearby event search once clubs are onboarded.
- **Venue/vendor context as a later marketplace**: not the first paid feature, but useful for partnerships and local-business lead revenue later.

### 6. Pricing, Pilot Strategy, And Go-To-Market

Recommended pricing to test:

| Tier | Price | Purpose |
|---|---:|---|
| Free | EUR 0 | 1 workspace admin, limited upcoming events, invite links, basic RSVPs, public page. |
| Club Basic | EUR 9-19/month | Unlimited events, recurring events, reminders, follower feed, calendar export, basic dashboard. |
| Club Plus | EUR 29-49/month | Multi-admin, imports, email fallback, CSV export, custom branding, analytics, QR follow links. |
| Organisation | EUR 99-199/month | Multiple groups/departments, advanced permissions, shared public directory, support. |
| Setup/import service | EUR 99-399 one-time | Import existing events, configure workspace, train admins, publish first event set. |

Pilot strategy:

- Recruit 5-10 organisations, not individual consumers.
- Choose mixed pilots: sports club, cultural venue, student group, volunteer organisation, hobby club.
- Offer 90 days free in exchange for structured feedback and real member invites.
- Do setup manually if needed; learn the import patterns before automating.
- Ask for willingness to pay before and after the pilot.

Go-to-market path:

1. Start local, where trust and direct contact matter.
2. Sell "reduce missed events and reminder work," not "use our new app."
3. Use existing channels: WhatsApp message, email template, QR poster, website link.
4. Track whether members follow and RSVP without handholding.
5. Turn successful pilots into case studies and referrals.

### 7. Evidence That Proves Or Disproves Monetization

Proof signals:

- 5 pilot organisations invite real members.
- At least 40-60% of active members follow the organisation or join at least one event.
- Organisers create/import repeat events without developer help after onboarding.
- Reminder delivery increases RSVPs or reduces manual reminder messages.
- At least 3 of 5 pilots say they would pay EUR 19/month.
- At least 1-2 pilots pay for setup/import.
- Organisers ask for dashboard/export/reminder features more than generic chat.

Disproof signals:

- Clubs like demos but do not invite members.
- Members ignore follow links or refuse another app/feed.
- Organisers only want a full Vereinsplaner/Spond replacement.
- Manual event entry is too painful without import.
- Reminders do not change behaviour.
- Clubs will use it only if permanently free.
- The app cannot create a repeat habit beyond one-off invite links.

## Recommended Roadmap

### Phase 0: Stabilise Prototype

- Make tests run under a test/local H2 profile by default.
- Add database migrations instead of relying only on `ddl-auto=update`.
- Add validation for event date/end date consistency.
- Add attendee-list endpoint for organiser/admin.
- Add basic event response fields needed by dashboard.

### Phase 1: Build The Paid Workspace

- Add `Workspace` / `Club` / `Group` entity.
- Move event ownership from direct `User organiser` to workspace plus creator.
- Add workspace admins and roles.
- Add public workspace profile with slug.
- Add follower relationship to workspace, not personal user.
- Add plan/trial fields even before billing integration.

### Phase 2: Deliver The Core Promise

- Store push tokens.
- Add email provider integration.
- Add scheduled reminder job.
- Add reminder delivery logs.
- Add recurring events and event templates.
- Add calendar export for workspace and user feed.

### Phase 3: Make It Easy To Adopt

- Add website/iCal/CSV import drafts.
- Add QR code and short follow links.
- Add WhatsApp-friendly event/share previews.
- Add CSV attendee export.
- Add dashboard: followers, upcoming events, RSVPs, reminders, imports.

### Phase 4: Expand Revenue

- Departments/groups inside workspace.
- Volunteer shift slots.
- Public local discovery.
- Website embeds.
- Payment/ticketing only after repeated pilot demand.
- Vendor lead/referral experiments after event supply exists.

## Next 30-Day Action Plan

Week 1:

- Fix test profile so the suite runs without local MySQL.
- Define the workspace data model and migration path from user-organiser events.
- Interview 10 local organisers; ask how they publish events today and what they would pay to stop manual reminders.

Week 2:

- Implement workspace, workspace admins, public workspace profile, and workspace followers.
- Keep member usage free.
- Create a manual onboarding checklist for pilot organisations.

Week 3:

- Implement reminder delivery MVP: one channel first, preferably email if Android push is not ready.
- Add recurring event MVP.
- Add organiser dashboard MVP with followers, upcoming events, RSVP counts.

Week 4:

- Onboard 3-5 pilot organisations.
- Manually import their next 5-20 events if needed.
- Give them a follow link/QR and WhatsApp/email text.
- Measure follows, RSVPs, reminders delivered, organiser feedback, and willingness to pay.

Decision at day 30:

- Continue if pilots invite real users, members follow, and at least some organisers express willingness to pay.
- Narrow further if only one segment responds.
- Pivot away from clubs if organisers will not invite members or if import/reminders do not reduce pain.

## Final Positioning

Use this as the product sentence:

**A lightweight event feed and reminder layer for clubs and local groups: keep your website and chats, publish events once, let members follow, RSVP, and get reminded.**

That positioning gives the app room to monetize without pretending to be a full club administration system, sports OS, ticketing marketplace, or social invitation app.
