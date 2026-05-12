# ADR-004: Telegram Delivery Limitations

## Status

Accepted.

## Context

Telegram `sendMessage` does not provide an idempotency key that this adapter can use to safely deduplicate retries across crashes. The application can know whether its own database transaction committed, but it cannot always know whether Telegram accepted a message before a crash or timeout.

## Decision

Document Telegram delivery as at-least-once. The worker retries retryable failures and marks successful sends as `DELIVERED`, but it does not claim exactly-once user-visible delivery.

## Consequences

- A crash after Telegram accepts a message but before `markDelivered` commits can produce a duplicate message.
- Retry behavior is still useful for transient Telegram/network failures.
- Operators and reviewers can reason about the limitation directly instead of relying on an inflated reliability claim.
