# Agent-assisted development lifecycle (ADLC)

Stages are thinking checkpoints, not mandatory artifacts or separate agents.

1. **Understand:** identify the request, repository, local constraints, authorization, and acceptance criteria. Investigation-only tasks end with findings.
2. **Assess:** classify risk using [scope and testing](../policy/scope-and-testing.md). Decide whether tests need implementation separately from which checks to run. For tiny tasks this can be an internal assessment; do not produce a ceremony-heavy plan.
3. **Agree when needed:** ask only for missing decisions or material scope/risk expansion. Larger designs and migrations need approval before implementation. Record exclusions where useful.
4. **Implement:** make scoped changes, preserve unrelated work, and use relevant skills only. Escalate discovered blockers rather than silently adding infrastructure. For Team-Sneakymouse Paper plugins, follow [SneakyUtils](../policy/sneakyutils.md) when adding or copying shared util/manager code.
5. **Verify:** run proportional checks and inspect the diff against acceptance criteria. Identify evidence type and gaps. Do not confuse successful publication with deployment.
6. **Review and deliver:** self-review for correctness, secrets, and scope; publish through the authorized branch/PR path; read back remote state. Do not merge or deploy without permission. Keep the report concise. When a reusable policy gap appears, propose a reviewed change to shared rules rather than rewriting agent memory.

## Risk gates

- A low-risk edit needs no standalone plan, new tests, multi-agent review, or new infrastructure by default.
- A bounded behavior change needs focused acceptance and evidence, normally using existing tooling.
- High-risk work needs an agreed verification/rollback approach. A short diff does not waive this gate.

## Evidence for nontrivial work

For nontrivial work, the final report (chat and, when opening a PR, the PR body) should include:

- requested scope and risk tier
- changed files
- checks run and results
- checks not run and why
- remaining risks

For a tiny mechanical task, a concise final message is enough.
