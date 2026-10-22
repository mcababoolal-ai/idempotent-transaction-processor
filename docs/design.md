# Design notes

## Architecture

REST ingestion is separate from the processing service. PostgreSQL persists transaction requests, account state, and ledger entries. The current worker is invoked synchronously after ingestion and can also be triggered through `POST /api/transactions/process`; a queue or scheduler can later call the same service.

## Idempotency

`transactionId` is the business idempotency key and primary key of `transactions`. A repeated ID with an identical business payload returns the existing outcome. A repeated ID with a different account, type, amount, currency, or sequence is rejected with HTTP 409. `ledger_entries.transaction_id` is unique as a second line of defence.

`requestId` identifies the delivery request and is not the business idempotency key. If multiple request IDs refer to the same transaction ID, the same business effect is still applied once.

## Ordering

Sequence numbers are contiguous and per-account. The account row's `last_processed_sequence` tracks progress. A future sequence is marked `PENDING`; when the missing earlier sequence arrives, the worker drains consecutive events. A permanently failed event blocks later events to avoid silently skipping a sequence.

This is a deliberate simple strategy. Real systems need to define whether gaps are allowed, how sequence ownership is established, and how operators repair poison events.

## Consistency

The account balance, ledger insertion, transaction status, and sequence advancement are written inside a database transaction. Account row locking serializes updates to one account. The ledger's unique transaction ID protects against duplicate ledger entries.

## Retry

The `simulateTransientFailures` input field is a demo-only failure injection hook. A transient failure increments the retry count and leaves the transaction `RETRY_PENDING` without applying a balance or ledger effect. The next processing cycle retries the same transaction. The maximum attempts is 3.

## Observability

The application logs transaction ID, account ID, sequence, state changes, and retry attempts. `GET /api/transactions/summary` returns counts by status and the number of records examined in the processing cycle.

## Limitations and production hardening

- The demo's API request and processing call are not a durable queue. Add a queue or database-backed outbox/claim mechanism for high-volume asynchronous processing.
- Add authentication, authorization, rate limiting, secret management, and request size limits.
- Use Flyway/Liquibase migrations instead of Hibernate `ddl-auto: update`.
- Add metrics (Micrometer/Prometheus), tracing, dashboards, and alerts.
- Add bounded worker concurrency, claim/lease semantics, dead-letter handling, and scheduled backoff.
- Add explicit reversal references and prevent reversing an original transaction more than once.
- Add robust account provisioning, currency/scale rules, and reconciliation.
- For multi-worker deployments, test transaction isolation and lock ordering under contention. Handle uniqueness and serialization failures explicitly.
- Avoid exposing simulated failure controls in production.
- Validate behavior with PostgreSQL Testcontainers as well as H2 tests before production use.

## AI-assisted development

AI assistance was used to scaffold repetitive project structure and test cases. The implementation should be reviewed by the candidate, run locally, and tested against the acceptance criteria before submission.
