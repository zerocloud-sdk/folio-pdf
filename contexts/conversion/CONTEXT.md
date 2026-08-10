# Conversion

This context owns transformations between PDF and external representations such as HTML, SVG, images, recognized text, and office documents.

## Language

**Capability Provider**:
A replaceable implementation of a conversion capability. Providers may use different local or remote technologies while the default distribution remains usable offline.
_Avoid_: built-in converter, mandatory cloud service
