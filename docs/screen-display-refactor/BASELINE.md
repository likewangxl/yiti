# S00 基线与范围证据

生成日期：2026-09-22 ；状态：READY_FOR_REVIEW（等待主代理独立复核）。

## 仓库基线

- 仓库绝对路径：`E:\cx-workspace\yiti`
- 执行时 HEAD：`dced49aaec6220b56991d774b0cae8435115d795`
- 分支：`feat/code-screen-panorama`
- `SCOPE.json.baselineCommit`：`dced49aaec6220b56991d774b0cae8435115d795`
- remote：`origin`，脱敏主机/仓库标识 `github.com/likewangxl/yiti`（未记录凭据）

## 启动时已有修改

启动快照显示工作区没有已跟踪的 staged/unstaged 修改，存在以下用户已有未跟踪规划文件；本任务保留它们，不将其作为 S00 越界：

- `docs/screen-display-refactor/EXECUTION_PLAN.md`
- `docs/screen-display-refactor/SCOPE.json`
- `docs/screen-display-refactor/START_PROMPT.md`
- `docs/分行经营大屏改造方案-2026-09-22.md`

上述文件按启动时字节内容记录在外部 `startup-state.json`，后续若内容变化，检查器会重新判断，不会用启动快照掩盖新的改动。

## 外部证据目录

所有基线与保护区哈希均写在仓库外，绝对路径为：

`E:\cx-workspace\runtime\screen-display-refactor\S00-20260922-01`

- `baseline.json`：仓库、提交、分支、脱敏 remote、启动路径和基线跟踪文件清单。
- `protected-hashes.json`：2,864 个 deny/restricted 跟踪文件的 SHA-256 与存在状态；其中 `DENY` 文件用于硬性哈希/删除检查，`RESTRICTED` 文件用于人工逐 hunk 复核提示。
- `startup-state.json`：启动时用户脏文件的字节哈希和路径快照。
- `capture-summary.txt`：不含凭据的摘要。
- `environment.txt`：Java/Maven/Node/npm 版本和命令级 JDK17 验证记录。

## S00 实现边界

本任务只新增或修改 `BASELINE.md`、`TASK_STATUS.md`、`check-scope.mjs` 和 `__tests__/check-scope.test.mjs`。检查器只读 Git 与文件状态，不修改业务文件、`SCOPE.json` 或工作区内容；外部证据目录不属于仓库提交内容。

检查器覆盖：NUL 分隔 Git 输出、中文路径、已提交/暂存/未暂存/未跟踪变更、重命名两端、删除、默认拒绝、deny > restricted > allow、启动时已有脏文件豁免、保护区哈希/删除和受限文件人工复核提示。

## 环境与限制

- `java -version`：17.0.18。
- 默认 `mvn -version` 现场显示 `JAVA_HOME` 为 JDK11；按计划仅对当前命令设置 `JAVA_HOME=C:\Program Files\Java\jdk-17.0.18` 后复核，Maven 实际使用 JDK17.0.18；未修改全局环境。
- Node：v24.14.0；npm：8.19.4。
- 本任务未执行 Maven、前端构建、真库测试或浏览器验收；这些不属于 S00 的必要验证。
