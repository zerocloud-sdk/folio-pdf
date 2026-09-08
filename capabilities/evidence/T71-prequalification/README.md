# T71 staged-product qualification

This is an earlier qualification run, not the eight-scope Foundation certificate.
It used actual staged 0.1.0 product and test jars in the pinned Ubuntu 24.04
Temurin JDK 17 image, with Native IN_PROCESS and the actual IN_PROCESS Facade.
The staged build receipt is [build-inputs.json](build-inputs.json). Its historical
hashes describe that build and must not be relabeled after candidate inputs change.

The [plan](plan.json) is explicitly unverified and lists eight proposed command
sets. Only `jdk17-in_process` was executed here. Its [82 public contract tests](jdk17-in_process/contract-tests.txt)
passed, as did the three real products' four-chain observations and the final
raw-stream preservation check. Product bytes, raw checker records, 51 rule
controls per product, semantic control, rasters and a one-pixel control are in
[the observations directory](jdk17-in_process/observations/result.properties).

The [preservation result](jdk17-in_process/observations/raw-preservation/result.json)
compares final effective retained streams and page content with the Source;
the incremental re-encoding negative is detected despite its unchanged Source
prefix and decoded data. All these commands ran within the declared container.

The final candidate is rebuilt after its delivery contract is frozen. The
[current Foundation Evidence authority](../../foundation-evidence.yaml), separate
candidate-specific records and final independent review determine qualification.
This preliminary run cannot substitute for any of the eight required tuples or
for refreshed transactions evidence.
