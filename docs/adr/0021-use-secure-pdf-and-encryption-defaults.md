# Use secure PDF and encryption defaults

The Foundation Release reads PDF versions 1.0 through 2.0 and their supported legacy password encryption, writes PDF 1.7 with AES-256 by default, and permits explicit PDF 2.0 output. Legacy RC4 output and other obsolete compatibility settings require an explicit Legacy Security Mode, retaining Reference Suite migration capability without making insecure algorithms an accidental default.
