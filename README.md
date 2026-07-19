# Marginalis

In-editor agent↔human comment threads for JetBrains IDEs. Design brief:
[docs/marginalis-handover.md](docs/marginalis-handover.md) · Sandbox notes:
[tenant-environment.md](tenant-environment.md)

**Status: M0** — one tool, `comment_add`, over the IDE's built-in HTTP server.
Agent writes, IDE shows a gutter icon, hover shows the note.

## Build & run

Requires JDK 21. The Gradle daemon must stay off in the tenant sandbox
(already configured in `gradle.properties`).

```sh
./gradlew buildPlugin   # build + package
./gradlew runIde        # launch sandbox IDE with the plugin
```

First-ever `./gradlew` run downloads the Gradle distribution from
services.gradle.org → needs the operator to enter install mode.

## M0 API

Served on the built-in server port (63342 by default, declared in the sandbox):

```sh
# smoke test
curl http://127.0.0.1:63342/api/marginalis/ping

# add a margin note (file: project-relative, line: 1-based)
curl -X POST http://127.0.0.1:63342/api/marginalis/comment_add \
  -H 'Content-Type: application/json' \
  -d '{"file": "src/main.py", "line": 12, "body": "This loop is O(n^2) — intentional?"}'

# list notes (reports live anchor lines)
curl http://127.0.0.1:63342/api/marginalis/comment_list
```

## Layout

- `transport/` — RestService endpoints (handover §6, Option B)
- `store/` — in-memory note registry (persistence is M2)
- `ui/` — gutter icon renderer (inlays/threads are M1)
