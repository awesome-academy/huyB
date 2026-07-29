# Phase 07 — Integrate Publisher into TourService (T2.9)

## Context links
- Spec: `../spec/t2-messaging.md`
- Plan: `plan.md`
- Target: `service/impl/TourServiceImpl.java` (`create` ~line 75, `update` ~line 114)
- Depends on: phase 06 (publisher + message DTO)

## Overview
- **Priority:** P2
- **Status:** completed
- **Depends on:** 06
- Publish a tour-promotion message when a tour is created ACTIVE or updated to ACTIVE. Surgical edit to an existing service; no new files.

## Key insights
- `create()` builds a `Tour` and, after `tourRepository.save(tour)`, `tour.getStatus()` reflects the persisted status (fallback is `ACTIVE` when request status is null — so create defaults to ACTIVE and WILL publish unless request explicitly `INACTIVE`).
- `update()` sets status only when `tourRequest.getStatus() != null`. Publish when the resulting status is `ACTIVE`. Simplest correct rule per spec: publish if `tourRequest.getStatus() == TourStatus.ACTIVE`.
- Both methods `@Transactional`; publish AFTER `save` returns so the message carries the persisted `tour.getId()` and title.
- Inject via existing `@RequiredArgsConstructor`: `private final TourPromotionPublisher tourPromotionPublisher;`
- Edge case to avoid over-notifying: on `update`, an already-ACTIVE tour saved again re-publishes. Spec T2.9 says "create/activate" — acceptable to publish on any update-to-ACTIVE for Day 2 (YAGNI on transition-detection). Note it as a known behavior.

## Requirements
**Functional**
- `create()`: if saved tour status == ACTIVE → `publisher.publishNewTour(new TourPromotionMessage(tour.getId(), tour.getTitle()))`.
- `update()`: if `tourRequest.getStatus() == ACTIVE` → publish with updated tour id/title.

**Non-functional**
- Additive; no change to existing create/update validation.

## Architecture / data flow
```
create(req)  → save(tour) → if tour.status==ACTIVE → publisher.publishNewTour(msg{id,title})
update(id,req) → save(tour) → if req.status==ACTIVE → publisher.publishNewTour(msg{id,title})
```
Downstream: fanout → notification broadcast + log (phase 06).

## Related code files
**Modify**
- `src/main/java/com/sunasterisk/bookingtours/service/impl/TourServiceImpl.java`

## Implementation steps
1. Add imports for `TourPromotionPublisher` and `TourPromotionMessage`.
2. Add field `private final TourPromotionPublisher tourPromotionPublisher;`.
3. In `create()`, replace the `return tourRepository.save(tour);` tail with: save to a local `Tour saved = tourRepository.save(tour);`, then `if (saved.getStatus() == TourStatus.ACTIVE) tourPromotionPublisher.publishNewTour(...);`, then `return saved;`.
4. In `update()`, similarly capture `Tour saved = tourRepository.save(tour);`, then `if (tourRequest.getStatus() == TourStatus.ACTIVE) tourPromotionPublisher.publishNewTour(...);`, `return saved;`.
5. (Optional DRY) private helper `publishIfActive(Tour tour)` shared by both call sites.
6. `mvn compile`; run app (RabbitMQ up); create an ACTIVE tour via admin flow → verify broadcast notifications + log; update a tour to ACTIVE → same.

## Todo
- [x] Import + inject `TourPromotionPublisher`
- [x] Publish on ACTIVE in `create()` after save
- [x] Publish on ACTIVE in `update()` after save
- [x] (Optional) `publishIfActive` helper
- [x] `mvn compile` clean
- [x] Manual: create/activate ACTIVE tour triggers both consumers

## Success criteria (T2.9)
- Admin create ACTIVE tour → both RabbitMQ consumers triggered (rows + log).
- Admin update tour to ACTIVE → both consumers triggered.
- Create/update INACTIVE tour → no publish.
- Existing tour tests still pass; `mvn test` green.

## Risk assessment
| Risk | L | I | Mitigation |
|------|---|---|------------|
| Re-publish on every update-to-ACTIVE (over-notify) | Med | Low | Accepted for Day 2; transition-detection deferred (YAGNI). Documented |
| Publish before tx commit → broadcast for uncommitted tour | Low | Med | Rare rollback post-save; acceptable dev scope |
| Constructor injection breaks compile | Low | High | `@RequiredArgsConstructor` regenerates — verify compile |
| RabbitMQ down at create time → `publishNewTour` throws inside tx → tour create fails | Med | High | Dev prerequisite documented (phase 05). If unacceptable, wrap publish in try/catch + log (decide at impl; default: let it fail-fast in dev) |

## Security considerations
- Admin-only entry points (unchanged). Message content server-generated.

## Rollback
- Remove injected field + the two publish blocks (and helper). Single-file revert.

## Next steps
- RabbitMQ pipeline complete. Both tracks done → run full `mvn test`, update `docs/project-changelog.md` and `docs/development-roadmap.md` (Day 2 messaging complete).
