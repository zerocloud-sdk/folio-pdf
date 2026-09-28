# Acceptance-only color profile

`sRGB2014.icc` is the unchanged ICC v2 profile revised in February 2015,
copyright International Color Consortium, 2015. Its SHA-256 is
`384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a`.

Source: [ICC sRGB profiles](https://registry.color.org/rgb-registry/srgbprofiles),
[original binary](https://registry.color.org/rgb-registry/profiles/sRGB2014.icc).
The [ICC profile license](https://registry.color.org/profile-library/) permits
redistribution and embedding. Modified profiles must remove the original
identity and copyright information and cannot be presented as originals.
This copy preserves every byte, including the copyright tag.

The profile is embedded only in original acceptance fixtures. It tests detached
ICCBased profile identity and bounded metadata decoding; it supplies neither
image samples nor expected rendering colors and is not included in product
artifacts or runtime dependencies.
