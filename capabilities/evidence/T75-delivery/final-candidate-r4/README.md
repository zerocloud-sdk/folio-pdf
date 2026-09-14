# T75 staged candidate after the inventory reader correction

The stage command actually exited 0, running from 2026-09-13 17:00:37.731495
UTC to 17:01:16.845508 UTC. It followed the complete successful
[host verification and four-JDK matrix](../final-validation-r4/README.md).
The 1,933 source inputs and 30 contract inputs exactly match that frozen
validation input set. `build-inputs.json` additionally identifies 23 artifacts
and 24 harness inputs. The build-input and build-command SHA-256 values are:

- `64cb458acef6c0e98aa5b0dbf8023d85fe569fa2269eb6c3c343959779325e02`
- `76b3d096020e5fd925bb8e47354e246210f9e68d33be9bfca66929d443d9ca19`

`staged-build.tar.xz` retains ten original build, command, output, result,
helper and freeze files. Every member was compared byte-for-byte with the
original directory `/tmp/t75-final-candidate-r4-68wk6eny`; the complete
archive identity catalog is adjacent. This is an unsigned local candidate.
The stage build skips tests because complete verification already finished;
stage success is not a new test or certification result.

The actual text certifier's initial `candidate-identity.txt` observation reports
candidate `ab18cc9c0473730a6aabec5d12e3172f22bea23a0a0f9529052a2bc186cbfc1b`
and contract `3de508f79fcbef7258feee8abc013d6f02b000840cac1cb263cb0d6f927b8f2b`.
That readiness command still reports overall NOT READY while obligations are
unfinished; its identity output does not establish certification. Fresh text
certification and all five invalidated obligation refreshes, independent
reviews, final inventory checks and authorized delivery remain required.
