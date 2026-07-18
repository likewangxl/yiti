# performance-engine-center/ CLAUDE.md

本文件为 `performance-engine-center` 模块提供上下文说明。




## 模块概述

**performance-engine-center** 是绩效计算中心（核心域），为整个平台提供指标库管理、KPI 方案设计、目标管理、客户分配关系查询、数据版本控制、调整审批流程、异步导出、数据范围注入等能力。

> 2026-05-29 eval 子域：人员标签新增"是否启用评价"（新表 `EVAL_USER_SETTING`，无记录=否；列表三态过滤[是默认/否近似/全部]，启用态由 setting 侧驱动分页；保存/导入/导出/模板贯通该列）。建表脚本 `docs/superpowers/sql/2026-05-29-eval-user-setting.sql` 须在目标库手工执行。
>
> 2026-05-30 eval 子域：标签去类型化——`EVAL_TAG` 删 `tag_type`（扁平池，唯一键改 `tag_name`），`EVAL_USER_TAG` 加 `role_type`（1=被评价/2=评价）。被评价/评价区分从「标签定义」搬到「使用处」（人员角色槽 / 规则挂载槽）；`saveUserRoles`/导入/`EvalRuleService` 三处加"评价角色≠被评价角色"局部排斥（新增 `PERF-40063 EVAL_ROLE_CONFLICT`，删 `EVAL_TAG_TYPE_MISMATCH`）。引擎查询从 JOIN `tag_type` 切到 `EVAL_USER_TAG.role_type`（方法名 `selectTagIdsByUserIdAndType` 不变）。迁移脚本 `docs/superpowers/sql/2026-05-30-eval-tag-detype.sql` 须在目标库手工执行（次序：先回填 role_type 再删 tag_type）。前端 Tags/UserTags/Rules 三页去类型 + 互斥下拉。spec/plan：`docs/superpowers/specs/2026-05-30-eval-tag-detype-design.md` / `docs/superpowers/plans/2026-05-30-eval-tag-detype-impl.md`。
>
> 2026-06-10 eval 子域：单一角色化——`EVAL_USER_TAG` 删 `role_type`、唯一键改 `UK_USER(USER_ID)`（每人至多一标签）。「被评价/评价」方向不再存于人员侧，完全由规则承载（标签出现在 `EVAL_RULE.be_eval_tag_id`=被评价，出现在 `EVAL_RULE_GROUP.eval_tag_id`=评价人）。`saveUserRoles(userId,beEvalTagId,evalTagIds)`→`saveUserRole(userId,tagId)` 单标签覆盖（`saveUserRoleWithSetting` 同步收口）；删人员侧局部排斥（规则自评排斥 `EVAL_ROLE_CONFLICT` 保留）；任务生成 `EvalTaskService.createTask` + 打分匹配 `EvalScoreService.resolveGroup` 改 `selectTagIdByUserId`（替代 `selectTagIdsByUserIdAndType`）；导入行/导出行/模板收敛单角色列（`roleName`/`role`）；DTO `EvalUserRoleRowDTO.beEvalTag+evalTags`→单 `tag`、`EvalUserTagRow` 删 `roleType`；删旧 `bind/unbind` 端点 + `batchBind/batchUnbind`。前端 `UserTags.vue` 合并单选、`Rules.vue` 术语中性化「评价对象标签」（`beEvalTagId` 字段名/接口不变）、`api/eval.js saveUserRoles(userId,tagId,evalEnabled)`。迁移脚本 `docs/superpowers/sql/2026-06-10-eval-single-role.sql`（优先保留被评价标签收敛；yiti 标准基线整段适用、onepl_test_bootstrap 老基线仅换唯一键、onepl 无此表）。eval 54 单测全绿。spec/plan：`docs/superpowers/specs/2026-06-10-eval-single-role-design.md` / `docs/superpowers/plans/2026-06-10-eval-single-role-impl.md`。
>
> 2026-06-10 eval 子域：待处理任务（导入式评价任务）——「新增评价任务」升级为「新增待处理任务」，按 来源(自动生成/手工导入) → 导入类型(评价任务/奖励分配) 分层（全部走 governance `DictApi`：`EVAL_PENDING_SOURCE`/`EVAL_IMPORT_TYPE`/`EVAL_SCORE_TYPE`/`EVAL_WEIGHT_TAG`）。本期打通 手工导入→评价任务导入 全链路。**新增独立表** `EVAL_ASSIGN_BATCH`（批次：来源/类型/截止时间）+ `EVAL_ASSIGN_ITEM`（打分人×被打分人显式配对：双方工号/姓名/部门/标签快照 + 权重标签 + 评价类型 NUM/GRADE + 分数/submitted），与规则驱动 `EVAL_TASK/TARGET/SCORE`（=自动生成路径）并存、互不影响。导入：10 列模板（被打分人 编号/姓名/部门/标签 + 打分人 编号/姓名/标签/部门 + 权重标签 + 评价类型），校验双方为系统有效员工 + 权重标签/评价类型命中字典 + 文件内配对不重复，all-or-none，截止时间随上传单独传。用户端「我的评价」→「待处理任务」：当前人作为打分人，按被打分人部门聚合未提交明细 → 处理某部门 → 按 score_type 逐人数值/等级打分提交、人数同步递减。新增 `EvalAssignImportService`/`EvalAssignService`（17 单测）、`EvalAssignAdminController`(`/api/admin/eval/assign/import[-template]`)/`EvalPendingController`(`/api/eval/pending-tasks[/items|/submit]`)、错误码 `PERF-40064/40065`、资源 `PERF_EVAL_23~27`。提交复用现有口径（数值 10~100、等级 5 档）+ 错误码（NO_PERMISSION/DUPLICATE/TASK_CLOSED/OUT_OF_RANGE）。**待办生成本期不做**（预留接缝，参考代码后续提供）。Mapper 一律 `extends BaseMapper`（insert/selectById 用内置，仅 batchInsert/聚合/条件更新写 XML——见根 CLAUDE.md「MyBatis-Plus 规范」红线）。迁移脚本 `docs/superpowers/sql/2026-06-10-eval-assign-pending-tasks.sql`（yiti + onepl_test_bootstrap 已执行）。spec：`docs/superpowers/specs/2026-06-10-eval-pending-task-import-design.md`。
>
> 2026-07-11 eval 子域：待处理任务新增「分组部门」汇总维度——`EVAL_ASSIGN_ITEM` 加 `GROUP_DEPT`(NOT NULL DEFAULT '')，导入模板由 10→11 列（第 4 列「分组部门」，可空、无字典校验、导入快照）。用户端「待处理任务」汇总/下钻分组键由 `be_eval_dept` 改为有效分组键 `COALESCE(NULLIF(group_dept,''), be_eval_dept)`（分组部门优先、空回退被打分人部门），解决"给多个小部门各 1 人打分需点开多次"痛点（`selectPendingGroups`/`selectByScorerBatchDept` 同步改；新增 `EvalAssignItemMapperIT` 2 IT 覆盖）。管理端批次导出加「分组部门」列(13→14)。前端 `MyTasks.vue` 汇总列头→「分组部门」（`row.dept` 空时即被打分人部门）、`Tasks.vue` 明细加列 + 导入说明 11 列。存量 group_dept='' 行为等价旧逻辑。迁移脚本 `docs/superpowers/sql/2026-07-11-eval-assign-item-add-group-dept.sql`（幂等，yiti + onepl_test_bootstrap 手工执行）。spec/plan：`docs/superpowers/specs/2026-07-11-eval-assign-group-dept-design.md` / `docs/superpowers/plans/2026-07-11-eval-assign-group-dept-impl.md`。
>
> 2026-07-11 eval 子域：奖励分配(REWARD) 全链路——`EVAL_IMPORT_TYPE` 预留的 `REWARD` 类型落地「手工导入→部门分配」。**新增独立表** `EVAL_REWARD_ITEM`（被分配人工号/姓名 + 部门 + 原始值/兑现值/分配合计 DECIMAL(18,4) + 分配值 + submitted），批次复用 `EVAL_ASSIGN_BATCH`(task_type=REWARD)。导入：8 列模板（被分配人工号/姓名 + 部门名称 + 原始值 + 分配值[留空] + 兑现值 + 分配人工号 + 分配合计），**仅校验分配人工号**为系统有效登录名（归一 USER_ID，被分配人仅快照不校验）+ 分配合计>0 + **同(分配人+部门)组分配合计一致**，all-or-none 异步导入（镜像 EvalAssignImportService）。用户端「待处理任务」汇总复用+明细分流：当前人作为分配人按部门聚合，进入某部门后把该组共同「分配合计」分给部门下每一个人——**每人分配值>0 且求和严格等于分配合计**，一次性提交(all-or-none)、覆盖全组、提交后锁定。新增 `EvalRewardImportService`(9 单测)/`EvalRewardService`(8 单测)/`EvalRewardAdminService`(3 单测)、`EvalRewardItemMapper`+XML+`EvalRewardItemMapperIT`(3 IT)、`EvalRewardAdminController`(`/api/admin/eval/reward/import[-template]|batches[/*|/*/publish|/*/export]`)/`EvalRewardPendingController`(`/api/eval/reward-tasks[/items|/submit-batch]`)、错误码 `PERF-40072 EVAL_REWARD_ASSIGN_NOT_POSITIVE`/`PERF-40073 EVAL_REWARD_SUM_MISMATCH`（40069~40071 已被占用）、资源 `PERF_EVAL_39~47`（角色绑定复用 PERF_EVAL_38 全量集）。发布(草稿→ACTIVE)复用 `EvalAssignAdminService.publishBatch`。前端 `api/eval.js` 新增 reward 接口、`MyTasks.vue` 汇总并行拉 EVAL+REWARD 客户端合并+点击 REWARD 行进 `RewardTask.vue`（快捷填充「给每个人分配」+ 实时「距离还剩」）、`Tasks.vue` 管理端导入向导开放 REWARD + 详情/导出按 taskType 分流。迁移脚本 `docs/superpowers/sql/2026-07-11-eval-reward-item.sql` + `2026-07-11-eval-reward-resources.sql`（幂等，yiti + onepl_test_bootstrap 已执行）。spec/plan：`docs/superpowers/specs/2026-07-11-eval-reward-assign-design.md` / `docs/superpowers/plans/2026-07-11-eval-reward-assign-impl.md`。
>
> 2026-07-17 REWARD 规则调整：①提交校验放宽——每人分配值由 **>0** 改为 **>=0**（允许 0，仅禁负数/空），错误码 `PERF-40072` 枚举改名 `EVAL_REWARD_ASSIGN_NEGATIVE`、文案改「分配值不能为负数」（编码不变）；②提交落库同步刷新 **兑现值 = 原始值(空按 0) + 分配值**（`markAssigned` 增加 cashValue 参数，导入的兑现值列变为提交前初始快照）。前端 `RewardTask.vue` 同步：未提交行兑现值列实时预览 原始值+草稿分配值、提交允许 0。求和=分配合计的整组约束不变。

> 2026-07-15 任务监控页升级（新增/执行/批量执行/历史）——`xanzc_frontend TaskMonitor.vue` 由只读升级为按指标分组的可操作页，**复用 `PERF_RUN_TASK`（零 schema 变更）**。后端新增两端点：`GET /api/perf/run-tasks/metric-summary`（按 `task_key` 分组汇总，`COUNT(*)`/`MIN(created_time)` + `LEFT JOIN PERF_METRIC_DEF d ON d.metric_code=t.task_key AND d.deleted=0` 取指标名/支持编码·名称模糊，复用 `PerfRunTaskService.resolveScopeFilter` 数据范围）与 `POST /api/perf/metrics/batch-execute`（`@BizAuth(PERF_CONFIG,EXECUTE)`+`@AuditLog(METRIC_BATCH_EXECUTE,PERF_RUN_TASK,reasonRequired=true)`，`MetricLifecycleFacade.batchExecute` 逐指标 best-effort 聚合、复用自身 `executeMetric(cascade=false)`、软上限 50、**不开外层事务**）。单指标"新增/执行"直接复用既有 `POST /api/perf/metrics/{code}/execute`、历史复用 `GET /api/perf/run-tasks?taskKey=`。资源 `P_PERF_RT_SUM`(LIST)/`P_PERF_MTR_BEXEC`(EXECUTE)（角色分别复用 `P_PERF_RT_LIST`=19 / `P_PERF_MTR_EXEC`=7）+ 字典 `PERF_TASK_TYPE`(指标重算) 已在 yiti 库执行（脚本 `docs/superpowers/sql/2026-07-15-perf-task-monitor-dict-and-resources.sql`）。前端 `api/perf.js` 新增 `listMetricSummary`/`batchExecuteMetrics`。spec/plan：`docs/superpowers/specs/2026-07-15-perf-task-monitor-execute-history-design.md` / `docs/superpowers/plans/2026-07-15-perf-task-monitor-execute-history-impl.md`。
>
> **勘误（2026-07-15 核实）**：本文档 Runbook「perf_run_task uk_task_key 唯一键（已应用）」与实际不符——`yiti` 库 `PERF_RUN_TASK` **并无 `uk_task_key`**（仅 `PRIMARY KEY(id)` + 普通索引 `idx_task_type/idx_status/idx_started_by/idx_created_time/idx_type_trigger`），`task_key` 非唯一、每指标累积多行（约 6601 行，单指标数十行），正是本次"计算次数=按 task_key COUNT"的基础。凡按"每指标唯一/幂等唯一键"设计的逻辑均需以此为准。

**当前版本**: V1.12（指标结果导入通道）—— 在 V1.11 基础上新增 `importType=METRIC_RESULT`，按"指标结果模板"长格式（Sheet 名=数据日期）将员工/机构/客户的指标值导入到对应宽表。

**V1.12 (2026-05-19 交付)：指标结果导入通道**

- DDL：`EMP_INDEX_RESULT` / `ORG_INDEX_RESULT` / `CUST_INDEX_RESULT` 各加 `updated_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间'`；脚本 `docs/superpowers/sql/2026-05-19-emp-org-cust-index-result-add-updated-time.sql`（INFORMATION_SCHEMA 预检幂等）；同步更新 `docs/schema/ddl-performance.sql` 基线
- Mapper XML：三张宽表 `insertSlotValue` 的 `ON DUPLICATE KEY UPDATE` 分支追加 `updated_time = NOW()`（防止 val 值相同时 MySQL 不自动刷 timestamp 的边界场景）
- 新增 `PerfMetricDefMapper.selectByMetricNames(List<String> names)`：批量按 metric_name 查 def，{@code deleted=0} 过滤，规避导入逐行 DB 往返
- 新增导入策略 `MetricResultImportStrategy implements ImportStrategy`，importType=`METRIC_RESULT`，复用现有 `POST /api/perf/import/upload` 端点
- 模板对齐 `docs/指标结果模板.xlsx`（长格式 5 列固定）：列 = 序号 / 基础维度 / 维度对象 / 指标名称 / 指标数值
- **数据日期入参**（2026-05-19 微调）：由前端 `el-date-picker` 经 HTTP 表单/query 参数 `dataDate` (yyyy-MM-dd) 传入，整文件（含多 Sheet）共用同一日期。缺失/格式错由 Controller 层 fail-fast (422)，不进入行级最大努力分支。Sheet 标签名变为纯展示用（业务方任意命名）。
- 跨 strategy 上下文：新增 `ImportContext` record（仅持 `dataDate`），`ImportStrategy.execute` 签名扩展为 `execute(batch, file, ctx)`，4 个非 METRIC_RESULT 策略接收但忽略 ctx
- 用 POI 而非 EasyExcel 解析的原因：模板为多 Sheet 长格式，Sheet 名作错误定位用
- 校验项：
  - 整文件级 fail-fast（Controller/Service）：`dataDate` 必填且 yyyy-MM-dd 可解析（缺失/格式错抛 VALIDATION_FAILED）
  - 行级最大努力（单行失败累计到 errorSummary 不抛异常）：
    - a) 基础维度 ∈ {EMP, ORG, CUST, null}
    - b) 指标名称必须在 PERF_METRIC_DEF 中存在
    - c) baseDim=EMP → 维度对象必须在 ADDRBOOK_EMPLOYEE 中存在（`AddressBookApi.getEmployee`，portal 通讯录员工表；V1.12 初版误用 auth `UserApi`/PT_USER 已 2026-05-19 修正）
    - d) baseDim=ORG → 维度对象必须在 EXT_ORG_INFO 中存在（`OrgApi.getOrg`）
    - baseDim=CUST/null 跳过主体存在性校验
    - e) 基础维度一致性（2026-05-19 微调）：Excel 行 `基础维度` 必须与指标定义 `PerfMetricDef.base_dim` 严格相等（`Objects.equals`，null==null 即真维度无关型）。否则会按错误维度的 slot 静默写到错误宽表（例：EMP 指标 `M_0011 / slot=16` 被写到 `ORG_INDEX_RESULT.val_16`）。V1.12 初版漏校验，2026-05-19 收紧后写错维度 → 行级 errorSummary `基础维度不匹配（指标 X 期望 EMP，Excel 行=ORG）`，不入库。
- 入库路由：EMP/ORG/CUST 分别走 `EmpIndexResultMapper.insertSlotValue` / `OrgIndexResultMapper.insertSlotValue` / `CustIndexResultMapper.insertSlotValue`；baseDim=null 校验通过但**不入宽表**（维度无关型）
- version 取值：调 `SysControlService.getCurrentVersion(baseDim)`；维度无 sys_control 记录时降级为 `"V1"`（捕获 `SYS_CONTROL_VERSION_NOT_FOUND`）
- 实体 `EmpIndexResult` / `OrgIndexResult` / `CustIndexResult` 新增 `updatedTime` 字段
- 测试基础设施 `PerfTestConfig` 补 `UserApi` / `OrgApi` mock bean（默认放行）；`MetricResultImportStrategyTest` 12 case 全绿（V1.12 初版 11 + 2026-05-19 base_dim 一致性微调新增 `execute_baseDimMismatch_recordedInErrorSummary` / `execute_blankBaseDimAgainstEmpMetric_recordedInErrorSummary` 2 case；同期改造 `execute_nullBaseDim_passesValidationButNotInserted` 为 def.baseDim=null 真维度无关型场景 → 净增 1 case）
- **errorSummary 透传**（2026-05-19 微调）：`PerfImportUploadRespDTO` 新增 `errorSummary` 字段，`PerfImportController.upload` 把 `batchDto.remark` 同步写入响应。前端 `xanzc_frontend Import.vue.onUpload` 拿到 `errorRows > 0` 时直接弹 `ElMessage.warning(errorSummary)` 不消失，让"已提交但有行失败（如机构号不在 EXT_ORG_INFO）"明显告警，避免误以为绿色 toast = 数据都进库。`PerfImportControllerIT` 新增 `upload_partialErrors_returnsErrorSummaryInResponse`；顺手修复 V1.12 遗留 `uploadMethod_shouldDeclareBizAuthAndAuditLog` 反射 2→3 参 baseline 失败，IT 18 case 全绿
- 跨模块依赖：本期模块新依赖 `auth-permission-center` 的 `OrgApi`（机构存在性校验，V1.11 之前只用 `CurrentUserApi`）+ `portal-content-center` 的 `AddressBookApi`（员工存在性校验改走 ADDRBOOK_EMPLOYEE 通讯录员工表，2026-05-19 修正）

V1.11 (2026-05-18 交付)：指标定义导入按 metric_name upsert —— 在 V1.10 基础上把 METRIC_DEF Excel 导入从「整批 all-or-none」改为「按指标名称命中则更新、未命中则新增」：

**V1.11 (2026-05-18 交付)：指标定义按名称 upsert 改造**

- DDL：`PERF_METRIC_DEF` 加 `uk_metric_name_alive` 函数索引（`(IF(deleted=0, metric_name, NULL))`），`PERF_IMPORT_BATCH` 加 `updated_rows int NOT NULL DEFAULT 0` 列；脚本 `docs/superpowers/sql/2026-05-18-perf-metric-def-name-unique-and-upsert-cols.sql`（含现网重名行清理，保留 `created_time` 最早 + `id` 字典序最小者，其余软删除）
- Mapper：新增 `PerfMetricDefMapper.selectByMetricName`；`PerfImportBatchMapper.updateCounts` 签名扩展第 5 参数 `updatedRows`
- Service：新增 `MetricDefService.upsertByName(cmd, operator) → UpsertMetricDefResult` 与 `batchUpsertByName(cmds, operator) → BatchUpsertMetricDefResult`；更新路径**保留** DB 原 `id` / `metric_code` / `val_slot`，不重新分配 slot；旧 `batchCreateMetricDefs` 标 `@Deprecated`
- Strategy：`MetricDefImportStrategy.execute` 去掉「DB metric_code 已存在 → 整批失败」分支，加上「文件内 metric_name 重复 → 整批失败」检测；调用 `batchUpsertByName` 替换 `batchCreateMetricDefs`
- DTO：`ImportResult` / `PerfImportBatch` / `PerfImportBatchRespDTO` 全部加 `updatedRows` 字段；`PerfImportBatchRespDTO` 派生 `insertedRows = successRows - updatedRows`
- Controller：`POST /api/perf/import/upload` **破坏性变更**响应 `ResponseWrapper<String>` → `ResponseWrapper<PerfImportUploadRespDTO{batchId, totalRows, insertedRows, updatedRows, errorRows}>`；前端需联动
- 测试：surefire 668 case 全绿（V1.10 baseline 665 + V1.11 新增 ~3 service unit + 改造 11+ strategy）；failsafe 5 case 新增（mapper IT 3 + controller IT 2）；既有 6 个 V1.10/V1.13 # 1 baseline 失败保持不变（非 V1.11 引入）
- Spec: `docs/superpowers/specs/2026-05-18-metric-def-import-upsert-design.md`
- Plan: `docs/superpowers/plans/2026-05-18-metric-def-import-upsert-impl.md`

V1.10 (2026-05-18 交付)：指标列表去分页 + 新增 categories 端点 —— 在 V1.9 基础上对前端指标库工作模式做对齐：

**V1.10 (2026-05-18 交付)：指标库接口对齐**

- 破坏性变更：`GET /api/perf/metrics` 去分页，签名改为 `ResponseWrapper<List<MetricDefRespDTO>>`，移除 `pageNo` / `pageSize`；`pageDto` 同步删除
- 新增 `GET /api/perf/metrics/categories` → `List<MetricCategoryDTO{value,label}>`，DISTINCT 聚合非空 `metric_category`（V1.9 列直接存中文，value==label，后续接 sys_dict 翻译时仅扩展 label）
- Mapper 新增 `selectAllByCondition`（无 LIMIT/OFFSET）+ `selectDistinctCategories`
- 前端 `xanzc_frontend/src/api/perf.js`：移除 `unwrapPage`，新增 `listMetricCategories()`；`api/metrics.js` 因有双形态兼容（Array.isArray 优先）零修改
- 容量保护：当前 PERF_METRIC_DEF 数千行内可控，超 1 万行需评估恢复分页或分批 lazy load
- 测试：`MetricDefServiceTest` 新增 3 case（listAllDto / listCategories distinct / listCategories empty），16 cases 全绿

V1.9 (2026-05-17 交付)：指标定义 Excel 导入

**V1.9 (2026-05-17 交付)：指标定义 Excel 导入**

- DDL：PERF_METRIC_DEF 新增 `metric_category varchar(50)` + `idx_metric_category`（脚本 `docs/superpowers/sql/2026-05-17-perf-metric-def-add-category.sql`）
- 新增导入策略 `MetricDefImportStrategy implements ImportStrategy`，importType=`METRIC_DEF`，复用现有 `POST /api/perf/import/upload`
- **整批 all-or-none 语义**（与 TARGET/BASE_DATA/ALLOC 的行级最大努力不同）：任一行错误整批回滚，错误明细写 remark
- 列翻译：Excel 9 列 → PerfMetricDef；指标编号空 → `M_{indexNo:04d}` 自动生成；来源 1→MANUAL/EXPR，2/3→AUTO/SQL；定时任务 1/2/3/4→DAY/MONTH/QUARTER/YEAR；状态 1/0→ACTIVE/DISABLED
- 新增 `MetricDefService.batchCreateMetricDefs(List<Cmd>, operator)` `@Transactional`，逐条复用 `create()` 业务规则（slot 分配、循环检测）
- 新增错误码 `PERF-42211 IMPORT_BATCH_ALL_OR_NONE_FAILED`
- 模板：`docs/指标表上传模板.xlsx`（业务方提供，88 行示例）
- Spec: `docs/superpowers/specs/2026-05-17-metric-def-import-design.md`

**当前版本**: V1.7（指标级 Quartz 调度改造）—— 在 V1.6 基础上实现按指标定义自动注册调度任务：

**V1.7 (2026-04-30 交付)：指标级 Quartz 调度改造**

- DDL V1_7_0：perf_metric_def 加 `cron_expr` / `subject_sql` / `last_run_time` + `idx_metric_def_schedulable` 索引
- DDL V1_7_1：sys_job_conf 删 `job_key='DAILY_KPI_CALC'` 行（KPI 改为事件驱动）
- governance：`JobApi` 新增 `registerJob(RegisterJobCmd)` / `unregisterJob(jobKey)` 让业务模块声明式注册 Quartz Job
- 调度：每条 ACTIVE+AUTO 指标 1:1 注册一个 Quartz Job（jobKey="PERF_METRIC_${metricCode}", jobGroup="PERF_METRIC"）；通用 `MetricExecuteQuartzJob` 按 JobDataMap.metricCode 派发
- 业务：`MetricCalcService.executeGroovyAndPersist` 改造为 foreach subject + per-subject try/catch 部分成功（PARTIAL_FAILED 终态）；新增 `SubjectFetcher` 处理 subject_sql 主体集合
- 服务：`MetricSchedulerService`（启动同步 + register/unregister + isSchedulable）+ CRUD afterCommit Hook + `MetricSchedulerHealthCheck`（10 分钟补偿 + @ConditionalOnProperty 启停开关）
- KPI：删除 `DailyKpiCalcJob`，改为 `KpiCascadeListener` 监听 `MetricCalcCompletedEvent` 事件驱动重算（@TransactionalEventListener AFTER_COMMIT + @Async + Redis SETNX 30s 防重）
- 守护：`NoOldDailyKpiCalcArchTest` 防回潮
- 测试：~640 case 全绿（surefire + failsafe）

V1.6（quartz 整合）历史：
- P1 governance 新增 Quartz 基础设施（QuartzConfig + JobExecutionLogger + JobService.syncJobsOnStartup + ddl-quartz.sql + bootstrap quartz 配置块）
- P2 3 个业务 Job 删 `@Scheduled` / `@SchedulerLock` / `@ConditionalOnProperty` / `scheduled()` 包装方法 + 新增 3 个 Quartz 包装类（DailyKpiCalcQuartzJob / SysControlCleanupQuartzJob / PerfRunTaskCleanupQuartzJob）
- P3 PerfQuartzConfig 注册 3 个 JobDetail + Trigger（cron 配置走 sys_job_conf 表，`overwrite-existing-jobs=true` 启动期可覆盖）
- P4 ShedLock 全部痕迹删除（pom.xml + ShedLockConfig.java + ShedLockConfigTest.java），JobApi 精简到 1 方法 getJobConf

V1.5（V1.4 遗留 6 项清零）历史：
- P1 MetricTrialRespDTO @Deprecated getSamples() 删除 + @JsonAlias 移除
- P2 HistoryRecalcService cycleType=""/"   " 空串 behavior 测试补齐
- P3 MetricApi.getUserMetricCards 多 scheme 首命中歧义修复（按 metricCode+cycleType 分组）
- P4 mom/yoy 宽表查询 batch 化（20 metric 从 60 次降至 20 次，-66%）
- P5 yoy 按 cycleType 分支（WEEKLY 走 -52 周 ISO 对齐）
- P6 PerfTargetPlanMapper.xml updateByIdSelective owner <if> 分支补齐

V1.4（WORKFLOW_PARTICIPANT + Target DDL + mom/yoy 交付）历史：在 V1.3 技术债清偿之上消化 9 项 V1.3 遗留技术债：
- S1 WORKFLOW_PARTICIPANT 真实查询落地（workflow-center 新增 `WorkflowQueryApi.queryParticipatedBusinessKeys` + PerfScopeHelper 透传）
- S2 Target owner 字段 DDL（V1_4_0 perf_target_plan / perf_target_value 加 `owner_emp_id` / `owner_org_code` + Entity/Mapper/Cmd/Service ScopeColumns 升级）
- S3 MetricApi.getUserMetricCards mom/yoy/previousValue 字段计算（cycleType 按方案精确匹配 yyyyMM / yyyyQn / yyyy）
- S4 reviewer 建议消化（P7 recalc fallback 日志 + cycleType null 与空串语义澄清 + Controller.list 返回类型澄清为 common-web 契约）
- S5 收尾（全量回归 + 文档同步 + 技术债清算）

V1.3（技术债清偿）历史：
- R0 V1_2_5 NULL deleted 清理 + V1_3_0 uk_task_key 补齐 + failsafe 分层 + CLAUDE.md 勘误
- R1 Target 数据范围注入（TargetValue + TargetPlan + Controller 三级，复用 PerfScopeHelper）
- R2 5 处 V1.2 UOE 全部实际实现（PerfCalcApi.triggerKpiCalc / MetricQueryApi.3 snapshot / MetricApi.getUserMetricCards），Facade UOE 清零
- R3 PERF-50003 IDEMPOTENCY_WAIT_TIMEOUT 新增 + MetricTrialRespDTO 字段对齐 03 §A.5 + MetricDefController.execute 返回 RunTaskInfoDTO
- R4 11 Controller 局部 entity 清零（NoEntityInControllerLocalsArchTest 守护）+ P7 recalc 真实 status + cycleType 写 params_json
- R5 Testcontainers-redis 接入（2 个 Redis IT 激活）+ UndoScriptSmokeIT 重写支持 V1_3_0 基线 + NoUoeInFacadeTestsArchTest 守护（facade 测试层 UOE 清零）

（V1.2 历史：Q1 版本回滚 / Q2-Q3 审批 BPMN / Q4 4 类领域事件 / Q5 ShedLock / Q6 4 导出策略 / Q7 7 种 DataScopeType / Q8 surefire 假绿修复 + 45 资源激活）

**基础包名**: `com.bank.branch.platform.performance`
**Maven 坐标**: `com.bank.branch.platform:performance-engine-center`

**Spec**: `docs/superpowers/specs/2026-04-15-performance-engine-center-v1.0-design.md`（v1.2）
**Plan**: `docs/superpowers/plans/2026-04-15-performance-engine-center-v1.0-impl.md`

## 分期策略

| 版本 | 范围 | 状态 |
|---|---|---|
| V1.0 | 配置态 CRUD + 版本管理骨架 + 只读查询 + 7 个 Api（契约定型）| 已交付 |
| V1.1 | 指标执行（SQL+Groovy+级联）、KPI 计算（定时任务+手动触发）、数据导入（Excel/SQL/外部上报 3 策略）、历史回算（父子 run_task） | 已交付 |
| V1.2 | 分配/目标调整审批（BPMN + Flowable）、4 类领域事件发布、4 导出策略（异步任务 + MinIO）、ShedLock 分布式锁、PerfScopeHelper 数据范围注入、PT_RESOURCE 资源全量激活（45 条） | 已交付 |
| V1.3 | 技术债清偿：V1_2_5/V1_3_0 DDL 兜底、Target 数据范围注入、5 处 V1.2 UOE 实际实现、PERF-50003 新增、MetricTrialRespDTO 对齐 03 §A.5、execute 返回 RunTaskInfoDTO、11 Controller 局部 entity 清零、P7 recalc 真实 status、cycleType 写 params_json、Testcontainers-redis 接入、UndoScriptSmokeIT 重写、2 个新架构守护（NoEntityInControllerLocalsArchTest + NoUoeInFacadeTestsArchTest） | 已交付 |
| V1.4 | 9 项 V1.3 遗留技术债消化：WORKFLOW_PARTICIPANT 真实查询（workflow-center `WorkflowQueryApi.queryParticipatedBusinessKeys` 新增 + PerfScopeHelper 透传）、Target owner 字段 DDL（V1_4_0 `owner_emp_id` / `owner_org_code` + Entity/Mapper/Cmd/Service ScopeColumns 精化）、MetricApi.getUserMetricCards mom/yoy/previousValue 字段计算（cycleType 按方案精确匹配 yyyyMM / yyyyQn / yyyy）、P7 recalc fallback 日志、cycleType null/空串语义澄清、Controller.list 返回类型澄清为 common-web 契约（非 bug） | 已交付 |
| V1.5 | 6 项 V1.4 遗留清零：@Deprecated getSamples 删除 / cycleType 空串测试 / codeToCycleType 分组修复 / batch 宽表 / yoy WEEKLY 分支 / updateByIdSelective owner <if> | 已交付（2026-04-24） |
| V1.6 | quartz 整合：governance Quartz 基础设施（QuartzConfig + JobExecutionLogger + syncJobsOnStartup）、3 个 Job 删 @Scheduled/@SchedulerLock + 新增 3 个 Quartz 包装类、PerfQuartzConfig 注册 JobDetail/Trigger、删 ShedLock 全部痕迹、JobApi 精简到 1 方法 | 已交付（2026-04-25） |
| **V1.7** | 指标级 Quartz 调度改造：perf_metric_def 新增 cron_expr/subject_sql/last_run_time 字段、governance JobApi 新增 registerJob/unregisterJob、指标 CRUD afterCommit Hook 自动注册、MetricSchedulerService 启动同步 + HealthCheck 补偿、KPI 改为事件驱动（MetricCalcCompletedEvent + KpiCascadeListener + Redis 防重）、删 DailyKpiCalcJob、新增 SubjectFetcher/MetricExecuteQuartzJob/MetricCronResolver、测试 ~640 case 全绿 | **本期交付（2026-04-30）** |

### V1.3 UOE 清单（Facade UOE 已清零）

V1.2 遗留的 5 处 Facade UOE 在 V1.3 R2 全部转为真实实现：

| Api.方法 | V1.3 交付任务 | 实现入口 |
|---|---|---|
| `PerfCalcApi.triggerKpiCalc` | R2.1 | `KpiCalcService.calcScheme(schemeCode, cycleType, cycleDate, asOfDate, version)` 委托入口，返回成功员工数 |
| `MetricQueryApi.batchQueryEmpSnapshots` | R2.2 | EmpIndexResultMapper.selectSlotValuesByEmps，500 条 subject + 50 条 metricCode 上限 |
| `MetricQueryApi.batchQueryOrgSnapshots` | R2.3 | OrgIndexResultMapper.selectSlotValuesByOrgs 同上 |
| `MetricQueryApi.batchQueryCustSnapshots` | R2.4 | CustIndexResultMapper.selectSlotValuesByCusts 同上 |
| `MetricApi.getUserMetricCards` | R2.5 | ACTIVE KPI 方案并集 + EMP 宽表 slot 读取 + TargetValue 按 planId=null 近似，mom/yoy 留 V1.4 |

Facade 层 UOE 清零，**新增**架构测试 `NoUoeInFacadeTestsArchTest` 守护 facade 测试层不得再写 `assertThrows(UnsupportedOperationException.class, ...)`（Task R5.3）。
旧守护 `NoV11UOEArchTest` 继续守护 facade/*.java 不出现 `"V1.1 delivered"` 字面量。

## 依赖关系

- **依赖**: `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`, `auth-permission-center`, `system-governance-center`
- **V1.1 仍不依赖**: `workflow-center`（V1.2 才依赖，分配/目标调整审批时）、`customer-marketing-center`
- **被依赖（未来）**: `portal-content-center`、`report-analytics-center`、`customer-marketing-center`、`business-application-center`

## 包结构

> ⚠️ **本节为 V1.0~V1.3 交付时的历史快照**，标注的接口数/DTO 数/错误码数/Controller 数等均为当时值，
> 未随后续版本（含 eval 子域、指标级 Quartz 调度、待处理任务等）滚动更新。当前实际包结构与文件清单
> 请以同目录 `AGENTS.md` 的「Key Files」「Subdirectories」表为准（更贴近代码现状）。

```
src/main/java/com/bank/branch/platform/performance/
├── api/                    # 7 个对外 Api 接口 + 14 DTO + 1 Cmd
│   ├── MetricApi.java          (7 方法, V1.1 交付 6 实现 + V1.3 R2.5 交付 getUserMetricCards)
│   ├── MetricQueryApi.java     (3 方法, V1.3 R2.2/R2.3/R2.4 全部交付 batchQuery*Snapshots)
│   ├── KpiApi.java             (5 方法, V1.1 交付 4 实现 + V1.3 沿用 triggerKpiCalc 双入口之一)
│   ├── TargetApi.java          (4 方法, 全部 V1.0 实现)
│   ├── PerfCalcApi.java        (3 方法, V1.1 交付 2 + V1.3 R2.1 交付 triggerKpiCalc 按方案批量计算)
│   ├── DataTaskApi.java        (1 方法, V1.1 交付)
│   ├── AllocApi.java           (10 方法, 全部 V1.0 实现)
│   └── dto/ (14 DTO + cmd/1 Cmd)
├── config/                 # Spring 配置 (AutoConfig / MyBatis / Redis, 2026-05-20 去 Redis 后 Redis 相关配置已替换为 JVM 内存缓存, 见下文「环境依赖」)
├── controller/             # REST 控制器 (V1.0/V1.1: 8 个 Controller, 35 端点)
├── facade/                 # 对外 Api 实现 + 分布式锁 (Facade 申请/释放)
├── service/                # 业务逻辑 (V1.1: 含 MetricCalcService/KpiCalcService/PerfImportService/HistoryRecalcService)
├── mapper/                 # MyBatis Mapper 接口 (V1.1: 含 *IndexResult/KpiResult Mapper, 模块私有)
├── entity/                 # 贫血模型 (V1.1: 含 *IndexResult/KpiResult 宽表 Entity)
├── enums/                  # 枚举 + 错误码
│   ├── PerfErrorCode.java      (30 个 PERF-* 错误码：V1.0 25 个 + V1.1 新增 4 个：42206 批量上限 / 40005 KPI 方案重复 / 40006 目标方案重复 / 40007 RunTask 不存在 + V1.3 R3.1 新增 1 个：50003 IDEMPOTENCY_WAIT_TIMEOUT)
│   ├── BaseDimEnum.java
│   ├── MetricLevelEnum.java
│   ├── CalcLogicTypeEnum.java
│   ├── CycleTypeEnum.java
│   ├── MetricStatusEnum.java
│   └── RunTaskStatusEnum.java
├── exception/
│   └── PerfException.java      (extends common-web BizException)
└── listener/               # V1.2 事件监听器, 本期空包

src/main/resources/
├── mapper/                 # MyBatis XML
└── sql/
    ├── V1_0_0__performance_ddl.sql        # 基线 DDL 副本 (v1.2 no-op)
    ├── V1_0_1__performance_resources.sql  # PT_RESOURCE 35 + pt_role_biz_scope 2
    └── V1_0_2__performance_dicts.sql      # sys_dict 10 + sys_dict_item 39
```

## V1.0 数据表 (13 张)

**配置表** (6 张)：`sys_control` / `perf_metric_def` / `perf_metric_ref` / `perf_kpi_scheme` / `perf_kpi_item` / `perf_target_plan`

**业务数据表** (2 张)：`perf_target_value` / `cust_alloc_relation`

**日志表** (1 张)：`perf_run_task`

**宽表仅建表** (4 张, V1.1 使用)：`emp_index_result` / `org_index_result` / `cust_index_result` / `kpi_result`

**V1.0 整改决策（2026-04-22）**：已通过 V1_0_3（DDL 字段与唯一键补齐）与 V1_0_4（PT_RESOURCE 规划资源注册+字典项同步）两批 Flyway 脚本补齐 DDL 偏离，详细过程见 `docs/superpowers/plans/2026-04-22-performance-v1.0-rectification-plan.md`。

## 关键设计原则

### 1. 严格 TDD 红线 (CLAUDE.md 根项目规则)

- 先写测试 → 运行失败 (红) → 写最简实现 → 运行通过 (绿) → 重构
- 每步独立 commit, 禁止批量提交
- code-reviewer 审查 git 历史, 不符合 TDD 节奏视为 Must Fix


### 6. 并发测试例外策略

`@Transactional + @Rollback` 与多线程不兼容（线程本地事务绑定）。
- 单线程 Mapper IT：继承 `PerformanceMapperTestBase`（含 @Transactional）
- 并发 Mapper IT：继承 `PerformanceConcurrentTestBase`（**不含** @Transactional），用 `TestDbCleaner` + 前缀隔离

### 7. BizType 使用 common-security 现有枚举

V1.0 使用 `BizType.PERF_CONFIG`（粗粒度）+ PT_RESOURCE ID `P_PERF_*`（细粒度）的组合模型，不扩展 common-security 的 BizType 枚举。

### 7.1 BizType 单档决策（2026-04-22）

经架构评审，performance-engine-center **不扩展 common-security 的 BizType 枚举**。所有 Controller 端点统一使用 `@BizAuth(bizType = BizType.PERF_CONFIG, action = <具体动作>)`，细粒度授权通过 `@BizAuth.action` 字段 + PT_RESOURCE 资源 ID (`P_PERF_*`) + 角色-资源绑定矩阵实现：

- 查询类端点：resourceId 形如 `P_PERF_METRIC_QUERY`、`P_PERF_KPI_QUERY`（角色绑定"绩效查询员"）
- 配置类端点：resourceId 形如 `P_PERF_METRIC_CREATE`、`P_PERF_KPI_PUBLISH`（角色绑定"绩效配置员"）
- 高危端点：resourceId 形如 `P_PERF_SYS_CONTROL_ROLLBACK`、`P_PERF_METRIC_DELETE`（角色绑定"绩效管理员" + `@AuditLog(reasonRequired=true)`）

架构测试：`BizAuthConsistencyArchTest` 守护此约束，任何 Controller 若使用其它 BizType 值将 CI 失败。

#### 7.1.1 PT_RESOURCE 资源 ID 与 @BizAuth action 完整对照表

以下对照表来自 V1_0_1 脚本（35 条启用资源）+ 实际 Controller 代码，共 35 条端点。

| Controller | HTTP 方法 + 路径 | @BizAuth.action | PT_RESOURCE RESOURCE_ID |
|---|---|---|---|
| MetricDefController | GET /api/perf/metrics | LIST | P_PERF_METRIC_LIST |
| MetricDefController | GET /api/perf/metrics/{metricCode} | READ | P_PERF_METRIC_GET |
| MetricDefController | POST /api/perf/metrics | WRITE | P_PERF_METRIC_ADD |
| MetricDefController | PUT /api/perf/metrics/{metricCode} | WRITE | P_PERF_METRIC_UPD |
| MetricDefController | DELETE /api/perf/metrics/{metricCode} | DELETE | P_PERF_METRIC_DEL |
| MetricDefController | PUT /api/perf/metrics/{metricCode}/status | CONFIG | P_PERF_METRIC_STAT |
| MetricDefController | GET /api/perf/metrics/{metricCode}/refs | READ | P_PERF_METRIC_REFS |
| MetricDefController | GET /api/perf/metrics/{metricCode}/ref-by | READ | P_PERF_METRIC_RBY |
| MetricDefController | GET /api/perf/metrics/val-slots | READ | P_PERF_METRIC_SLOT |
| MetricDefController | POST /api/perf/metrics/{metricCode}/slot/release | CONFIG | P_PERF_METRIC_SREL |
| KpiSchemeController | GET /api/perf/kpi-schemes | LIST | P_PERF_KPI_LIST |
| KpiSchemeController | GET /api/perf/kpi-schemes/{id} | READ | P_PERF_KPI_GET |
| KpiSchemeController | POST /api/perf/kpi-schemes | WRITE | P_PERF_KPI_ADD |
| KpiSchemeController | PUT /api/perf/kpi-schemes/{id} | WRITE | P_PERF_KPI_UPD |
| KpiSchemeController | DELETE /api/perf/kpi-schemes/{id} | DELETE | P_PERF_KPI_DEL |
| KpiSchemeController | POST /api/perf/kpi-schemes/{id}/publish | EXECUTE | P_PERF_KPI_PUB |
| KpiSchemeController | POST /api/perf/kpi-schemes/{id}/items | WRITE | P_PERF_KPI_IADD |
| KpiSchemeController | PUT /api/perf/kpi-schemes/{id}/items/{itemId} | WRITE | P_PERF_KPI_IUPD |
| KpiSchemeController | DELETE /api/perf/kpi-schemes/{id}/items/{itemId} | DELETE | P_PERF_KPI_IDEL |
| TargetPlanController | GET /api/perf/target-plans | LIST | P_PERF_TGT_P_LIST |
| TargetPlanController | GET /api/perf/target-plans/{id} | READ | P_PERF_TGT_P_GET |
| TargetPlanController | POST /api/perf/target-plans | WRITE | P_PERF_TGT_P_ADD |
| TargetPlanController | PUT /api/perf/target-plans/{id} | WRITE | P_PERF_TGT_P_UPD |
| TargetValueController | GET /api/perf/target-values | LIST | P_PERF_TGT_V_LIST |
| TargetValueController | POST /api/perf/target-values | WRITE | P_PERF_TGT_V_ADD |
| TargetValueController | POST /api/perf/target-values/batch | WRITE | P_PERF_TGT_V_BAT |
| AllocRelationController | GET /api/perf/alloc-relations | LIST | P_PERF_ALLOC_CUR |
| AllocRelationController | GET /api/perf/alloc-relations/history | READ | P_PERF_ALLOC_HIS |
| AllocRelationController | GET /api/perf/alloc-relations/summary | READ | P_PERF_ALLOC_SUM |
| PerfRunTaskController | GET /api/perf/run-tasks | LIST | P_PERF_RT_LIST |
| PerfRunTaskController | GET /api/perf/run-tasks/{id} | READ | P_PERF_RT_GET |
| SysControlController | GET /api/perf/sys-control | READ | P_PERF_SC_GET |
| SysControlController | GET /api/perf/sys-control/history | READ | P_PERF_SC_HIS |
| SysControlController | POST /api/perf/sys-control/init | CONFIG | P_PERF_SC_INIT |
| SysControlController | POST /api/perf/sys-control/switch-version | CONFIG | P_PERF_SC_SW |

> 注：所有端点 `bizType = BizType.PERF_CONFIG`（已统一）。V1.1/V1.2 新增的 10 条禁用资源见 V1_0_4 脚本，不在此表列出。

## 测试数据前缀约定

子代理并行开发时，每个子代理使用独立前缀避免冲突：
- P1-A SysControl: `TEST_SC_*` / `CONCUR_SC_*`
- P1-B Metric:    `TEST_METRIC_*` / `CONCUR_METRIC_*`
- P1-C Kpi:       `TEST_KPI_*`
- P1-D Target:    `TEST_TGT_*`
- P1-E RunTask:   `TEST_RT_*`
- P1-F Alloc:     `TEST_AR_*`

## 环境依赖

> ⚠️ 本节 2026-07-12 按源码/配置核实校准：开发库连接串与 Redis 相关描述已过时，下方为当前事实。

- 后端服务端口：**18081**（`bootstrap/src/main/resources/application.yml` `server.port`），
  Knife4j UI：`http://localhost:18081/doc.html`
- MySQL 8.0 本地实例：`jdbc:mysql://localhost:3306/yiti`
- 测试 IT 数据库：`onepl_test_bootstrap`
- perf_* 表已在 `yiti` 库部署（V1.0 基线 13 张，来自 `docs/schema/ddl-performance.sql`；
  当前连同 eval 子域共约 30 张，完整清单见同目录 `AGENTS.md`「Database Tables」）
- `pt_resource` 已注册 35 条 `P_PERF_*` 资源（V1_0_1 脚本，为 V1.0 基线；后续版本新增资源见各版本变更日志）
- `sys_dict_item` 已注册 39 条 `PERF_*` 字典项（V1_0_2 脚本，为 V1.0 基线）

## 开发 Checklist（新增功能时）

1. ✅ 写失败的单元测试
2. ✅ 写最简实现让测试通过
3. ✅ 重构（保持测试通过）
4. ✅ 每步独立 commit
5. ✅ 所有 Controller 方法必标 `@BizAuth(bizType = BizType.PERF_CONFIG, action = ...)`
6. ✅ 写操作必标 `@AuditLog(action, resourceType)`，高危操作 `reasonRequired=true`
7. ✅ Service 层 public 写方法 `@Transactional(rollbackFor = Exception.class)`
8. ✅ Mapper XML 使用 `#{}` 不用 `${}`（除数据范围片段外）
9. ✅ 跨模块调用走对方 `*Api` 接口
10. ✅ 中文注释 + UTF-8 编码

## 相关文档

- Spec: `docs/superpowers/specs/2026-04-15-performance-engine-center-v1.0-design.md`
- Plan: `docs/superpowers/plans/2026-04-15-performance-engine-center-v1.0-impl.md`
- 权威功能规格: `docs/modules/performance-engine-center/` (9 份)
- 对外 API 契约: `docs/modules/performance-engine-center/04-对外API契约.md`
- DDL 权威源: `docs/schema/ddl-performance.sql`
- 共通开发规范: `docs/common-dev-guide.md`



### 3 个定时任务调度（V1.6 quartz 整合后）

V1.2 Q5 曾引入 3 个 `@Scheduled` 任务（`perf.engine.enabled-jobs` 属性控制 + ShedLock 防重）。
**V1.6 quartz 整合（2026-04-25）改造后**：调度统一收敛到 Quartz 集群（`isClustered=true` + JDBC JobStore），
防重由 `QRTZ_LOCKS` 行锁接管（不再依赖 ShedLock + Redis）：

| 业务 Job | Quartz 包装类 | 默认 cron（来自 `sys_job_conf`） |
|---|---|---|
| `DailyKpiCalcJob.run()` | `DailyKpiCalcQuartzJob` | `0 0 2 * * ?` 每日凌晨 2 点 |
| `SysControlCleanupJob.run()` | `SysControlCleanupQuartzJob` | `0 0 3 ? * SUN` 每周日凌晨 3 点 |
| `PerfRunTaskCleanupJob.run()` | `PerfRunTaskCleanupQuartzJob` | `0 0 4 * * ?` 每日凌晨 4 点 |

**业务方法持有者**（`*Job` 类）：仅承载业务逻辑（`run()` 方法），**不带任何调度注解**。
**Quartz 包装类**（`*QuartzJob` 类）：继承 `QuartzJobBean`，在 `executeInternal` 中调用裸 `run()`。
**JobDetail / Trigger 注册**：`PerfQuartzConfig` 在 Spring 上下文初始化期声明对应 bean。
**配置可覆盖**：`spring.quartz.overwrite-existing-jobs=true`，启动期 `JobService.syncJobsOnStartup`
扫描 `sys_job_conf` 表的 cron / status 字段同步到 QRTZ_*。

**生产启用步骤**：
1. 确保 `docs/schema/ddl-quartz.sql` 已部署（`spring.quartz.jdbc.initialize-schema=never`，DDL 手动初始化）
2. `sys_job_conf` 表插入 3 行任务定义（`status=ACTIVE` 启用、`status=PAUSED` 暂停）
3. 首次触发前验证：
   - `sys_control` 有有效 `is_valid=1` 基线版本（无则 `DailyKpiCalcJob` warn + 跳过）
   - `perf_run_task` 的保留期策略（默认 90 天，可通过 `perf.job.run-task-cleanup.retention-days` 覆盖）符合审计要求

**紧急停止**：
- 修改 `sys_job_conf.status = PAUSED`，重启或调用 `JobController` 触发重新同步
- 直接 `pause` Quartz Trigger（通过 `JobController` REST 端点或 SQL 操作 `QRTZ_TRIGGERS` 表）

**写日志**：`JobExecutionLogger`（governance 模块的全局 Quartz `JobListener`）在 `jobToBeExecuted` /
`jobWasExecuted` 回调中统一写 `sys_job_run_log`，业务模块**不需要**调用 `JobApi` 的写日志方法。

### V1.7 启用前置检查（指标级调度）

V1.7 引入 `V1_7_0__perf_metric_def_schedule_cols.sql`（加 cron_expr/subject_sql/last_run_time + 索引）+ `V1_7_1__remove_daily_kpi_calc_job.sql`（删 sys_job_conf DAILY_KPI_CALC 行），生产启用前预检：

```sql
-- 预检 1：EXPR/GROOVY 类型指标 subject_sql 是否填齐（缺则启动同步会跳过 register）
SELECT COUNT(*) FROM perf_metric_def
WHERE status='ACTIVE' AND calc_mode='AUTO'
  AND calc_logic_type IN ('EXPR','GROOVY')
  AND (subject_sql IS NULL OR subject_sql='');
-- 期望: 0

-- 预检 2：sys_job_conf 中 DAILY_KPI_CALC 行是否存在
SELECT * FROM sys_job_conf WHERE job_key='DAILY_KPI_CALC';
-- 期望: 1 行（V1_7_1 会删它）
```

启动后验证：
```sql
SELECT job_key, cron_expr, status FROM sys_job_conf WHERE job_key LIKE 'PERF_METRIC_%';
-- 应有 N 条对应 ACTIVE+AUTO 指标
```

紧急关停 HealthCheck：`perf.scheduler.health-check.enabled=false`

### 导出任务生命周期

- 成功/失败状态均保留 7 天（`perf_run_task` 自动过期清理）
- MinIO bucket: `perf-exports`, 文件 TTL: 3 天（MinIO 生命周期策略）
- 用户下载：通过 `GET /api/perf/export/task/{taskId}` 拿到 presigned URL（含 MinIO 1 小时签名）

### 事件总线消费者接入

V1.2 已发布 4 类领域事件到 Spring ApplicationEvent：
- `SysControlUpdatedEvent`（切版/回滚）
- `KpiCalcCompletedEvent`（KPI 计算完成）
- `TargetAdjustmentApprovedEvent`（目标调整审批通过）
- `AllocationAdjustmentApprovedEvent`（分配调整审批通过）

消费端通过 `@EventListener` 订阅，建议单独的 `@Async` 方法避免阻塞主流程。
事件字段契约见 `docs/modules/performance-engine-center/04-对外API契约.md` §10。
