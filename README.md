# StudySync

A study group & room booking platform backend, built with Spring Boot.

## Status

**Done:**

- JWT authentication (register/login)
- Room CRUD
- PostgreSQL persistence via Flyway migrations

**Not built yet:**

- Room booking
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

| Method | Path             | Auth required |
|--------|------------------|---------------|
| POST   | `/auth/register` | No            |
| POST   | `/auth/login`    | No            |
| GET    | `/rooms`         | Yes           |
| GET    | `/rooms/{id}`    | Yes           |
| POST   | `/rooms`         | Yes           |
| PUT    | `/rooms/{id}`    | Yes           |
| DELETE | `/rooms/{id}`    | Yes           |

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
