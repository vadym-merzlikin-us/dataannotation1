# CodeJunk

Single repository holding two applications plus a database:

| Service             | Folder      | Stack                                              | Port (compose) |
| ------------------- | ----------- | -------------------------------------------------- | -------------- |
| `codejunk-backend`  | `backend/`  | Java 17, Gradle (Groovy DSL), Spring Boot 3.3       | `8090`         |
| `codejunk-frontend` | `frontend/` | Angular 20 (standalone, signals), nginx at runtime  | `4600`         |
| `codejunk-db`       | –           | Postgres 17                                         | `5433`         |

Two features share the shell:

- **Books** (`/books`) – authors and their books, side by side, each panel
  paged 20 at a time, with collapsible detail editors underneath.
- **Queue** (`/queue`) – the original annotation queue, still in memory.

```
DataAnnotation/                 (repo folder; the apps are named codejunk-*)
├── backend/            Spring Boot REST API + Flyway migrations + Dockerfile
├── frontend/           Angular SPA + nginx config + Dockerfile
└── docker-compose.yml  db + backend + frontend, compose project "codejunk"
```

## Run everything with Docker

```bash
docker compose up --build
```

Then open <http://localhost:4600>. nginx serves the Angular bundle and proxies
`/api` to the backend container, so the browser only ever talks to one origin
and no CORS configuration is needed. The API is also exposed directly on
<http://localhost:8090> for curl/Postman, and Postgres on `localhost:5433`.

Host ports and database credentials come from [`.env`](.env). The ports are
`4600` / `8090` / `5433` rather than the usual `4200` / `8080` / `5432` because
the `brighton2` stack already publishes those on this machine; change `.env` if
that stops being true. Ports *inside* the containers are always `80`, `8080`
and `5432`.

Stop with `docker compose down`, or `docker compose down -v` to drop the
database volume as well.

## Database

Postgres 17 — the current mature major, with community support through
November 2029.

The schema is owned by Flyway
([`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration)),
and Hibernate runs with `ddl-auto: validate` so a drifting entity fails fast at
startup.

| Table     | Columns                                             |
| --------- | --------------------------------------------------- |
| `authors` | `id`, `first_name`, `second_name`, `description`     |
| `books`   | `id`, `name`, `description`, `author_id` → `authors` |

`V2__seed_demo_data.sql` inserts 25 placeholder authors and 26 books for the
first of them, purely so both paginators have more than one page. Delete that
migration and recreate the volume once real data exists.

## Run locally without Docker

The backend needs a database. The easiest path is to start just that one
service and leave the rest to Gradle:

```bash
docker compose up -d db
```

Backend (from `backend/`) — defaults already point at `localhost:5433`:

```bash
./gradlew bootRun
```

Frontend (from `frontend/`):

```bash
npm install
npm start
```

`ng serve` runs on <http://localhost:4200> and forwards `/api` to
`localhost:8080` via [`proxy.conf.json`](frontend/proxy.conf.json). When the
Angular dev server calls the API directly instead, the backend allows
`http://localhost:4200` through `app.cors.allowed-origins`.

## Tests

```bash
cd backend && ./gradlew test
cd frontend && npm test
```

The backend suite starts a real Postgres 17 via Testcontainers, so **Docker must
be running**. The frontend suite uses Karma + Jasmine and needs a local Chrome.

> Docker Engine 29 rejects the unversioned API request docker-java sends by
> default, and on Windows the `docker_engine` named pipe is only a redirect
> stub. The `test` task in [`backend/build.gradle`](backend/build.gradle) sets
> `api.version` and `DOCKER_HOST` to work around both; remove those lines if a
> future Testcontainers release makes them unnecessary.

## API

### `/api/bookdb` — the editing surface used by the UI

| Method | Path                                          | Result                          |
| ------ | --------------------------------------------- | ------------------------------- |
| `GET`  | `/api/bookdb/authors?page=0&size=20`          | page of authors                 |
| `GET`  | `/api/bookdb/authors/{id}`                    | one author                      |
| `POST` | `/api/bookdb/authors`                         | `201` + created author          |
| `PUT`  | `/api/bookdb/authors/{id}`                    | updated author                  |
| `GET`  | `/api/bookdb/books?authorId=1&page=0&size=20` | page of that author's books     |
| `GET`  | `/api/bookdb/books/{id}`                      | one book                        |
| `POST` | `/api/bookdb/books`                           | `201` + created book            |
| `PUT`  | `/api/bookdb/books/{id}`                      | updated book                    |

Pages come back as `{ content, page, size, totalElements, totalPages }`.
`size` defaults to 20 and is capped at 200.

### `/api/public` — the published contract

| Method | Path                            | Result                              |
| ------ | ------------------------------- | ----------------------------------- |
| `GET`  | `/api/public/books?authorId={id}` | every book by that author, unpaged |

Deliberately one method for now, and kept in its own controller so the editing
endpoints and the published contract can evolve separately.

### `/api/tasks` — annotation queue

| Method   | Path                    | Body                      | Result                  |
| -------- | ----------------------- | ------------------------- | ----------------------- |
| `GET`    | `/api/tasks`            | –                         | all tasks, newest first |
| `POST`   | `/api/tasks`            | `{ "text": "…" }`         | `201` + created task    |
| `PUT`    | `/api/tasks/{id}/label` | `{ "label": "positive" }` | updated task            |
| `DELETE` | `/api/tasks/{id}`       | –                         | `204`                   |

Health check: `GET /actuator/health`.

Queue tasks still live in an in-memory `ConcurrentHashMap`
([`AnnotationTaskRepository`](backend/src/main/java/com/codejunk/backend/annotation/AnnotationTaskRepository.java)),
so restarting the backend resets them. Only authors and books are persisted.
