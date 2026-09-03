# TwelveMonkeys ImageIO TIFF 3.14.0 manifest

This manifest covers the optional pure-Java TIFF codec selected by T18. The
upstream project is <https://github.com/haraldk/TwelveMonkeys>, release tag
`twelvemonkeys-3.14.0` (peeled commit
`62f6e2fba80b3eee99707985ebf4a4fd33abf07b`). Its Maven metadata identifies the project license as BSD-3-Clause;
the authoritative license and copyright notice are the `LICENSE.txt` file at
that tag.

| Maven artifact | Role | JAR SHA-256 |
| --- | --- | --- |
| `com.twelvemonkeys.imageio:imageio-tiff:3.14.0` | TIFF ImageIO reader/provider | `68aa1b4a176d1242b9e49334df188ebfbb7c9201f6071dfe42500d63486224b6` |
| `com.twelvemonkeys.imageio:imageio-core:3.14.0` | ImageIO provider base | `a1b832b5090bd4677696f999b5ccb8954e987eb9674632a6286a6de2bb1c3c78` |
| `com.twelvemonkeys.imageio:imageio-metadata:3.14.0` | image metadata support | `03768fc012bd2573236da803099aba6961dfb29c190103f9790fc49ac27f84c1` |
| `com.twelvemonkeys.common:common-lang:3.14.0` | common language utilities | `8d4529d6f56a010bc7e130ebfcdaf14bc11586e9d9ae66f6dca66f91da7eafef` |
| `com.twelvemonkeys.common:common-io:3.14.0` | common I/O utilities | `ae01308bd48c68e76f6a1f76880cf7f4a3a004aa83d78e5448de358a4d957e8f` |
| `com.twelvemonkeys.common:common-image:3.14.0` | common image utilities | `9edb1afd32278d20ad660869bfa5b0a27cf9b3553b6eb3f8fc51a2fc13109b66` |

All six artifacts are BSD-3-Clause. `pdf-document` declares `imageio-tiff` as
optional, so neither it nor its graph becomes an implicit consumer runtime.
The artifacts remain separate Maven dependencies and are not shaded or copied
into Folio PDF JARs. The repository-only, non-installed `pdf-acceptance`
module declares the same root at runtime to exercise the available-codec path.

T18 discovers the provider through standard `javax.imageio` registration.
Public API and capability results contain no TwelveMonkeys class name or
provider object. A missing reader is a supported deployment state and is
reported as `OPTIONAL_CODEC_UNAVAILABLE`. No upstream source, test fixture,
binary output, or behavioral result was copied into Folio PDF.

## Upstream BSD 3-Clause License

Copyright (c) 2008-2020, Harald Kuhr

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
* Neither the name of the copyright holder nor the names of its contributors
  may be used to endorse or promote products derived from this software
  without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
