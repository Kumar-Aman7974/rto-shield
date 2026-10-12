# RTO Shield — Profit-Aware Order Delivery & RTO Recovery

A rule-driven backend + demo frontend that scores delivery risk, routes orders to profit-aware journeys, and executes low-cost recovery actions to reduce RTO (Return-to-Origin) losses for e-commerce sellers.

---

## Table of Contents

1. Project Title and Introduction
2. Key Features
3. Problem Statement and Solution
4. Technology Stack
5. Architecture Overview
6. Application Workflows
7. Project Structure
8. Database Design
9. API Documentation
10. Swagger API Testing Guide
11. How to Run (Quickstart)
12. Testing
13. Contributing
14. TODO / Known Gaps
15. Appendix — Useful File Locations

---

## 1. Project Title and Introduction

RTO Shield helps merchants lower failed-delivery (RTO) losses by combining:

- A deterministic order-health scorer
- A profit-aware router that chooses the best customer journey
- A rescue engine that picks low-cost recovery actions

Intended users: technical interviewers, developers, and product/ops stakeholders.

One-line summary: A compact, demo-ready system that scores order delivery risk, routes orders to profit-aware journeys, and runs recovery/rescue flows to minimize RTO losses.

Motivation: Built for interview demos — shows system design, rule-driven business logic, end-to-end flows (intake → scoring → routing → delivery events → recovery → dashboard) and measurable profit outcomes.

---

## 2. Key Features

| Feature | Description |
|---|---|
| Order intake | POST /orders and store webhook (/webhooks/store/order-created) |
| Order Health Scoring | Rule-based OrderHealthScorer produces healthScore, riskLevel, scoreReasons |
| Profit-Aware Routing | JourneyRouter + ProfitCalculator choose journeys and courier |
| Journey Execution | JourneyExecutor sends confirmations and handles customer replies |
| Delivery Events | POST /courier/events processes courier status updates |
| Rescue & Recovery | RescueService + RecoveryController pick low-cost recovery actions |
| Trust Ledger | TrustPointsService + /customers/{id}/trust (append-only, idempotent) |
| Audit Trail | Every decision recorded via AuditController + AuditLog entity |
| Simulator | POST /simulator/run + /simulator/reset runs before/after experiments |
| Frontend Dashboard | React (Vite + Tailwind) visualizes metrics and simulator |
| Demo Endpoint | POST /demo/run — one-button 5-step demo for interviews |

Only features present in code are listed above. Non-implemented items are marked in the TODO section.

---

## 3. Problem Statement and Solution

### Problem

High RTO rates reduce seller margins and inventory availability. Standard approaches (one-size-fits-all courier, blanket prepaid offers) don't account for order-level profit trade-offs.

### Solution

Compute an order-level expectedLoss, compare with low-cost recovery action costs, and choose journeys or interventions that maximize realized margin. Each decision is auditable and tunable via ConfigService.

Core idea: Business rules live in config, not hard-coded. Money math is centralized in ProfitCalculator.

---

## 4. Technology Stack

| Technology | Purpose | Location / Where Used |
|---|---|---|
| Java 17, Spring Boot 3.3.5 | Backend framework and DI | pom.xml, src/ |
| Spring Data JPA | Persistence | src/repo, src/domain |
| PostgreSQL (Docker) | Runtime DB for demo | docker-compose.yml |
| Maven | Build tool | pom.xml |
| React 18 + Vite + Tailwind | Frontend dashboard | frontend/ |
| springdoc OpenAPI | API docs (Swagger UI) | Auto-configured from controllers |
| JUnit (spring-boot-starter-test) | Tests | src/test |

---

## 5. Architecture Overview

### Major Components

- API layer: Controllers under src/main/java/com/rtoshield/*Controller.java
- Service layer: Scoring, routing, delivery, recovery, simulator
- Persistence: JPA entities in src/domain and repositories in src/repo
- Frontend: frontend/ (React) consumes API

### Architecture Diagram

```mermaid
flowchart TB
  subgraph Backend
    A[Controllers] --> B(Services)
    B --> C[Repositories (JPA)]
    B --> D[Audit / Trust Ledger]
  end
  F[Frontend (Vite React)] -->|HTTP| A
  G[Postgres Docker] -->|JPA| C
```

### Layered Architecture Diagram

```mermaid
flowchart TD
  subgraph Presentation Layer
    FC[Frontend React]
    SC[Swagger UI]
  end

  subgraph API Layer
    OC[OrderController]
    CEC[CourierEventController]
    RC[RecoveryController]
    TC[TrustController]
    AC[AuditController]
    SimC[SimulationController]
    DC[DemoController]
    MC[MetricsController]
    NC[NotificationController]
    WC[WhatsAppWebhookController]
    SWC[StoreWebhookController]
    RDC[ReferenceDataController]
    CC[ConfigController]
  end

  subgraph Service Layer
    OS[OrderService]
    OHS[OrderHealthScorer]
    JR[JourneyRouter]
    PC[ProfitCalculator]
    DSS[DeliveryStateService]
    RS[RescueService]
    TPS[TrustPointsService]
    AS[AuditService]
    SS[SimulationService]
    CFG[ConfigService]
    JE[JourneyExecutor]
  end

  subgraph Persistence Layer
    REPO[Repositories]
    DB[(PostgreSQL)]
  end

  FC --> OC
  SC --> OC
  OC --> OS
  OS --> OHS
  OS --> JR
  JR --> PC
  OS --> JE
  CEC --> DSS
  DSS --> RS
  DSS --> TPS
  DSS --> AS
  RC --> RS
  TC --> TPS
  AC --> AS
  SimC --> SS
  OS --> REPO
  DSS --> REPO
  RS --> REPO
  TPS --> REPO
  AS --> REPO
  SS --> REPO
  REPO --> DB
```

---

## 6. Application Workflows

### 6.1 Order Intake → Scoring → Routing → Execution

```mermaid
sequenceDiagram
  participant Client
  participant OrderController
  participant OrderService
  participant Scorer
  participant Router
  participant Executor
  Client->>OrderController: POST /orders (CreateOrderRequest)
  OrderController->>OrderService: createAndProcess(req)
  OrderService->>Scorer: score(order)
  Scorer-->>OrderService: ScoreResult
  OrderService->>Router: decide(order, score)
  Router-->>OrderService: DecisionOutcome
  OrderService->>Executor: execute(order, decision)
  Executor-->>OrderService: status update
  OrderService-->>Audit: record(...)
```

### 6.2 Delivery Event → Rescue

```mermaid
sequenceDiagram
  participant Courier
  participant CourierEventController
  participant DeliveryStateService
  participant RescueService
  Courier->>CourierEventController: POST /courier/events
  CourierEventController->>DeliveryStateService: handle(event)
  DeliveryStateService->>TrustPointsService: recordEvent(...)
  DeliveryStateService->>AuditService: record(...)
  DeliveryStateService->>RescueService: onFailedAttempt(order,...)
  RescueService-->>DeliveryStateService: updates (RESCHEDULED / RTO)
```

### 6.3 Simulator Flow

```mermaid
sequenceDiagram
  participant Client
  participant SimulationController
  participant SimulationService
  participant DataGenerator
  participant OrderService
  participant SimulationReport
  Client->>SimulationController: POST /simulator/run?orders=50&engine=ON
  SimulationController->>SimulationService: run(orders, engine)
  SimulationService->>DataGenerator: generateOrders(50)
  DataGenerator-->>SimulationService: simulated orders
  SimulationService->>OrderService: createAndProcess(order)
  OrderService-->>SimulationService: processed order
  SimulationService->>SimulationReport: aggregate before/after
  SimulationReport-->>Client: SimulationReport
```

### 6.4 Trust Ledger Flow

```mermaid
sequenceDiagram
  participant Client
  participant TrustController
  participant TrustPointsService
  participant TrustLedgerRepository
  Client->>TrustController: POST /customers/{id}/trust/events
  TrustController->>TrustPointsService: recordEvent(customerId, eventType, orderId)
  TrustPointsService->>TrustLedgerRepository: save(entry)
  TrustPointsService-->>TrustController: updated trust balance + tier
  TrustController-->>Client: TrustResponse
```

### 6.5 The 5-Step Demo Flow

```mermaid
flowchart LR
  A[Step 1: Score Order] --> B[Step 2: Route Journey]
  B --> C[Step 3: Simulate Delivery Risk]
  C --> D[Step 4: Recovery Action]
  D --> E[Step 5: Dashboard Metrics]
```

1. Score — Order health scored
2. Route — Profit-aware journey selected
3. Simulate delivery risk — Courier events processed
4. Recovery action — Rescue engine intervenes
5. Dashboard metrics — Results visualized

---

## 7. Project Structure

### Actual Directory Structure (from IDE)

```
rto-shield/
├── .idea/
├── frontend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/rtoshield/
│   │   │       ├── audit/
│   │   │       │   ├── AuditController.java
│   │   │       │   └── AuditService.java
│   │   │       ├── common/
│   │   │       │   ├── GlobalExceptionHandler.java
│   │   │       │   └── NotFoundException.java
│   │   │       ├── config/
│   │   │       │   ├── ConfigAdminService.java
│   │   │       │   ├── ConfigController.java
│   │   │       │   ├── ConfigKeys.java
│   │   │       │   ├── ConfigRules.java
│   │   │       │   ├── ConfigService.java
│   │   │       │   ├── ConfigView.java
│   │   │       │   ├── DataSeeder.java
│   │   │       │   └── WebCorsConfig.java
│   │   │       ├── confirmation/
│   │   │       │   ├── JourneyExecutor.java
│   │   │       │   ├── MockNotificationSender.java
│   │   │       │   ├── NotificationController.java
│   │   │       │   ├── NotificationSender.java
│   │   │       │   ├── ReminderScheduler.java
│   │   │       │   └── WhatsAppWebhookController.java
│   │   │       ├── delivery/
│   │   │       │   ├── CourierEventController.java
│   │   │       │   ├── CourierEventRequest.java
│   │   │       │   ├── DeliveryStateService.java
│   │   │       │   └── OrderLocker.java
│   │   │       ├── demo/
│   │   │       │   └── DemoController.java
│   │   │       ├── domain/
│   │   │       │   ├── ActionType.java
│   │   │       │   ├── AuditLog.java
│   │   │       │   ├── CampaignSource.java
│   │   │       │   ├── ConfigEntry.java
│   │   │       │   ├── Courier.java
│   │   │       │   ├── CourierPincodeStat.java
│   │   │       │   ├── Customer.java
│   │   │       │   ├── CustomerTier.java
│   │   │       │   ├── DecisionOutcome.java
│   │   │       │   ├── DeliveryEvent.java
│   │   │       │   ├── DeliveryEventType.java
│   │   │       │   ├── InventoryAction.java
│   │   │       │   ├── Journey.java
│   │   │       │   ├── Notification.java
│   │   │       │   ├── Order.java
│   │   │       │   ├── OrderStatus.java
│   │   │       │   ├── PaymentType.java
│   │   │       │   ├── Product.java
│   │   │       │   ├── RiskLevel.java
│   │   │       │   ├── RtoReason.java
│   │   │       │   ├── ScoreResult.java
│   │   │       │   ├── TrustEventType.java
│   │   │       │   └── TrustLedgerEntry.java
│   │   │       ├── intake/
│   │   │       │   ├── dto/
│   │   │       │   │   ├── CreateOrderRequest.java
│   │   │       │   │   ├── OrderResponse.java
│   │   │       │   │   └── StoreOrderPayload.java
│   │   │       │   ├── AddressQualityService.java
│   │   │       │   ├── OrderController.java
│   │   │       │   ├── OrderService.java
│   │   │       │   ├── ReferenceDataController.java
│   │   │       │   └── StoreWebhookController.java
│   │   │       ├── metrics/
│   │   │       │   └── MetricsController.java
│   │   │       ├── recovery/
│   │   │       │   ├── DelayMonitorScheduler.java
│   │   │       │   ├── RecoveryController.java
│   │   │       │   └── RescueService.java
│   │   │       ├── repo/
│   │   │       │   ├── AuditLogRepository.java
│   │   │       │   ├── ConfigEntryRepository.java
│   │   │       │   ├── CourierPincodeStatRepository.java
│   │   │       │   ├── CourierRepository.java
│   │   │       │   ├── CustomerRepository.java
│   │   │       │   ├── DeliveryEventRepository.java
│   │   │       │   ├── NotificationRepository.java
│   │   │       │   ├── OrderRepository.java
│   │   │       │   ├── ProductRepository.java
│   │   │       │   └── TrustLedgerRepository.java
│   │   │       ├── routing/
│   │   │       │   ├── CourierRecommender.java
│   │   │       │   ├── JourneyRouter.java
│   │   │       │   └── ProfitCalculator.java
│   │   │       ├── scoring/
│   │   │       │   ├── OrderHealthScorer.java
│   │   │       │   ├── PincodeStatsService.java
│   │   │       │   └── RuleBasedHealthScorer.java
│   │   │       ├── simulator/
│   │   │       │   ├── DataGenerator.java
│   │   │       │   ├── SimulatedScenario.java
│   │   │       │   ├── SimulationController.java
│   │   │       │   ├── SimulationReport.java
│   │   │       │   ├── SimulationResetResponse.java
│   │   │       │   ├── SimulationService.java
│   │   │       │   └── SimulationTotals.java
│   │   │       ├── trust/
│   │   │       │   ├── TrustController.java
│   │   │       │   └── TrustPointsService.java
│   │   │       └── RtoShieldApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
├── .gitignore
├── docker-compose.yml
├── pom.xml
├── PROJECT_CONTEXT.md
└── README.md
```

### Key Classes and Responsibilities

| Class | Responsibility |
|---|---|
| OrderController / OrderService | Single pipeline entry (createAndProcess) |
| OrderHealthScorer / RuleBasedHealthScorer | Computes ScoreResult (healthScore, reasons) |
| JourneyRouter / ProfitCalculator / CourierRecommender | Selects journey and courier, sets expectedLoss/actionCost |
| DeliveryStateService / OrderLocker | Accepts CourierEventRequest, enforces idempotency |
| RescueService / DelayMonitorScheduler | Chooses cost-effective recovery actions (reschedule, OTP) |
| SimulationService / DataGenerator | Replays pipeline for invented orders |
| TrustPointsService | Append-only, idempotent per (order + event type) |
| JourneyExecutor / ReminderScheduler | Sends confirmations and handles customer replies |
| ConfigService / ConfigRules / ConfigKeys | Business rule configuration (tunable) |
| AuditService | Records every decision |

---

## 8. Database Design

### Entities (Verified from src/domain)

| Entity | Table | Key Fields |
|---|---|---|
| Order | orders | id, customer_id, product_id, quantity, paymentType, campaignSource, phone, addressLine, city, pincode, addressQuality, orderValue, healthScore, riskLevel, scoreReasons, journey, decisionReason, expectedLoss, actionCost, courier_id, status, attemptCount, rtoReason, inventoryAction, otpCode, simulated, createdAt, lastEventAt |
| Customer | customers | id, name, phone, email, trustBalance, tier, totalOrders, createdAt |
| CourierPincodeStat | courier_pincode_stats | courier_id, pincode, attempts, successes |
| AuditLog | audit_log | id, orderId, decision, reason, inputs, createdAt |
| TrustLedgerEntry | trust_ledger_entry | Append-only ledger entries |
| DeliveryEvent | delivery_event | Courier event records |
| Notification | notification | Customer notifications |
| Product | product | Product catalog |
| ConfigEntry | config_entry | Business rule configuration |
| Courier | courier | Courier master data |

### Enums

- ActionType
- CampaignSource
- CustomerTier
- DeliveryEventType
- InventoryAction
- Journey
- OrderStatus
- PaymentType
- RiskLevel
- RtoReason
- TrustEventType

### ER Diagram (Simplified)

```mermaid
erDiagram
    ORDER }o--|| CUSTOMER : places
    ORDER }o--|| PRODUCT : contains
    ORDER }o--o{ COURIER : assigned_to
    COURIER ||--o{ COURIER_PINCODE_STAT : has
    CUSTOMER ||--o{ TRUST_LEDGER_ENTRY : has
    ORDER ||--o{ AUDIT_LOG : has
    ORDER ||--o{ DELIVERY_EVENT : has
    ORDER ||--o{ NOTIFICATION : has
```

### Entity Relationship — Detailed

```mermaid
erDiagram
    CUSTOMER ||--o{ ORDER : places
    CUSTOMER ||--o{ TRUST_LEDGER_ENTRY : owns
    CUSTOMER {
        long id
        string name
        string phone
        string email
        int trustBalance
        string tier
        int totalOrders
        datetime createdAt
    }

    ORDER ||--|| PRODUCT : contains
    ORDER ||--o{ DELIVERY_EVENT : generates
    ORDER ||--o{ AUDIT_LOG : logs
    ORDER ||--o{ NOTIFICATION : triggers
    ORDER }o--|| COURIER : assigned_to
    ORDER {
        long id
        long customer_id
        long product_id
        int quantity
        string paymentType
        string campaignSource
        string phone
        string addressLine
        string city
        string pincode
        string addressQuality
        decimal orderValue
        int healthScore
        string riskLevel
        string scoreReasons
        string journey
        string decisionReason
        decimal expectedLoss
        decimal actionCost
        long courier_id
        string status
        int attemptCount
        string rtoReason
        string inventoryAction
        string otpCode
        boolean simulated
        datetime createdAt
        datetime lastEventAt
    }

    PRODUCT {
        long id
        string name
        decimal price
        int stock
    }

    COURIER ||--o{ COURIER_PINCODE_STAT : has
    COURIER {
        long id
        string name
        string code
    }

    COURIER_PINCODE_STAT {
        long courier_id
        string pincode
        int attempts
        int successes
    }

    TRUST_LEDGER_ENTRY {
        long id
        long customer_id
        string eventType
        long orderId
        int points
        datetime createdAt
    }

    AUDIT_LOG {
        long id
        long orderId
        string decision
        string reason
        string inputs
        datetime createdAt
    }

    DELIVERY_EVENT {
        long id
        long orderId
        string status
        int attemptNo
        datetime createdAt
    }

    NOTIFICATION {
        long id
        long orderId
        long customerId
        string channel
        string message
        datetime createdAt
    }

    CONFIG_ENTRY {
        long id
        string key
        string value
        string description
    }
```

---

## 9. API Documentation

All endpoints are available at runtime via Swagger UI:

http://localhost:8080/swagger-ui.html

### Endpoint Reference

| Method | Endpoint | Purpose | Request Body | Response |
|---|---|---|---|---|
| POST | /orders | Create order + run pipeline | CreateOrderRequest | 201 Created, OrderResponse |
| GET | /orders | List all orders | — | List of OrderResponse |
| GET | /orders/{id} | Get one order | — | OrderResponse |
| POST | /webhooks/store/order-created | Store webhook intake | StoreOrderPayload | 201 Created, OrderResponse |
| POST | /courier/events | Post delivery event | CourierEventRequest | OrderResponse (updated) |
| POST | /simulator/run | Run before/after simulator | Query params orders, engine | SimulationReport |
| POST | /simulator/reset | Remove simulated orders | — | SimulationResetResponse |
| POST | /demo/run | One-button 5-step demo | — | Stepwise objects + simulation report |
| GET | /metrics/summary | Simple counts (stub) | — | totalOrders, delivered, rto, note |
| GET | /audit?limit=50 | Read audit log | Query params | AuditView records |
| GET | /audit?orderId=12 | Audit by order | Query param | AuditView records |
| GET | /customers/{id}/trust | View trust balance + ledger | — | Trust info + ledger |
| POST | /customers/{id}/trust/events | Apply trust event | eventType, orderId | Updated trust |
| GET | /customers | Read seeded customers | — | Customer list |
| GET | /products | Read seeded products | — | Product list |
| GET | /couriers | Read seeded couriers | — | Courier list |
| GET | /courier-stats | Read pincode stats | — | Stats list |
| GET | /notifications?orderId=1 | List notifications | Query params | Notification list |
| GET | /notifications?customerId=2 | List by customer | Query params | Notification list |
| POST | /recovery/{id}/reschedule | Minimal recovery action | — | OrderResponse |

### Complete Swagger API List (Controllers → Endpoints)

#### 1. OrderController (`/orders`)

| Method | Endpoint | Description |
|---|---|---|
| POST | /orders | Create order + run full pipeline |
| GET | /orders | List all orders |
| GET | /orders/{id} | Get order by ID |

#### 2. StoreWebhookController (`/webhooks/store`)

| Method | Endpoint | Description |
|---|---|---|
| POST | /webhooks/store/order-created | Store webhook intake |

#### 3. CourierEventController (`/courier`)

| Method | Endpoint | Description |
|---|---|---|
| POST | /courier/events | Post courier delivery event |

#### 4. RecoveryController (`/recovery`)

| Method | Endpoint | Description |
|---|---|---|
| POST | /recovery/{id}/reschedule | Manual reschedule action |

#### 5. TrustController (`/customers`)

| Method | Endpoint | Description |
|---|---|---|
| GET | /customers/{id}/trust | View trust balance + ledger |
| POST | /customers/{id}/trust/events | Apply trust event |

#### 6. AuditController (`/audit`)

| Method | Endpoint | Description |
|---|---|---|
| GET | /audit | Read audit log (by limit or orderId) |

#### 7. SimulationController (`/simulator`)

| Method | Endpoint | Description |
|---|---|---|
| POST | /simulator/run | Run before/after simulator |
| POST | /simulator/reset | Remove simulated orders |

#### 8. DemoController (`/demo`)

| Method | Endpoint | Description |
|---|---|---|
| POST | /demo/run | One-button 5-step demo |

#### 9. MetricsController (`/metrics`)

| Method | Endpoint | Description |
|---|---|---|
| GET | /metrics/summary | Simple counts (stub) |

#### 10. NotificationController (`/notifications`)

| Method | Endpoint | Description |
|---|---|---|
| GET | /notifications?orderId=1 | List notifications by order |
| GET | /notifications?customerId=2 | List notifications by customer |

#### 11. ReferenceDataController

| Method | Endpoint | Description |
|---|---|---|
| GET | /customers | Read seeded customers |
| GET | /products | Read seeded products |
| GET | /couriers | Read seeded couriers |
| GET | /courier-stats | Read pincode stats |

#### 12. ConfigController (`/config`)

| Method | Endpoint | Description |
|---|---|---|
| GET | /config | View all config entries |
| PUT | /config/{key} | Update config value |

#### 13. WhatsAppWebhookController (`/webhooks/whatsapp`)

| Method | Endpoint | Description |
|---|---|---|
| POST | /webhooks/whatsapp | WhatsApp reply webhook |

### Example: Create Order Request

```
POST /orders
Content-Type: application/json

{
  "customerId": 101,
  "productId": 12,
  "quantity": 1,
  "paymentType": "COD",
  "campaignSource": "ADS",
  "addressLine": "123 MG Road",
  "city": "Bangalore",
  "pincode": "560001",
  "phone": "9876500002"
}
```

### Example: Courier Event Request

```
POST /courier/events
Content-Type: application/json

{
  "orderId": 1,
  "status": "CUSTOMER_UNREACHABLE",
  "attemptNo": 2
}
```

Valid statuses: OUT_FOR_DELIVERY, DELIVERED, REFUSED, CUSTOMER_UNREACHABLE, WRONG_ADDRESS, DELAYED

### Authentication & Security

The project currently ships without JWT auth. ConfigController comments note that /config/** and audit should be protected in M10. For now, developer/local demo uses open APIs. Do NOT expose demo DB to public endpoints.

---

## 10. Swagger API Testing Guide

This section walks you through testing every major endpoint using Swagger UI — no external tools (Postman/curl) required.

### 10.1 Accessing Swagger UI

1. Start the backend (mvn spring-boot:run)
2. Open: http://localhost:8080/swagger-ui.html
3. You'll see all controllers grouped by tag (Orders, Courier Events, Simulator, Demo, Audit, Trust, etc.)

### 10.2 Step-by-Step Test Sequence

Follow this order for a complete end-to-end test:

#### Step 1 — Seed Check (verify data exists)

| # | Endpoint | Action | Expected |
|---|---|---|---|
| 1 | GET /customers | Click Try it out then Execute | List of seeded customers (e.g., id 101) |
| 2 | GET /products | Execute | List of products (e.g., id 12) |
| 3 | GET /couriers | Execute | List of couriers |

If empty, restart backend — seed data loads on startup.

#### Step 2 — Create an Order (Score → Route → Execute)

1. Expand POST /orders
2. Click Try it out
3. Paste this body:

```
{
  "customerId": 101,
  "productId": 12,
  "quantity": 1,
  "paymentType": "COD",
  "campaignSource": "ADS",
  "addressLine": "123 MG Road",
  "city": "Bangalore",
  "pincode": "560001",
  "phone": "9876500002"
}
```

4. Click Execute
5. Verify in response:
   - healthScore (0–100)
   - riskLevel (LOW / MEDIUM / HIGH)
   - scoreReasons (array of reasons)
   - journey (chosen journey)
   - expectedLoss, actionCost
   - status (initial status)
6. Copy the id from response — you'll need it next.

#### Step 3 — Simulate a Delivery Event

1. Expand POST /courier/events
2. Click Try it out
3. Paste (replace 1 with your order id):

```
{
  "orderId": 1,
  "status": "CUSTOMER_UNREACHABLE",
  "attemptNo": 1
}
```

4. Click Execute
5. Verify:
   - attemptCount incremented
   - status changed (e.g., RESCHEDULED if rescue kicked in)
   - rtoReason may be populated

#### Step 4 — Trigger Recovery

1. Expand POST /recovery/{id}/reschedule
2. Click Try it out
3. Enter your order id (e.g., 1)
4. Click Execute
5. Verify: status = RESCHEDULED

#### Step 5 — Inspect Audit Trail

1. Expand GET /audit
2. Click Try it out
3. Enter orderId = your order id (e.g., 1)
4. Click Execute
5. Verify: You see entries for each decision (scoring, routing, delivery, rescue)

#### Step 6 — Check Trust Ledger

1. Expand GET /customers/{id}/trust
2. Click Try it out
3. Enter id = 101
4. Click Execute
5. Verify: trustBalance, tier, and ledgerEntries array

#### Step 7 — Run the One-Button Demo

1. Expand POST /demo/run
2. Click Try it out then Execute
3. Verify: Response contains 5 stepwise objects:
   - Step 1: Scored order
   - Step 2: Routed journey
   - Step 3: Simulated delivery risk
   - Step 4: Recovery action
   - Step 5: Metrics summary

#### Step 8 — Run a Batch Simulation

1. Expand POST /simulator/run
2. Click Try it out
3. Set orders = 50, engine = ON
4. Click Execute
5. Verify: SimulationReport with before/after margins

#### Step 9 — Reset Simulation Data

1. Expand POST /simulator/reset
2. Click Try it out then Execute
3. Verify: SimulationResetResponse confirms cleanup

#### Step 10 — Check Metrics

1. Expand GET /metrics/summary
2. Click Try it out then Execute
3. Verify: totalOrders, delivered, rto, note

#### Step 11 — Check Notifications

1. Expand GET /notifications
2. Click Try it out
3. Enter orderId = 1 or customerId = 101
4. Click Execute
5. Verify: list of notifications sent

#### Step 12 — View Config

1. Expand GET /config
2. Click Try it out then Execute
3. Verify: list of config entries (business rules)

### 10.3 Swagger Testing Checklist

| # | Test | Endpoint | Pass Criteria |
|---|---|---|---|
| 1 | Seed data loads | GET /customers | Non-empty list |
| 2 | Order created | POST /orders | 201 + healthScore present |
| 3 | Risk scored | POST /orders | riskLevel + scoreReasons present |
| 4 | Journey routed | POST /orders | journey + expectedLoss present |
| 5 | Courier event accepted | POST /courier/events | 200 + status updated |
| 6 | Rescue triggered | POST /courier/events | status = RESCHEDULED |
| 7 | Recovery manual | POST /recovery/{id}/reschedule | status = RESCHEDULED |
| 8 | Audit recorded | GET /audit?orderId= | Entries present |
| 9 | Trust ledger works | GET /customers/{id}/trust | Balance + ledger |
| 10 | Demo runs | POST /demo/run | 5 steps returned |
| 11 | Simulator runs | POST /simulator/run | SimulationReport |
| 12 | Simulator resets | POST /simulator/reset | ResetResponse |
| 13 | Metrics stub | GET /metrics/summary | Counts + note |
| 14 | Notifications | GET /notifications | List returned |
| 15 | Config view | GET /config | Config entries |

### 10.4 Common Issues & Fixes

| Issue | Cause | Fix |
|---|---|---|
| 500 on POST /orders | Invalid customerId or productId | Run GET /customers and GET /products first |
| 400 on POST /courier/events | Invalid status enum | Use one of the 6 valid statuses |
| Empty audit log | No decisions made yet | Create an order first |
| Simulator hangs | DB not running | Run docker compose up -d |
| Swagger UI blank | Backend not started | Run mvn spring-boot:run |

---

## 11. How to Run (Quickstart)

### Prerequisites

- Java 17
- Maven
- Node.js (16+)
- Docker

### Step 1 — Start Database

```
docker compose up -d
```

### Step 2 — Start Backend

```
mvn spring-boot:run
```

### Step 3 — Start Frontend (Optional)

```
cd frontend
npm install
npm run dev
```

### Step 4 — Open

| Service | URL |
|---|---|
| Swagger API Docs | http://localhost:8080/swagger-ui.html |
| Frontend Dashboard | http://localhost:5173/ |

### Step 5 — Run the Demo

- Play the demo: POST /demo/run (walks the 5-step demo)
- Batch simulations: POST /simulator/run to compare margins

---

## 12. Testing

### Run Unit Tests

```
mvn test
```

### What's Covered

- Unit tests use in-memory fakes (testsupport/Fakes.java)
- Service-level coverage of business logic
- Scoring, routing, profit calculation, rescue logic

### Pre-Merge Checklist

```
mvn clean test
```

And verify /demo/run still works.

---

## 13. Contributing

### Guidelines

- Follow ownership and contracts in PROJECT_CONTEXT.md
- Branch per feature: feature/<desc>
- Rebase before push
- Run mvn clean test before merging
- Merge only if mvn clean test passes and /demo/run remains functional

### Ownership

- Follow the ownership table in PROJECT_CONTEXT.md (teams A/B)
- Money math is centralized in ProfitCalculator — do not spread financial logic elsewhere
- OrderService.createAndProcess is the single entry point for new orders

---

## 14. TODO / Known Gaps

| # | Item | Status | Notes |
|---|---|---|---|
| 1 | Security (JWT / API key) for admin endpoints | TODO | ConfigController notes this as M10 TODO |
| 2 | Metrics aggregation | Stub | MetricsController is a stub; full revenue/margin/recovery aggregation is TODO (M11b) |
| 3 | JourneyExecutor.handleReply | Partial | TODOs for certain journey types (see PROJECT_CONTEXT.md) |
| 4 | Authentication | Not implemented | Ships without JWT; open APIs for demo only |

---

## 15. Appendix — Useful File Locations

| Area | Path |
|---|---|
| Score & Routing | src/main/java/com/rtoshield/scoring and src/main/java/com/rtoshield/routing |
| Simulator | src/main/java/com/rtoshield/simulator |
| Demo Flow | src/main/java/com/rtoshield/demo/DemoController.java |
| Entities | src/main/java/com/rtoshield/domain |
| Repositories | src/main/java/com/rtoshield/repo |
| Config | src/main/java/com/rtoshield/config |
| Intake | src/main/java/com/rtoshield/intake |
| Delivery | src/main/java/com/rtoshield/delivery |
| Recovery | src/main/java/com/rtoshield/recovery |
| Trust | src/main/java/com/rtoshield/trust |
| Audit | src/main/java/com/rtoshield/audit |
| Confirmation | src/main/java/com/rtoshield/confirmation |
| Metrics | src/main/java/com/rtoshield/metrics |
| Common | src/main/java/com/rtoshield/common |
| Tests | src/test |
| Frontend | frontend/ |
| DB Config | docker-compose.yml |
| Project Context | PROJECT_CONTEXT.md |

If anything in this README seems out of date, open the code paths referenced above. For precise DTO shapes and examples, use the Swagger UI which is generated from controller signatures.

---

## How to Evaluate Quickly (5–10 Minutes)

1. Run docker compose up -d, start backend, open Swagger.
2. Exercise POST /demo/run.
3. Start frontend and open the dashboard; run a small simulation (POST /simulator/run).
4. Run unit tests (mvn test) to see service-level coverage.

---

## Contact

Project built as a demo — check PROJECT_CONTEXT.md for module responsibilities and signatures.

Repository: https://github.com/Kumar-Aman7974/rto-shield

Generated README for interview evaluation.
