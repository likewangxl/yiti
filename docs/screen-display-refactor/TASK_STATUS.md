# S00～S17 任务状态

本表按执行计划维护；`READY_FOR_REVIEW` 表示已交付、等待独立核验，不等同于 `VERIFIED`。

| task_id | status | start_commit | end_commit | changed_files | code_status | test_status | live_status | evidence | blockers | next_step |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S00 | VERIFIED | `dced49aaec6220b56991d774b0cae8435115d795` | - | `BASELINE.md`、`TASK_STATUS.md`、`check-scope.mjs`、`__tests__/check-scope.test.mjs` | implemented; 主代理已独立复核 | `node --test .../check-scope.test.mjs`：10 passed, exit 0；实际范围检查 exit 0；`git diff --check` exit 0 | N/A | `E:\cx-workspace\runtime\screen-display-refactor\S00-20260922-01` | 无 | 进入 S01 |
| S01 | VERIFIED | `dced49aaec6220b56991d774b0cae8435115d795` | - | `DATA_CAPABILITIES.md`、`BUSINESS_DECISIONS.md`、`TASK_STATUS.md` | 源码与会议纪要只读盘点完成；主代理已逐项复核 | 15/12/13槽位覆盖、6来源、D01～D12、外部依赖、链接和敏感模式检查均通过；范围检查与`git diff --check`通过 | N/A；未连接数据库、未调用运行API | 本目录两份S01文档 | 具体配置、真实数据与业务口径仍为UNCONFIRMED | 等用户评估消耗后再决定是否执行S02 |
| S02 | NOT_STARTED | - | - | - | - | - | - | - | - | 等 S01 |
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
