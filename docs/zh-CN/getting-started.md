# Folio PDF 快速开始

> **独立项目声明：** Folio PDF by ZeroCloud 与 LibrePDF OpenPDF、Apryse 或
> iText 没有隶属、赞助或背书关系。

Folio PDF 是采用 Apache-2.0 许可证的 Java 组件套件，Maven 坐标位于
`net.zerocloud` 命名空间。项目以 Java 8 为运行时基线，并在 JDK 8、11、
17 和 21 上验证。当前版本仍处于实验阶段，尚未发布 Foundation Release。

## 构建

无需安装系统 Maven；仓库内的 Maven Wrapper 固定使用 Maven 3.9.16。
T29 测试需要单独安装 HarfBuzz 10.2.0 及项目 helper，准备方式见下方的
[显式塑形](#显式-harfbuzz-塑形t29experimental) 和
[完整安装合同](../harfbuzz-shaping.md#explicit-installation)。构建前声明其绝对路径：

```sh
export FOLIO_HARFBUZZ_HELPER=/explicit/folio-harfbuzz-10.2.0/bin/folio-harfbuzz
./mvnw -B -ntp verify
```

普通构建保留认证记录器的行为测试，包括缺工具时必须保留 `INDETERMINATE`
记录。T03/T09/T10 中依赖独立验收工具的测试须先按
[固定工具安装说明](../third-party/t03-standards-tools.md) 完成配置，再显式运行：

```sh
./mvnw -B -ntp -Pindependent-certification -pl pdf-acceptance -am \
  -Dtest=T03EvidenceCommandTest,T09EvidenceCommandTest,T10EvidenceCommandTest,T10StandardsQualificationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

显式认证测试缺工具时会失败，不会跳过或改记为通过。普通构建成功不代表已完成
独立认证；最终候选的八个环境／执行模式组合仍由 Foundation runner 单独认证。

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

## Stable Migration Facade：创建、发布、重开

选择 `net.zerocloud:pdf-migration-itext7`，不要同时引入 Preview。当前两种 artifact
保留相同的 12 项生命周期映射；T09 增加的值映射见下文。以下代码兼容 Java 8：

```java
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDocument;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfReader;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfWriter;
import net.zerocloud.pdf.itext7.layout.Document;

PdfDocument pdf = new PdfDocument(new PdfWriter("blank.pdf"));
pdf.addNewPage();
new Document(pdf).close(); // 关闭时由 Native Workflow 发布；重复 close 不会重试发布。
try (PdfReader reader = new PdfReader("blank.pdf");
     PdfDocument reopened = new PdfDocument(reader)) {
    System.out.println(reopened.getNumberOfPages()); // 1
}
```

`PdfReader` 返回前已释放原始 Path，并保留有界私有快照供后续检查。
`PdfDocument` 接管快照后负责在关闭时删除；提前关闭 Reader 不会删除 Document 正在使用的快照。
`Document.close()` 负责关闭关联的 `PdfDocument`；已关闭对象不能继续查询或添加页。
发布失败会映射为带 Native 安全失败信息的 `PdfException`。
Facade 沿用 IN_PROCESS 默认值；HARDENED_WORKER 通过 Native `WorkflowRequest`
显式选择，Facade 没有增加执行模式配置入口。

#70 的历史认证只覆盖空白文档事务与生命周期映射，范围为固定 Ubuntu 24.04/Linux x86-64
的 JDK 8/11/17/21。Windows、macOS 和其他 Foundation obligation 仍未认证。
实际候选产物、32 条证据记录和复跑方法见[英文认证合同](../t03-certification.md)。

## T09 PDF 值检查与修改

Native `DocumentPatch` 支持按声明顺序插入、替换、删除字典条目和数组元素，
替换间接对象的直接值，以及替换 stream 解码数据。`PdfValuePath` 从当前 Session
的 `ObjectReference` 开始，逐级选择字典名或从零开始的数组下标。普通非法 Patch
会整体回滚；捕获失败后仍可检查、继续合法修改、发布并重开。资源策略耗尽属于
终止失败，不能继续发布。完整英文契约见 [PDF Values and Document Patches](../document-values.md)。

Facade 的 Reader/Writer 构造方式可修改现有文档：

```java
import net.zerocloud.pdf.itext7.kernel.pdf.PdfDictionary;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfName;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfNumber;
import net.zerocloud.pdf.itext7.kernel.pdf.PdfStream;

try (PdfReader reader = new PdfReader("input.pdf");
     PdfDocument document = new PdfDocument(reader, new PdfWriter("output.pdf"))) {
    PdfDictionary root = document.getCatalog().getPdfObject();
    root.put(new PdfName("Counter"), new PdfNumber(2));
    ((PdfNumber) root.get(new PdfName("Counter"))).setValue(7);
    root.put(new PdfName("Data"), new PdfStream(new byte[] {1, 2, 3}));
    ((PdfStream) root.get(new PdfName("Data"))).setData(new byte[] {4, 5});
} // 等待真实 Native REWRITE 发布完成，再释放快照。
```

九类值都有映射。字典和数组的 `get` 默认解引用，传 `false` 可取得原始引用；
引用只属于创建它的 Session，跨文档使用会收到带真实 Native cause 的
`PdfException`。容器和 stream 视图在关闭后以 `PDF_VALUE_VIEW_EXPIRED` 失败，
标量仍可读取。每次引用检查共享 100,000 个遍历值和累计 64 MiB 解码数据的预算。
被插入的 detached 容器会复制；修改原 Java 容器不会修改已经插入的副本。

`PdfReader(InputStream)` 与 `PdfWriter(OutputStream)` 始终保留调用方流的所有权，
不会关闭这些流。输出在 Document 关闭时才写入并 flush；输出失败的实际 Native
`DocumentFailure` cause 中保留 Publication Receipt，调用方流可能已有部分数据。
重复关闭不会重试发布。

`PdfString(byte[])` 保留原始二进制数据，`getValueBytes()` 返回副本；文本转换规则
明确使用 PDFDocEncoding 或带 BOM 的 UTF-16BE/UTF-8。Java 文本含孤立代理字符时
会在修改前拒绝。stream 的 Length、Filter、DecodeParms 等元数据由引擎维护，
`setData` 使用 Flate；未知编码的数据在未触碰时原样保留，请求解码则安全失败。
版本、安全状态和页面树也受 Native 保护。

T09 的实现、公开测试和 jar 检查不等于八组独立认证。实际资格以当前候选绑定的
[Foundation Evidence 索引](../../capabilities/foundation-evidence.yaml)为准；
[T09 认证合同](../t09-certification.md)规定三种产物、四条证据链和 51 项标准规则。
Facade 实际运行 IN_PROCESS，不能把 Native Worker 的认证归于 Facade。
视觉验收固定为 144 DPI、零容差 AE=0；缺规则、工具、输入身份或不确定结果不能
通过。原始流保留检查读取增量文件的最终有效对象，不能只看旧前缀是否仍有原字节。

## T10 页面操作、合并与拆分

Native Interface 保留六个 version-1 命令：`InsertBlankPage`、`RemovePages`、
`MovePages`、`CopyPages`、`MergeDocuments` 和 `SplitDocument`。页码与范围均从
1 开始且包含两端；移动位置以移除所选范围后的序列为准，复制位置以原序列为准。
安全保留范围以外的页面图、批注关系或内容编码会在修改前以稳定失败码拒绝。

Stable Facade 提供按索引增加、取得、移除、移动和复制页面的成员，以及
`PdfDocument.getMerger()` 与 `getSplitter()`。具名来源／目标构造器接收
`Map<String, PdfReader>`、primary 来源名和 `Map<String, PdfWriter>`，并复制映射的
迭代顺序。合并按调用参数顺序消费完整的非 primary 来源；拆分必须恰好覆盖所有已
声明目标，并在一次 Native Workflow 中产生完整产品组。成功拆分后，后续 Document
命令以 `COMMAND_REJECTED` 失败。关闭后可通过 `getPublicationReceipts()` 取得按目标
声明顺序排列的真实不可变收据；失败 stream 可能含部分输出。

页面句柄在移动和复制后继续指向原页面身份。`PdfPage.getPdfObject()` 返回已有的
受验证 Values 视图。Document 在构造时验证所有 Reader 的唯一所有权，保留并最终
删除私有快照；调用方 stream 不会被关闭，Path 目标在真正发布前不会被截断。所有
Facade 页面操作固定使用 IN_PROCESS，未增加 Worker 配置入口。完整成员、生命周期、
失败和保留边界见[英文页面合同](../page-manipulation.md)。

T10 对 Native 的 IN_PROCESS／HARDENED_WORKER 分别在 Ubuntu 24.04/Linux x86-64
JDK 8、11、17、21 上认证，Facade 则单独记录实际 IN_PROCESS。每个 Native／Facade
产物都绑定 qpdf 语法、84 条经真实负例合格的 pdfcpu/Arlington 标准规则、公共 Native
重开语义和 PDFium/ImageMagick 零像素差视觉链。缺工具、缺规则、配置哈希变化、页序
错误或单像素变化均不能通过。当前候选的权威记录是
[Foundation Evidence 索引](../../capabilities/foundation-evidence.yaml)；Windows、
macOS 与其他未完成 Foundation obligation 不在本次认证范围内，全局状态仍为
NOT READY。复跑步骤见 [T10 认证合同](../t10-certification.md)。

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
## 显式 HarfBuzz 塑形（T29，experimental）

T29 为既有 Composition 命令接入项目自有的 HarfBuzz C 适配器。验收固定使用
HarfBuzz 10.2.0 和附有哈希的显式 Noto 字体，覆盖 Arabic、Hebrew、Devanagari、Thai。
原生库和 helper 必须另行安装，默认产品不捆绑它们，也不发现系统字体或在线下载字体。

源码包及哈希固定在 `scripts/harfbuzz-pin.properties`。准备 Python 3.12+、Meson
1.3.2+、Ninja、pkg-config 和本机 C/C++ 编译器后，执行：

```sh
python3 scripts/install-harfbuzz.py /explicit/harfbuzz-10.2.0.tar.xz \
  /explicit/folio-harfbuzz-10.2.0
```

安装目录必须不存在。命令不会下载依赖；`installation.json` 记录实际构建产物、
工具及源码哈希。安装成功只说明本机构建完成，不代表四平台验收通过。

仓库验证先显式导出 helper 路径。独立验收还需准备固定版本的 qpdf、PDFium、
ImageMagick，以及安装了 fontTools 4.59.2 的 Python 环境：

```sh
export FOLIO_HARFBUZZ_HELPER=/explicit/folio-harfbuzz-10.2.0/bin/folio-harfbuzz
export FOLIO_SHAPING_PYTHON=/absolute/python-with-fonttools-4.59.2
./scripts/acceptance /new/evidence-directory
```

入口输出原生数值、重开语义、实际嵌入子集、语法和八页视觉记录，并单独记录安装
追溯结果。Linux 观察器核对回执和文件哈希，再读取一次真实 helper 启动的加载映射；
它不逐次观察每个 Workflow 原生调用。存在 `LD_PRELOAD`/`LD_AUDIT` 符号插入时，
加载归属不能由该观察证明，结果保持 `INDETERMINATE`。其他平台的加载观察尚未实现。

使用 `pdf-conversion` 中的 `HarfBuzzCapabilityProvider`，提供 helper 的绝对路径、
已有的 staging 目录、精确版本 `"10.2.0"` 和有限的 `ProviderLimits`，再注册到
`WorkflowEnvironment`。在 `WorkflowRequest` 上显式选择：

```java
.providerPreference(ProviderPreference.prefer(
        ShapingRequest.CAPABILITY_ID, HarfBuzzCapabilityProvider.PROVIDER_ID))
```

仅注册 Provider 不开启塑形。开启后仍传入逻辑顺序文本，并声明 `FontSelection.explicit`
及既有排版/字体上限。每个完整 ICU 字素选择第一个能覆盖全簇的字体；不同字体分别
覆盖基字和附加符并不足以满足这个合同。相邻 inline 一起分段；字素内部若改变字号，
保留各自字号与整体断行边界，但分段塑形不保证跨字号附加符定位，可能出现原生引擎
插入的 dotted-circle 字形。
fallback 预算包含初始选字和整字素覆盖检查中的每次字符探测。

候选行按实际边界重新塑形和测量，窄行可以在原始 ICU 字素边界拆开先前形成的连字。
表格最小宽度比较原始字素边界上的合法片段划分，同时考虑独立字形与更窄的连字；
候选搜索及片段长度计入既有表格工作上限，实际断行仍须通过。嵌入子集包含实际替换字形及组合依赖；
ToUnicode 映射完整输入簇，ActualText 保存每个原生 run 的逻辑文本。公开查询仍按
绘制顺序返回 TextItem，不承诺跨 bidi run 重建整个原始段落，也不添加 Tagged PDF。
这类 ActualText 要求有效 PDF 版本至少为 1.5；较旧 Source 的增量塑形会以
`PDF_VERSION_UNSUPPORTED` 在发布前失败，即使文本和字体映射都只包含 BMP 字符，
也不会隐式升级 Source 版本。
开启塑形可能改变字形数、行数、页数和提取结果。直接 `DrawPositionedUnicodeText`
仍按既有未塑形合同执行。

IN_PROCESS 与既有 Linux/JDK 支持范围内的 HARDENED_WORKER 使用相同公开合同。
两者的原生 Provider 都由父进程调用；原生 helper 不受 PDF Worker 全面的文件系统、
网络、内存或 CPU 隔离。Provider 的字节上限、超时、进程终止与 staging 清理继续生效。
缺引擎、版本不匹配、错误结果或超限在发布前失败，目标保持原内容。
验收记录明确列出 Worker 的适用范围；Linux Worker 不可用时保留 IN_PROCESS
产物，但合并证据保持 `INDETERMINATE`。当前迁移 Facade 没有塑形映射，需通过
Native Interface 显式选择 Provider。

完整边界、注册示例、迁移与协议见 [HarfBuzz shaping](../harfbuzz-shaping.md)。
四平台证据尚未完整，缺失平台/工具记为 `INDETERMINATE`；本机测试不代表兼容性认证。

## Unicode 分段与双向排版（T28，experimental）

本节描述未显式选择 T29 塑形时的行为。

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
可以正常选取。显式 HarfBuzz 塑形见上节；亚洲字体资源产品属于 #34。

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
按 ADR-0040，Foundation 0.1.0 仅要求实际执行过的 Ubuntu 24.04/Linux x86-64
与 JDK 8、11、17、21 配置。Windows x86-64、macOS x86-64/arm64 明确未认证，
不属于本版本必需发布门禁。每次认证必须绑定实际 JDK vendor/build、系统镜像、
执行配置、原生引擎、工具和候选产物哈希；同架构或同 JDK 主版本不能代替证据。

运行 `./scripts/inventory readiness` 查看具体未完成义务；存在任何必需缺口时
命令返回非零。已完成切片的候选、环境和证据身份保存在当前索引；密码作用域、
表格阶段、其他独立证据、对应 Facade 和发布控制等义务仍需后续切片完成。
`inventory validate` 成功与常规 `verify` 成功只表示相应检查通过，不能宣称发布就绪。
完整状态见[生成的就绪报告](../generated/foundation-readiness.md)，
证据格式见[Foundation readiness](../foundation-readiness.md)。

借入的流和通道按声明各读取一次，保持打开。一个 Session 内，字节完全相同的私有
字体快照可以共享存储；每个声明仍计入内存用量，每次使用仍消耗字体来源数量与
累计字节配额。字体选择和调用方所有权不变。

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
高级 overflow/relayout 和表格使用后续版本；Unicode 行为见 T28，显式 shaping 见 T29。

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

## 一维矢量条码（T30，experimental）

`DrawBarcode1D.version1` 在已有的一基页码上绘制黑色 PDF 矢量条形，支持
Code128 的 AUTO/A/B/C、GS1、RAW、FNC1–4，普通/扩展 Code39、Codabar、
EAN-13/8、UPC-A/E、独立或组合的两位/五位附加码、ITF、MSI、POSTNET 和 PLANET。

```java
Barcode1D barcode = Barcode1D.builder(Barcode1D.Mode.EAN13, "590123412345")
        .moduleWidth(1).barHeight(48).quietZone(11)
        .guardExtension(5).supplement("05").build();
new DocumentWorkflow().execute(WorkflowRequest.create(output, SaveMode.REWRITE), session -> {
    session.execute(AddBlankPage.INSTANCE);
    session.execute(DrawBarcode1D.version1(1, barcode,
            CanvasMatrix.of(1, 0, 0, 1, 36, 144)));
    return null;
});
```

上例生成完整 EAN-13 `5901234123457` 并附加 `05`。尺寸单位是放置变换前的 PDF 点；
默认窄模块 1 点、高度 48 点、左右空白各 10 点，EAN-13 各 11 点。增大模块宽度时
须显式增大空白边距。邮政模式默认条宽 1.44 点、间距 3.24 点、长条 9 点、短条
3.6 点、左右空白各 9 点。邮政格式的支持不代表当前邮政业务准入认证。

需要文字时，向 builder 传入 `.humanReadable(BarcodeText.builder(selection, limits, 8).build())`，
其中 `selection` 是显式 `FontSelection`，`limits` 是完整的 `FontLimits`。默认在条形下方
居中，字体包围盒与最近条形边缘间隔 3 点；可选择 ABOVE、LEFT/RIGHT、校验字符显示、
Code39/Codabar 起止符显示及 `alternateText`。RAW 模式的文字必须显式提供替代内容。
文字复用 T19 字体契约，按标量顺序绘制，不做段落塑形；未声明文字时不读取字体。

普通 Code128 用 `Barcode1D.FNC1` 至 `FNC4` 表示功能符，反斜线字符串保持字面含义。
含显式 FNC4 时，AUTO 只选择 A/B 字符集，使数字串保留扩展字符的移位和锁定语义。
GS1 输入采用 `[01]09501101530003[10]LOT` 这样的 AI 记法。
RAW 用 `Barcode1D.rawCode128(104, 33, 34)` 指定起始符和数据符，库生成校验符及结束符。
Code39/Codabar/ITF/MSI 可选 `generateChecksum(true)`；ITF 无校验时输入位数须为偶数，
生成校验时须为奇数。零补位、去空格、非法字符替换均不会隐式发生。

非法数据、模式选项、几何或大小分别返回 `BARCODE_INPUT_INVALID`、`BARCODE_MODE_INVALID`、
`BARCODE_GEOMETRY_INVALID`、`BARCODE_LIMIT_EXCEEDED`。字体及工作流资源失败保留原有错误身份。
传播出回调的失败保留既有目标，发布收据为 `NOT_ATTEMPTED`。In-Process 与 Linux Hardened Worker
共享此契约；无签名的 INCREMENTAL 保留原始修订，签名和修改权限检查先于字体读取。
完整模式、长度、校验、尺寸和文字规则以[英文契约](../one-dimensional-barcodes.md)为准。

## 二维矢量条码（T31，experimental）

`Barcode2D` 支持 QR Model 2、DataMatrix ECC200 和标准 PDF417。
`DrawBarcode2D.version1` 在已有页面上放置可复用的矢量 Form；相同内容、局部尺寸和
颜色可跨页共享资源。`MeasureBarcode2D.version1` 返回含静区的尺寸和模块矩阵尺寸，
不要求页面存在，也不绘制内容。

```java
Barcode2D barcode = Barcode2D.builder(Barcode2D.Mode.QR, "Folio 二维 😀")
        .encoding("UTF-8").qrVersion(3)
        .qrErrorCorrection(Barcode2D.QrErrorCorrection.Q)
        .moduleWidth(2).moduleHeight(2).quietZone(8).build();
new DocumentWorkflow().execute(WorkflowRequest.create(output, SaveMode.REWRITE), session -> {
    Barcode2DSize size = session.query(MeasureBarcode2D.version1(barcode));
    session.execute(AddBlankPage.INSTANCE);
    session.execute(DrawBarcode2D.version1(1, barcode,
            CanvasMatrix.of(1, 0, 0, 1, 36, 144)));
    return size;
});
```

QR 可固定版本 1–40 和 L/M/Q/H 纠错级别，0 表示自动版本；不会自动提高指定的纠错
级别。DataMatrix 可选 AUTO、ASCII、C40、TEXT、X12、EDIFACT、BASE256、RAW，
通过 `dataMatrixSize(width,height)` 约束模块数，任一维为 0 时自动选择该维。
`rawDataMatrix(66,67)` 表示 `AB` 的数据码字，由库补齐填充、ECC 和模块排列。
RAW EDIFACT 的显式结束序列需要足够的数据容量；自动尺寸会保留其解释，固定尺寸
不足则明确失败。例如 `[240,5,240]` 的载荷是 `A`，自动选择 12×12，固定 10×10 会失败。

DataMatrix 的 Macro 05/06、FNC1、读者编程和 Structured Append 使用显式 builder
方法，不解析扩展字符串。序列位置是 1–total，total 为 2–16，文件 ID 为 1–64516；
从参考 API 的零基文件 ID 迁移时应加一。读者编程不能与 FNC1 组合，Macro 不能与
读者编程或序列头组合，RAW 不能再附加这些类型化头。AUTO Macro 与 FNC1 或非默认
ECI 组合时采用 ASCII 压缩，保留头部语义，但不保证最小尺寸。

PDF417 可选 AUTO、BINARY、RAW，ECC 为 -1（自动）或 0–8。列数为 1–30，行数为
3–90，总码字最多 928；0 表示自动选择该维。正的 `pdf417AspectRatio` 表示不含静区的
物理高度/宽度，与固定行列互斥。`pdf417Macro("001075",0,2)` 使用数字三元组文件 ID
（每组三位 000–899）、零基段号和总段数 1–99999，也支持单段 Macro。
`rawPdf417(1)` 的载荷是 `AB`；库生成长度、填充、行指示和 ECC。RAW 中不接受内嵌
Macro 控制，应另外使用类型化 Macro 方法。没有位图反色、Micro 或截短 PDF417 API。
RAW 的字符集 ECI 不会结束 901/924 字节压缩模式；901 的字节尾部和 924 的完整分组
仍须有效，回到文本压缩需要显式 900 切换码。

字符编码支持 Cp437、Shift_JIS、ISO-8859-1 至 -11、-13 至 -16、UTF-8。UTF-8 使用
ECI 26，支持完整 Unicode 标量往返；不会替换字符、规范化文本或降级到别的编码。
ISO-8859-12 未定义，会明确失败。`.eci(number)` 与 `.encoding(name)` 相互替换，
最后一次选择生效。RAW 的显式 ECI 仅描述已有码字，不会进行文本转码。

尺寸均为变换前的 PDF 点，原点在含静区完整框的左下角。默认模块宽 1，QR 和
DataMatrix 高 1，PDF417 行高 3。静区默认分别为 4、1、2 点；最小值分别是模块宽
的 4 倍、DataMatrix 模块宽/高的较大值、PDF417 模块宽的 2 倍。QR 模块须为正方形，
PDF417 行高至少为模块宽的 3 倍。改变一个尺寸不会自动改变其他尺寸。颜色默认黑色，
可用 `foregroundRgb` 指定 [0,1] 的 RGB 值。绘制不会填白背景，调用方须让页面已有和
后续内容保持静区与对比度。

不支持的编码或模式选项返回 `BARCODE_MODE_INVALID`，非法载荷/容量溢出返回
`BARCODE_INPUT_INVALID`，非法尺寸或奇异矩阵返回 `BARCODE_GEOMETRY_INVALID`，
声明长度或生成内容超限返回 `BARCODE_LIMIT_EXCEEDED`。资源失败保持既有终止语义。
传播出回调的失败保留已有发布目标，收据为 `NOT_ATTEMPTED`；非终止性的无效声明在
被回调捕获后也不会留下局部绘制。两种执行模式、权限检查、签名保护和无签名增量
保存均遵守既有 Workflow 契约。详见[英文契约](../two-dimensional-barcodes.md)和
[T31 固定验收 profile](../../capabilities/profiles/T31-two-dimensional-barcodes.md)。
独立标准认证及 compatible 依赖门槛仍未完成，本能力保持 experimental。
