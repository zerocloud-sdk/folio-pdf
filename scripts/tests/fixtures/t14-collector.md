# T14 collector protocol archive

`t14-collector.zip` contains normalized development process/retention records
for public collector regression tests. They originate from the T14 original
corpus, public IN_PROCESS products and pinned independent tools. They are not
certification of a tested candidate or of the required JDK/profile matrix.

`scripts/generate-t14-collector-fixture.py <completed-development-run> <fresh-archive>`
first validates the receipt, then changes only its observation-directory path
to `/workspace/run` and recomputes affected process and manifest hashes. PDF,
metadata, control, tool, raster and finding bytes are preserved. The ZIP entries
have fixed timestamps. The tests exercise missing/altered files, contradictory
processes, wrong modes, changed authorities and resealed negative findings.
Final certification always runs real tools again in each actual environment.
