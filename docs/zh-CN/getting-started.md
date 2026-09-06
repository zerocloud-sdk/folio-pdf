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
## Unicode 分段与双向排版（T28，experimental）

现有 `ComposeParagraphs` 各版本及表格段落现在使用固定 ICU4J 77.1 处理字素、词、
断行、script 和 bidi。相邻 text inline 合并分析，换行和视觉重排都不拆开组合序列。
请传入逻辑顺序文本，不要事先把 RTL 字符倒序。方向按首个强方向字符确定，没有时
使用 LTR；数字保持内部顺序，奇数方向层级的括号等字符使用镜像映射。方向控制符
参与分析但不绘制，仍计入输入配额。

字体必须显式提供，按声明顺序逐标量回退。例如：

```java
FontSelection fonts = FontSelection.explicit(
        FontSource.path(Paths.get("/my/fonts/NotoSans-Regular.ttf")),
        FontSource.path(Paths.get("/my/fonts/NotoSansCJKsc-Regular.ttf")));
```

SC、TC、JP、KR 的顺序决定区域字形；默认 Locale、系统字体和联网查找不参与选择。
项目测试使用的完整静态 Noto 字体附有版本、SHA-256、来源与 OFL notices，只属于
测试/验收资源，不随默认产品提供。产品支持所述静态 TrueType 格式，不会自动把
可变字体实例化。缺字仍通过既有错误返回。

ICU 不负责 shaping：此处没有 GSUB/GPOS、连字、kerning、组合附加符定位、阿拉伯或
印度文字上下文塑形、韩文 Jamo 合成、变体序列选字、断字或竖排。已编码的预组合字形
可以正常选取。HarfBuzz 属于 #30，亚洲字体资源产品属于 #34。

公开重开查询观察 PDF 绘制顺序，因此 `PageText` 返回视觉顺序与实际镜像字符，
不包含方向控制符或强制换行符，也不重建原始逻辑段落；需要逻辑文本的应用应保存
输入声明。本次没有源代码或二进制接口破坏，但旧版的 ASCII/标量断行、页数和操作
字节数可能改变。已有资源上限与失败前不发布约束继续生效；过宽的完整簇不会被拆碎。

两种模式执行同一处理。完整字体的名称、cmap 别名与 GSUB 元数据按解析生命周期
计入预算，已加载字体保持 Session 计量。七配置合并验收为同时保留六个完整字体，
显式使用 2 GiB 计量内存预算和 1 GiB Worker 堆；单独 Unicode 公共测试声明 1 GiB
计量内存并保留默认 Worker 设置，32/160 MiB 负例验证资源拒绝与目标文件保留。产品
默认值没有修改。完整英文边界和迁移说明见 [Unicode Composition](../unicode-composition.md)。
Linux/JDK 验证不代表 Windows、macOS 或完整 Foundation 认证；能力仍为 experimental。

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
更高时行框随之扩大。行内图形底边与文本基线对齐，不拆分。T28 现在使用 Unicode
断行机会，过长内容只在完整字素簇边界拆行；空格会保留。LF、U+2028、U+2029 显式
换行，版本 1 的 tab、CR 和孤立代理项会失败。JUSTIFIED 在含空格的完整簇之后扩展
字距，不把空格与随后的组合附加符推开。

`ParagraphFlow.areaBreak()` 明确前进到下一个区域。页面声明是有限列表，区域耗尽或
内容无法放入剩余区域时返回 `COMPOSITION_AREA_EXHAUSTED`，不会无限生成页面。
非法几何返回 `COMPOSITION_INVALID`；声明、行数和页面操作字节超限返回
`COMPOSITION_LIMIT_EXCEEDED`。完整 `CompositionLimits`、`FontLimits` 和每个图形的
`CanvasResourceLimits` 都必须显式提供，并受工作流总资源策略约束。

字体复用 T19 的显式 FontSource、ReferenceFontSet、确定性选字、嵌入与子集机制，
不使用系统字体或联网查找。IN_PROCESS 与 HARDENED_WORKER 接受相同声明；命令顺序、
Query barrier、Session 生命周期、调用方流/通道所有权及签名/密码权限约束继续有效。
当前仅向文档追加新页面，不填充已有页面区域。缩进、tabs、keep、widow/orphan、
高级 overflow/relayout 和表格使用后续版本；Unicode 行为见 T28，shaping 仍单独规划。

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


## 有界表格组成（T26，experimental）

使用 `ParagraphFlow.version3`、`ComposeParagraphs.version3` 和
`CompositionLimits.version3`，即可在同一个 Document Workflow 中混排段落、
表格和显式区域换行。字体仍需通过 `ReferenceFontSet` 或 `FontSelection.explicit`
明确提供。已有 version 1/2 段落调用保持原有行为。

```java
Table table = Table.version1(Table.Layout.FIXED, TableWidth.points(200),
        TableWidth.points(40), TableWidth.percentage(25), TableWidth.auto())
        .row(TableRow.version1(
                TableCell.version1().paragraph(Paragraph.version1(12).text("A", 10).build()).build(),
                TableCell.version1().paragraph(Paragraph.version1(12).text("B", 10).build()).build(),
                TableCell.version1().paragraph(Paragraph.version1(12).text("A", 10).build()).build()))
        .build();
ParagraphFlow flow = ParagraphFlow.version3(FontSelection.referenceFontSet())
        .page(LayoutPage.version1(612, 792, PageMargins.of(72, 72, 72, 72)))
        .table(table).build();
workflow.execute(request, session -> {
    session.execute(ComposeParagraphs.version3(flow, limits));
    return session.query(PageCount.INSTANCE);
});
```

此固定布局的列宽是 40、50、110 点。表格百分比相对当前 Layout Area，列宽和
单元格最小宽度的百分比相对整张表。`AUTO` 布局保留显式列宽，根据内容最小宽度、
自然行宽及跨度约束求解自动列；完整算法与独立数值样例见
[英文公共契约](../table-composition.md)。
该确定性算法不重新分配已满足的最小宽度；若跨度最小宽度占满整表而产生零宽列，
会拒绝布局。可明确指定正列宽，或给整表留出剩余宽度。

单元格的 `rowspan`、`colspan` 默认为 1；按声明顺序占据所在行第一个空闲列。
每个网格位置必须恰好被一个单元格覆盖，跨行占满的后续行需显式声明为空行。
`CellPadding` 和 `TableBorders` 按上、右、下、左声明点数，默认均为 0。
黑色边条完全位于单元格内部，相邻边的可见厚度相加，跨度内部不画分隔线。
单元格内容由零个或多个 version-1 Paragraph 组成，提取顺序遵循单元格声明顺序。

`CompositionLimits.version3()` 要求完整的原有组成限制、`maximumLayoutAttempts`
及 `tableLimits(TableLimits.builder()...)`。后者的 maximumTables、maximumRows、
maximumCells、maximumGridSlots 和 maximumLayoutWork 按整次命令累计，
maximumColumns 按每张表限制。字体、图形及 Workflow 的资源预算继续生效。

整张表必须放入一个区域，放不下时尝试后续显式区域；不会拆表、拆行或自动补页。
非法跨度返回 `TABLE_INVALID_SPAN`，无解几何返回 `TABLE_CONSTRAINT_UNSATISFIED`，
超过资源预算返回相应稳定限制错误。传播到 Workflow 外的失败保留原目标并返回
`NOT_ATTEMPTED`。version 3 不保留可 relayout 的表格；重复表头、分页拆行、表格
keep 和增量大表 flushing 属于后续 #28。

两种执行模式使用相同声明与公共行为测试。当前仍为 experimental，独立 standards
验证、依赖兼容性与 Foundation 字体/平台认证尚未完成；T26 不表示完整表格能力已认证。

## 表格分页扩展（T27，experimental）

T27 使用 `Table.version2`，配合 `ParagraphFlow.version4`、
`ComposeParagraphs.version4` 和 `CompositionLimits.version4` 显式启用分页。
已有 version-3 整表布局仍可继续使用；不能在旧版本流中混入新版本表格。
版本 4 的限制包含 tableLimits、maximumLayoutAttempts 和 maximumRelayouts。

新表格按显式 Layout Area 的顺序跨区域、跨页排版，FIXED/AUTO 列宽在当前
区域重新求解。行可在完整文本行之间拆分；每个续接单元格片段重新应用内边距和
内部边框。rowspan/colspan 保留其剩余跨度，已经输出的正文不会在续页重复。
使用 `splitRows(false)` 可要求整行放置。页声明用尽时会明确失败，不自动补页。

`header(TableRow)` 和 `footer(TableRow)` 分别添加重复表头、表尾。
它们使用相同的列声明，各自必须是完整网格，跨度不能跨越表头、正文或表尾。
每个正文片段依次输出表头、正文、表尾；表尾紧接正文。
`skipFirstHeader(true)` 只省略首片段表头，`skipLastFooter(true)` 只省略
末片段表尾，省下的空间参与布局计算。重复输出的文本行同样消耗行数预算。

`keepTogether(true)` 要求整表放入一个区域；`keepWithNext(true)` 把末片段
与下一个流元素绑定。约束无法满足时返回 `TABLE_CONSTRAINT_UNSATISFIED`。
`overflow(Paragraph.Overflow.WRAP / REJECT / VISIBLE)` 控制单元格的水平溢出：
按字符兜底换行、拒绝拆开过长单词，或完整绘制超出内容框的单词/图形。
三种模式都不允许垂直裁掉内容；超高原子图形必须移到可容纳它的区域，否则失败。

版本 4 默认 BUFFERED，也可显式传入 `ComposeParagraphs.FlushMode.IMMEDIATE`。
`RelayoutParagraphs.version1(pages...)` 用新页面声明替换保留布局；失败时保留
上一次成功结果，并消耗一次重排预算。`FlushParagraphs` 释放声明后禁止重排；
flush 不会提前发布文件。所有输出仍由 Document Workflow 成功结束后统一进入
既有发布流程。

增量大表使用 `BeginLargeTable.version1(flow, limits, maximumRetainedRows)`，
其中版本 4 的 flow 只包含一张尚无正文行的版本 2 FIXED 表和有限页面声明。
AUTO 表格布局、AUTO 或缺失表宽不适用。begin 固定表头、表尾、列宽和字体快照；
随后用 `AppendTableRows.version1(rows...)` 追加正文。

`FlushTable.version1()` 输出已确定的片段并释放完整行跨度组；暂留最后一个
未确定片段和未完成的 rowspan。`InspectLargeTable.version1()` 返回累计接收、
当前保留、已完全释放的正文行数。保留行数上限和 Workflow owned-memory 预算
在接收新批次前生效；它们不表示 JVM heap 或 RSS。行数、单元格、网格槽位、
inline、字符以及布局和 fallback 工作量使用累计预算，flush 不会重置。

保留内存包含嵌套图形程序、图片字节、显式/软蒙版、ICC 与颜色空间数组、
字形数据和固定元数据；接收前只读取长度，不复制载荷或打开资源。同一声明
的重复引用按保守估算分别计费。初始表头/表尾保留到 complete，正文图形预算
随保留行一起释放；临时布局和 Worker 传输还会占用重叠预算。超出 Workflow
内存预算返回 `MEMORY_LIMIT_EXCEEDED`，既有目标保持原样，发布为 `NOT_ATTEMPTED`。
每个 Composition 图形只在单次绘制期间保留声明缓存，递归绘制内部仍可复用资源；
不同摆放位置和重复表头/表尾可分别生成 PDF 资源，并受累计对象/存储预算约束。
输出页面持有 PDF 资源，原始图形声明随对应表格状态释放。

最后必须调用 `CompleteTable.version1()`，完成末片段并处理最终表尾省略。
开放中的表格拒绝无关修改及 `RelayoutParagraphs`；可重排的表格流应使用前述
BUFFERED `ComposeParagraphs.version4`。遗漏 complete 会使 Workflow 失败，
保留既有目标，并返回 `NOT_ATTEMPTED`；flush 始终不会提前发布。

T27 的固定字体 qpdf、独立语义与 19 页 PDFium/ImageMagick 验收链均通过，
当前仍为 experimental；独立 standards、依赖兼容性与 Foundation 字体/平台
认证尚未完成。完整规则与固定数值样例见
[英文 T27 契约](../table-pagination.md)，完整验证与独立代码评审结果见
[T27 记录](../../capabilities/evidence/T27-table-pagination.md)。
