# T71 first review candidate, interrupted

This is historical evidence for candidate
`f331a0130bb59dfcbd9149b8c3e0d63d26ab98ed888e5b5b2cb503d4ef281bca`.
The unsigned [build receipt](build-inputs.json), [build command](build-command.json)
and [build transcript](build.txt) describe its actual 955 source inputs, 23
artifacts, 24 harness files and 30 contract inputs. Original staged bytes are
retained locally in ignored `.build-cache/t71/aborted-stage-f331a013`.

Independent Spec review found that malformed Page Contents and missing effective
Resources could publish. [The public probe](structure-probe/CoreStructureProbe.java)
accepts source path, output path, Native execution profile and a variant name.
The retained malformed outputs and raw observations are under `structure-probe/`.
Direct Kids replacement was separately rejected and is not a reported defect.

The [T09 attempt](../foundation/T71-values/) was stopped during JDK 17
observations. Earlier completed observations keep their actual old identity and
results. The attempt did not complete all eight scopes and never updated the
current Foundation Evidence inventory. Neither these observations nor the
initial focused green logs certify the corrected implementation.

The correction's public RED, GREEN and Refactor observations are in
[the separate correction record](../T71-review-correction/). Current completed
certifications, when present, are selected only by the Foundation Evidence
inventory with matching candidate and contract identities.
