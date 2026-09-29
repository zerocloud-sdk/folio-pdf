# T15 collector protocol fixture

`t15-collector.zip` is Apache-2.0 project-authored development test data. It
contains the twelve public development products and pinned independent-tool
observations produced while implementing #77. Only repository/output path
prefixes were normalized to `/workspace` and `/workspace/run`; process stream
hashes and the retention manifest were recomputed after that normalization.
ZIP metadata uses a fixed epoch. No PDF, raster, rule finding, comparator metric
or tool identity was replaced with a passing value.

This archive has no environment observation, candidate identity, Foundation
index entry or certification authority. It is never used to certify a build.
`test_t15_foundation.py` extracts it into temporary directories, proves that
the collector accepts its internally consistent protocol, then tampers with
reports, tools, controls, identities, modes and products. Resealing altered
files must not conceal those defects. Actual Foundation certification always
runs the staged public products and tools anew in every required tuple.
