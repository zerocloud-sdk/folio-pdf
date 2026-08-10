# Contributing

Open PDF by ZeroCloud accepts contributions under Apache License 2.0 inbound
terms. Contributors retain their copyright; no copyright assignment is
required.

## DCO sign-off

Every commit must certify the [Developer Certificate of Origin](DCO.txt) with
a real-name sign-off:

```text
Signed-off-by: Your Name <your.email@example.com>
```

Use `git commit -s` to add the line. A pull request with a missing or invalid
sign-off is not eligible to merge.

## Clean-room provenance

Every pull request must include a provenance statement that identifies:

- who authored the contribution;
- every specification, public API document, fixture, or other reference used;
- the origin and license of every new fixture, resource, and dependency;
- whether the contributor has seen iText source, non-public implementation
  details, or closed add-on material relevant to the contribution.

Do not copy or adapt iText source code, resources, fixtures, binary-derived
implementation details, or proprietary add-on material. Do not reverse
engineer closed add-ons. Permitted foundations are public standards, public API
documentation, project-owned fixtures, permissively licensed dependencies, and
counsel-approved black-box evidence produced by an independent Compatibility
Curator. That curator role is currently vacant.

If provenance cannot be established, stop and discuss the material in a GitHub
issue without posting restricted or proprietary content.

## Development contract

- Keep shipped code compatible with Java 8 language and runtime APIs.
- Test behavior through the public `DocumentWorkflow.execute` seam.
- Do not expose PDFBox or another backend in public or protected signatures.
- Add or update Capability Matrix evidence with behavior changes.
- Keep the Facade Surface Manifest separate from behavioral coverage.
- Run `./mvnw -B -ntp verify` before submitting.
- Run `./scripts/verify-jdk-matrix.sh` for changes affecting shipped code or
  build compatibility.

Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
General questions and defects belong in the canonical
[issue tracker](https://github.com/zerocloud-sdk/open-pdf/issues).
