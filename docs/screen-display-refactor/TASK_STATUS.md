# S00～S17 任务状态

本表按执行计划维护；`READY_FOR_REVIEW` 表示已交付、等待独立核验，不等同于 `VERIFIED`。

| task_id | status | start_commit | end_commit | changed_files | code_status | test_status | live_status | evidence | blockers | next_step |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S00 | VERIFIED | `dced49aaec6220b56991d774b0cae8435115d795` | - | `BASELINE.md`、`TASK_STATUS.md`、`check-scope.mjs`、`__tests__/check-scope.test.mjs` | implemented; 主代理已独立复核 | `node --test .../check-scope.test.mjs`：10 passed, exit 0；实际范围检查 exit 0；`git diff --check` exit 0 | N/A | `E:\cx-workspace\runtime\screen-display-refactor\S00-20260922-01` | 无 | 进入 S01 |
| S01 | VERIFIED | `dced49aaec6220b56991d774b0cae8435115d795` | - | `DATA_CAPABILITIES.md`、`BUSINESS_DECISIONS.md`、`TASK_STATUS.md` | 源码与会议纪要只读盘点完成；主代理已逐项复核 | 15/12/13槽位覆盖、6来源、D01～D12、外部依赖、链接和敏感模式检查均通过；范围检查与`git diff --check`通过 | N/A；未连接数据库、未调用运行API | 本目录两份S01文档 | 具体配置、真实数据与业务口径仍为UNCONFIRMED | 等用户评估消耗后再决定是否执行S02 |
| S02 | VERIFIED | `40aa91ea` | - | 前端`screen/presentation/contract/`与测试；后端`dto/req/presentation/`与`ScreenPresentationContractTest`；`PRESENTATION_CONTRACT.md`、`TASK_STATUS.md` | 七类组件展示子协议已实现并经主代理复核 | 前端9项、后端6项定向测试通过；模块结构4项和BizAuth 2项通过；范围/格式检查通过 | N/A；纯契约任务 | 本地测试输出与本任务文档 | 保存发布尚未接线，属于S03；两个既有Controller实体依赖架构测试失败与本轮无关 | 创建S02聚焦提交后进入S03 |
| S03 | VERIFIED | `06cdfe07` | - | `CodeScreenPresentationDTO`、`ScreenDisplayPayloadDTO`、`ScreenDisplayContractValidator`、`CodeScreenPresentationValidator`、`ScreenCanvasServiceTest`、`ScreenPresentationDisplayPayloadTest`及大屏契约文档 | 展示配置已接入既有JSON校验、CAS保存、发布包和回滚校验，并经主代理复核 | 展示/画布/配置相关147项测试通过；契约/范围/格式检查通过 | N/A；未连接数据库和浏览器 | 定向Surefire报告与三份契约文档 | 新block需先保存取得ID再被display引用；真实持久化留S17 | 创建S03聚焦提交并进入S04 |
| S04 | VERIFIED | `7ab88468` | - | `PublishedDatasourceDefinition`、`ScreenCanvasServiceImpl`、`ScreenDatasourceServiceImpl`、`ScreenConfigServiceImpl`及对应测试/文档 | 新展示发布包已冻结服务端来源定义；运行仍校验当前启停/权限；主代理已复核 | 展示/画布/配置/数据源相关256项通过；契约/范围/格式检查通过 | N/A；未连接数据库和浏览器 | Surefire报告与大屏契约文档 | 只冻结查询定义，不冻结上游结果；旧包保持兼容 | 创建S04聚焦提交并进入S05 |
| S05 | VERIFIED | `796bd95f` | - | `businessSourceCandidates.js`及测试、`PanoramaDatasourcePicker.vue`、`PanoramaBindings.vue`、操作指南、`TASK_STATUS.md` | 业务化来源分类/搜索/兼容/禁用说明已接入现有绑定页；主代理已复核 | 候选模型、Picker、Bindings共31项通过；生产构建与范围/格式检查通过 | N/A；未调用后端写接口 | Vitest与Vite构建输出、操作指南 | 真实候选仍取决于目标环境API；不新增上游能力 | 创建S05聚焦提交并进入S06 |
| S06 | VERIFIED | `34acb94c` | - | `presentationEditorModel.js`、`PresentationEditor.vue`及测试、`PanoramaBindings.vue`与集成测试、操作指南、`TASK_STATUS.md` | 三栏组件工作台已接入现有草稿保存与冲突链，主代理已复核 | presentation与Bindings共51项通过；生产构建及范围/格式检查通过 | 浏览器真实验收留S17 | Vitest/Vite输出与操作指南 | 新组件必须先绑定已有blockId；高级字段映射暂保留 | 创建S06聚焦提交并进入S07/S08 |
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

## S03 任务记录

- Red：严格展示payload测试先证明未知版本、未知字段、版本嵌套、未知枚举和非当前组件树block引用未被旧校验器拒绝。
- Green：新增 `ScreenDisplayPayloadDTO` 并保持版本在presentation根；严格反序列化后复用S02跨字段校验。展示引用必须属于当前组件树，跨屏/悬空引用继续Fail Close。
- 既有Canvas保存无需新增表或端点：canvasStyle、组件树和block更新仍在同一事务/CAS；发布包复制同一style，回滚恢复归档style和组件树。定向服务测试验证字段保存、发布快照及新协议回滚。
- 当前前端若同一次创建新ChartWidget并配置展示引用，必须先保存获得blockId再二次保存；没有引入客户端临时ID旁路。
- 主代理终审扩展到 `CodeScreenPresentationValidatorTest`、`ScreenConfigServiceTest` 和完整 `ScreenCanvasServiceTest`，相关147项均通过；严格未知字段/枚举、当前组件树block归属、保存字段保留、发布快照和新协议回滚均有断言。

## S04 任务记录

- 新展示协议发布时，服务端从已校验数据源生成 `sourceDefinition`；字段规范化后计算SHA-256。敏感连接键拒绝，客户端内容不参与。
- 正式取数解析 `screenCode + blockId` 后读取当前数据源做存在/ACTIVE/条线/命名组与权限校验，再以发布定义构造只读有效来源交给既有查询引擎。草稿预览和旧包维持原路径。
- `ScreenConfigServiceImpl` 的运行包校验同样使用快照语义，避免共享数据源修改使旧新协议页面无法回读；当前数据源安全状态仍是即时门禁。
- 定向测试验证配置字段顺序规范化、当前配置变更后仍使用旧定义、哈希篡改、敏感键、缺快照、旧包兼容和新协议回滚。未新增表、REST、连接配置或上游写入。
- 主代理终审扩展至展示校验、画布、屏配置、数据源保存与运行时共256项测试；新增运行链断言证明当前配置改成另一槽位后，查询引擎仍收到发布定义。范围内未改查询公式、SQL白名单或上游模块。

## S05 任务记录

- Red：业务候选测试在 `businessSourceCandidates.js` 不存在时失败；随后实现分类、字段元数据、搜索过滤、机构组安全组合、禁用原因、换源字段清理和最新请求门。
- 配置页复用现有数据源列表API，不新增端点。候选按指标/KPI/受控来源展示名称、编码、条线、维度、结果形状、指标及公式说明；搜索身份使用服务端编码与元数据，不从页面标题猜测。
- 前端不再用旧helper误删后端已支持的机构组KPI快照；只允许schema2/ORG/SNAPSHOT/SINGLE。CUSTOM_SQL和其他不安全组合保留不可选说明。
- 加载失败继续由现有代际和错误路径显式抛出；未修改 `api/http.js`，没有fallback成功或数据源试跑。
- 主代理复核确认旧Picker回退标签和既有Bindings测试保持通过，生产构建成功；构建只有既有Sass API弃用和大chunk提示，不影响本任务产物。

## S06 任务记录

- Red/Green：执行者先以模型不存在建立失败测试；编辑器模型9项、三栏组件5项、PanoramaBindings 22项及其余presentation测试合计51项通过。
- 状态模型深拷贝加载配置，支持七类组件的新增、复制、删除、显隐、同区域排序、标题/内容/格式/交互、已有block绑定、dirty/cancel/commit及序列化前契约校验；编辑器内部状态不会进入发布JSON。
- 页面接入左侧组件列表、中间即时预览、右侧属性区。旧无display配置需显式启用；已有display配置自动加载。保存通过现有saveCanvas/CAS，409和普通失败均保留本地会话。
- 新建未绑定组件阻止保存；删除只移除展示引用。切屏/路由切换及浏览器关闭对dirty配置提示。旧高级字段映射、设置、角色、发布、回滚入口保持原语义。
- 生产构建成功；仅有既有Sass API弃用和chunk体积提示。未启动服务、未调用真实写接口，浏览器验收留S17。
