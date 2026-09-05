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
