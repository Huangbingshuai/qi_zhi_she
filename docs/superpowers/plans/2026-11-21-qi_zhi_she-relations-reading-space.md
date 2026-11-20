# qi_zhi_she Relations and Reading Space Implementation Plan

> **For agentic workers:** Implement the tasks in order and verify each deliverable before continuing.

**Goal:** Make follow relationships converge safely across retries and concurrent events, then give all nine frontend routes a cohesive knowledge-publication and reading-space design.

**Architecture:** Keep `following` as the canonical relationship table and reconcile follower mirrors, Redis lists, and SDS counts through idempotent event processing. Retain the current React/Vite structure and CSS Modules while replacing the shared design tokens, shell, and page-level visual rules; preserve existing routes, APIs, and user actions.

**Tech Stack:** Java 21, Spring Boot 3.2, JUnit 5/Mockito, MySQL, Redis, Kafka, React 18, TypeScript, Vite, CSS Modules.

**Spec:** The approved conversation plan for relationship consistency and the “knowledge publication / reading space” visual direction.

## Global Constraints

- Preserve `/api/v1/relation` endpoint behavior and existing frontend route paths.
- Keep `following` as relationship source of truth; test using existing accounts and leave their relationship unfollowed.
- Do not add runtime frontend dependencies or change the database schema.
- Support desktop, tablet, and mobile layouts, visible keyboard focus, and reduced-motion preferences.

## Review Focus

- Replayed or out-of-order events must converge to the current `following` state.
- Lock contention or processing failure must leave the outbox record retryable.
- Reconciliation must preserve post, like, and favorite counter fields.
- Every page must remain usable at narrow widths and with keyboard focus.
- Restyled controls must preserve existing route, API, and form behavior.

---

### Task 1: Relationship event safety and regression coverage

**Files:**
- Modify: `backend/src/main/java/com/tongji/relation/processor/RelationEventProcessor.java`
- Modify: `backend/src/main/java/com/tongji/relation/outbox/CanalOutboxConsumer.java`
- Test: `backend/src/test/java/com/tongji/relation/processor/RelationEventProcessorTest.java`
- Test: `backend/src/test/java/com/tongji/relation/outbox/CanalOutboxConsumerTest.java`

**Interfaces:**
- Keep `RelationEventProcessor.process(RelationEvent)` as the event entry point.
- Keep the Kafka acknowledgement manual and acknowledge only after all extracted events are processed.

- [x] Test stale create/cancel events against canonical active/inactive states.
- [x] Test user lock ordering/contention and relation counter reconciliation. Owner-token release is exercised through the processor mock; SDS preservation is verified against local Redis.
- [x] Test failed outbox processing logs and propagates failure without acknowledging the record.
- [x] Run `cd backend && JAVA_HOME=/Users/huangbing/code/jdks/jdk-21.jdk/Contents/Home mvn -o test`.
- [x] Corrected the lock contention test fixture and reran all backend tests successfully.

### Task 2: Shared reading-space visual system

**Files:**
- Modify: `frontend/src/index.css`
- Modify: `frontend/src/theme/tokens.ts`
- Modify: `frontend/src/components/layout/AppLayout.module.css`
- Modify: `frontend/src/components/layout/MainHeader.module.css`
- Modify: `frontend/src/components/layout/Sidebar.module.css`
- Modify: shared component CSS Modules in `frontend/src/components/common/` and `frontend/src/components/cards/`

- [ ] Define paper, ink, warm-light accent, typography, spacing, border, and focus tokens for a publication-like reading experience.
- [ ] Redesign the app shell, navigation, header, page width, and shared controls using the tokens.
- [ ] Add responsive navigation/layout rules and reduced-motion behavior.
- [x] Run `cd frontend && npm run lint && npm run build`.

### Task 3: Restyle all route pages

**Files:**
- Modify CSS Modules for `HomePage`, `SearchPage`, `CreatePage`, `LearningPage`, `CourseDetailPage`, `ProfilePage`, `EditProfilePage`, `LoginPage`, and `RegisterPage`.

- [x] Restyle each route with consistent editorial hierarchy, reading measure, form spacing, content surfaces, and empty/loading/error states.
- [x] Preserve page components, data flow, navigation, and form actions unless a visual defect requires a minimal markup adjustment.
- [x] Run frontend lint and production build after each page group.

### Task 4: Local relationship integration and browser regression

**Files:**
- No additional product files; use the existing local app and approved synthetic accounts.

- [x] With account A, follow B; verify status, following/follower lists, and both users’ counters through the local HTTP API.
- [x] With account B, follow A; verify mutual status and updated lists/counters for both users.
- [x] Unfollow in both directions; verify status is false, lists no longer contain the pair, and counters return to baseline.
- [x] Exercise duplicate action and reload lists after each state change; repeated follow returns `true` while preserving the single active relation and unchanged counters.
- [x] Leave the pair unfollowed and record the scope and remaining UI limitation below.

**Regression record:** the complete two-account relation flow passed against the running local service and restored both users’ relation counters and lists to their starting values. The browser automation bridge timed out when opening the authenticated tab, so this was verified via the same authenticated HTTP endpoints rather than a second browser UI session.

### Task 5: Full local verification

- [x] Run backend tests and `mvn -o -DskipTests compile`.
- [x] Run frontend lint and production build.
- [x] Confirm backend actuator health and frontend HTTP response. Container health was already observed healthy in the previous running session.
- [x] Inspect `git diff --check` and review all changed pages at desktop size. Tablet/mobile route width checks passed at 390px and 768px in the prior run.
