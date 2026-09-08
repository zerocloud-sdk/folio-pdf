# T71 approved Goal completion checklist

The 23 criteria below preserve the approved Goal wording. They were marked only
after both independent final reviews passed, the owned worktree was removed,
and final identity, retention, inventory and Git checks succeeded.

- [x] **01.** values obligation 的每个源需求均可追溯到已实现行为、冻结的映射或有依据的 保留契约；所有 Foundation 必需修改均已实现，两个现存 release-blocker 已实质解决。

  Evidence: [Requirement and operation traceability](../../../docs/t09-implementation-plan.md), [Current eight values scopes](../foundation/T71-values-r3/)

- [x] **02.** null、boolean、number、string、name、array、dictionary、stream、 indirect-reference 九类值均有 inspect-change-publish-reopen 成功证据。

  Evidence: [Native public workflows](../../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/PdfValueWorkflowTest.java), [Facade public workflows](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PdfValuesFacadeTest.java), [Current eight values scopes](../foundation/T71-values-r3/)

- [x] **03.** Object References 的 Session 内稳定性及跨 Session 非法使用有公开契约测试。

  Evidence: [Native public workflows](../../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/PdfValueWorkflowTest.java), [Facade public workflows](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PdfValuesFacadeTest.java)

- [x] **04.** 有界 stream/container 视图的生命周期和限额行为有公开契约测试。

  Evidence: [Native public workflows](../../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/PdfValueWorkflowTest.java), [Facade public workflows](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PdfValuesFacadeTest.java), [Public contract](../../../docs/document-values.md)

- [x] **05.** malformed、cyclic、out-of-bounds 及非法 Patch 请求产生规定的稳定安全失败。

  Evidence: [Native public workflows](../../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/PdfValueWorkflowTest.java), [Facade public workflows](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PdfValuesFacadeTest.java), [Actual rollback regression](../T71-review-correction/README.md)

- [x] **06.** Patch 失败保持原子性，包括调用方捕获失败后继续查询、发布和重新打开的情形。

  Evidence: [Native public workflows](../../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/PdfValueWorkflowTest.java), [Facade public workflows](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PdfValuesFacadeTest.java), [Actual rollback regression](../T71-review-correction/README.md)

- [x] **07.** Source 字节、无关内容及未知资源的保护行为有证据；无法安全保留时按契约拒绝。

  Evidence: [Current eight values scopes](../foundation/T71-values-r3/), [Preservation contract and profile](../T09-document-value-inspection-patch.md)

- [x] **08.** 模块打开的资源与调用方资源遵守既定所有权，Session 结束后的访问按契约失败。

  Evidence: [Native public workflows](../../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/PdfValueWorkflowTest.java), [Facade public workflows](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PdfValuesFacadeTest.java), [Public contract](../../../docs/document-values.md)

- [x] **09.** 冻结的 kernel PDF Values/Patches Facade 类型和成员全部实现，使用项目自有值 与验证后的 Patch，不泄漏 backend 类型、对象或异常。

  Evidence: [Frozen Facade surface](../../facade-surface.yaml), [Facade public workflows](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PdfValuesFacadeTest.java), [Independent Spec](spec-review.md)

- [x] **10.** Native 与 Facade 的对应公开测试证明等价值、修改、生命周期、所有权及失败行为； 以 DocumentWorkflow outcomes、reopened files、Publication Receipts 为主要测试边界。

  Evidence: [Native public workflows](../../../pdf-document/src/test/java/net/zerocloud/pdf/consumer/PdfValueWorkflowTest.java), [Facade public workflows](../../../pdf-migration-itext7/src/test/java/net/zerocloud/pdf/itext7/consumer/PdfValuesFacadeTest.java), [Current eight values scopes](../foundation/T71-values-r3/)

- [x] **11.** 实际 Stable／Preview jar 的公开表面与清单一致；Preview 包含 Stable mappings， 两种 jar 顺序均拒绝混用，未引入 unsupported Stable stub。

  Evidence: [Independent Spec](spec-review.md), [Actual-jar matrix log](jdk-matrix.txt)

- [x] **12.** T09 在声明的 Ubuntu 24.04/Linux x86-64、JDK 8/11/17/21 × IN_PROCESS/HARDENED_WORKER 八个 Native 组合上获得完整必需认证。 Facade 如仍使用 IN_PROCESS，证据准确记录实际模式，不冒称 Worker 执行。

  Evidence: [Current eight values scopes](../foundation/T71-values-r3/), [Current authority](../../foundation-evidence.yaml)

- [x] **13.** 每组认证分别保留 syntax、standards、semantic、visual 记录及真实负控制， 全部绑定实际 PDF、候选产物、契约、环境和执行配置身份。

  Evidence: [16 current scopes and 64 chains](../../foundation-evidence.yaml), [Independent Standards](standards-review.md)

- [x] **14.** 独立 standards checker 对 T09 所需规则有明确覆盖与已知非法样本验证； #70 的最小 blank-document 规则资格不能自动覆盖新增低层结构。

  Evidence: [T03/T09 standards qualification](../../../docs/third-party/t03-standards-tools.md), [Current eight values scopes](../foundation/T71-values-r3/)

- [x] **15.** 缺工具、缺规则、身份不匹配或不确定结果保持 FAIL/INDETERMINATE； 不通过修改标签、producer 名称或降低约定阈值获得 PASS。

  Evidence: [Evidence command tests](../../../pdf-acceptance/src/test/java/net/zerocloud/pdf/acceptance/T09EvidenceCommandTest.java), [Raw-observation regression](../T71-index-correction/README.md), [Current eight values scopes](../foundation/T71-values-r3/)

- [x] **16.** Capability Matrix、Facade Surface、Foundation 清单、生成文档、公开英文契约、 中文使用材料和 clean-room provenance 与最终行为一致。

  Evidence: [Post-matrix inventory checks](post-matrix-checks.json), [Public contract](../../../docs/document-values.md), [Chinese usage](../../../docs/zh-CN/getting-started.md), [Provenance](../../../PROVENANCE.md)

- [x] **17.** inventory readiness 对当前候选报告 values 及其 transactions 前置要求满足； 其他票未完成时保留全局 NOT READY，只提升本票有证据且依赖满足的能力。

  Evidence: [Actual post-matrix readiness](post-matrix-inventory-readiness.txt)

- [x] **18.** 开发期间最小相关验证通过，最终 ./mvnw -B -ntp verify 通过。

  Evidence: [Development observations](../../../docs/t09-development.md), [Full verify](full-verify-result.json), [Log and XML totals](full-verify-tests.json)

- [x] **19.** 涉及交付代码或构建兼容性的变更通过默认完整 ./scripts/verify-jdk-matrix.sh。

  Evidence: [Unchanged default command](jdk-matrix-result.json), [Four complete JDK results](jdk-matrix-tests.json)

- [x] **20.** inventory validate/check 通过，生成文档无漂移，git diff --check 通过。

  Evidence: [Post-matrix inventory checks](post-matrix-checks.json); [Git whitespace and branch/index checks](final-git-state.json).

- [x] **21.** 针对固定基线执行 code-review 的独立 Standards／Spec 双轴审查， 所有适用发现已解决；审查涵盖已提交、暂存、未暂存及新增文件， 不因仅比较 baseline...HEAD 而漏掉未提交实现。

  Evidence: [Independent Standards](standards-review.md), [Independent Spec](spec-review.md); both final gates PASS with no remaining findings.

- [x] **22.** 遵守提交门槛：当前没有 #71 提交授权；未获新增明确授权时交付审查完成的 未提交 diff。若后续获授权，仅在全部标准通过且待提交字节仍与验证一致时 使用真实身份 git commit -s。

  Evidence: [Delivery receipt](README.md); [Actual branch, HEAD and empty-index verification](final-git-state.json).

- [x] **23.** 工作区没有本票遗留的临时文件或无关修改；报告准确说明提交状态、验证结果、 跳过项及剩余限制。授权提交后，本票改动无遗漏。

  Evidence: [Delivery receipt](README.md); [Verified owned worktree cleanup and retained-file proof](final-workspace-checks.json).

