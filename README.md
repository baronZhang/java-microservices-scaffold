# Java Microservices Scaffold

> **Practice project.** It has never run in production and has no real users or traffic. Results below come from one local machine.

An API gateway and five services (auth, user, product, inventory, order) on Spring Boot 4.1.1, Spring Cloud 2025.1.3, and JDK 25. The default setup runs the full order flow with no middleware. Nacos, MySQL, Redis, and Kafka are optional profiles.

[中文说明](README.zh-CN.md)

## What it covers

- **Order consistency without Seata:** synchronous stock reservation plus a local message table. The inventory service handles idempotency, empty rollback, and hanging requests in the TCC style.
- **Overselling:** with 50 units in stock and 200 concurrent orders, exactly 50 succeed. An integration test checks this on every build.
- **Authentication:** the gateway validates JWTs and keeps a revocation list, each service validates again, and service-to-service calls use client-credentials tokens.
- **Failure handling:** timeouts, retries, circuit breakers, rate limiting, gray releases, and tracing, with scripted experiments that stop or slow down running instances.
- **Build rules:** `./mvnw verify` runs formatting, static analysis, ArchUnit architecture rules, and unit and integration tests.

## Problems found while testing

**Two retries became six requests.** Stock reservation was set to retry at most twice, but a contract test's stub received six requests. `nacos-client` had pulled in Apache HttpClient 5, Spring Boot chose it as the HTTP client, and it automatically re-sends on 503 and 429, including POST. The fix was `spring.http.clients.imperative.factory: jdk`, which leaves one layer in charge of retries.

**Normal traffic reached the gray release.** Spring Cloud's `HintBasedServiceInstanceListSupplier` falls back to all instances when none match the hint, and during startup only the gray instance had passed its health check. The fix was a strict filter: normal traffic now gets a 503 rather than reaching a gray instance.

## Test status

| Check | Result | Last run |
|---|---|---|
| `./mvnw clean verify` | 87 tests, 0 failures, 0 skipped | 2026-11-04 |
| [GitHub Actions](https://github.com/baronZhang/java-microservices-scaffold/actions/workflows/ci.yml) `verify` job | 87 tests, 0 failures, 0 skipped | 2026-11-05 |
| `scripts/smoke-test.sh` through the gateway | 37 of 37 checks passed | 2026-11-04 |
| Nine experiments in `scripts/experiments/` | Run, with real output | 2026-10-27 |
| MySQL + Redis, Nacos, and Kafka profiles | Smoke test passed | 2026-10-27 |
| Six images and full compose stack | Smoke test passed | 2026-10-27 |

**Not verified:** the Kubernetes manifests (YAML parsing only, no cluster) and the CI `image` job (Trivy scan and GHCR push), which runs only when triggered by hand. Seata and RocketMQ were evaluated but not used.

## Quick start

Requires JDK 25 or later.

```bash
./mvnw verify            # quality gates and all tests
scripts/start-all.sh     # start all services with the demo profile
scripts/smoke-test.sh    # run the core API checks through the gateway
scripts/stop-all.sh
```

Add middleware with a profile, for example:

```bash
docker compose -f deploy/docker-compose.infra.yml up -d mysql
MS_PROFILES=demo,mysql,redis scripts/start-all.sh
```

To run everything in containers, build the images and use `deploy/docker-compose.yml`. Its header has the commands.

## Experiments

`scripts/experiments/` has nine runnable experiments:

| Script | Topic |
|---|---|
| `01-load-balance.sh` | Load balancing and instance failure |
| `02-fault-injection.sh` | Slow and stopped services |
| `03-reliable-messaging.sh` | Reliable messaging and duplicate consumption |
| `04-gray-and-config-refresh.sh` | Gray release and config refresh |
| `05-rate-limit.sh` | Rate limiting |
| `06-lock-and-snowflake.sh` | Distributed locks and Snowflake IDs |
| `07-oversell.sh` | End-to-end overselling |
| `08-tracing.sh` | Distributed tracing |
| `09-nacos-push-refresh.sh` | Nacos config push |

## Ports

| Service | Port |
|---|---|
| gateway | 9080 |
| auth-service | 9081 |
| user-service | 9082 |
| product-service | 9083 (gray instance 9093) |
| inventory-service | 9084 (second instance 9094) |
| order-service | 9085 (second instance 9095) |

## Layout

```text
common/      shared contracts, web and security starters, test support
gateway/     API gateway
*-service/   five business services, each with its own database and Flyway migrations
config/      local configuration center
contracts/   service and API contract samples
deploy/      Docker Compose, MySQL init scripts, Kubernetes manifests
scripts/     start and stop, smoke test, experiments, dependency checks
```

## Notes

- Code comments are in Chinese. A few mention `docs/`, which holds private study notes and is not in this repository.
- Passwords and tokens in this repository are demo values for local use only.
- The API matches the [Vue 3 demo frontend](https://github.com/baronZhang/vue3-scaffold-demo), which lives in its own repository.
- This project was developed with Claude Code. The results above come from real test runs.
