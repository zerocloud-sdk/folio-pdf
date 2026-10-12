# Issue #83 Codex run — stop note

- **Stopped:** 2026-10-09 07:34:55 CST (SIGSTOP+SIGTERM sent), all processes gone by ~07:35:10 CST.
- **Why:** user request 2026-10-09 07:33 CST ("先停下来把worker的问题查清楚再继续跑") — pause #83 and root-cause the intermittent
  HARDENED_WORKER "The Worker did not terminate after completing its response." failure before continuing.
- **Snapshot before stopping:** `/workspace/contracts/issue-83-stop-snapshot.txt`.

## What was running
- `codex exec -m gpt-6.1-sol ...` PID 3596732 (started 2026-10-07 11:35:38 CST), + `codex-code-mode-host` PID 3596998.
- Driver `python3 -B capabilities/evidence/T83-delivery/refresh-final-r11.py` PID 1159180 (started 04:42:44 CST).
  - `run-gate.py refresh-final-r11-incremental host python3 -B scripts/t03-foundation.py certify capabilities/evidence/foundation/T83-final-r9/incremental --obligation incremental` PID 1593575 (07:29:37)
  - `scripts/t03-foundation.py certify ... --obligation incremental` PID 1593576
  - `podman run --rm ... eclipse-temurin@sha256:a4da3193... java ... T15EvidenceCommand ... HARDENED_WORKER` PID 1603345 (+ rootless helper 1603356), started 07:32:59
  - container `af1edb43ff42` (`lucid_hodgkin`, conmon 1603380, java 1603383) in the run-private storage
    `CONTAINERS_STORAGE_CONF=/workspace/folio-pdf/.build-cache/t83-storage.conf` — T15 incremental, JDK 8, HARDENED_WORKER.
- Progress at stop (from refresh-final-r11/results.json + validation logs): text exit 0 (48 certs/192 chain records),
  images exit 0 (56/224), incremental in progress at JDK 8 HARDENED_WORKER — incomplete, no failure reported yet.
  No Maven/surefire JVMs were running at the time.

## What was killed
- SIGTERM: 3596732, 3596998, 1159180, 1593575, 1593576, 1603345, 1603356. 1159180 was SIGSTOPped first, so it
  received SIGCONT; it was gone within 5s so no SIGKILL was needed. 1593575 was briefly a zombie, then reaped.
- Container af1edb43ff42 was stopped and auto-removed (`--rm`) when its `podman run` client got SIGTERM; nothing is left in the run storage.
  `podman ps -a` (with and without the t83 storage conf) shows only the unrelated `folio-t78-qpdf-build` (exited 9 days ago).
- No images or volumes were pruned. No files or evidence were deleted. No git operations were run.
- Left alone: single-threaded rootless-podman pause processes (`podman` with no args, parent=init helper),
  started between Oct 2 and Oct 8. They hold the user namespace and are shared infrastructure, not containers.

## State after stop
- No `codex exec`, `refresh-final-*`, `run-gate.py`, `t03-foundation.py`, T15/T14 java or Maven processes remain.
- `git status --porcelain | wc -l` = 67; HEAD = 42fe5c797711070b319b9419e14fcb7adfa98735 (unchanged).
- refresh-final-r11 is **incomplete** (incremental and later obligations were not certified). Partial outputs remain under
  capabilities/evidence/foundation/T83-final-r9/incremental/ and capabilities/evidence/T83-delivery/refresh-final-r11/.
