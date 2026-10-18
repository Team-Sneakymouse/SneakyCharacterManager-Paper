# Scope and testing

## Choose by risk, not line count

Assess observable behavior, blast radius, reversibility, and existing coverage. A one-line permission bypass, schema edit, or thread-safety change is not “trivial.” The following are defaults, not compulsory ceremony:

- **Mechanical / low risk:** documentation, formatting, or an explicitly requested bootstrap-call comment with narrow known effects. Inspect the exact diff and use cheap relevant checks. New tests are not automatic. Existing CI can provide additional evidence.
- **Bounded logic / ordinary risk:** behavior changes with limited blast radius and existing or easily extended coverage. Add or update focused tests when behavior is new or existing coverage is insufficient. Prefer existing test infrastructure; run relevant tests and build checks.
- **High risk:** permissions, persistent data, migrations, concurrency, integrations, production configuration, or broad changes. Establish acceptance criteria, regression coverage, integration evidence, and rollback expectations before implementation. Ask when adequate assurance exceeds authorized scope.

### Classification examples

| Change | Tier | Why |
|--------|------|-----|
| Comment out `saveDefaultConfig()` when explicitly requested | Mechanical | Narrow, known startup effect; still report the behavior change |
| Docs-only or formatting-only edit | Mechanical | No runtime behavior |
| Fix incorrect bounded calculation; unit harness exists | Ordinary | Behavior change; focused test if coverage is thin |
| Rename a private method or extract a small pure helper with no behavior change | Ordinary | Prefer existing compile/tests; no new harness |
| Add a non-security config key with local defaults | Ordinary | Bounded behavior; extend tests if the key changes logic |
| Dependency patch within the same major, no API change intended | Ordinary | Run build/relevant tests; escalate if public behavior shifts |
| Remove or weaken a permission check | High | Authorization blast radius, regardless of diff size |
| Change async/thread ownership or shared mutable state | High | Concurrency and data races |
| Schema, migration, or persistent data shape | High | Durability and rollback |
| Production deploy config, secrets wiring, or CI publish triggers | High | Live blast radius |

When unsure between ordinary and high, classify high and ask for assurance scope. When unsure between mechanical and ordinary, classify ordinary.

## Separate two decisions

1. **Implement tests?** Identify the behavior and coverage gap. Reuse existing coverage when sufficient; do not add redundant assertions merely because production code changed.
2. **Run checks?** Select existing tests, build, static checks, or integration checks relevant to the risk. Running existing tests does not imply permission to implement new test infrastructure.

New logic and reproducible bugs normally merit focused tests. Where feasible, demonstrate that a new regression test catches the bug before applying the fix. TDD is a useful method, not a universal condition for editing source code.

## Scope boundaries

- Do not create a new harness, mock architecture, suite-wide refactor, or unrelated coverage without approval.
- Ask before verification would materially expand the task, require privileged resources, or incur significant unexpected time/cost. Ordinary relevant checks do not need repeated approval.
- If the user says “no test changes,” keep test files unchanged. Use proportionate existing checks where appropriate and report the gap. For high-risk work, seek agreement on sufficient assurance rather than pretending the restriction makes the change safe.
- If a repo currently mandates tests for every behavior change, surface the conflict. Adoption must deliberately replace conflicting rules; this policy does not bypass them.

## Example: template startup

Request: comment out `saveDefaultConfig()`, commit directly to main, no new tests.

Expected scope: comment out that call only; confirm the diff is limited to it; perform a cheap diff check; commit/push as authorized and verify the remote change. Explain that startup no longer intentionally creates the default config. Do not build a new regression suite or claim a live-server test took place. This exception does not generalize to dangerous one-line edits.
