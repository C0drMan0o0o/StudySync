# StudySync

A study group & room booking platform backend, built with Spring Boot.

## Status

**Done:**

- JWT authentication (register/login)
- Room CRUD
- Room booking with two-layer conflict detection (application + database)
- PostgreSQL persistence via Flyway migrations

**Not built yet:**

- Study groups
- API docs

## Tech stack

- Java 21, Spring Boot 4.1.0, Maven
- PostgreSQL 16 (Docker), Flyway migrations, Spring Data JPA (`ddl-auto: validate`)
- Spring Security 7, JWT (`jjwt`), BCrypt, Bucket4j rate limiting

## Running locally

```bash
docker compose up -d      # start Postgres
mvn spring-boot:run       # start the app on :8080, Flyway migrates on boot
mvn test                  # run the unit test suite
```

Postgres must be up and accepting connections before `mvn spring-boot:run` —
the app fails fast on startup if it can't reach the database.

Default local DB credentials (`studysync`/`studysync`/`studysync`) are set in
`docker-compose.yml` and `application.yml` — fine for local dev only.

`jwt.secret` defaults to a dev-only value in `application.yml`; override it
via the `JWT_SECRET` environment variable for anything beyond local dev.

## API

| Method | Path                   | Auth required                   |
|--------|------------------------|---------------------------------|
| POST   | `/auth/register`       | No                              |
| POST   | `/auth/login`          | No                              |
| GET    | `/rooms`               | Yes                             |
| GET    | `/rooms/{id}`          | Yes                             |
| POST   | `/rooms`               | Yes                             |
| PUT    | `/rooms/{id}`          | Yes                             |
| DELETE | `/rooms/{id}`          | Yes                             |
| POST   | `/bookings`            | Yes                             |
| GET    | `/bookings/me`         | Yes                             |
| GET    | `/rooms/{id}/bookings` | Yes                             |
| DELETE | `/bookings/{id}`       | Yes (owner only, 403 otherwise) |

Authenticated requests need `Authorization: Bearer <token>` from
`/auth/login` or `/auth/register`. `/auth/login` and `/auth/register` are
rate-limited to 10 requests/minute per IP.

## Design notes

- **Flyway autoconfiguration moved in Spring Boot 4.1**: it's no longer part
  of `spring-boot-autoconfigure`. Depending on `flyway-core` alone silently
  skips migrations on boot; the fix is depending on
  `spring-boot-starter-flyway` instead.
- **Hibernate stays read-only on schema**: `ddl-auto: validate` — Flyway owns
  all schema changes via versioned migrations in `db/migration`.
- **Booking conflicts are enforced at two layers, not one**: `BookingService`
  checks for overlapping bookings before inserting, but that check-then-insert
  pattern has a genuine TOCTOU race — two concurrent requests can both pass
  the check before either commits. A Postgres exclusion constraint
  (`btree_gist` + `EXCLUDE USING gist` on `room_id` and
  `tsrange(start_time, end_time)`, added in `V3.1`) is what actually prevents
  the double-booking; the application check just returns a fast, friendly 409
  for the common case. A constraint violation surfaces as
  `DataIntegrityViolationException`, which `BookingService` catches and
  rethrows as `BookingConflictException` so callers see one consistent 409
  either way. Verified by firing 10 concurrent conflicting `POST /bookings`
  requests at the same slot: exactly one succeeds, the rest get 409, and the
  Postgres logs show the exclusion constraint — not the application check —
  rejecting the losers.
