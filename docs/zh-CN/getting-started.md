# Open PDF 快速开始

> **独立项目声明：** Open PDF by ZeroCloud 与 LibrePDF OpenPDF、Apryse 或
> iText 没有隶属、赞助或背书关系。

Open PDF 是采用 Apache-2.0 许可证的 Java 组件套件，Maven 坐标位于
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

## 创建并重新打开空白 PDF

```java
import java.nio.file.Path;
import java.nio.file.Paths;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.query.PageCount;

Path output = Paths.get("blank.pdf");
DocumentWorkflow workflow = new DocumentWorkflow();

workflow.execute(WorkflowRequest.create(output), session -> {
    session.execute(AddBlankPage.INSTANCE);
    return null;
});

WorkflowOutcome<Integer> inspected = workflow.execute(
        WorkflowRequest.open(output),
        session -> session.query(PageCount.INSTANCE));

System.out.println(inspected.getResult()); // 1
```

T01 仅实现单个路径目标的新建流程以及单个路径来源的只读流程。具名多来源、
多目标、增量保存、期限、取消和完整资源策略属于后续任务。PDFBox 仅作为
`pdf-document` 的内部实现依赖，不会出现在公开接口中。

英文 README、Javadoc、ADR、Capability Matrix 和 API 契约是权威规范；本页
提供中文使用说明。如有安全问题，请按 [SECURITY.md](../../SECURITY.md) 中的
临时私密报告方式联系维护者。
