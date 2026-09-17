# Volleyball Score System — Event & Data Schema

This document defines the shared "contract" between the Python producer and the Spring Boot consumer. Both sides must stay consistent with this schema when writing code.

---

## 1. Kafka Topic

- **Topic name:** `voleybol-skor-events`
- **Message key:** `macId` (guarantees ordered processing of messages belonging to the same match)
- **Message value format:** JSON

---

## 2. Event Types

### 2.1 `SKOR_ARTIS` (SCORE_INCREASE)

Sent whenever a team's score increases. Score values are **absolute** (the current total score), not a delta.

```json
{
  "eventId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
  "macId": "mevcut-mac",
  "olayTipi": "SKOR_ARTIS",
  "takim": "A",
  "setNo": 1,
  "skorA": 12,
  "skorB": 9,
  "zaman": "2026-09-15T14:32:00Z"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `eventId` | string (UUID) | yes | Unique identifier per event — used for idempotency checks (see Section 5) |
| `macId` | string | yes | Fixed value for now: `"mevcut-mac"` |
| `olayTipi` | string | yes | `"SKOR_ARTIS"` |
| `takim` | string (`"A"` / `"B"`) | yes | The team whose score increased |
| `setNo` | integer | yes | Current set number |
| `skorA` | integer | yes | Team A's current total score |
| `skorB` | integer | yes | Team B's current total score |
| `zaman` | string (ISO-8601, UTC) | yes | Timestamp the event was produced |

### 2.2 `MAC_BITTI` (MATCH_OVER)

Produced **automatically** when the match ends according to volleyball rules (a team wins the 3rd set) — there is no manual trigger; the rules engine generates this event on its own. When the consumer receives it:
1. Saves the final score to the DB with `durum = BITTI`
2. Deletes the `mac:mevcut-mac` key from Redis

```json
{
  "eventId": "b2c3d4e5-6789-01bc-def2-234567890abc",
  "macId": "mevcut-mac",
  "olayTipi": "MAC_BITTI",
  "setNo": 3,
  "skorA": 25,
  "skorB": 20,
  "kazananTakim": "A",
  "zaman": "2026-09-15T15:10:00Z"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `eventId` | string (UUID) | yes | Unique identifier per event — used for idempotency checks (see Section 5) |
| `macId` | string | yes | `"mevcut-mac"` |
| `olayTipi` | string | yes | `"MAC_BITTI"` |
| `setNo` | integer | yes | Set number in which the match ended |
| `skorA` | integer | yes | Team A's final score |
| `skorB` | integer | yes | Team B's final score |
| `kazananTakim` | string (`"A"` / `"B"`) | yes | Winning team |
| `zaman` | string (ISO-8601, UTC) | yes | Timestamp the event was produced |

**Volleyball rules (automatic end-of-match logic):** A match is played over 5 sets; winning 3 sets wins the match. Sets 1–4 end at 25 points (win by at least 2); set 5 (the deciding set) ends at 15 points (win by at least 2).

> Note: the `takim` field is not sent with this event type (it's not meaningful outside of score-increase events).

---

## 3. Redis Data Structure

- **Key:** `mac:mevcut-mac`
- **Type:** Hash

| Hash field | Example value | Description |
|---|---|---|
| `setNo` | `"1"` | Current set |
| `skorA` | `"12"` | Team A's live score |
| `skorB` | `"9"` | Team B's live score |
| `durum` | `"DEVAM_EDIYOR"` | `DEVAM_EDIYOR` (IN_PROGRESS) \| `BITTI` (OVER) |
| `guncellemeZamani` | `"2026-09-15T14:32:00Z"` | Timestamp of the last update |

This key is fully deleted (`DEL mac:mevcut-mac`) once `MAC_BITTI` has been processed.

An additional Redis structure is also kept for idempotency tracking — see Section 5.

---

## 4. DB Table — `mac_skor`

Auto-created by JPA (`ddl-auto: update`) from the `MacSkor` entity.

| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL (PK) | Auto-incrementing primary key |
| `mac_id` | VARCHAR(100) | `"mevcut-mac"` |
| `set_no` | INT | Set number at the time of recording |
| `skor_a` | INT | Team A's score at the time of recording |
| `skor_b` | INT | Team B's score at the time of recording |
| `durum` | VARCHAR(20) | `DEVAM_EDIYOR` (IN_PROGRESS) \| `BITTI` (OVER) |
| `kazanan_takim` | VARCHAR(1) | Only populated on `durum = BITTI` rows |
| `kayit_zamani` | TIMESTAMP | When this row was written to the DB (default: `now()`) |

**Write rule:** Every record is added as a new row (INSERT), never updated (no UPDATE). This preserves the match's progression over time without losing history.

- The scheduled job (every 10 minutes) → inserts a `durum = DEVAM_EDIYOR` row
- On `MAC_BITTI` → inserts a `durum = BITTI` row (this row is the match's final result)

---

## 5. Idempotency

Because Kafka guarantees "at least once" delivery, an event could theoretically reach the consumer more than once. To prevent this from causing problems:

- Every event carries a unique `eventId` (UUID) field (see Section 2)
- Before processing an event, the consumer checks a Redis **Set** called `processed-events` to see whether this `eventId` has already been processed
- If it has, the event is skipped (logged, but no DB/Redis update is performed)
- Only **after** processing succeeds is the `eventId` added to the `processed-events` set (with a 24-hour TTL)

This mechanism is critical for preventing the `MAC_BITTI` event from being processed twice and creating a duplicate final record in the DB.

---

## 6. Changelog

Whenever the schema changes, this document must be updated, and the corresponding code on both the `voleybol-producer` and `voleybol-consumer` sides must be adapted accordingly.

- **v1:** Initial schema — `macId`, `olayTipi`, `setNo`, `skorA`, `skorB`, `zaman` fields; manual match-end trigger
- **v2:** Added the `eventId` field (for idempotency); match-end is now automatic, driven by volleyball rules