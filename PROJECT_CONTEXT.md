# PROJECT_CONTEXT (read this before you code, update it when you change a contract)

## The 5-step demo (this is what we are graded on)
1. A COD order is scored with reasons -> 2. routed to the right confirmation flow -> 3. a delivery risk happens -> 4. a recovery action fires -> 5. dashboard + simulator show a better margin outcome.

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
| domain, repo, config, common, pom.xml, application.yml, docker-compose.yml | Person A (Person B asks A for changes) |
| intake, scoring, routing, trust, audit, simulator | Person A |
| confirmation, delivery, recovery, metrics, demo, static/dashboard.html, Dockerfile, Postman | Person B |

## Modules and status
Mark TODO / DOING / DONE here when you start and finish.

| # | Module | Owner | Status |
|---|---|---|---|
| M0 | Setup + skeleton | both | DONE |
| M1 | Data + seed + base config | A | DONE (seed data, ConfigService, GET /customers /products /couriers /courier-stats) |
| M2 | Order intake | A | DONE: POST /orders, store webhook, GET /orders. Address quality now depends on the address text only |
| M3 | Order-health score | A | DONE (rules from config, reasons add up to the score) |
| M4 | Journey router + ProfitCalculator | A | DONE (7 journeys, profit-based choice, CourierRecommender). `chooseRescueAction` is still a stub (needed by M7) |
| M5 | Journey execution | B | TODO (stub in confirmation/JourneyExecutor) |
| M6 | Delivery events | B | **DONE** (delivery/DeliveryStateService, OrderLocker, 24 unit tests) |
| M7 | Rescue and recovery | B | TODO (stubs in recovery/) |
| M8 | Trust points | A | DONE (append-only ledger, count-once rule, writes an audit row) |
| M9 | Audit log + config API | A | DONE: GET /config, GET/PUT /config/{key}, POST /config/reset, GET /audit |
| M10 | Security (API key) | B | TODO, last. Must also protect /config/** and /audit/** |
| M11a | Simulator | A | TODO / in progress |
| M11b | Dashboard | B | TODO (/metrics/summary has simple counts) |
| M11c | Demo endpoint + packaging | B | /demo/run runs step 1 only |

## Contracts between you two (a signature change = tell the other person + edit this table)
| Method | Owner | Called by | State |
|---|---|---|---|
| OrderHealthScorer.score(Order) -> ScoreResult | A | OrderService | done |
| JourneyRouter.decide(Order, ScoreResult) -> DecisionOutcome | A | OrderService | done. May return the new journey PREPAID_SHIP |
| ProfitCalculator.pFail / expectedLoss / actionCost / evaluate | A | Router, RescueService | done |
| ProfitCalculator.chooseRescueAction(Order) -> ActionType | A | RescueService | stub |
| TrustPointsService.recordEvent(customerId, orderId, TrustEventType) | A | DeliveryStateService, RescueService | done (also writes the audit row) |
| PincodeStatsService.recordResult(courierId, pincode, success) | A | DeliveryStateService | done |
| PincodeStatsService.refusalRate / courierSuccessRate | A | Scorer, Router | done |
| AuditService.record(orderId, decision, reason, inputs) | A | everyone | done |
| ConfigService.get / getInt / getDouble / getDecimal | A | everyone | done (every new key in ConfigKeys also needs a rule in ConfigRules) |
| OrderService.createAndProcess(CreateOrderRequest) | A | controllers, Simulator, DemoController | done |
| OrderService.get(id) | A | controllers | done |
| JourneyExecutor.execute(Order, DecisionOutcome) | B | OrderService | stub (must treat PREPAID_SHIP like FULL_COD: status CONFIRMED) |
| JourneyExecutor.handleReply(orderId, text) | B | WhatsAppWebhookController | stub |
| NotificationSender.send(Order, channel, message) | B | JourneyExecutor, RescueService | done (mock) |
| DeliveryStateService.handle(CourierEventRequest) -> Order | B | CourierEventController, Simulator (A) | **done (M6)** |
| OrderLocker.lock(orderId) -> Optional<Order> | B | DeliveryStateService | done (row lock, SELECT ... FOR UPDATE) |
| RescueService.onFailedAttempt(Order, DeliveryEventType) | B | DeliveryStateService | stub (M7) |
| RescueService.markRto(Order, RtoReason) | B | RescueService (3rd failed attempt) | stub (M7) |

Other changes everyone should know:
- `Order` now has `expectedLoss` and `actionCost`, saved when the order is routed.
- `Journey` has a 7th value, `PREPAID_SHIP`.

## Delivery events (M6): the rules
Endpoint: `POST /courier/events` with `{"orderId": 1, "status": "OUT_FOR_DELIVERY", "attemptNo": 1}`. `attemptNo` is optional.
Status values: OUT_FOR_DELIVERY, DELIVERED, CUSTOMER_UNREACHABLE, REFUSED, WRONG_ADDRESS, DELAYED.

| Event | Allowed when order status is | New status | Side effects |
|---|---|---|---|
| OUT_FOR_DELIVERY | CONFIRMED, SHIPPED, DELIVERY_FAILED, RESCHEDULED | OUT_FOR_DELIVERY | none |
| DELIVERED | OUT_FOR_DELIVERY | DELIVERED | trust DELIVERED (+ PAID_ONLINE if PREPAID), pincode stats success |
| CUSTOMER_UNREACHABLE / REFUSED / WRONG_ADDRESS | OUT_FOR_DELIVERY | DELIVERY_FAILED, attemptCount + 1 | trust UNREACHABLE / REFUSED / FAKE_ADDRESS, pincode stats failure, then `RescueService.onFailedAttempt` |
| DELAYED | CONFIRMED, SHIPPED, OUT_FOR_DELIVERY, DELIVERY_FAILED, RESCHEDULED | unchanged | none (only the event and lastEventAt are recorded) |

Every other combination returns **409** and saves nothing. An unknown order returns 404, `attemptNo` below 1 returns 400.

Decisions that M7 and the simulator rely on:
1. `order.attemptCount` = number of FAILED attempts. The attempt in progress is `attemptCount + 1`.
2. A repeat of the same (order + attempt + status) is ignored: no event, no points, no audit row, the order is returned unchanged. Without `attemptNo`, a repeated failure is still recognised, also after M7 moved the order to RESCHEDULED. A retried OUT_FOR_DELIVERY is only recognised if `attemptNo` is sent, so the courier should send it for retries.
3. If `attemptNo` is sent it must equal the current attempt, otherwise 409.
4. M6 records the trust event on EVERY failure. TrustPointsService counts it once per (order + event type). M7's `markRto` may call `recordEvent` again for the RTO reason: a same-type call is a safe duplicate. COURIER_DELAY has no trust event.
5. M6 never sets RESCHEDULED or RTO. That is M7: after a failure M7 sets RESCHEDULED (attempts 1 and 2) or RTO (attempt 3, via `markRto`). While the stub M7 is in place the order stays DELIVERY_FAILED, and a new OUT_FOR_DELIVERY is still allowed.
6. Journeys that wait for the customer (OTP_CONFIRM, PARTIAL_COD, ...) are AWAITING_CONFIRMATION until M5 confirms them. Courier events on them return 409. This is correct.
7. The whole call is one transaction. If trust, pincode stats or rescue throws, the event is not saved either.
8. Audit rows written: `DELIVERY_<event>` with the reason `"REFUSED on attempt 1: OUT_FOR_DELIVERY -> DELIVERY_FAILED"`.

## Rules for working in sync
- A stub returns safe dummy values. Code against the stub. Never write your own copy of your partner's logic.
- Money maths lives only in ProfitCalculator. Every number (cost, points, threshold) is a key in ConfigKeys, never hardcoded.
- Only OrderService.createAndProcess creates orders. Simulator and demo use it too.
- main always runs. Merge only when POST /demo/run still works and `mvn clean test` passes.
- Own branch each (feature/a-..., feature/b-...), git pull --rebase before pushing, push every hour.
- Never "Reformat project" in IntelliJ.
- Repeated events: a trust event counts once per (order + event type), and a courier event once per (order + attempt + status).
- Tests use in-memory fakes from `testsupport/Fakes.java`, not Mockito. Services that other tests fake by subclassing (RescueService, TrustPointsService, PincodeStatsService): if you add a constructor field, fix the `new ...(null, ...)` line in the tests that use it.
- Check-in every 3 hours: what is done, what is blocking, did any signature change?

## Checkpoints
Hour 2 skeleton frozen (tag contracts-v1) | Hour 6 both parts run on stubs | Hour 12 first end-to-end run | Hour 18 feature freeze | Hour 22 code freeze (tag release-1)
