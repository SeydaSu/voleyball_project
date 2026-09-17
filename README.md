# 🏐 Volleyball Live Score Tracking System

**Event-Driven Architecture with Kafka + Redis + Spring Boot + Python**

This project is designed to track score changes in volleyball matches in a **real-time**, **reliable**, and **performant** way.

The system uses the following flow: **Python Producer → Kafka → Spring Boot Consumer → Redis → PostgreSQL**.

---

## 🎯 The Core Problem

The score in a volleyball match changes constantly. For example:

```text
12 → 13 → 14 → 15 → ...
```

Writing directly to the database on every single score change would:

* Create unnecessary database load.
* Require a large number of `INSERT / UPDATE` operations.
* Cause performance problems under high traffic.

At the same time, users or display screens need to be able to see the **live score** the moment it changes.

To satisfy both needs, the system uses three layers, each addressing a different concern:

| Technology     | Responsibility                        | Analogy      |
| -------------- | -------------------------------------- | ------------ |
| **Kafka**      | Carries events reliably                | 📮 Mailbox   |
| **Redis**      | Holds the latest score in memory       | 🧠 Live cache |
| **PostgreSQL** | Stores permanent records               | 🗄️ Archive  |

---

# 🏗️ Overall Architecture

```text
┌─────────────────┐
│  Python Producer│
│                 │
│  Score changed  │
└────────┬────────┘
         │
         │ produce
         ▼
┌─────────────────────────┐
│          Kafka          │
│                         │
│  voleybol-skor-events   │
└────────────┬────────────┘
             │
             │ consume
             ▼
┌─────────────────────────┐
│    Spring Boot Consumer │
│                         │
│      @KafkaListener      │
└────────────┬────────────┘
             │
             │ live update
             ▼
┌─────────────────────────┐
│         Redis           │
│                         │
│  Current match score    │
└────────────┬────────────┘
             │
             │ @Scheduled
             │ every 10 minutes
             ▼
┌─────────────────────────┐
│       PostgreSQL        │
│                         │
│  Persistent / snapshot  │
│       records           │
└─────────────────────────┘
```

---

# 🔄 System Flow

## 1. Python Producer

A score increase is triggered by a function call or a simple CLI/UI action.

The Python Producer:

1. Detects the score change.
2. Converts the event into JSON format.
3. Sends it to the Kafka topic.

Example event:

```json
{
  "eventType": "SKOR_GUNCELLENDI",
  "macId": "mac-001",
  "setNo": 2,
  "skorA": 15,
  "skorB": 12
}
```

---

## 2. Kafka

The producer sends the message to the topic:

```text
voleybol-skor-events
```

Kafka **persists** the message to disk and keeps it queued.

```text
Python
   │
   │ produce
   ▼
Kafka Topic
   │
   │ consume
   ▼
Spring Boot
```

If the Spring Boot service is not running at that moment, the message can wait in Kafka, and the consumer can resume processing once it comes back online.

---

# 3. Spring Boot Consumer

The Spring Boot side acts as the **brain** of the system.

Its core responsibilities:

### Listening

Listens to events coming from Kafka using `@KafkaListener`.

### Live update

Updates the current score in Redis as soon as a message arrives.

### Scheduled persistence

Uses `@Scheduled` to periodically transfer the current state from Redis to PostgreSQL.

### Match completion

When a `MAC_BITTI` (MATCH_OVER) event arrives, the final score is written directly to PostgreSQL and Redis is cleared.

---

# ⚡ Redis

Redis holds the current match state in memory.

Example structure under the single-match assumption:

```text
KEY: mac:mevcut-mac

HASH:
    setNo  → 2
    skorA  → 15
    skorB  → 12
    durum  → DEVAM_EDIYOR
```

## Why Redis?

The question "what's the score right now?" is expected to be asked very frequently.

Because Redis holds data in memory, it:

* Provides very fast reads/writes.
* Is well suited for frequently changing state.
* Is ideal for serving live score information.

Real-world use cases include:

* Live scoring systems
* Session management
* Caching
* Counters
* Real-time state

---

# 🔑 Why a Redis HASH?

The score consists of multiple fields:

```text
setNo
skorA
skorB
durum
```

These could also be stored as a single JSON string.

However, using a HASH allows only the necessary field to be updated.

For example, to change:

```text
skorA = 15
```

you don't need to re-parse and rewrite the entire JSON.

On the Spring Data Redis side, this can be done using:

```text
HashOperations
```

---

# 🗄️ PostgreSQL

PostgreSQL is the system's **persistent data layer**.

Data in Redis is transferred to PostgreSQL at regular intervals.

These records are used for:

* Storing historical match data
* Reporting
* Making the data permanent
* Running retrospective queries

---

# ⏱️ Scheduled Job

Updating Redis and writing to PostgreSQL are **independent** of each other.

Redis:

```text
EVENT ARRIVES
     ↓
Redis is updated immediately
```

PostgreSQL:

```text
10 minutes pass
      ↓
Scheduler runs
      ↓
Redis is read
      ↓
Written to PostgreSQL
```

On the Spring Boot side:

```java
@Scheduled(...)
```

is used for this.

This is why PostgreSQL being slightly behind Redis is **by design**.

> The purpose of the DB is not to serve live data, but to permanently archive it.

---

# 🏁 When the Match Ends

When the `MAC_BITTI` event arrives, the system does not wait for the scheduler to run.

The consumer performs the final steps directly:

```text
MAC_BITTI
   │
   ▼
The final score is read from Redis
   │
   ▼
The final record is written to PostgreSQL
   │
   ▼
The Redis key is deleted
```

For example, the key:

```text
mac:mevcut-mac
```

is deleted.

This way, the temporary Redis state of a completed match is not kept around in the system.

---

# 📨 Kafka Concepts

| Concept             | Its equivalent in this project              |
| -------------------- | -------------------------------------------- |
| **Topic**            | `voleybol-skor-events`                       |
| **Producer**         | Python                                       |
| **Consumer**         | Spring Boot                                  |
| **Consumer Group**   | The group formed by consumer instances       |
| **Message Key**      | `macId`                                      |
| **Partition**        | The segments Kafka distributes messages into |
| **Event**            | A score change / the match ending            |

---

# 🔑 Kafka Message Key and Ordering

Score ordering matters in volleyball.

For example:

```text
12 → 13 → 14 → 15
```

If messages are processed out of order, the score in Redis could go backwards.

For this reason, the message key used is:

```text
macId
```

For example, events where:

```text
macId = mac-001
```

are routed to the same partition.

This ensures that events belonging to the same match are processed in order.

```text
mac-001
   │
   ├── Score 12
   ├── Score 13
   ├── Score 14
   └── Score 15
```

---

# 👥 Consumer Group

Multiple instances of the same consumer can be run at once.

For example:

```text
             Kafka
               │
       ┌───────┴───────┐
       │               │
 Consumer 1        Consumer 2
```

Consumers within the same **consumer group** share and split the Kafka messages between them.

This structure allows the system to scale when needed.

---

# 🔌 Why Don't We Make a Direct Python → Spring Boot API Call?

As an alternative, Python could send a request directly to a Spring Boot API:

```text
Python
   │
   │ HTTP POST
   ▼
Spring Boot
```

However, this approach is **synchronous communication**.

It would require Spring Boot to:

* Be up and running
* Be able to accept the request right at that moment
* Be able to respond to the request

When Kafka is used instead:

```text
Python
   │
   │ event
   ▼
Kafka
   │
   │ later
   ▼
Spring Boot
```

communication becomes **asynchronous**.

The Python Producer and the Spring Boot Consumer don't know each other directly.

They communicate only through the shared Kafka topic.

---

# 🔓 Decoupling

One of Kafka's key advantages is that it provides **loose coupling / decoupling**.

If Spring Boot temporarily goes down:

```text
Python
   │
   ▼
Kafka
   │
   X
Spring Boot
```

messages can still be retained in Kafka.

Once Spring Boot comes back up:

```text
Kafka
   │
   ▼
Spring Boot
```

it can resume consuming the messages.

This pattern is very common in real production systems for communication between microservices.

---

# ❌ Why Didn't We Just Use PostgreSQL?

If we only used the DB:

```text
Every score change
        ↓
INSERT / UPDATE
        ↓
PostgreSQL
```

we would need to write to the database on every single score change.

This could cause:

* Unnecessary write operations
* Higher DB load
* Higher I/O
* Performance problems under high traffic

---

# ❌ Why Didn't We Just Use Redis?

Redis is very fast, but in this project it isn't used on its own as a permanent archive.

For example:

```text
Redis
  ↓
Container restart / crash
  ↓
State can be lost
```

This is why PostgreSQL is used for persistent storage.

---

# 🧩 Why Kafka + Redis + PostgreSQL?

Each technology solves a different problem:

| Layer          | Responsibility                              |
| -------------- | -------------------------------------------- |
| **Kafka**      | Reliable, asynchronous transport of events   |
| **Redis**      | Fast, live state management                  |
| **Scheduler**  | Periodic synchronization                     |
| **PostgreSQL** | Persistent data and archive                  |

As a result, the system works like this:

```text
                 EVENT
                   │
                   ▼
              ┌─────────┐
              │  Kafka  │
              └────┬────┘
                   │
                   ▼
              ┌─────────┐
              │ Spring  │
              │  Boot   │
              └────┬────┘
                   │
                   ▼
              ┌─────────┐
              │  Redis  │
              └────┬────┘
                   │
             every 10 min
                   │
                   ▼
              ┌─────────┐
              │Postgres │
              └─────────┘
```

---

# 🔄 Event-Driven vs Time-Driven

The project deliberately uses two different triggering models.

## Event-Driven

An action is taken when something happens.

```text
Kafka Event
    ↓
@KafkaListener
    ↓
Redis Update
```

For example:

```text
SKOR_GUNCELLENDI (SCORE_UPDATED)
MAC_BITTI (MATCH_OVER)
```

---

## Time-Driven

An action is taken at a fixed time interval.

```text
10 minutes pass
      ↓
@Scheduled
      ↓
Redis → PostgreSQL
```

Keeping these two mechanisms separate is one of the key points in the system's design.

---

# ⚠️ The Cost of This Design

This architecture is more complex than a simple CRUD application.

More components mean more failure scenarios to consider.

For example:

### What happens if a Kafka message is lost?

Kafka's persistence and consumer mechanisms become important for ensuring message reliability and reprocessing.

### What happens if Redis goes down?

The live state can be temporarily lost. The system can be reconstructed from the latest record in PostgreSQL.

### What if the consumer processes the same message twice?

For example, if the same score event is processed twice:

```text
15 → 16
15 → 16
```

a situation like this could occur.

This is why concepts like **idempotency**, event IDs, and proper consumer management become important.

---

# 🌍 Real-World Equivalents

| This project           | Its real-world equivalent               |
| ----------------------- | ---------------------------------------- |
| Kafka                   | Asynchronous communication between microservices |
| Redis                   | Cache, session store, live state         |
| Scheduler               | Periodic synchronization / reporting     |
| PostgreSQL              | Persistent, queryable data               |
| Python Producer         | The event-producing service              |
| Spring Boot Consumer    | The event-processing service             |

For example, in a real e-commerce system:

```text
Order Service
     │
     ▼
   Kafka
     │
     ├──────► Stock Service
     │
     ├──────► Payment Service
     │
     └──────► Notification Service
```

a similar event-driven approach could be used.

---

# 🎯 Key Takeaways from This Project

This project puts the following concepts into practice:

* Event-driven architecture
* Asynchronous communication
* Kafka Producer / Consumer
* Kafka Topic
* Consumer Group
* Kafka Partition
* Message Key
* Event ordering
* Redis
* Redis HASH
* Spring Data Redis
* Spring Boot
* `@KafkaListener`
* `@Scheduled`
* PostgreSQL
* Caching
* State management
* Decoupling
* Fault tolerance
* Persistence
* Real-time data processing

---

# 🧠 The Logic, in Short

If we summarize the entire logic of the system in one sentence:

> **When the score changes, an event is sent through Kafka; Spring Boot consumes this event and updates the live score in Redis; the state in Redis is periodically transferred to PostgreSQL, and when the match ends, the final score is permanently recorded and Redis is cleared.**

```text
SCORE CHANGED
     │
     ▼
Python Producer
     │
     ▼
Kafka
     │
     ▼
Spring Boot Consumer
     │
     ▼
Redis
     │
     ├──────────────► Live score
     │
     │ every 10 minutes
     ▼
PostgreSQL
     │
     │
     └── MAC_BITTI ──► Final record
                         +
                       Redis cleanup
```