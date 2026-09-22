# S01 现有大屏数据能力与口径清单

状态：`VERIFIED`；代码基线：`dced49aaec6220b56991d774b0cae8435115d795`；盘点日期：2026-09-22。

本清单只基于当前源码和会议纪要。未连接数据库、未调用运行接口、未读取真实经营数据，也未检查目标环境中的屏、数据源、指标、KPI方案和机构覆盖。源码出现某个类或查询分支，只证明代码具有相应能力，不证明当前环境已有可用配置或真实数据。

## 1. 状态定义

能力状态与数据就绪状态必须分开记录：

| 状态 | 含义 |
| --- | --- |
| `CODE_SUPPORTED` | 当前源码存在类型化保存、校验和查询路径；仍可能因配置、权限或数据缺失而不可用 |
| `CODE_RESTRICTED` | 源码只支持特定主体、形状、范围或高权限入口，不能泛化使用 |
| `CODE_UNSUPPORTED` | 当前源码没有安全可用的实现，或被本轮范围明确排除 |
| `CONFIGURED_UNKNOWN` | 未读取目标环境，无法确认是否已创建并发布对应屏/数据源/字段绑定 |
| `LIVE_UNVERIFIED` | 未核对真实返回值、数据日期、机构覆盖、单位和业务口径 |
| `AVAILABLE` | 代码支持、配置存在、真实数据与业务口径均已现场验证；本次盘点没有任何条目达到此状态 |
| `UNCONFIRMED` | 代码存在能力，但配置、数据或业务口径至少一项未验证；本文大多数候选属于此状态 |
| `UNAVAILABLE` | 当前契约不支持、被安全边界禁止，或本轮明确不使用 |

后续 S05 只能把 `CODE_SUPPORTED/CODE_RESTRICTED` 且满足当前屏条件的候选显示给用户。只有完成目标环境只读核验与业务签认后，才可把某个具体 `metricCode/sourceId` 从 `UNCONFIRMED` 改为 `AVAILABLE`。

## 2. 统一取数及运行身份

- 运行取数入口为 `POST /api/screen/data`，要求 REPORT/READ 权限；数据源列表、保存、更新、删除和高危试跑使用独立权限。源码：[ScreenDataController.java:32](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/ScreenDataController.java)、[ScreenDatasourceAdminController.java:49](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/ScreenDatasourceAdminController.java)。
- 当前代码化大屏使用 `schemaVersion=2`，由服务端根据 `screenCode + blockId` 解析发布快照；客户端不能覆盖 `dsId`。请求可带周期、日期范围、机构/员工上下文和分行批次ID；授权机构集合由服务端注入。源码：[ScreenDataReqDTO.java:14](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenDataReqDTO.java)。
- 响应为 `columns + rows`，可带列角色、单位、小数位、金额量级，以及批次、日期、版本、覆盖和缺失项等质量信息。并非所有来源都会返回 `quality`。源码：[ScreenDataRespDTO.java:17](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ScreenDataRespDTO.java)。
- 机构目录可提供机构编码/名称、城市、经营归属、经营层级、机构性质、坐标系和定位状态；是否已正确配置仍未知。源码：[PanoramaInstitutionDTO.java:9](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/PanoramaInstitutionDTO.java)。
- 前端代码模板只有分行综合、对公、零售三类。当前入口允许分行 `TEST`，对公/零售 `TEST` 或 `LIVE`；`LIVE` 在页面上明确不等于生产真实性认证。源码：[CodeScreenPage.vue:57](../../xanzc_frontend/src/views/screen/CodeScreenPage.vue)。

周期方面，后端请求支持 `LATEST/LAST_10D/LAST_1M/LAST_6M_EOM/RANGE`，当前代码化绑定界面只声明前四种预设；趋势默认近六个月月末，其余通常默认最新。源码：[bindings.js:27](../../xanzc_frontend/src/views/screen/panorama/bindings.js)、[ScreenDataReqDTO.java:33](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/req/ScreenDataReqDTO.java)。

## 3. 现有来源类型矩阵

| 来源 | 源码能力与输出形状 | 范围与权限限制 | 本轮适用性 | 当前状态 |
| --- | --- | --- | --- | --- |
| `WIDE_TABLE` | `EMP_INDEX_RESULT/ORG_INDEX_RESULT` 指标宽表；指标编码保存时解析为槽位快照。聚合可为 `NONE` 单行、`SUBJECT` 按主体、`DATE` 按日期；DATE为时序，其他为单值/列表 | 指标必须存在、槽位和维度匹配。命名机构组只允许可证明机构约束的 `ORG_INDEX_RESULT + org_code`；最新批次路径可返回完整性质量 | 分行/对公/零售的指标卡、趋势、排名、结构、城市和机构数据的主要候选 | `CODE_SUPPORTED + CONFIGURED_UNKNOWN + LIVE_UNVERIFIED = UNCONFIRMED` |
| `KPI_RESULT` | 员工 `KPI_RESULT` 的周期总分时序，输出日期和 `KPI总分` | 需要 `empId`；保存强制时序；命名机构组运行时禁止 | 不适合作为本轮机构/分行总分或机构排名来源；可保留旧个人场景，但个人看板不在范围 | 本轮机构大屏 `UNAVAILABLE` |
| `KPI_DETAIL` | `SNAPSHOT` 输出细项实际、目标、权重、得分、完成率、缺口；`TREND` 按指标透视得分或完成率 | 方案必须ACTIVE；主体仅EMP/ORG。命名机构组仅允许 `schemaVersion=2 + ORG + SNAPSHOT + SINGLE`，不允许EMP或TREND | 机构完成情况、目标明细和机构级排名可作为候选；命名机构组趋势不能使用此源 | `CODE_RESTRICTED + CONFIGURED_UNKNOWN + LIVE_UNVERIFIED = UNCONFIRMED` |
| `M98_STAT` | 固定命名机构组SUMMARY模板，查询表/列/过滤不由普通配置提供，强制单值 | 仅严格策略和指定业务条线；服务端授权集合 | 只有现有固定配置与业务已签认时可作为存量来源，不能扩展成通用配置 | `CODE_RESTRICTED + CONFIGURED_UNKNOWN + LIVE_UNVERIFIED = UNCONFIRMED` |
| `FREE_REPORT` | 读取已存在的固定 TEST 分支经营批次，强制单值 | 仅严格批次配置；命名组准入限CORP/COMMON；只读核验自由报表元数据 | 可用于已存在的测试展示，不作为正式生产统一来源 | `CODE_RESTRICTED + CONFIGURED_UNKNOWN + LIVE_UNVERIFIED = UNCONFIRMED` |
| `CUSTOM_SQL` | 受控白名单SQL，支持单值/时序；时序要求日期列 | 试跑和列探测需 REPORT/EXECUTE_SQL 高危权限。命名机构组运行查询禁止普通CUSTOM_SQL | 新经营大屏标准配置明确不开放；旧配置只读兼容 | 本轮新配置 `UNAVAILABLE` |

来源保存分派见 [ScreenDatasourceServiceImpl.java:450](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenDatasourceServiceImpl.java)，运行分派和命名机构组阻断见 [ScreenQueryEngine.java:771](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenQueryEngine.java)，机构组安全组合见 [ScreenNamedGroupDatasourcePolicy.java:24](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenNamedGroupDatasourcePolicy.java)。

### 3.1 KPI 完成率现状

大屏 `KPI_DETAIL` 当前完成率固定为 `actual_value / target_value × 100`，目标为0时返回空；命名机构快照同时返回实际、目标、权重、得分、缺口和关注标记。源码：[ScreenQueryEngine.java:1347](../../report-analytics-center/src/main/java/com/bank/branch/platform/report/service/screen/ScreenQueryEngine.java)。

绩效明细页面的另一条现有计算路径为 `(actual-base)/target × 100`，见 [KpiScoreCalcService.java:899](../../performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiScoreCalcService.java)。本轮不修改绩效模块，也不能由标题判断应使用哪条公式。所有完成率槽位保持 `UNCONFIRMED`，直到 D01 确认具体指标、方案和公式。

## 4. 分行综合模板槽位

绑定身份与单位白名单来自 [bindings.js:90](../../xanzc_frontend/src/views/screen/panorama/bindings.js)；金额允许 `YUAN/TEN_THOUSAND/HUNDRED_MILLION`，计数允许 `COUNT/TEN_THOUSAND_COUNT`，比例允许 `PERCENT/RATIO`。本文不为槽位猜测具体指标编码或数据源ID。

| 槽位 | 业务项/字段角色 | 允许来源与限制 | metricCode/sourceId | 状态 |
| --- | --- | --- | --- | --- |
| `deposit` | 存款余额；value金额，change比例，date维度 | WIDE_TABLE单行；必须明确全行口径 | 未现场核验 | `UNCONFIRMED`：配置与真实值未知 |
| `depositIncrease` | 存款较上月净增；金额 | WIDE_TABLE单行；不能用余额或变化率替代 | 待确认 | `UNCONFIRMED` |
| `depositAverage` | 存款月均余额；金额 | WIDE_TABLE单行；不能用时点余额替代 | 待确认 | `UNCONFIRMED` |
| `loan` | 贷款余额；金额 | WIDE_TABLE单行；需确认全行/条线口径 | 待确认 | `UNCONFIRMED` |
| `customers` | 营销有效归属客户数；计数 | WIDE_TABLE单行；指标名称仍带测试期历史口径风险 | 待确认 | `UNCONFIRMED` |
| `revenue` | 当前契约名为“手工测试收入”；金额 | 仅可绑定已确认收入指标；不能因槽位名改标题就变正式收入 | 待确认 | `UNCONFIRMED`：业务口径与真实来源未签认 |
| `rate` | 目标完成率；比例 | ORG KPI_DETAIL SNAPSHOT或已落宽表比例；公式必须确认 | 待确认KPI方案/指标/sourceId | `UNCONFIRMED`：D01/D02 |
| `trend` | date必需；deposit/loan/depositIncrease至少一项，可含customers/rate | WIDE_TABLE DATE为主要候选；命名机构组KPI_DETAIL TREND不允许 | 待确认各系列 | `UNCONFIRMED` |
| `composition` | 行模式name+value，或列模式corporate+retail；金额或比例 | WIDE_TABLE单行/分类行；公司与零售单位类型须一致，分母口径待确认 | 待确认三类结构来源 | `UNCONFIRMED`：D05/D06 |
| `ranking` | orgCode/name/value必需；可含increase/average/change | WIDE_TABLE SUBJECT；全机构取数还需验证行数上限与覆盖 | 待确认排名指标 | `UNCONFIRMED` |
| `attention` | label/count必需，可含orgCode | KPI_DETAIL命名组快照可生成关注项，或明确表型来源 | 待确认 | `UNCONFIRMED` |
| `branches` | orgCode必需；机构/城市/坐标为维度，可含deposit/loan/customers/target/rate | 身份优先使用授权机构目录；经营数值可来自WIDE_TABLE SUBJECT，不应复制坐标 | 待确认经营字段；目录无sourceId | `UNCONFIRMED`：目录配置/覆盖未知 |
| `branchTrend` | date必需，deposit/loan至少一项，可含customers/rate | 单机构WIDE_TABLE DATE；上下文orgCode仍由服务端授权 | 待确认 | `UNCONFIRMED` |
| `citySummary` | cityCode或orgCode至少一项；可含deposit/loan/customers/revenue/rate | 需要已有城市汇总口径；不能前端叠加父子机构 | 待确认 | `UNCONFIRMED` |
| `loanRate` | 可选零售贷款目标完成率；比例 | KPI_DETAIL或已确认宽表比例；不得从loan金额或rate槽复制 | 待确认 | `UNCONFIRMED` |

分行主槽顺序包含14项，`loanRate`为可选项，源码：[bindings.js:207](../../xanzc_frontend/src/views/screen/panorama/bindings.js)。

## 5. 对公模板槽位

对公契约来自 [corporateBindings.js:59](../../xanzc_frontend/src/views/screen/panorama/corporateBindings.js)。所有具体metricCode/sourceId仍需目标环境核验。

本节每个简写为 `UNCONFIRMED` 的原因相同：源码有对应展示契约，但具体来源/指标编码、已发布配置、真实返回值、单位、日期与机构覆盖均未现场核验。

| 槽位 | 业务项/字段角色 | 允许来源与限制 | 状态 |
| --- | --- | --- | --- |
| `corpDeposit` | 对公存款余额；金额单值 | WIDE_TABLE单行，对公条线 | `UNCONFIRMED` |
| `corpDepositAverage` | 对公存款月日均；金额单值 | WIDE_TABLE单行，不以时点值替代 | `UNCONFIRMED` |
| `corpLoan` | 对公贷款余额；金额单值 | WIDE_TABLE单行 | `UNCONFIRMED` |
| `corpRevenue` | 对公营业收入；金额单值 | WIDE_TABLE单行，收入口径待确认 | `UNCONFIRMED` |
| `corpCustomers` | 有效对公客户；计数单值 | WIDE_TABLE单行，需明确“有效”定义 | `UNCONFIRMED` |
| `corpNplRate` | 对公不良率；比例单值 | WIDE_TABLE或已确认指标结果；逆向指标不套普通完成率 | `UNCONFIRMED` |
| `corpTrend` | date + deposit/loan至少一项 | WIDE_TABLE DATE；机构组KPI趋势不可用 | `UNCONFIRMED` |
| `corpSegments` | name维度、customers计数、loan金额 | 需要明确的分类结果源；不能由名称拼接 | `UNCONFIRMED` |
| `corpRanking` | orgCode/name/deposit必需，可含increase/rate/nplRate | WIDE_TABLE SUBJECT；完成率可来自KPI_DETAIL SNAPSHOT后适配 | `UNCONFIRMED` |
| `corpAttention` | label/count必需，可含owner/deadline | 仅已有明确事项数据时使用；不从错误日志推导业务关注 | `UNCONFIRMED` |
| `corpTargets` | name维度、actual/target金额 | ORG KPI_DETAIL SNAPSHOT或明确目标结果；当前契约只允许金额类目标 | `UNCONFIRMED` |
| `branches` | 授权机构身份字段，不承载对公经营指标 | 使用服务端机构目录；显示集合规则待D03/D04 | `UNCONFIRMED` |

## 6. 零售模板槽位

零售契约来自 [retailBindings.js:55](../../xanzc_frontend/src/views/screen/panorama/retailBindings.js)。所有具体metricCode/sourceId仍需目标环境核验。

本节每个简写为 `UNCONFIRMED` 的原因相同：源码有对应展示契约，但具体来源/指标编码、已发布配置、真实返回值、单位、日期与机构覆盖均未现场核验。

| 槽位 | 业务项/字段角色 | 允许来源与限制 | 状态 |
| --- | --- | --- | --- |
| `retailAum` | 零售AUM；金额单值 | WIDE_TABLE单行 | `UNCONFIRMED` |
| `retailDeposit` | 储蓄存款余额；金额单值 | WIDE_TABLE单行 | `UNCONFIRMED` |
| `retailDepositAverage` | 储蓄月日均；金额单值 | WIDE_TABLE单行 | `UNCONFIRMED` |
| `retailRevenue` | 零售营业收入；金额单值 | WIDE_TABLE单行，收入口径待确认 | `UNCONFIRMED` |
| `retailValueCustomers` | 价值客户；计数单值 | WIDE_TABLE单行，客户定义待确认 | `UNCONFIRMED` |
| `retailLoan` | 个人贷款；金额单值 | WIDE_TABLE单行 | `UNCONFIRMED` |
| `retailNplRate` | 个贷不良率；比例单值 | WIDE_TABLE或已确认指标结果；逆向指标单独说明 | `UNCONFIRMED` |
| `retailTrend` | date必需；aum/deposit至少一项，可含loan/revenue/customers | WIDE_TABLE DATE；命名机构组KPI趋势不可用 | `UNCONFIRMED` |
| `retailSegments` | name、customers、aum | 需要明确的客群分类结果源 | `UNCONFIRMED` |
| `retailRanking` | orgCode/name必需；aum/deposit至少一项，可含average/date/increase/rate/nplRate | WIDE_TABLE SUBJECT；KPI完成率需单独适配并确认 | `UNCONFIRMED` |
| `retailAttention` | label/count必需，可含owner/deadline | 明确表型来源或KPI快照；不从模拟数组补值 | `UNCONFIRMED` |
| `retailTargets` | name、actual、target必需，实际与目标均为金额类型 | ORG KPI_DETAIL SNAPSHOT或明确目标结果；非金额目标不适用当前契约 | `UNCONFIRMED` |
| `branches` | 授权机构身份字段 | 服务端机构目录；显示规则待确认 | `UNCONFIRMED` |

零售槽顺序见 [retailBindings.js:129](../../xanzc_frontend/src/views/screen/panorama/retailBindings.js)。

## 7. 不能由本轮解决的依赖

| 依赖 | 影响 | 本轮处理 |
| --- | --- | --- |
| X02 KPI/目标导入和机构方案关联 | 没有现成ORG方案/目标时，完成率和目标组件没有真实来源 | 记录 `BLOCKED_DATA`；不创建方案、不导入、不重算 |
| X04 新上游查询/不可变结果能力 | 某些全机构趋势、完整分页或跨源一致性可能缺契约 | 优先复用现有接口；无法满足则记录 `BLOCKED_SCOPE`，不直查上游私表 |
| X05 机构画像与组织治理 | 缺经营层级/性质/城市/坐标时不能正确过滤和定位 | 保留缺失状态；不修改画像管理或权限 |
| X06 工作台、个人看板和全局导航 | 会议中的入口位置和个人排名不能在本轮实现 | 保持现状，只改大屏中心内经营屏行为 |

## 8. S02 前的准入结论

1. S02可以设计组件、来源和显示配置契约，因为三类模板字段形状、单位白名单和受控来源类型已经能从源码确定。
2. S05可设计来源选择器，但任何具体metricCode/sourceId候选必须来自运行环境API，不能在代码或文档中凭名称硬编码。
3. S07～S10可以用明确标注的合成夹具验证零值、缺值、单位、排序与交互；不得据此把真实数据状态改为AVAILABLE。
4. 真实完成率、公司/零售结构分母、全机构覆盖和过滤规则依赖 [BUSINESS_DECISIONS.md](BUSINESS_DECISIONS.md) 中未关闭决策。
5. S17前必须对计划使用的每个具体来源完成只读核验：屏/来源是否启用、字段元数据、指标编码、单位、日期、机构覆盖、质量状态及业务签认。
