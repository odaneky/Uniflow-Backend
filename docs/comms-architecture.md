# UniFlow Communications Architecture

Living reference for the campus comms platform implemented on `feature/comms-platform`.

## Boundaries

| Layer | Responsibility |
|-------|----------------|
| Keycloak | Authentication only (OIDC tokens) |
| `CurrentUserProvider` | UniFlow `user_id` resolution |
| Academic query ports | Enrolment, roster, advisor relationships |
| `MessagingPolicy` / `ForumAccess` | Who may read/write |
| Communication aggregates | Conversations, announcements, forums |
| Transactional outbox | Sole durable async path |
| Notification rows | Per-user durable alerts |
| `SseEventPublisher` | Live push to connected clients (best-effort) |
| `EmailSender` | External delivery port |

Comms tables store **UniFlow `user_id` only** — never Keycloak `sub`.

## Channels

- **Direct messages** — `conversations`, `messages`, `conversation_participants`
- **Announcements** — audience-scoped broadcasts + `announcement_reads`
- **Section forums** — separate `forum_topics` / `forum_posts` (not reused DM tables)
- **Notifications** — unified delivery inbox with deep links

## Write path

1. Policy check (`MessagingPolicy`, `ForumAccess`)
2. Rate limit check (`CommsRateLimiter`) — returns **429** with `Retry-After`
3. Persist domain row + outbox row in **one transaction**
4. `OutboxDispatcher` claims rows (`FOR UPDATE SKIP LOCKED`), invokes handlers
5. `NotificationDeliveryService` persists IN_APP notification, pushes SSE, optionally sends email

No `@TransactionalEventListener(AFTER_COMMIT)` for notifications.

## Real-time

- **SSE:** `GET /api/v1/me/events/stream` — fetch-based streaming with `Authorization: Bearer`
- **Frontend:** `src/comms/useCommsStream.ts` + 45s poll fallback in shell
- **Single-node:** `LocalSseEventPublisher`
- **Multi-node:** swap to a Redis-backed `SseEventPublisher` when deployment requires it (not enabled by default)

## Email & preferences

- `EmailSender` port; `LoggingEmailSender` in local dev
- Enable: `lms.notifications.email.enabled=true`
- Preferences: `notification_preferences` — missing row = enabled
- `GET/PUT /api/v1/me/notification-preferences`

## Observability (Phase 4)

Micrometer metrics (no user id tags):

| Metric | Meaning |
|--------|---------|
| `uniflow.outbox.pending` | Gauge — PENDING + FAILED rows |
| `uniflow.outbox.processed` | Counter — `event_type`, `outcome` |
| `uniflow.outbox.dispatch.duration` | Timer — handler latency |
| `uniflow.comms.rate_limit` | Counter — bucket name |

## Rate limits (configurable)

```yaml
lms.comms.rate-limit.message-send.limit: 30
lms.comms.rate-limit.message-send.window-seconds: 60
```

Buckets: `message_send`, `conversation_create`, `forum_post`, `forum_topic`.

Process-local by default; replace `CommsRateLimiter` with a shared-store implementation for multi-node.

## Attachments

Messages may reference `document_id` (owner must match sender). Upload via document module first, then pass `documentId` on `POST /conversations/{id}/messages`.

## Compliance

- `GET /api/v1/conversations/{id}/compliance-export` — **SYSTEM_ADMIN** only
- Writes `MESSAGE_THREAD_ACCESSED` to audit trail
- Returns participant list + full message metadata for authorised review

## Phased delivery status

| Phase | Scope | Status |
|-------|-------|--------|
| 0 | Outbox, policy, inbox, poll badges | Done |
| 1 | Announcements + fan-out | Done |
| 2 | Section forums | Done |
| 3 | SSE, email port, preferences | Done |
| 4 | Rate limits, metrics, attachments, compliance | Done |
| 4+ | Redis SSE, distributed rate limits, retention jobs | Deferred until ops need |

## Key files

- `communication/service/CommunicationService.java`
- `communication/service/ForumService.java`
- `common/outbox/OutboxDispatcher.java`
- `notification/service/NotificationDeliveryService.java`
- `common/sse/LocalSseEventPublisher.java`
- `communication/policy/InMemoryCommsRateLimiter.java`
- `src/comms/MessagesInbox.tsx`, `src/comms/useCommsStream.ts`
