# S00～S17 任务状态

本表按执行计划维护；`READY_FOR_REVIEW` 表示已交付、等待独立核验，不等同于 `VERIFIED`。

| task_id | status | start_commit | end_commit | changed_files | code_status | test_status | live_status | evidence | blockers | next_step |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S00 | VERIFIED | `dced49aaec6220b56991d774b0cae8435115d795` | - | `BASELINE.md`、`TASK_STATUS.md`、`check-scope.mjs`、`__tests__/check-scope.test.mjs` | implemented; 主代理已独立复核 | `node --test .../check-scope.test.mjs`：10 passed, exit 0；实际范围检查 exit 0；`git diff --check` exit 0 | N/A | `E:\cx-workspace\runtime\screen-display-refactor\S00-20260922-01` | 无 | 进入 S01 |
| S01 | VERIFIED | `dced49aaec6220b56991d774b0cae8435115d795` | - | `DATA_CAPABILITIES.md`、`BUSINESS_DECISIONS.md`、`TASK_STATUS.md` | 源码与会议纪要只读盘点完成；主代理已逐项复核 | 15/12/13槽位覆盖、6来源、D01～D12、外部依赖、链接和敏感模式检查均通过；范围检查与`git diff --check`通过 | N/A；未连接数据库、未调用运行API | 本目录两份S01文档 | 具体配置、真实数据与业务口径仍为UNCONFIRMED | 等用户评估消耗后再决定是否执行S02 |
| S02 | VERIFIED | `40aa91ea` | - | 前端`screen/presentation/contract/`与测试；后端`dto/req/presentation/`与`ScreenPresentationContractTest`；`PRESENTATION_CONTRACT.md`、`TASK_STATUS.md` | 七类组件展示子协议已实现并经主代理复核 | 前端9项、后端6项定向测试通过；模块结构4项和BizAuth 2项通过；范围/格式检查通过 | N/A；纯契约任务 | 本地测试输出与本任务文档 | 保存发布尚未接线，属于S03；两个既有Controller实体依赖架构测试失败与本轮无关 | 创建S02聚焦提交后进入S03 |
| S03 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S02 |
| S04 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S03 |
| S05 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S02、S03 |
| S06 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S03、S05 |
| S07 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S02、S06 |
| S08 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S02、S06 |
| S09 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S07、S08 |
| S10 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S07、S08 |
| S11 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S01、S02 |
| S12 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S10、S11 |
| S13 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S09、S12 |
| S14 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S04、S07～S13 |
| S15 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S03、S04、S06、S14 |
| S16 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S07～S15 |
| S17 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S16、隔离运行环境 |

## S00 任务记录

- Red：先新增 10 个行为测试，在检查器不存在时运行 `node --test docs/screen-display-refactor/__tests__/check-scope.test.mjs`，10 个测试失败，退出码 1；失败原因为待实现的 `check-scope.mjs` 不存在。
- Green：实现最小只读检查器后，同一命令 10/10 通过，退出码 0。
- 实际范围检查：`node docs/screen-display-refactor/check-scope.mjs --manifest docs/screen-display-refactor/SCOPE.json --baseline-dir "E:\cx-workspace\runtime\screen-display-refactor\S00-20260922-01"`，退出码 0；启动时已有 4 个规划路径被保留，路径/哈希检查通过。
- 未执行：Maven、前端构建、数据库测试、浏览器验收和提交/推送。
- 主代理独立复核：重新运行 10 项检查器测试、实际范围检查和 `git diff --check` 均通过；回读外部证据确认 HEAD/分支正确、4 个启动时已有规划路径被保留、2,864 个保护/受限跟踪文件已记录，remote 不含用户信息，证据目录未发现密码/令牌样式内容。S00 状态更新为 `VERIFIED`。

## S01 任务记录

- 只读核对会议纪要、数据源管理/查询控制器、请求与响应DTO、机构目录DTO、命名机构组策略、来源保存/查询分派，以及分行/对公/零售绑定契约。
- 新增 `DATA_CAPABILITIES.md`：定义能力/就绪状态，登记六类来源、三类模板全部槽位、限制和外部依赖；所有具体metricCode/sourceId及真实数据保持待现场验证。
- 新增 `BUSINESS_DECISIONS.md`：记录D01～D12，区分会议结论、源码事实、仍需确认、影响任务和建议默认行为。
- 未连接数据库、未调用运行或写接口、未修改业务源码、未执行构建/浏览器验收、未提交或推送。
- 主代理复核时发现并修正三处静态矩阵错误：零售排名候选字段、零售关注可选字段、零售目标单位约束；随后复跑结构、链接、范围、敏感模式与格式检查，S01更新为`VERIFIED`。

## S02 任务记录

- Red：前端依赖安装完成后，`displayContract.spec.js` 因目标契约模块不存在失败；后端 `ScreenPresentationContractTest` 因 `dto.req.presentation` 类型不存在而编译失败。首次前端运行发现node_modules缺失，仅记为环境准备，不作为Red证据。
- Green：前端展示契约9项测试通过；后端纯契约6项测试通过，使用命令级JDK17且未连接数据库。
- 保留根/运行协议版本2；新行为仅由 `displaySchemaVersion=1` 启用。DTO与前端契约均不使用任意脚本或URL，后端业务服务尚未接线。
- npm按现有锁文件安装依赖，package及锁文件未计划修改；未运行浏览器、真库或发布验证，未提交或推送。
- 主代理复核补强来源AUTO单位拒绝、必需嵌套对象、负数样式、系列/列/页签/排名稳定身份与字段完整性，并修正Java嵌套项重复后未继续校验的问题。
- 扩大架构检查中，`RptModuleStructureArchTest` 4项与 `RptBizAuthConsistencyArchTest` 2项通过；`RptNoEntityInControllerArchTest` 和 `RptNoEntityInControllerLocalsArchTest` 因既有 `FreeReportController.listBatches` 返回 `RptFreeReportBatch` 失败。本轮未修改该控制器或实体，按范围仅记录，不越界修复。
