# Spec review — JVM settings parsing R4

Baseline remains `df2df726a2695267bf63a7c9df49a4a7481f0169`. Read-only review of the environment-observer correction and its focused test against C6/C12/C16; no builds or source edits.

The correction at `scripts/t03-foundation.py:1177-1178` splits raw property lines at `=` before stripping each key/value. Legitimate empty JVM settings therefore remain parseable, while multiline path continuations that are not property declarations are ignored. Required runtime vendor/build values still come from the actual retained JVM settings; missing required properties fail rather than receive guessed values. Declared release IMPLEMENTOR, Java executable hash, OS/image identity and actual prlimit identity checks remain intact.

The new test drives the real `observe_environment` orchestration with explicitly synthetic observer payloads containing empty `sun.cpu.isalist` and `user.timezone`, multiline library paths, distinct release/runtime vendor names and fixed launcher fields. It checks preservation of the declared environment plus independently observed runtime/launcher values; synthetic inputs do not become certification. The retained focused T03 suite reports 14 tests passing, exit 0.

No applicable source finding identified. The stopped JDK8 rehearsal remains retained as failure evidence; it is not a passing certification. Updated source/configuration pins, fresh staging and affected observations are required before final review, as specified by execution order step 5 and C12. Shipping runtime, Java contracts and publication scope remain unchanged. Final all-profile evidence, predecessor readiness and submission gates remain pending.
