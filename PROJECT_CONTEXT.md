# PROJECT_CONTEXT (read this before you code, update it when you change a contract)

## The 5-step demo (this is what we are graded on)
1. A COD order is scored with reasons  ->  2. routed to the right confirmation flow  ->  3. a delivery risk happens
->  4. a recovery action fires  ->  5. dashboard + simulator show a better margin outcome.

Anything that does not serve these 5 steps is out of scope.

## Scope fence
| Out | In |
|---|---|
| ML models | Rule-based scorer behind an interface |
| React / frontend framework | One HTML page + Chart.js |
| Real Shopify, courier, Twilio, payment gateway | Webhook-shaped endpoints + mock sender + mock courier |
| Flyway, JWT login, per-journey custom flows | Tables from entities, API key only, one generic journey executor |

The simulator uses invented data. We say so openly in the demo.

## Ownership (you only edit your own folders)

| Folder | Owner |
|---|---|
| `domain`, `repo`, `config`, `common`, `pom.xml`, `application.yml`, `docker-compose.yml` | **Person A** (Person B asks A for changes) |
| `intake`, `scoring`, `routing`, `trust`, `audit`, `simulator` | **Person A** |
| `confirmation`, `delivery`, `recovery`, `metrics`, `demo`, `static/dashboard.html`, Dockerfile, Postman | **Person B** |

## Modules and status

Mark TODO / DOING / DONE here when you start and finish.

| # | Module | Owner | Status                                                                                         |
|---|---|---|------------------------------------------------------------------------------------------------|
| M0 | Setup + skeleton | both | DONE (this repo)                                                                               |
| M1 | Data + seed + base config | A | DONE (seed data, ConfigService, GET /customers /products /couriers /courier-stats)             |
| M2 | Order intake | A | DONE (basic): POST /orders, store webhook, GET /orders. Sharpen address quality if time allows |
| M3 | Order-health score | A | DONE (stub in `scoring/RuleBasedHealthScorer`)                                                 |
| M4 | Journey router + ProfitCalculator | A | DONE (stub router; ProfitCalculator maths done, `chooseRescueAction` is a stub)                |
| M5 | Journey execution | B | TODO (stub in `confirmation/JourneyExecutor`)                                                  |
| M6 | Delivery events | B | TODO (stub in `delivery/DeliveryStateService`)                                                 |
| M7 | Rescue and recovery | B | TODO (stubs in `recovery/`)                                                                    |
| M8 | Trust points | A | DONE (stub `TrustPointsService.recordEvent`; tier maths done)                                  |
| M9 | Audit log + config API | A | AuditService DONE; `PUT /config/{key}` TODO                                                    |
| M10 | Security (API key) | B | TODO, last                                                                                     |
| M11a | Simulator | A | TODO (stub endpoints)                                                                          |
| M11b | Dashboard | B | TODO (`/metrics/summary` has simple counts)                                                    |
| M11c | Demo endpoint + packaging | B | `/demo/run` runs step 1 only                                                                   |

## Contracts between you two (a signature change = tell the other person + edit this table)

| Method | Owner | Called by | State |
|---|---|---|---|
| `OrderHealthScorer.score(Order) -> ScoreResult` | A | OrderService | stub |
| `JourneyRouter.decide(Order, ScoreResult) -> DecisionOutcome` | A | OrderService | stub |
| `ProfitCalculator.pFail / expectedLoss / actionCost` | A | Router, RescueService | done |
| `ProfitCalculator.chooseRescueAction(Order) -> ActionType` | A | RescueService | stub |
| `TrustPointsService.recordEvent(customerId, orderId, TrustEventType)` | A | DeliveryStateService, RescueService | stub |
| `PincodeStatsService.recordResult(courierId, pincode, success)` | A | DeliveryStateService | done |
| `PincodeStatsService.refusalRate / courierSuccessRate` | A | Scorer, Router | done |
| `AuditService.record(orderId, decision, reason, inputs)` | A | everyone | done |
| `ConfigService.get / getInt / getDouble / getDecimal` | A | everyone | done |
| `OrderService.createAndProcess(CreateOrderRequest)` | A | controllers, Simulator, DemoController | done |
| `OrderService.get(id)` | A | DeliveryStateService | done |
| `JourneyExecutor.execute(Order, DecisionOutcome)` | B | OrderService | stub |
| `JourneyExecutor.handleReply(orderId, text)` | B | WhatsAppWebhookController | stub |
| `NotificationSender.send(Order, channel, message)` | B | JourneyExecutor, RescueService | done (mock) |
| `DeliveryStateService.handle(CourierEventRequest) -> Order` | B | CourierEventController, Simulator (A) | stub |
| `RescueService.onFailedAttempt / markRto` | B | DeliveryStateService | stub |

## Rules for working in sync
1. A stub returns safe dummy values. Code against the stub. Never write your own copy of your partner's logic.
2. Money maths lives only in `ProfitCalculator`. Every number (cost, points, threshold) is a key in `ConfigKeys`, never hardcoded.
3. Only `OrderService.createAndProcess` creates orders. Simulator and demo use it too.
4. `main` always runs. Merge only when `POST /demo/run` still works.
5. Own branch each (`feature/a-...`, `feature/b-...`), `git pull --rebase` before pushing, push every hour.
6. Never "Reformat project" in IntelliJ.
7. Decision on repeated events: a trust event counts once per (order + event type), and a courier event once per (order + attempt + status).
8. Check-in every 3 hours: what is done, what is blocking, did any signature change?

## Checkpoints
Hour 2 skeleton frozen (tag `contracts-v1`) | Hour 6 both parts run on stubs | Hour 12 first end-to-end run |
Hour 18 feature freeze | Hour 22 code freeze (tag `release-1`)
