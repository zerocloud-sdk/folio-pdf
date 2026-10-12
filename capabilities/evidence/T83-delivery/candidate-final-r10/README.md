# Retained unsigned candidate

Each parts manifest binds every ZIP entry and the complete archive. Concatenate
the listed parts in order without separators, verify the whole SHA-256, then
unzip at a separate review location. Entry paths preserve the repository layout.
All parts are at most 48 MiB. Source and staged artifact bytes were checked
against the build receipt before and after compression. This archive performs
no signing, publication, upload or credential access.
