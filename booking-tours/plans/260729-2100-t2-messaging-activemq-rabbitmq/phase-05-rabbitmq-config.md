# Phase 05 — RabbitMQ Dependency & Configuration (T2.6)

## Context links
- Spec: `../spec/t2-messaging.md`
- Plan: `plan.md`
- Depends on: none (parallel with 01, 02)

## Overview
- **Priority:** P2
- **Status:** completed
- **Depends on:** none
- Wire RabbitMQ (Docker broker): fanout exchange, two queues, two bindings, `RabbitTemplate` with JSON converter, `@EnableRabbit`. Infra only — publisher/listeners are phase 06.

## Key insights
- RabbitMQ is an EXTERNAL broker → requires Docker running before app boot, else the app fails to start (connection refused). Document the prerequisite prominently.
- `spring-boot-starter-amqp` brings `RabbitTemplate`, `@RabbitListener`, auto-declaration of `@Bean`-declared exchanges/queues/bindings on a running broker.
- `TourPromotionMessage` uses JSON, so register `Jackson2JsonMessageConverter` and set it on `RabbitTemplate` (and it is auto-used by listener container factory when present as a bean).
- Fanout exchange ignores routing keys → both bound queues receive every published message.
- Keep exchange/queue names as shared constants in `RabbitMQConfig` (referenced by publisher + listeners → DRY; `@RabbitListener(queues=...)` needs compile-time constants).

## Requirements
**Functional**
- `FanoutExchange "tour.promotions"` (durable).
- `Queue "tour.promo.notification.queue"` + `Queue "tour.promo.log.queue"` (durable).
- Two `Binding`s: each queue → the fanout exchange.
- `RabbitTemplate` + `Jackson2JsonMessageConverter` bean.
- `@EnableRabbit`.

**Non-functional**
- Connection params from properties (`localhost:5672`, guest/guest) — dev only.

## Architecture / data flow
Declarative topology only. On boot (broker up), Spring AMQP declares the exchange, queues,
and bindings idempotently. No message flow until phase 06/07.

## Related code files
**Modify**
- `pom.xml` — add `spring-boot-starter-amqp`
- `src/main/resources/application-dev.properties` — add RabbitMQ block

**Create**
- `src/main/java/com/sunasterisk/bookingtours/config/RabbitMQConfig.java`

## Implementation steps
1. Prerequisite (document at top of phase): start broker →
   `docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management`
   Management UI: http://localhost:15672 (guest/guest).
2. `pom.xml`: add `org.springframework.boot:spring-boot-starter-amqp`.
3. `application-dev.properties`, add:
   ```
   # RabbitMQ (Docker)
   spring.rabbitmq.host=localhost
   spring.rabbitmq.port=5672
   spring.rabbitmq.username=guest
   spring.rabbitmq.password=guest
   ```
4. Create `config/RabbitMQConfig.java`:
   - `@Configuration @EnableRabbit`.
   - Name constants: `TOUR_PROMOTIONS_EXCHANGE = "tour.promotions"`, `PROMO_NOTIFICATION_QUEUE = "tour.promo.notification.queue"`, `PROMO_LOG_QUEUE = "tour.promo.log.queue"`.
   - `@Bean FanoutExchange tourPromotionsExchange()` → `new FanoutExchange(TOUR_PROMOTIONS_EXCHANGE)`.
   - `@Bean Queue promoNotificationQueue()` → `new Queue(PROMO_NOTIFICATION_QUEUE)` (durable default).
   - `@Bean Queue promoLogQueue()` → `new Queue(PROMO_LOG_QUEUE)`.
   - `@Bean Binding b1(...)` → `BindingBuilder.bind(promoNotificationQueue()).to(tourPromotionsExchange())`.
   - `@Bean Binding b2(...)` → bind log queue to exchange.
   - `@Bean Jackson2JsonMessageConverter jacksonConverter()`.
   - `@Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv)` → set converter.
5. `mvn compile`; boot app with Docker RabbitMQ running; confirm connection log + exchange/queues visible in Management UI.

## Todo
- [x] Start RabbitMQ Docker container
- [x] Add `spring-boot-starter-amqp` to `pom.xml`
- [x] Add RabbitMQ block to `application-dev.properties`
- [x] Create `RabbitMQConfig` (exchange, 2 queues, 2 bindings, converter, template, constants)
- [x] `mvn compile` clean
- [x] App boots connected; topology visible in Management UI

## Success criteria (T2.6)
- App starts with RabbitMQ connected (no connection-refused).
- Exchange `tour.promotions` and both queues declared (verify in Management UI).
- `mvn compile` passes.

## Risk assessment
| Risk | L | I | Mitigation |
|------|---|---|------------|
| Docker not running → boot fails | Med | High | Document prerequisite; dev-only; fail-fast acceptable |
| Prod profile lacks RabbitMQ config → prod boot fails if starter present | Med | High | Scope config to dev; if prod must boot without broker, gate listeners/config by profile (open question — see below) |
| Queue durability mismatch on redeploy | Low | Low | Use consistent durable defaults; delete queues in UI if redeclaring with different args |

## Security considerations
- guest/guest only usable from localhost by default (RabbitMQ restriction) — fine for dev. Never ship guest creds to prod.

## Rollback
- Remove `RabbitMQConfig.java`, revert `pom.xml` + properties. Stop/remove Docker container. Delete queues/exchange in Management UI if needed.

## Unresolved question
- Does the app need to boot in `prod` WITHOUT RabbitMQ? If yes, gate `RabbitMQConfig` + listeners behind `@Profile("dev")` or a feature flag. Spec says dev-only → defaulting to no profile gating, config lives in `application-dev.properties`.

## Next steps
- Unblocks phase 06 (publisher + listeners use these beans/constants).
