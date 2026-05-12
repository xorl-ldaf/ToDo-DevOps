# ADR-001: Hexagonal Architecture

## Status

Accepted.

## Context

The project needs to show backend design beyond a single Spring Boot CRUD module. The domain rules for users, tasks, reminders, and background processing should be testable without booting Spring or depending on PostgreSQL, Kafka, or Telegram.

## Decision

Keep domain and application code in `core/*`, define application ports for infrastructure dependencies, and implement those ports in inbound/outbound adapters.

## Consequences

- Domain code stays independent from framework APIs.
- Use cases can be tested with fake ports.
- REST, JPA, Kafka, and Telegram integrations remain replaceable.
- The repository has more modules and wiring than a minimal CRUD app, but the boundaries are explicit and useful for interviews.
