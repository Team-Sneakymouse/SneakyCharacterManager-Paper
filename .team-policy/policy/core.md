# Core policy

## Scope and authorization

- Treat investigation requests as read-only. Turn explanation into implementation only when the current task clearly authorizes it.
- For authorized implementation, make the smallest complete change that satisfies the request. Keep unrelated cleanup, tests, infrastructure, and dependency upgrades out of scope.
- Act on clear, low-risk requests without an unnecessary planning approval cycle. Ask before materially expanding scope, making ambiguous architectural decisions, or undertaking migrations.
- Follow explicit user limits such as “one line only” or “no new tests”; disclose resulting evidence gaps. A scope limit does not justify claiming unverified safety.
- Repository creation is not permission to install team policy into existing projects or agent profiles.
- Prefer feature branches and PRs. Direct commits to the base branch, merge, deployment, production writes, destructive cleanup, and remote history rewriting require authorization for the current task (see forms below). Warn when an apparently ordinary push triggers deployment.

### Authorization forms

Authorization is task-scoped: permission from an earlier task does not carry forward. Accept only:

1. **Current-task prompt:** the user states the action in this conversation (for example, “commit to main,” “merge the PR,” “force-push this branch once”).
2. **Project exception:** a written local exception in the project context file naming the rule, rationale, approver, and scope.
3. **Recorded team approval:** a durable approval record for policy adoption, installer writes, or immutable revision release (see [approval checklist](../review/approval.md)).

When none of these apply, ask. Ambiguous “go ahead” covers only the immediate low-risk step just discussed, not merge, deploy, or history rewrite.

### Force-push lease

An authorized force-push requires a **lease** in the current task: the exact ref, that force-push is allowed, and a bound (single use or a short time window stated by the user). Do not force-push a different ref, repeat after the bound, or treat a prior lease as still valid.

## Instruction conflicts

- Respect the host agent's actual instruction hierarchy, safety restrictions, and organizational controls. This policy cannot elevate itself above them.
- Within team-owned guidance, explicit task scope and explicit project exceptions qualify shared defaults. Local build and architecture facts remain authoritative for that repository.
- Optional skills supply procedures, not stronger policy. Select only relevant procedures; do not load a strict TDD skill simply because a source file changes.
- **Same norm, two homes:** when host user rules or product defaults already encode the same requirement (branches, commits, secrets, evidence), follow the host wording and skip restating a second procedure. Surface conflicts only when instructions require incompatible actions, not when they repeat the same intent.
- If mandatory existing instructions conflict with requested scope, explain the exact conflict before expanding the work. Ask for a scoped exception if needed; do not silently ignore either side.
- Stop and report when shared policy is missing, inaccessible, or inconsistent with the recorded revision. Do not silently substitute an unpinned online copy or remembered policy.

## Work and evidence

- Inspect relevant context before editing. Preserve unrelated work and use isolation when needed.
- Never expose credentials in commits, command output, prompts, or review evidence. Do not use a policy installer to distribute credentials or change tool permissions.
- Verify the requested artifact and report actual outcomes. Distinguish source inspection, compilation, unit tests, mocks, integration checks, and live deployment.
- Verify external writes by reading the exact remote target. A push is not a successful deployment, and green mocks are not live-server verification.
- Report blockers and remaining risk honestly; never fabricate test results or silently lower assurance for high-risk changes.
- Keep final reports proportional: what changed, where it is, relevant evidence, and remaining gaps. For nontrivial work, use the evidence shape in [development workflow](../workflows/development.md).
