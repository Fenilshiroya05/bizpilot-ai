---
name: git-workflow
description: Use before committing or pushing BizPilot AI changes — checks for secrets, verifies .gitignore coverage, reviews git status/diff, keeps commits focused with conventional messages. Never commits or pushes unless explicitly asked; never touches unrelated files/projects outside this repo. Triggers on "commit this", "what should I commit", "check before I push", "write a commit message".
---

# Git Workflow

## 1. Purpose

Keep BizPilot AI's git history clean and safe: no leaked secrets, focused commits, meaningful messages — and never act (commit/push) without explicit instruction.

## 2. When to Use

- Before creating a commit, to check what's actually staged/changed.
- When asked to draft a commit message.
- Before pushing, to sanity-check what would go to GitHub.

## 3. When Not to Use

- Don't use this to justify committing/pushing proactively — it governs *how*, not a license to *decide* to commit. The decision to commit or push always comes from the user.

## 4. Project-Specific Context

- Repo root is this project directory (`Personal Project/`), remote `origin` on GitHub — confirm this hasn't drifted before assuming it's still scoped correctly (this repo was previously, briefly, accidentally rooted at the user's home directory — always sanity-check `git rev-parse --show-toplevel`).
- `.gitignore` already excludes `.env*` (except `.env.example`), build output (`backend/target/`, `frontend/node_modules/`, `dist/`), IDE/OS files, logs, and local DB/upload artifacts.
- CLAUDE.md §43 commit style: `feat: add customer management`, `fix: prevent cross tenant document access`, `test: add quotation integration tests` — type-prefixed, imperative, scoped to one logical change.
- Never commit: `.env`, API keys, passwords, generated secrets, local database files (CLAUDE.md §43).

## 5. Required Workflow

1. Run `git status` first — always, before staging anything, to see the full picture (never blindly `git add -A`/`git add .`).
2. Run `git diff` (and `git diff --staged` once something is staged) and actually read it — don't assume the diff matches intent.
3. Re-check `.gitignore` covers anything sensitive that's currently untracked; if something sensitive is untracked and *not* ignored, flag it before it gets anywhere near `git add`.
4. If anything looks like a secret (API key, password, token, connection string) even in an innocuously-named file, open and check its actual contents before staging.
5. Stage specific files by name — never `-A`/`.` — so nothing unintended rides along.
6. Draft a conventional commit message (`type: summary`) describing *why*, scoped to one logical change; split unrelated changes into separate commits rather than bundling them.
7. Only actually run `git commit` or `git push` if the user explicitly asked for it in this turn — drafting a message or diff review is not the same as permission to commit.

## 6. Technical Rules

- Never use `--no-verify`, `--no-gpg-sign`, or `-c commit.gpgsign=false` unless explicitly requested.
- Never `git add -A` / `git add .` — stage named paths.
- Never force-push, `reset --hard`, or otherwise rewrite history unless explicitly requested, and warn if it would affect already-pushed commits.
- Never touch files outside this repository's working tree, and never assume a broad `git status` output belongs to this project without checking `git rev-parse --show-toplevel` first.
- Prefer new commits over `--amend` unless the user explicitly asks to amend.

## 7. Quality Checks

- Every commit represents one logical change (a feature, a fix, a test addition) — not a grab-bag.
- Commit message type matches the actual change (`feat`/`fix`/`test`/`docs`/`chore`/`refactor`).
- Nothing in `git status` after staging is unexpected or unexplained.

## 8. Security Considerations

Treat "check for secrets before committing" as mandatory, not optional, even when the filename looks harmless (e.g. `config.yml`, `notes.txt`). If a secret is found already committed in history, stop and flag it to the user rather than trying to silently rewrite history.

## 9. Testing Expectations

Not applicable directly — but recommend that `./mvnw test` (or the relevant test command) has been run and passes before suggesting a commit is ready.

## 10. Expected Final Output/Report

State: what's staged vs. unstaged, anything flagged as sensitive or unexpected, the proposed commit message(s) and how the changes are split across them, and explicit confirmation that no commit/push was performed unless the user asked for it.
