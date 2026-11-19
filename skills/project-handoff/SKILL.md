---
name: project-handoff
description: Create, update, or resume from a project-root HANDOFF.md. Use when asked to save project context, hand over a coding session, or record what changed, what was applied, which checks ran and passed, and what still needs verification.
---

# Project Handoff

Use the project's language or the language the user requests. Keep the handoff short enough to resume work without rereading the conversation.

1. Locate the project root, read project instructions, and check for an existing handoff. Reuse its path. Otherwise use `HANDOFF.md` at the project root or the path the user specifies.
2. Identify the work covered by the handoff. Compare the current files and available diffs with the request and session evidence. Record the branch and revision when available. When Git metadata or a diff is unavailable, state which files or other evidence you used. Preserve decisions and open items from the existing handoff when still relevant.
3. Record completed changes with project-relative paths and describe how they connect to the actual entry point. Distinguish implemented work from deployment, release, or device installation. Record only the environments or variants where application is verified.
4. Gather test commands, output, and environment details. Run relevant existing checks when requested or useful within scope. Use the outcome of each check to choose its status. Use **Passed** for a completed check that passed, **Failed** for a check that ran and failed, **Blocked** for a check prevented from running, and **Not run** when no attempt is evidenced. Include counts if reported. Mark old runs as prior evidence and distinguish compilation, unit tests, integration tests, device checks, and live-service checks. Label temporary verification scripts as session-local evidence and give the project's normal command for reruns.
5. List unresolved items with a specific next check, expected outcome, and any observed blocker. Keep unrelated work, credentials, and sensitive user data out of the handoff.
6. Read back the updated handoff and verify paths, statuses, and claims against the evidence. Report the handoff location, counts that passed, and checks still pending. For a request to resume work, read the handoff, confirm important claims against current files, and continue the requested task.

## Handoff structure

- **Scope and baseline** — project, date, module or environment, and revision if known.
- **Changes applied** — behavior, affected code, and decisions relevant to the next session.
- **Verification** — table with check or command, status, and evidence or blocker.
- **Next checks** — reproducible steps for remaining work.
