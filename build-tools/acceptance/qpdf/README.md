# T78 optional-Length qpdf supplement

This acceptance-only Apache-2.0 derivative is separately identified as
`qpdf 12.4.0-folio-t78-r1`. It never replaces the original qpdf tool used by
previous profiles. Public source commit `babad179ce5db9a21635c8d1ac17baa59637eada`
is frozen in `scripts/t78-qpdf-pin.properties` with the source archive and patch
hashes. `scripts/t78-qpdf-runtime.sha256` pins the executable, loader and every
dynamic library. The runtime uses the copied loader and explicit library path.

ISO 32000-1 Table 20 defaults absent/null V2 `Length` to 40 bits. Upstream
qpdf guessed 128 while opening and read the absent value while writing the
decrypted derivative. The patch corrects those two conditions. It does not
suppress a warning, bypass password proof, accept a malformed present value,
change owner-key algorithms or alter another profile. A version suffix names
the derivative. A CLI-only CMake switch excludes unrelated fuzz/test targets
from configuration; no third-party test fixture is an acceptance oracle.

Rebuild with `python3 scripts/provision-t78-qpdf.py`. It uses the immutable
Ubuntu 24.04/JDK21 image from the Foundation authority and the build package
versions retained in `t78-build-packages.txt`, then requires exact binary and
runtime hashes. Missing archived packages or a different build fail closed.
The selected GCC libstdc++/libgcc runtime uses the GCC Runtime Library
Exception; copied runtime libraries retain their upstream glibc LGPL,
OpenSSL Apache-2.0, zlib and libjpeg-turbo licenses. These caches are not
product dependencies or Maven release artifacts.

The source audit records 20 independent original qualification cases: 16
warning-free positive `--check`/normal-password decryption cases and four
invalid-password/present-length controls. Unmodified 12.4.0 failed exactly
the four intended absent/null V2 cases; this derivative passed all 20.
The T78 recorder independently exercises actual original inputs/products and
its syntax control again in every certification environment. qpdf remains
the syntax observer; it is not relabeled as an ISO standards validator.
