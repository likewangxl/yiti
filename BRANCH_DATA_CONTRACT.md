# 分行经营大屏数据口径（TEST 冻结版）

本表是分行经营大屏前后端联调使用的展示口径，冻结日期为 2026-09-11。它记录当前测试库和 `ScreenDataRespDTO.quality` 的约定，不代表真实银行经营口径已经审批或签字。正式业务口径仍以有权部门确认的指标定义为准。

## 范围与数据身份

- 本轮数据来自现有测试库 `yit_test`。财务快照的 `dataDate` 当前为 2026-08-30；客户和任务由营销域按当前采集状态计算，采集时间可以是 2026-09-11。客户采集时间推进不等于把财务快照改成 9 月 11 日。
- 财务机构结果按 `ORG_INDEX_RESULT(org_code, data_date, version)` 取数，指标身份由 `PERF_METRIC_DEF` 的 `metric_code`、机构维度和版本共同确定。当前测试输入仍包含 8/30 的金融数据。
- 目标从 `PERF_TARGET_PLAN` 与 `PERF_TARGET_VALUE` 的有效计划、周期和指标关系取得；营销客户、归属和任务来自 `MARKETING_CUSTOMER_INFO`、`MARKETING_CUSTOMER_CLAIM`、`MARKETING_TOUCH_TASK`，必要时由营销域的有效归属快照校验。报告服务返回的结果和 `quality` 是最终展示依据，前端不从行数据猜批次、日期或来源。
- 首个 `LATEST` 请求不带 `batchId`，成功响应锁定一个不透明的不可变 `batchId`（性能批次可以是 `PERF_RUN_TASK` 产生的 opaque id，前端不得假定格式）。同一轮其余槽位、最多 4 家机构趋势预取和机构下钻都必须带同一个 `batchId`。刷新重新选择最新成功批次；批次、数据日期、版本、质量缺失或权限不一致时整轮弃用，不能拼接旧模型。
- 每个槽位和趋势响应都返回 `quality`。`COMPLETE`、`STALE` 才允许进入展示模型；`STALE` 必须明显警告。`PARTIAL`、`NO_COMPLETE_BATCH`、缺失质量或混批只显示质量错误，不显示正常合计。`NO_COMPLETE_BATCH` 的 rows 为空，`batchId/dataDate/version/calculatedAt/ageDays` 为 null。

## 14 个发布槽位

| 槽位 | 展示指标与原始来源 | 时间粒度 | 原始单位 | 汇总与缺失规则 |
| --- | --- | --- | --- | --- |
| `deposit` | `M_0309`：零售一般性存款余额，财务域 `ORG_INDEX_RESULT` | 最新机构快照 | `YUAN`（元） | 机构组按有效机构实际值求和；缺机构不补 0 |
| `loan` | `M_0347`：对公一般性贷款余额，财务域 `ORG_INDEX_RESULT` | 最新机构快照 | `YUAN`（元） | 按机构实际值求和；来源缺失保持缺失 |
| `depositAverage` | `M_0314`：零售存款月均余额，财务域 `ORG_INDEX_RESULT` | 最新月度/机构快照 | `YUAN`（元） | 按同一批有效机构求和；不得用余额代替月均 |
| `depositIncrease` | `TEST_BRANCH_MOM`：本日零售余额减上月末基期（`M_0309 - M_0309__PME`） | 机构日值相对上月末 | `YUAN`（元） | 逐机构计算后求和；没有基期不得伪造净增 |
| `composition` | `TEST_BRANCH_CORP_DEP` 对公手工余额 + `M_0309` 零售余额 | 同一机构快照 | 两列均 `YUAN`（元） | 对公、零售分项分别求和；任一分项缺失时构成不可视为正常完整构成 |
| `customers` | `TEST_BRANCH_CUSTOMERS`：当前营销有效主归属客户数；去重关联客户、有效主归属和机构 | 当前营销采集快照 | `COUNT`（户） | 按当前有效主归属机构计数；同一客户不得重复计入或跨机构相加 |
| `attention` | `TEST_BRANCH_ATTENTION`：`FIRST_TOUCH` 且任务状态为 `PENDING`/`IN_PROGRESS` 的有效主归属任务 | 当前营销任务快照 | `COUNT`（项） | 按有效机构计任务数；取消、完成、失效任务不计入 |
| `revenue` | `TEST_BRANCH_REVENUE`：手工演示收入 | 与测试收入快照一致 | `YUAN`（元） | 仅消费报告返回的手工测试值；不得由客户数乘 12500 新造 |
| `rate` | `TEST_BRANCH_RATE_GROUP_CONTRIB`：发布的机构组率贡献槽；报告按 `DATE` 聚合时依据 typed actual=`M_0309` 与 target=`TEST_BRANCH_TARGET` 重算组率；机构明细/城市汇总另绑定 `TEST_BRANCH_RATE_ORG` | 同一批目标与实际快照 | `PERCENT`（百分数） | 机构率=`actual/target*100`；机构组率=`SUM(actual)/SUM(target)*100`，不能平均机构率 |
| `trend` | `M_0309`、`M_0347`、`TEST_BRANCH_MOM` 的经营趋势 | 日/月历史序列，依绑定 period | `YUAN`（元） | 所有历史行来自同一锁定批次；混期、缺日、质量不完整要显式标记 |
| `ranking` | 机构排名的存款余额及可用净增/月均字段 | 同一批机构快照 | 金额字段 `YUAN`（元） | 按机构实际值排序；缺值不降级为零并参与正常排名 |
| `branches` | 机构明细：`M_0309`、`M_0347`、`TEST_BRANCH_CUSTOMERS`、`TEST_BRANCH_TARGET`、`TEST_BRANCH_RATE_ORG` | 同一批机构快照 | 金额 `YUAN`、客户 `COUNT`、率 `PERCENT` | 仅授权且收到的机构；`expectedSubjects/receivedSubjects` 必须随质量展示 |
| `branchTrend` | 选中机构的存款/贷款等趋势下钻 | 同一批次的机构日/月序列 | 金额 `YUAN`，其他字段按 `columnsMeta` | 下钻请求带已锁定 `batchId`；迟到、混批或无质量时清空旧下钻/整屏模型 |
| `citySummary` | 城市范围的存款、贷款、客户、手工收入和 `TEST_BRANCH_RATE_ORG` 目标率汇总 | 同一批城市快照 | 金额 `YUAN`、客户 `COUNT`、率 `PERCENT` | 只汇总有效机构实际数据；缺机构不作为零值正常合计 |

`TEST_BRANCH_TARGET` 的有效目标限定为计划 `TEST_BRANCH_Q3_20260910`、周期 `2026Q3`、指标 `M_0309`，并按目标有效期筛选。目标为 0 或不存在时，完成率不得显示为正常完成；应显示缺失/不可计算状态。动态目标变更由绩效计算批次重新计算，前端只消费新批次结果。

## 单位和展示换算

响应 `columnsMeta` 对 `METRIC` 列提供权威原始 `unit`（规范值 `YUAN`、`COUNT`、`PERCENT`）和小数位；`DIM` 列不声明单位。前端只在单位与发布绑定一致时读取正常数值：

- 金额原始单位为元，展示换算为亿元（`YUAN / 100000000`），标签必须写 `亿元`。
- 客户原始单位为户，当前展示约定换算为万户（`COUNT / 10000`），标签必须写 `万户`；不得把已换算数标成户，也不得把元和户混算。
- 百分比原始值按百分数展示，例如 `85.9` 展示为 `85.9%`；比例或零目标不自动当作正常完成率。
- 新批次 `columnsMeta.unit` 与发布绑定单位冲突、单位未知或角色不符时，前端记录口径异常并留空该值，不静默按错误绑定单位换算。旧无批次响应只保留已有兼容行为。

## Quality 展示字段

报告返回的 `quality` 字段为：`batchId`、`dataDate`、`version`、`dataClassification`（例如 `TEST`/`PROD`）、`status`（`COMPLETE|STALE|NO_COMPLETE_BATCH|PARTIAL`）、`calculatedAt`、`sourceAsOf`、`expected`/`received`（指标格）、`expectedSubjects`/`receivedSubjects`（机构）、`missingSubjects`、`missing`、`mixedPeriod`、`selectedComplete`、`newerIncomplete`、`historyCoverage`、`maxAgeDays`、`ageDays`、`message`。其中 `historyCoverage` 是按数据日期的 typed 数组，每项含 `dataDate`、`expected`、`received`、`expectedSubjects`、`receivedSubjects`、`complete`、`missingSubjects`、`missing`。页面分别展示：

- 数据日期：业务数据所属日期；
- 绩效计算批次时间：该不可变绩效批次计算完成时间；
- 本次查询/刷新时间：浏览器本轮请求时间；
- 数据截至：`sourceAsOf` 中的财务、营销、目标、收入日期或采集时间；
- 数据分类、机构覆盖、指标格覆盖、缺失机构/指标、混期、历史缺日/不完整日期和较新但不完整候选日期；长列表以摘要和可展开详情呈现。

这几个时间不能互相替代。`sourceAsOf.financial` 仍为 8/30 时，不得因为本次查询发生在 9/11 就把页面说成 9/11 财务经营数据。

## TEST 边界

当前输入仍是测试数据，页面保留 `TEST` 标识和“非生产业务数据”说明。客户数是当前营销有效主归属采集结果，收入是手工演示收入；二者都不代表已批准的正式银行经营指标。任何正式发布前都需要业务、数据和权限责任方确认指标定义、授权机构集合、时效阈值、目标有效期及缺失处理规则。

## 运维启动和元数据边界

- 测试服务必须同时启用 `redengine-task-e2e,branch-dashboard-e2e` 两个 profile。RAM Quartz 调度中只启用 `BRANCH_DASHBOARD_BATCH`，默认触发周期为 5 分钟；其他任务不作为本轮分行大屏验收的运行前提。
- 财务数据日期与营销当前状态必须分开展示：现有金融输入的 `dataDate` 为 2026-08-30，客户和任务取营销域当前采集状态（可为 2026-09-11）。客户采集时间推进不改变财务数据日期，也不能把两者合并成一个“更新时间”。
- 当前测试库的 11 项指标数据库 `unit` 仍为 `NULL`，另有 2 项指标 `description` 缺失。`TEST` 发布配置可以使用显式单位覆写支持演示，但该覆写不等于补齐数据库元数据，也不能绕过 `columnsMeta` 的单位一致性校验。
- 未经业务、数据和权限责任方正式签认的指标口径必须保持 `TEST` 分类，不得标记为 `PROD`。本表记录的是测试联调约定，不能作为真实银行经营口径已批准或已签字的证明。
