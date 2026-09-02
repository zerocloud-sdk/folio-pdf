# Use secure PDF and encryption defaults

The Foundation Release reads PDF versions 1.0 through 2.0 and their supported legacy password encryption, writes PDF 1.7 with AES-256 by default, and permits explicit PDF 2.0 output. Legacy RC4 output and other obsolete compatibility settings require an explicit Legacy Security Mode, retaining Reference Suite migration capability without making insecure algorithms an accidental default.

T16 implements the AES-256 choice as Standard-handler V=5/R=6 with AESV3.
For PDF 1.7 it emits the established ADBE Extension Level 8 declaration, but
the project found no public normative Adobe supplement for that profile and
therefore makes only a qualified industry-compatibility claim. Explicit PDF
2.0 output is the fully normative R6 path. Legacy Security Mode is immutable
request state, permits only the exact documented legacy profiles, and never
changes the secure default or another request.
