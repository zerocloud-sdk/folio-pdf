# Issue #83 Codex stop #2

- When: 2026-10-10 14:52:07 CST (SIGTERM sent ~14:51:57 CST, after snapshot at 14:51:54 CST).
- Why: user request (2026-10-10 14:51 CST) to stop #83's Codex and root-cause the T28 elapsed-time failures in a scratch copy.
- Running at stop: launcher bash 4111755 -> `codex exec` 4111757 (thread 01a1231f-374b-7a13-80f6-87a22819b527) -> codex-code-mode-host 4111979.
  No refresh-relaunch / run-gate / t03-foundation / podman / mvnw / java / codex-linux-sandbox children existed. Last jsonl events: Codex had just
  preserved full-verify-final-r12 reports (preserve-full-verify-r12-r1) and was editing refresh-relaunch-r18.py / delivery files (file_change events).
- Killed: SIGTERM to 4111979, 4111757, 4111755; all three exited within 10 s; no SIGKILL needed.
- Containers: `podman ps -a` (default store) showed only the unrelated 10-day-old exited `folio-t78-qpdf-build` (left alone); t83 store had no containers. Nothing removed.
- After: no codex/run-gate/refresh/t03 processes; HEAD 42fe5c797711070b319b9419e14fcb7adfa98735; `git status --porcelain | wc -l` = 68 (measured 14:52:21 CST).
  No git operations, file deletions, or image/volume pruning. Snapshot: issue-83-stop2-snapshot.txt.
