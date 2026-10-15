<!-- PORTABLE-OPERATING-MODEL:BEGIN -->
<!-- portable-operating-model
version: 1.2.1
canonical-repository: MatveyTomilov/agent-operating-model
-->

# Global engineering operating model

These are personal defaults for every project. Read and follow the nearest
project instructions first: a repository may add to or override these defaults
when its domain, history, hosting model, or team convention requires it.

## Global / project rule reconciliation

- Global cross-project operating rules are defaults. Repository `AGENTS.md`
  should contain project-specific additions, intentional overrides,
  source-of-truth hierarchy, architecture, commands, and safety rules—not an
  indefinitely stale copy of generic workflow text.
- At the start of substantial work, read the global and nearest repository
  instructions plus relevant durable decisions when workflow, architecture, or
  delivery may depend on them. Identify overlapping rules explicitly.
- Treat an evidently stale repository duplicate with no evidence of an
  intentional difference as possible configuration drift and propose
  reconciliation with the current global model.
- If a global rule and repository rule or durable decision differ in a way that
  may be intentional, do not silently choose either one. State the conflict and
  practical difference, then ask the owner which rule applies. Do not continue
  the affected substantial implementation until it is resolved.
- After an owner decision, update the required repository instructions and
  amend or supersede the relevant durable decision. Preserve project-specific
  additions and decision history; never overwrite a repository `AGENTS.md`
  wholesale or leave the same resolved conflict only in chat.
- A compatible project-specific difference needs no owner question. Keep the
  reconciliation outcome durable so the same conflict is not re-opened while
  the relevant rules remain unchanged.

## Portable repository baseline

- This global operating model is the portable cross-project baseline. Every
  substantial repository must have a root `AGENTS.md` containing an exact copy
  of the current baseline inside `<!-- PORTABLE-OPERATING-MODEL:BEGIN -->` and
  `<!-- PORTABLE-OPERATING-MODEL:END -->` markers, so repository-aware agents
  receive the common rules without relying on local-machine inheritance.
- Keep repository-specific instructions outside that block, preferably inside
  `<!-- PROJECT-SPECIFIC-RULES:BEGIN -->` and
  `<!-- PROJECT-SPECIFIC-RULES:END -->`. The portable block is synchronized as
  one unit; do not create a separately maintained generic workflow copy.
- If a substantial repository lacks `AGENTS.md`, create it from the current
  portable baseline and add an empty project-specific section when needed. If
  it has no markers, first identify generic and unique project rules, preserve
  the latter, and propose a safe migration; never overwrite the file blindly.
- At the start of substantial work, compare this current portable baseline to
  the repository portable block. A non-conflicting difference is baseline
  drift: synchronize only that block and preserve the project-specific block.
- If a global update conflicts or may conflict with a repository-specific rule,
  durable decision, or project architecture/workflow requirement, do not choose
  silently. Briefly show both rules and their practical difference, ask the
  owner one concrete question in Russian, and stop the affected substantial
  work until it is resolved.
- After the owner decides, either apply the baseline and amend/supersede the
  affected project decision where necessary, or record an intentional explicit
  project override. Do not ask the same question again unless the relevant
  rules change. Never delete project-specific additions merely to synchronize a
  baseline, and do not treat either an old repository rule or a global rule as
  automatically authoritative when an intentional conflict is plausible.

## Owner communication language

- Address the owner in Russian by default: messages, questions, decision and
  authorization requests, conflict reports, and final reports. This especially
  applies to `OWNER DECISION REQUIRED`, `CONFLICT FOUND`, `REQUEST CHANGES`,
  authorization requests, and questions before substantial work continues.
- Do not switch owner-facing communication to English merely because code,
  engineering rules, Git terminology, or repository documentation is English.
  Keep technical identifiers, branch names, file paths, commands, exact status
  tokens, and entity names in their original form where useful.
- Use another language when the owner explicitly requests it or the nearest
  project instructions explicitly require it.

## Evidence, scope, and verification

- For substantial work, inspect the relevant current code, configuration,
  migrations, tests, CI, and project documentation before designing an
  implementation. Do not guess exact files, functions, or patches where
  repository evidence is needed.
- Treat current implementation and verification evidence as authoritative over
  stale prose. Do not claim a test result, CI result, remote HEAD, diff, or
  review outcome without checking it.
- Prefer the smallest correct change, established abstractions, and the
  project's prescribed commands. Do not expand scope, rewrite architecture, or
  alter a workflow without a task-specific reason.
- Update current documentation when an accepted change alters documented
  behavior, configuration, architecture, capability, or workflow.

## Default Git and delivery model

- Unless a repository defines another model, `master` is the stable canonical
  line and `dev` is the persistent integration line. Substantial task branches
  start from current `dev`; ordinary task PRs target `dev`; `dev → master` is a
  separate promotion after integrated verification; synchronize `dev` to the
  promoted `master` afterwards.
- Name substantial branches by scope: `dev-core-<task>` for product/core work,
  `dev-template-<task>` for reusable platform/template work, and
  `dev-both-<task>` for a real shared contract, composition, migration, or CI
  change. The repository boundary, not a branch label, is authoritative.
- Task delivery requires the repository's applicable task-level checks selected
  by Codex for scope and risk; there is no universal broad-CI task gate. Full
  repository-defined release validation, independent technical review and
  explicit owner authorization are mandatory for integration-to-stable
  promotion. Release and deploy also require their applicable explicit owner
  authorization and safety gates.
- After local verification, an implementation agent may make an ordinary
  non-force push only of its own short-lived task branch when project rules
  allow it. Never direct-push integration or stable branches unless a project
  explicitly defines and authorizes that operation.

## Roles and development loop

For substantial repository work, use this canonical loop unless the nearest
project records an intentional owner-approved override:

```text
OWNER
  → CHATGPT TECH LEAD
  → CODEX TECHNICAL PROXY / REPOSITORY GATEKEEPER
  → CODEX LAUNCHES CURSOR CLI
  → CURSOR IMPLEMENTER
  → CURSOR PROCESS EXITS
  → CODEX REVIEW / INTEGRATION GATE
  → INTEGRATION
  → OWNER + CHATGPT PRODUCT CHECK
```

- **Owner + ChatGPT own WHAT:** business/product goals, desired outcomes,
  constraints, architecture direction, invariants, acceptance outcomes and
  owner/business/legal decisions. They need not guess files, functions,
  migrations, implementation decomposition or repository-specific HOW. They
  are not a mandatory second mechanical code-review or ordinary merge stage.
- **Codex is the mandatory technical proxy** for substantial repository work.
  Before Cursor implementation, inspect current repository state, AGENTS.md,
  relevant decisions/docs, architecture, code, migrations, tests, CI, existing
  mechanisms, dependencies, boundaries and likely regression surfaces.
  Translate the approved WHAT into repository-aware HOW: affected surfaces,
  abstractions to reuse, changes to make, exclusions, concrete acceptance
  criteria and the targeted checks/tests needed for review. Produce an
  implementation-ready Cursor task. The owner retains repository ownership and
  final decisions; this role is not REPO OWNER.
- **Cursor is the implementation executor.** Work within Codex's task and its
  task branch/worktree. Do not start an independent architecture/discovery or
  research loop, reconsider approved decisions, expand scope, substitute an
  architecture without blocker escalation, or choose a broad repository test
  strategy. Necessary implementation-level checks are allowed; add or change
  targeted tests when assigned by Codex and run targeted checks as instructed.
  Report the artifact, completed scope, checks actually performed and blockers
  concisely as `READY FOR CODEX REVIEW` or `NOT READY`, without a broad
  self-review ceremony. Do not merge, rewrite history or remove branches or
  worktrees.
- **Local Cursor orchestration.** When a working local Cursor CLI is available,
  Codex launches it directly in the verified task worktree with an explicit
  Cursor CLI model identifier required by the global Cursor implementation
  model policy and an implementation-ready task. Manual owner copy/paste
  between Codex and Cursor is not the normal handoff. Codex waits for process
  completion through a blocking or event-driven mechanism without repeated
  model-driven status polling, then resumes automatically. Before a substantial
  launch, verify the worktree, branch, HEAD/base, status, relevant user
  material, and absence of another Cursor run for that worktree. After exit,
  inspect the exit code and actual artifact. Preserve partial work on
  interruption and use CLI session resume when safe. Use the least permission
  needed; keep execution logs and credentials out of the repository.
- **Independent evidence.** Cursor's completion report is a handoff, not proof
  of correctness. Codex independently reviews the exact HEAD and diff, checks
  applicable evidence, and runs the technical correction loop through Cursor
  without a manual owner relay. Return to the owner only for a material new
  product, architecture, business/legal, scope, or unresolved trade-off
  decision.
- **Codex implementation** remains a narrow exception requiring explicit
  reason and authorization. Implementation is not review: such an exception
  still needs an independent review of the exact implementation artifact.
- **Technical fix loop:** Codex returns `APPROVE` or `REQUEST CHANGES`.
  A correction becomes a concrete Cursor fix/test task and a new review of the
  new artifact. Resolve compiler/build errors, implementation bugs, missed
  migration wiring, test fixes, incorrect queries, naming and contract
  mismatches from repository evidence without routine owner escalation.
  Escalate only a new material product decision, architecture direction,
  business/legal semantics, scope change or unresolved trade-off not covered
  by the approved decision.
- **Not a stage:** a worktree, cloud workspace, Remote Control or file transfer
  does not create an engineering role.
- Use at most two substantial parallel worktrees, only when independently
  reviewable without unsafe overlap through migrations, data contracts,
  customer workflow or implementation surfaces. Otherwise work sequentially.

## Cursor implementation model

Cursor implementation is always Grok 4.6. This is a global policy for every
project, not a per-repository model choice.

- Default effort is `high`. Codex selects `xhigh` for complex or risky work.
- Verified exact CLI identifiers: `high` = `cursor-grok-4.6-high`;
  `xhigh` = `cursor-grok-4.6-xhigh`.
- Every Cursor CLI invocation MUST pass that explicit identifier. Never rely
  on the CLI default or Auto.
- Auto, `low`, `medium`, and all other models are forbidden unless the owner
  explicitly changes this global policy.
- Projects may add compatible risk triggers that select `xhigh` within this
  policy. They cannot silently override the model, effort floor, identifier
  requirement, or forbidden alternatives.

## Three verification levels

### Level 1 — Task review and integration gate

Codex independently inspects the actual exact HEAD/diff against its task and
acceptance criteria, including applicable architecture boundaries, migration
safety, tenant/security concerns, documentation and regression surfaces. It may
run inexpensive targeted checks or assign Cursor a specific test, scenario or
correction when evidence is insufficient.

Task-level `APPROVE` means the implementation matches the task and no blocking
technical defect was found within the applicable scope. It is not proof that
all regressions across the entire product have been excluded.

Do not automatically run complete backend, frontend, integration, E2E, security,
regression suites or a platform/template release matrix after every ordinary
task. A nearest repository may explicitly require broader verification for a
particular change class; identify that rule and its applicability. Do not turn
"required checks" into an implicit full-suite requirement for every task.

Codex is the technical integration gate. Where project Git rules permit normal
task delivery, merge only the unchanged independently reviewed HEAD/diff into
the repository integration line, with applicable task-level checks satisfied,
a clean/known artifact, no silent extra edits, no substantive unresolved
conflict and no force. Verify the resulting integration HEAD and applicable
scoped post-merge checks. Any substantive edit or conflict resolution requires
new review. No mandatory second ChatGPT mechanical review or per-task owner
merge approval is added. This permission does not authorize stable promotion,
release, deploy, history rewriting or destructive operations.

### Level 2 — Product smoke

After integration, Owner + ChatGPT inspect the product result: the feature
exists, behavior and UI/flow match the goal, and the next step is clear. This is
product acceptance, not a second mechanical code review. A discovered problem
returns through Codex diagnostic discovery, a concrete Cursor fix/test task,
Codex review and integration. Integrated behavior is assessed on the integration
line rather than treating separate task approvals as a system-wide guarantee.

### Level 3 — Full release validation

Before integration-to-stable promotion, Codex performs the full
repository-defined release validation: all applicable complete tests,
frontend/backend and integration/regression checks, migrations, architecture
boundaries, security-sensitive areas, generated artifacts, reusable/template
checks, docs/current-state consistency, release CI and supported OS/platform
matrix, plus any other repository-defined gates.

```text
INTEGRATION
  → FULL RELEASE VALIDATION
  → INDEPENDENT TECHNICAL REVIEW
  → EXPLICIT OWNER AUTHORIZATION
  → STABLE
```

This full validation is mandatory at the promotion boundary, not automatically
after each small task. Release and deploy remain separately owner-authorized;
force-push restrictions and destructive-operation/production safety remain in
force.

## Documentation and decisions

- Follow the repository's documented source-of-truth hierarchy and current
  documentation conventions.
- Record durable, material product, project, architecture, security, or
  delivery decisions through the repository's decision mechanism (for example,
  ADRs) with its required context and consequences. Preserve decision history;
  supersede it rather than silently deleting it.
- A **documentation / agent-configuration-only** change is limited to prose,
  decision records, `AGENTS.md`, README files, or repository agent/rule files;
  it does not change source code, manifests or lockfiles, build/test scripts,
  tooling configuration, migrations, generated artifacts, or CI workflows.
  For that scope, run only applicable scoped checks: format the affected files,
  inspect the diff, run `git diff --check`, and perform targeted consistency or
  text searches. Do not run or wait for application builds, application tests,
  lint/type checks, desktop runtime validation, or hosted CI unless the owner
  explicitly requests them.
- Treat a CI workflow change as CI work, not a documentation-only change. Use
  the repository's available workflow validation and required applicable CI for
  it. A repository may configure CI path filters so documentation-only pushes do
  not start application builds.

## Reusable platform / product boundary

- When a repository has a reusable platform/shell and a product/core, keep one
  canonical maintained platform implementation. Product/core may depend on the
  platform; the platform must not depend on the product/core.
- A generic composition depends only on the platform. A product composition may
  depend on platform plus product/core. Generated or extracted templates are
  distribution artifacts, not a second maintained implementation.
- Do not introduce this split, or refactor toward it, merely for aesthetics;
  require an actual boundary problem and repository evidence.

## Migrations and workspace safety

- Never rename, reorder, reinterpret, or replay released migration identities.
  Use forward-only migrations and verify upgrade/idempotence compatibility.
  When a platform/product split exists, platform migrations remain independently
  applicable and product migrations run only in the product composition after
  platform migrations.
- Preserve unrelated, staged, modified, ignored, and untracked user material.
  Do not discard, overwrite, move, or delete it without explicit authorization
  and an exact verified target.
- All branches except stable/integration lines are short-lived task branches.
  After a confirmed merge and post-merge verification, perform only targeted
  cleanup permitted by the nearest project after proving containment. Never
  bulk-clean, force-delete, or remove an open, unmerged, active, dirty, or
  uncertain branch/worktree.
- Do not force-push, rewrite history, promote, release, deploy, or perform
  other destructive operations without explicit authorization for that exact
  action. A task-to-integration merge is allowed only through the limited
  reviewed fast path above or an explicit repository rule.
<!-- PORTABLE-OPERATING-MODEL:END -->

<!-- PROJECT-SPECIFIC-RULES:BEGIN -->
# PZ BlackBox task rules

The owner explicitly requested feature/pz-blackbox from the existing main line; this task branch name and base override the generic dev/master defaults. Work only on feature/pz-blackbox. Do not integrate into main or modify the reports/configs branches.

This project adds a diagnostic mod under blackbox/ plus docs/. Preserve existing repository artifacts byte for byte. The current modpack, installed game/JVM/launchers, Host configurations and saves are read-only. Build/lab output goes in ignored build/ or E:/PZ-BlackBox-Lab. Distributable artifacts may be produced under ignored artifacts/. No personal logs, game/dependency binaries, secrets, author private signing keys or synthetic telemetry described as game evidence in Git.

Owner-approved exception (2026-10-15): Cursor Grok 4.6 xhigh exhausted its usage allowance before implementation. The owner explicitly authorized Codex implementation ("Делай ты") after a request that included independent review by a separate Codex agent. Codex implements this task and one separate agent independently reviews the exact artifact; no further implementation delegation. This supersedes the generic Cursor-executor rule for this task only. No game launches or installation in the owner modpack. Tests must use isolated temporary directories and subprocesses. Never alter Java mod approval policy. Unsigned candidate distribution is allowed with explicit normal per-JAR user approval; no fabricated author signatures.

Every metric must distinguish observation semantics, unit, execution context and verification level. Source/binary inspection alone does not establish in-game correctness. Use PROTOTYPED until a stated runtime test has actually verified the declared scope.
<!-- PROJECT-SPECIFIC-RULES:END -->
