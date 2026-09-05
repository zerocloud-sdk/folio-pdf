# Folio PDF 快速开始

> **独立项目声明：** Folio PDF by ZeroCloud 与 LibrePDF OpenPDF、Apryse 或
> iText 没有隶属、赞助或背书关系。

Folio PDF 是采用 Apache-2.0 许可证的 Java 组件套件，Maven 坐标位于
`net.zerocloud` 命名空间。项目以 Java 8 为运行时基线，并在 JDK 8、11、
17 和 21 上验证。当前版本仍处于实验阶段，尚未发布 Foundation Release。

## 构建

无需安装系统 Maven；仓库内的 Maven Wrapper 固定使用 Maven 3.9.16：

```text
./mvnw -B -ntp verify
```

安装 Podman 后，可以运行完整的 JDK 矩阵：

```text
./scripts/verify-jdk-matrix.sh
```

## Document Workflow

```java
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;

Path output = Paths.get("blank.pdf");
DocumentWorkflow workflow = new DocumentWorkflow();

WorkflowRequest create = WorkflowRequest.builder()
        .target("primary-output", PublicationTarget.path(output))
        .saveMode(SaveMode.REWRITE)
        .build();

workflow.execute(create, session -> {
    session.execute(AddBlankPage.INSTANCE);
    return null;
});

WorkflowRequest inspect = WorkflowRequest.builder()
        .source("published", DocumentSource.path(output))
        .primarySource("published")
        .saveMode(SaveMode.REWRITE)
        .build();

WorkflowOutcome<Integer> inspected = workflow.execute(
        inspect,
        session -> session.query(PageCount.INSTANCE));

System.out.println(inspected.getResult()); // 1
```

T03 支持具名 Path、调用方持有的 stream/channel 以及有大小上限的 bytes
来源，并要求明确选择 primary Source。发布目标可以是 Path 或调用方持有的
stream；每个目标都会得到 `COMMITTED`、`FAILED` 或
`NOT_ATTEMPTED` 收据。调用方提供的资源不会被关闭，多个目标之间不承诺
全局原子性，stream 写入失败时可能留下部分输出。

`REWRITE` 发布完整替换文件。`INCREMENTAL` 要求已有 primary Source，保持
Source 的全部字节作为不变前缀，并按版本 1 命令策略追加非空修订。T03 还提供显式
取消、期限和不携带路径、文件名、内容、元数据、凭据或后端异常的进度阶段。
不可变的 `WorkflowEnvironment` 持有期限检查所需的 Clock；成功的
`WorkflowOutcome` 会给出 capability ID、进程内执行配置、Save Mode、安全
诊断和每个目标的收据。PDFBox 仅作为 `pdf-document` 的内部实现依赖，不会
出现在公开接口中。

已有签名会受到保守保护：已签名 Source 可用于无 Target 的只读 Query，不能以
`REWRITE` 重新发布；只有唯一且一致的 DocMDP P=3 策略可授权受支持的非 Widget
`UpdateAnnotations` 命令。普通签名以及 DocMDP P=1/P=2 不授权当前修改。完整规则
见英文权威文档 [增量发布与已有签名策略](../incremental-signature-policy.md)。

英文 README、Javadoc、ADR、Capability Matrix 和 API 契约是权威规范；本页
提供中文使用说明。如有安全问题，请按 [SECURITY.md](../../SECURITY.md) 中的
临时私密报告方式联系维护者。

## T23 页面渲染

`Rendering` 通过项目自有 `RenderPage` Query 渲染当前页面，默认使用离线的
PDFBox Renderer。下例在同一个 Workflow 中按 144 DPI 消费第一页 PNG：

```java
workflow.execute(inspect, session -> {
    try (RenderedPage page = session.query(RenderPage.version1(1,
            RenderOptions.builder().dpi(144).build()))) {
        page.writePngTo(callerOwnedPngStream);
    }
    return null;
});
```

页码从 1 开始；Query 观察此前完成的 Commands。`Rendering.renderPages` 按
传入顺序逐页渲染，保留重复页，并在每次 consumer 返回后关闭结果。DPI、scale、
CropBox/MediaBox、显式裁剪、RGB/GRAY、背景、透明度和批注显示策略均有明确规则；
完整尺寸舍入与失败契约见英文权威文档 [页面渲染](../rendering.md)。

结果字节只能在当前线程、当前 callback 内消费，提前 `close()` 可释放暂存；
未关闭结果在 callback 结束时失效。PNG 写入不会关闭或 flush 调用方 stream，
失败可能留下部分字节。PNG 消费不生成 Publication Receipt；Workflow 中另行
声明的 PDF Targets 保留既有 Path/stream 发布与收据语义。

两种执行模式共享累计 decoded-pixel、Folio-owned 内存、暂存、时间、取消与并发
配额。逐页处理仍需容纳一页光栅，内存计量不等于整个 JVM 或 RSS 硬隔离。替代字体、
缺失字形、平台 codec 和缺失批注外观通过安全枚举诊断报告。注册的替代 Provider
遵守既有选择规则；远程内容披露必须显式授权。恶意多租户输入使用
`HARDENED_WORKER` 并遵守其部署要求。

JPEG、JPX、JBIG2 只允许作为图像的唯一末端平台 codec；在平台解码前，受限的头部
读取必须确认实际尺寸与已计入配额的 PDF 声明一致。该规则同样适用于内联图像、资源
图像和遮罩，不一致时返回稳定的渲染失败。

T23 的公共测试与三组独立 PDFium 视觉证据不构成兼容性认证；标准证据、正式语义
证据和前置能力的 promotion/dependency gates 尚未满足，因此状态仍为 experimental。
## 段落跨区域排版（T24，experimental）

在 `DocumentWorkflow.execute` 中执行
`ComposeParagraphs.version1(paragraphFlow, compositionLimits)`，可以把语义段落
排入显式声明的多个区域，并跨页继续。`LayoutPage.version1(width, height,
PageMargins.of(top, right, bottom, left), areas...)` 使用 PDF 点；显式区域坐标
相对于边距框的左下角。省略区域时，整个边距框就是一个区域。页面按声明顺序追加，
只创建内容或区域换页实际到达的页面前缀。

`Paragraph.version1(leading)` 可组合 `text(text, fontSize)` 和
`graphic(canvasTransparencyGroup, width, height)`，支持 LEFT、CENTER、RIGHT、
JUSTIFIED 对齐及 `maximumWidth`。固定 leading 是行框最小高度，字体上下界或图形
更高时行框随之扩大。行内图形底边与文本基线对齐，不拆分。普通空格提供优先换行点，
长单词可按 Unicode 标量拆行；保留空格，LF 显式换行，tab、CR 和孤立代理项会失败。
JUSTIFIED 只扩展自动换行且非末行中非末尾的空格。

`ParagraphFlow.areaBreak()` 明确前进到下一个区域。页面声明是有限列表，区域耗尽或
内容无法放入剩余区域时返回 `COMPOSITION_AREA_EXHAUSTED`，不会无限生成页面。
非法几何返回 `COMPOSITION_INVALID`；声明、行数和页面操作字节超限返回
`COMPOSITION_LIMIT_EXCEEDED`。完整 `CompositionLimits`、`FontLimits` 和每个图形的
`CanvasResourceLimits` 都必须显式提供，并受工作流总资源策略约束。

字体复用 T19 的显式 FontSource、ReferenceFontSet、确定性选字、嵌入与子集机制，
不使用系统字体或联网查找。IN_PROCESS 与 HARDENED_WORKER 接受相同声明；命令顺序、
Query barrier、Session 生命周期、调用方流/通道所有权及签名/密码权限约束继续有效。
当前仅向文档追加新页面，不填充已有页面区域。缩进、tabs、keep、widow/orphan、
高级 overflow/relayout、表格和 Unicode shaping/布局不在 T24 范围内。

完整英文契约和示例见 [Paragraph composition](../paragraph-composition.md)。
能力仍为 experimental；实现票关闭或本机验证通过都不代表 Foundation 兼容性认证。

### 高级段落分页（T25）

选择 `Paragraph.version2`、`ParagraphFlow.version2`、`ComposeParagraphs.version2`
和 `CompositionLimits.version2()` 可启用高级分页。原来的 version1 调用保持原行为。
新增限额 `maximumLayoutAttempts`（每次布局的候选行及搜索步数）和
`maximumRelayouts`（当前缓冲流的重新布局尝试数）必须显式设置。

`indentation(left, right, firstLine)` 设置左右缩进和仅首行生效的额外缩进，单位为点；
左右非负，首行可为负，但与左缩进之和不能为负。跨区域、跨页续行不重复首行缩进。
文本内的 tab 在 version2 中定位其后的完整字段，不输出字符。`tabStop` 支持左对齐、
居中、右对齐及指定字符锚点；默认重复间距为 36 点，`tabInterval` 可修改。
停靠点相对于段落左缩进，不会使笔位置后退；含 tab 的行保持左对齐。

`keepTogether(true)` 要求整段位于同一区域；`keepWithNext(true)` 要求末行与下一段的
首个片段同区。`widows(n)` 限制每个续行片段的最少行数，`orphans(n)` 限制每个跨区
片段在分割前的最少行数，二者默认 1。规则也适用于同页多栏，且不会静默放宽。
有限区域无法满足约束时返回 `COMPOSITION_CONSTRAINT_UNSATISFIED`；搜索超限时返回
`COMPOSITION_LIMIT_EXCEEDED`。

`overflow` 默认 WRAP，长词可按标量拆行；REJECT 保持单词完整并寻找能容纳它的区域；
VISIBLE 允许完整长词、图形或 tab 字段的横向墨迹超出区域。所有模式的垂直溢出都只
使用显式声明的有限区域，不截断、丢弃内容或无限增页。

version2 默认 `FlushMode.BUFFERED`。在同一个 Session 内，查询能立即看到布局结果，
随后可执行 `RelayoutParagraphs.version1(newPages...)`，使用原语义内容和已准备的字体
重新布局。新结果成功后才替换该流追加的页面，失败保留上次成功的内容。
`FlushMode.IMMEDIATE` 或 `FlushParagraphs.version1()` 释放缓冲并禁止 relayout；
其他成功的修改命令也会封存之前的段落流。此时 relayout 返回
`COMPOSITION_RELAYOUT_UNSAFE`。flush 不提前发布目标文件。

回调结束后的 Session 已失效，继续 relayout 会抛出既有的 `IllegalStateException`；
重开已发布 PDF 没有语义缓冲，返回 `COMPOSITION_RELAYOUT_UNSAFE`。
无签名文件的 REWRITE/INCREMENTAL、命令顺序、字体所有权及密码权限合同继续适用。
英文合同及完整边界见 [Advanced paragraph pagination](../paragraph-pagination.md)，
[独立验收记录](../../capabilities/evidence/T25-paragraph-pagination.md) 分别记录每项规则。
此能力仍为 experimental，不代表 standards、依赖门槛或 Foundation 字体平台认证已完成。
