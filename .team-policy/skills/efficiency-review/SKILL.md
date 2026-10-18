---
name: efficiency-review
description: "Use when reviewing runtime efficiency or hot paths. Assess Minecraft/Paper and JVM costs with scoped, evidence-backed findings."
version: 0.1.0
author: Team-Sneakymouse
metadata:
  status: optional
---

# Efficiency review

Optional skill; not installed by this repository.

## Overview

Review where work scales, how often it runs, and which thread pays for it. Prioritize tick latency, client frame time, allocation, memory retention, and network/database load over cosmetic micro-optimizations. Apply the shared [scope/testing policy](../../policy/scope-and-testing.md) and [core policy](../../policy/core.md); this procedure does not expand authorization or impose new tests.

## When to use

Use for an explicit efficiency review, a suspected bottleneck, or changed hot paths: frequent events, low-interval scheduled tasks, entity/block workloads, rendering, I/O, or large collections. For unrelated tiny edits, do not launch a repository-wide audit. Review-only means findings, not implementation.

## Process

1. **Bound the review.** Identify the requested code paths, actual Paper/Minecraft/JVM versions, runtime thread model, and available evidence. Read local architecture constraints. Done when the review target and evidence limits are clear; ask only when missing information materially changes the review.
2. **Map amplification.** Trace entry points and callees. Record cadence/event frequency, candidate population, nesting, viewer/recipient fan-out, thread ownership, and synchronous versus deferred cost. Include startup bursts and cumulative work across instances/plugins. Done when each relevant hot path has a cost model, even if input sizes are explicitly unknown.
3. **Inspect relevant traps.** Use the checklist below, skipping unrelated areas. Follow convenience APIs into the pinned implementation when traversal or scheduling semantics matter. Done when applicable areas are covered and unsupported implementation assumptions are marked unknown.
4. **Rank evidence.** Distinguish measured bottlenecks, source-backed scaling risks, and hypotheses needing measurement. Prefer existing profiler/metrics evidence; do not provision a server, add instrumentation dependencies, or run a disruptive load test without approval. Done when each finding has a code location and concrete reasoning rather than a generic warning.
5. **Report proportionally.** Give prioritized findings, smallest safe remedies, behavior risks, and a verification approach. No automatic rewrites, test harnesses, or speculative optimization. Done when all in-scope areas are accounted for and measurement gaps are explicit.

## Minecraft / Paper checklist

### Frequent work and event amplification

- Inspect every-low-number-of-ticks tasks and common listeners such as movement, damage, interactions, and inventory events. Check for early exits, cancelled-event handling consistent with intended behavior, and whether an actual relevant state change occurred.
- Estimate cost as cadence × active instances × candidate population × per-item work; add viewer fan-out and nested scans. Cheap per-player work can become expensive when repeated for every entity or recipient.
- Prefer event-driven invalidation or dirty-state updates where semantics allow. Consider shared scheduling, staggering, bounded batches, and skipping empty/inactive workloads. Preserve deadlines and responsiveness; spreading work may introduce visible latency.

### Entity retrieval and spatial queries

- Avoid retrieving all entities and then filtering class in a loop. Request the narrowest candidate set that the target implementation can actually retrieve efficiently.
- A class-filtered API or result type does **not** prove class-indexed traversal. `getNearbyPlayers` does not fulfill the early class-selection requirement merely by returning players: convenience implementations can collect regional entities before filtering. Trace the implementation for the deployed version, not only the method signature.
- In marker-heavy worlds, compare a world-scoped player-list scan against regional all-entity traversal. `World.getPlayers()` plus bounds checks can make candidate work depend on player population; it is not universally faster for every density or radius. Check whether special/NPC player representations are included.
- Consider a maintained typed/spatial index only when justified; account for movement, world changes, unloads, removals, synchronization, and index maintenance cost.
- Preserve hitbox overlap versus point distance, sphere versus axis-aligned box, world scope, boundary inclusivity, and special-entity behavior. A squared-distance shortcut is not automatically behavior-identical at rounding/edge boundaries.

### Displays and client rendering

- Review plugin-controlled display-entity view range/render distance, visibility, entity count, model complexity, and per-viewer update cadence. Distinguish client rendering cost from network delivery cost; verify API units and implementation for the target version. Measure client FPS as well as server timing.
- Audit large spawn bursts and entity churn. Prefer reuse/differential updates where safe; bound spawn/removal work and viewer synchronization. Clean up on disable, world unload, and ownership removal; avoid duplicate restoration on reload.
- Ask whether visual interpolation, transformations, resource-pack models, or client-side presentation can replace repeated server computation/packets. Keep authoritative gameplay, permissions, economy, and collision decisions server-controlled. Account for client compatibility and resource-pack costs; do not simply move lag from server to clients.

### Blocks, chunks, and main-thread work

- Identify large block edits, physics/light updates, and neighbor propagation. Budget work across ticks when allowed; do not disable physics blindly or move Bukkit world mutation to arbitrary worker threads.
- Check hidden chunk loads/generation, synchronous chunk access, excessive chunk tickets, and retained chunks. Bulk entity/block queries can trigger more work than the loop itself suggests. Validate async chunk APIs and ownership for the pinned platform.
- Check world/entity ownership on the actual server scheduler (including region-threaded platforms where applicable). “Async” is not permission to access Bukkit state concurrently.

### I/O and communication

- No blocking web requests, DNS, database calls, filesystem reads/writes, sleeps, lock waits, or future `join/get` on the tick/owning thread. Trace wrappers and callbacks; a future-returning API can still block during admission or result handling.
- Snapshot necessary state on its owning thread; perform eligible work in a bounded executor; marshal application back safely. Recheck session, lifecycle/generation, and request validity before applying stale results.
- Bound queues, concurrency, response sizes, timeouts, and retries. Handle overload and cancellation; blindly retrying uncertain writes can duplicate side effects.
- Audit packet/particle/sound/metadata fan-out: updates × recipients × payload. Send only changed state to intended viewers; coalesce when semantics permit. Be careful with APIs where a null recipient list broadcasts rather than selecting nobody.

## Java / Kotlin / general checklist

- **Algorithmic scaling:** identify nested scans, repeated sorting, list membership inside loops, and O(N²) or worse paths. State what N represents and realistic bounds. An O(players × entities) path needs both dimensions explained; small bounded quadratic work is not automatically a defect.
- **Repeated backend work:** N+1 database/HTTP queries, repeated auth/config lookups, full-table/global reads, missing pagination/indexes, and duplicate concurrent requests. Prefer bounded queries and batching where correctness allows; preserve ordering and write semantics.
- **Allocation/GC:** hot-loop collection copies, Kotlin `filter/map/groupBy` chains, boxing, temporary locations/vectors/components, repeated serialization, and string construction. A sequence is not automatically faster; compare size and usage before recommending one.
- **Repeated computation:** regex compilation, parsing/formatting, reflection, registry/config lookups, expensive getters, and repeated distance/sort work. Precompute stable values or update on change when justified; don't trade a cheap operation for an invalidation problem.
- **Memory retention:** unbounded caches/maps/history, player/entity references, listener registrations, scheduled tasks, queued closures, and buffers. Look for TTL/size bounds and cleanup on quit, unload, disable, and reload. Check key lifecycle and stale entries, not just collection type.
- **Concurrency:** unbounded thread/task creation, blocking shared executors, lock contention, thread ping-pong, and callbacks while holding locks. Moving work off-thread does not eliminate its CPU/memory cost or synchronization risk.
- **Logging:** repeated per-tick/event logs, eager debug-string construction, large exception storms, and sensitive payloads. Rate-limit/coalesce without hiding operational failures.
- **Caches and indexes:** require clear ownership, invalidation, bounds, and consistency expectations. Avoid stale authorization/economy state and global cache invalidation on every event.

## Evidence and output

For each actionable finding, report:

- **Location and path:** file/line, caller, trigger/cadence, and owning thread.
- **Cost:** candidate set, complexity, amplification, or retained resources. Label assumptions about population/frequency.
- **Evidence/confidence:** measured result, source-backed risk, or hypothesis; include target implementation provenance when relied upon.
- **Impact/priority:** affected server tick latency, client FPS, network, backend, memory, or startup. Unknown impact is not measured severity.
- **Smallest remedy:** expected trade-offs, behavior boundaries to preserve, and suggested verification.

Use an existing appropriate profiler (for example, an available server sampling profiler or JVM profiler) only within authorized scope. For performance comparisons, record scenario, entity/player counts, versions, warmup, duration, and comparable conditions. Inspect tail/worst-case tick latency and allocation as relevant, not only averages. Do not invent improvement percentages; mocks, static guards, and compilation cannot prove live performance.

If no actionable finding is supported, say so and name unmeasured risks. Do not manufacture findings to fill a checklist.

## Common pitfalls

- Treating `getNearbyPlayers` or a typed result as proof of an early typed/indexed query.
- Classifying by line count instead of workload and blast radius.
- Recommending “make it async” without ownership, queue bounds, or stale-result fencing.
- Assuming reduced display render distance removes every entity cost.
- Replacing an exact spatial/recipient rule with a superficially cheaper but different one.
- Expanding a review into implementation or new testing infrastructure without permission.

## Completion criteria

- In-scope hot paths have cadence, candidate/fan-out, thread, and evidence classification.
- Applicable entity, rendering, block/chunk, I/O, algorithm, and JVM risks are covered or explicitly out of scope.
- Findings are located, prioritized, and actionable; measurements are distinguished from hypotheses.
- Proposed changes preserve behavior and ownership constraints, with proportionate verification.
- No production settings, code, profiles, or dependencies changed merely to perform the review.
