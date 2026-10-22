# Idempotent Background Transaction Processing Service

A Java 21 and Spring Boot reference implementation for an idempotent background transaction-processing assessment. It demonstrates transaction ingestion, duplicate detection, per-account sequence ordering, retry simulation, account balances, a ledger, status reporting, and integration tests.

> **Important:** This repository is an assessment/demo implementation, not a production-ready financial system. Review the limitations in [Design Notes](docs/design.md) before extending or deploying it.

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Run with Docker Compose](#run-with-docker-compose-recommended)
- [Run locally](#run-locally)
- [Run tests](#run-tests)
- [API Reference](#api-reference)
- [Sample Requests](#sample-requests)
- [Transaction Rules](#transaction-rules)
- [Project Structure](#project-structure)
- [Troubleshooting](#troubleshooting)
- [Known Limitations and Production Hardening](#known-limitations-and-production-hardening)

## Features

- REST API for submitting transactions and querying their state.
- Idempotency based on `transactionId`.
- Conflict response when a reused transaction ID has a different business payload.
- Database uniqueness constraints for transaction IDs, account/sequence pairs, and ledger transaction IDs.
- Per-account sequence-based processing: a transaction waits when a preceding sequence is missing.
- Atomic account-balance, ledger, transaction-status, and sequence updates within a database transaction.
- Demo-only transient-failure simulation and retry tracking.
- Status summary endpoint.
- Integration tests for duplicate requests, payload conflicts, sequence ordering, and retry behavior.

## Architecture

```text
REST client (Postman / curl)
          |
          v
TransactionController
          |
          v
TransactionProcessingService
          |
          +---- TransactionRepository ---- transactions
          +---- AccountRepository -------- accounts
          +---- LedgerRepository --------- ledger_entries
```

PostgreSQL persists transaction requests, account balances and sequence progress, and ledger entries. The processing service can be invoked when a transaction is submitted or explicitly through the processing endpoint.

## Technology Stack

- Java 21
- Spring Boot 3.5.x
- Spring Web
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Maven
- JUnit 5 and Spring Boot Test
- H2 in-memory database for integration tests
- Docker Compose (optional, recommended for local execution)

## Prerequisites

For the Docker Compose option:

- Docker Desktop installed and running.

For running the application directly on your machine:

- Java 21
- Maven 3.9+
- PostgreSQL 16 (or a compatible PostgreSQL instance)

Check the Java and Maven versions:

```bash
java -version
mvn -version
```

## Run with Docker Compose (recommended)

Run these commands from the repository root:

```bash
docker compose up --build
```

Docker Compose starts PostgreSQL and builds/starts the Spring Boot application. The API is available at:

```text
http://localhost:8080/api
```

Stop the services:

```bash
docker compose down
```

**Delete the local demo database volume** (this permanently deletes data stored in the Docker volume):

```bash
docker compose down -v
```

Use the volume-removal command only when you want to reset the demo data.

## Run locally

### 1. Start PostgreSQL

Start the database service defined in `docker-compose.yml`:

```bash
docker compose up -d postgres
```

The default local database settings are:

| Setting | Default |
|---|---|
| JDBC URL | `jdbc:postgresql://localhost:5432/transactions` |
| Database | `transactions` |
| Username | `app` |
| Password | `app` |
| Application port | `8080` |

These are local-demo credentials. Override them for any non-demo environment.

### 2. Start Spring Boot

```bash
mvn spring-boot:run
```

Alternatively, package and run the executable JAR:

```bash
mvn clean package
java -jar target/idempotent-transaction-processor-1.0.0.jar
```

The application reads these environment variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `PORT` (application port; defaults to `8080`)

Example PowerShell configuration:

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/transactions"
$env:SPRING_DATASOURCE_USERNAME = "app"
$env:SPRING_DATASOURCE_PASSWORD = "app"
mvn spring-boot:run
```

## Run tests

Run the automated integration tests:

```bash
mvn test
```

The test profile uses an in-memory H2 database, so PostgreSQL does not need to be running for the test suite.

The tests cover:

- Repeated delivery of the same transaction.
- Reusing a transaction ID with a different payload.
- Sequence 2 arriving before sequence 1.
- A simulated transient failure followed by successful processing.

Run the tests before submitting changes. This repository was scaffolded as a reference implementation; verify the build and behavior in your own environment before treating it as a completed deliverable.

## API Reference

Base URL:

```text
http://localhost:8080/api
```

All request bodies shown below use JSON. For POST requests, set the header:

```http
Content-Type: application/json
```

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/transactions` | Validate and register a transaction; processing may occur immediately if its sequence is next. |
| `POST` | `/transactions/process` | Trigger a processing cycle for waiting/retryable transactions. |
| `GET` | `/transactions/{transactionId}` | Get a transaction by ID. |
| `GET` | `/accounts/{accountId}` | Get account balance and last processed sequence. |
| `GET` | `/accounts/{accountId}/ledger` | Get ledger entries for an account. |
| `GET` | `/transactions/summary` | Read stored counts by transaction status. |

### Transaction request fields

| Field | Required | Description |
|---|---|---|
| `transactionId` | Yes | Unique business transaction ID and idempotency key. |
| `requestId` | Yes | Identifies the incoming request/delivery. It is not the business idempotency key. |
| `accountId` | Yes | Account the transaction affects. |
| `transactionType` | Yes | `CREDIT`, `DEBIT`, or `REVERSAL`. |
| `amount` | Yes | Positive amount with up to two decimal places. |
| `currency` | Yes | Three uppercase letters, for example `INR`. |
| `sequenceNumber` | Yes | Positive, contiguous sequence scoped to an account; expected to start at 1. |
| `simulateTransientFailures` | No | Demo-only failure injection, from `0` to `3`. Do not expose this in a production API. |

## Sample Requests

The examples below use Bash-style `curl`. On Windows PowerShell, use `curl.exe` rather than the `curl` alias if necessary.

### 1. Create a credit transaction

```bash
curl -i -X POST "http://localhost:8080/api/transactions" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-1001",
    "requestId": "REQ-1001",
    "accountId": "ACC-101",
    "transactionType": "CREDIT",
    "amount": 1000.00,
    "currency": "INR",
    "sequenceNumber": 1
  }'
```

For a fresh account and a valid sequence, the transaction should be processed and the balance should become INR 1,000.00.

### 2. Create a debit transaction

```bash
curl -i -X POST "http://localhost:8080/api/transactions" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-1002",
    "requestId": "REQ-1002",
    "accountId": "ACC-101",
    "transactionType": "DEBIT",
    "amount": 250.00,
    "currency": "INR",
    "sequenceNumber": 2
  }'
```

If the credit above has been processed successfully, the balance should become INR 750.00.

### 3. Replay a transaction to verify idempotency

Submit the same business transaction ID with the same business fields. The `requestId` may be different:

```bash
curl -i -X POST "http://localhost:8080/api/transactions" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-1002",
    "requestId": "REQ-1002-REPLAY",
    "accountId": "ACC-101",
    "transactionType": "DEBIT",
    "amount": 250.00,
    "currency": "INR",
    "sequenceNumber": 2
  }'
```

The existing transaction result should be returned without applying the debit a second time. The account balance should remain INR 750.00.

If the same transaction ID is reused with a different business payload, the API should return `409 Conflict`.

### 4. Verify out-of-order processing

First submit sequence `2` for a fresh account:

```bash
curl -i -X POST "http://localhost:8080/api/transactions" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-2002",
    "requestId": "REQ-2002",
    "accountId": "ACC-201",
    "transactionType": "DEBIT",
    "amount": 40.00,
    "currency": "INR",
    "sequenceNumber": 2
  }'
```

It should be stored as `PENDING` because sequence `1` is missing.

Now submit sequence `1`:

```bash
curl -i -X POST "http://localhost:8080/api/transactions" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-2001",
    "requestId": "REQ-2001",
    "accountId": "ACC-201",
    "transactionType": "CREDIT",
    "amount": 100.00,
    "currency": "INR",
    "sequenceNumber": 1
  }'
```

The processor should process sequence 1 and then sequence 2. The expected final balance is INR 60.00.

### 5. Simulate a transient failure

The following request simulates one transient failure before a later processing cycle succeeds:

```bash
curl -i -X POST "http://localhost:8080/api/transactions" \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-3001",
    "requestId": "REQ-3001",
    "accountId": "ACC-301",
    "transactionType": "CREDIT",
    "amount": 500.00,
    "currency": "INR",
    "sequenceNumber": 1,
    "simulateTransientFailures": 1
  }'
```

The initial status should be `RETRY_PENDING`. Trigger another processing cycle:

```bash
curl -i -X POST "http://localhost:8080/api/transactions/process"
```

The transaction should then become `PROCESSED`, and the balance should be INR 500.00 with one ledger entry.

### 6. Query transaction, balance, ledger, and summary

```bash
curl "http://localhost:8080/api/transactions/TXN-1001"
curl "http://localhost:8080/api/accounts/ACC-101"
curl "http://localhost:8080/api/accounts/ACC-101/ledger"
curl "http://localhost:8080/api/transactions/summary"
```

For the summary endpoint, the response contains the stored status counts. It is a read-only endpoint and does not trigger processing.

## Transaction Rules

- A new account starts with a balance of zero and a last processed sequence of zero.
- `CREDIT` increases the balance.
- `DEBIT` decreases the balance, provided sufficient funds are available.
- In the current demo, `REVERSAL` is implemented as a negative balance delta. It does not yet enforce linkage to an original transaction or prevent multiple reversals of that original transaction.
- Sequence numbers are contiguous and scoped to an account, starting at `1`.
- A future sequence is marked `PENDING` until the missing sequence arrives.
- A transient failure increments the retry count and leaves the transaction `RETRY_PENDING`; the next processing cycle can retry it.
- A permanently failed sequence blocks later sequences for that account. The demo does not include an operator workflow to repair or skip a failed sequence.
- A repeated `transactionId` with the same business payload returns the existing record. A repeated ID with a different business payload is treated as an idempotency conflict.
- A successful HTTP response alone is not proof of business success. Check the transaction `status`, account balance, and ledger.

## Project Structure

```text
idempotent-transaction-processor/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── README.md
├── docs/
│   └── design.md
└── src/
    ├── main/
    │   ├── java/com/example/processor/
    │   │   ├── controller/
    │   │   ├── dto/
    │   │   ├── model/
    │   │   ├── repository/
    │   │   ├── service/
    │   │   └── TransactionProcessorApplication.java
    │   └── resources/application.yml
    └── test/
        ├── java/com/example/processor/
        └── resources/application-test.yml
```

## Troubleshooting

### Port 8080 is already in use

Stop the other application using port 8080 or set a different port:

```bash
PORT=8081 mvn spring-boot:run
```

On Windows PowerShell:

```powershell
$env:PORT = "8081"
mvn spring-boot:run
```

### PostgreSQL connection refused

- Confirm Docker Desktop is running.
- Check containers with `docker compose ps`.
- Check logs with `docker compose logs postgres` and `docker compose logs app`.
- Confirm port `5432` is not already occupied by another PostgreSQL service.

### Transaction remains pending

Check whether the account's preceding sequence number exists and has been processed. The implementation requires contiguous sequences starting from 1. A failed or retry-pending earlier sequence can block later transactions.

### Reset local demo data

```bash
docker compose down -v
docker compose up --build
```

This deletes the Docker database volume and all stored demo transactions. Do not run it if you need to retain test evidence.

## Known Limitations and Production Hardening

Before using this design in a real transaction system, consider:

- Use Flyway or Liquibase migrations instead of `ddl-auto: update`.
- Add authentication, authorization, secrets management, rate limiting, and request-size limits.
- Use a durable queue or database-backed job-claim mechanism for asynchronous processing and restart recovery.
- Add bounded worker concurrency, leases/claims, exponential backoff, and dead-letter handling.
- Add explicit recovery and operator workflows for permanently failed sequence numbers.
- Enforce reversal references and prevent duplicate reversals.
- Test concurrent duplicate requests and database lock/serialization failures against PostgreSQL, for example with Testcontainers.
- Add metrics, tracing, dashboards, and alerts.
- Review transaction boundaries and retry behavior under application crashes and database timeouts.
- Add reconciliation and stronger business rules for account and currency management.

See [`docs/design.md`](docs/design.md) for additional architecture decisions and trade-offs.

## Assessment Submission Checklist

- [ ] `mvn test` passes locally.
- [ ] `docker compose up --build` starts the application successfully.
- [ ] The sample POST and GET requests have been verified.
- [ ] Duplicate requests do not create duplicate ledger entries or balance changes.
- [ ] Out-of-order transactions behave according to the documented sequence policy.
- [ ] Retry behavior and retry exhaustion have been tested.
- [ ] README and design notes accurately describe the implementation and its limitations.
- [ ] Repository contains no secrets, local database files, or unnecessary generated artifacts.
