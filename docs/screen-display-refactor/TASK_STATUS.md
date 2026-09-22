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
| S07 | VERIFIED | `6743219a` | - | `displayMetricsModel.js`、`MetricDisplayWidgets.vue`及测试；四类Dashboard/Runtime/Page最小接入；文档与`TASK_STATUS.md` | 新协议指标卡/完成情况驱动运行展示，旧包回归保持；主代理已复核 | 模型/组件/分行/对公/支行相关71项通过；生产构建通过 | 浏览器真实数据验收留S17 | Vitest/Vite输出与代码化大屏文档 | Retail套件1项既有CRLF静态文本断言失败，retail.scss未改；真实来源仍UNCONFIRMED | 创建S07提交并进入S08 |
| S08 | VERIFIED | `14a77355` | - | `displaySeriesTableModel.js`、`SeriesTableWidgets.vue`及测试；四类Dashboard最小接入；文档与`TASK_STATUS.md` | 新协议趋势/明细表由配置驱动，旧固定展示保持兼容；主代理已复核 | 模型/组件/分行/对公/支行相关63项通过；生产构建通过 | 浏览器真实数据验收留S17 | Vitest/Vite输出与代码化大屏文档 | 不支持的混合单位同轴明确拒绝；真实趋势来源仍UNCONFIRMED | 创建S08提交并进入S09/S10 |
| S09 | VERIFIED | `f946dc8c` | - | `compositionTabsModel.js`、`CompositionTabsWidget.vue`及测试；四类Dashboard/Runtime接入；代码化大屏文档 | 三类业务结构页签已接入新展示协议，旧协议保持兼容；主代理已复核 | S09模型/组件/集成16项通过；相邻Dashboard 100项通过、1项既有CRLF断言失败；生产构建通过 | 浏览器真实验收留S17 | Vitest/Vite输出与代码化大屏文档 | 真实结构分母和指标来源仍UNCONFIRMED；导航动作留S13 | 创建S09提交并进入S10 |
| S10 | VERIFIED | `91612586` | - | `institutionRankingModel.js`、`InstitutionRankingWidget.vue`及测试；四类Dashboard接入；代码化大屏文档 | 新协议全量机构排名与可控轮播已实现，旧TOP10仅保留旧协议；主代理已复核 | S10模型/组件/集成43项通过；相关回归528项通过、1项既有CRLF断言失败；生产构建通过 | >10家真实机构验收留S17 | Vitest/Vite输出与代码化大屏文档 | 真实排名指标/方向/总分来源仍UNCONFIRMED；真实接口若截断将明确不完整 | 创建S10提交并进入S11/S12 |
| S11 | BLOCKED_DATA | `69789625` | - | `institutionViewModel.js`及测试；既有`PanoramaInstitutionDTO`/授权目录只读复核 | 展示/统计集合分离模型已实现；真实机构白名单因D03/D04未确认未接运行页 | 模型7项通过；现有后端授权目录字段与activeProfiles链已复核 | 未连接真实机构画像/数据库 | Vitest输出、BUSINESS_DECISIONS D03/D04/X05、现有DTO源码 | 缺允许的operatingLevel/orgNature值或签认名单，禁止名称/编码猜测 | 获得D03/D04口径后接入运行页；当前继续S12可独立能力 |
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

## S07 任务记录

- 展示模型仅在 `displaySchemaVersion=1` 下启用，按visible/区域/order稳定解析METRIC_CARD和COMPLETION，多实例互不覆盖；旧presentation返回disabled并走原Dashboard。
- 标题按CUSTOM/指标名称快照解析，身份仍来自componentId/dataRef。主副字段只读现有blockResults/kpis/targets，不新增actual/target公式或模拟值。
- 0/null、负数、超100、金额换算、COUNT、RATIO/PERCENT单次转换和换源清旧值均有纯测试；完成率文本保留原值，progress才钳制。
- PanoramaRuntime把发布presentation传给分行/对公/零售Dashboard；支行live页面从renderPackage读取presentation。旧KPI区仅在新协议未启用时显示。
- 相关71项测试及生产构建通过。RetailDashboard套件的静态SCSS断言因仓库现有CRLF与期望LF不一致失败1项，本轮未修改retail.scss；其余零售行为测试和构建通过，按存量问题记录。

## S08 任务记录

- 展示模型仅解析新协议中visible的TREND/DETAIL_TABLE实例；旧协议保持原分支。系列和列使用稳定key、显式字段/名称/单位及数组顺序。
- 趋势只读已有blockResults或model.trend；缺日期和重复日期记录问题并停止图表，金额/计数/比例混合时拒绝共轴。0/null分别保留，不生成历史值。
- 明细表按visible过滤、按配置列顺序显示，来源行保持稳定顺序；列标题和显示格式独立于字段身份。
- 分行/对公/零售/支行运行页接入统一SeriesTableWidgets；新趋势存在时隐藏原固定趋势，旧包不受影响。
- 相关63项测试和生产构建通过；未修改业务查询、趋势计算或共享组件目录，浏览器验收留S17。

## S09 任务记录

- Red：新增四类Dashboard集成测试；未接入时9项中8项稳定失败，分别证明新结构组件缺失、旧固定结构未隐藏以及条线动作未携带机构上下文。
- Green：综合、对公、零售、支行Dashboard接入`COMPOSITION_TABS`，`PanoramaRuntime`只转发固定`businessLine + tabKey + context`动作，不拼接URL；新协议存在可见结构组件时隐藏旧结构，旧协议继续原路径。
- 业务结构按配置读取公司、零售和核定总量；缺总量、零分母、负数、单位冲突或缺一方均明确显示不可计算，不把两条线强制归一为100%，也未擅自增加中收结构。
- 主代理独立复核：模型/组件/集成16项通过；四类相邻Dashboard共100项通过。另1项`RetailDashboard.spec.js`仍因未修改的`retail.scss`为CRLF而失败，与S07记录一致；生产构建通过，仅有既有Sass弃用和chunk体积提示。

## S10 任务记录

- Red/Green：先新增四类Dashboard集成用例证明新排名未挂载、旧TOP10仍显示；随后接入统一排名模型和组件，并补充`displaySchemaVersion!=1`不得启用新分支的回归。
- 排名目录只接受服务端已授权的`safeModel.institutions`，结果只读`safeModel.rankings`，指标身份和方向只读`RANKING.content.rankingMetrics`；缺机构编码、目录外记录、重复记录和显式停用/无权均拒绝进入排名。
- 新协议不做TOP10或五条裁切；0/1/7/23/200家、并列、负数、合法0、缺数、服务端`limit/hasMore/truncated`不完整提示均有测试。手动切换、hover/focus、页面隐藏会暂停，继续按钮恢复，卸载清理计时器。
- 主代理纠正了“每行必须重复active/authorized”的错误前提：当前`PanoramaInstitutionDTO`已经由后端`authorizeRuntime + activeProfiles`生成，DTO没有这两个字段；运行接入用`sourceAuthorized:true`声明目录来源可信，仅显式false时排除。
- 主代理独立复核：S10模型/组件/集成43项及生产构建通过；扩大相关回归为528/529通过，唯一失败仍为未修改`retail.scss`的CRLF静态断言。真实超过10家机构和真实指标方向留S17核验。

## S11 任务记录

- 新增机构展示模型：只接受服务端授权目录，按显式`allowedOperatingLevels`/`allowedOrgNatures`白名单过滤；缺规则返回`UNCONFIRMED`空集合，不按名称、编码长度或前端角色猜测。
- 合法0、无指标和无坐标机构保留各自状态；目录外指标记录拒绝；同名不合并；父子或经营归属同时展示时标记`CANNOT_AGGREGATE`，不现场改写上级正式值。
- 主代理回读`PanoramaInstitutionDTO`和`ScreenConfigServiceImpl.buildPanoramaInstitutions`：运行DTO已经包含`operatingLevel/orgNature/city/坐标`，并由`authorizeRuntime + activeProfiles`保证授权和ACTIVE；无需改后端契约。
- 停止条件触发：D03/D04尚未给出允许的层级/性质枚举或签认机构清单，当前不能安全接入运行过滤。模型7项通过，但任务按`BLOCKED_DATA/X05`记录，不把单元夹具当真实机构口径。
