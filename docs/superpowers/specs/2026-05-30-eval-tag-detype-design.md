# 评价标签去类型化设计（标签管理不区分评价人/被评价人）

> 状态：设计已确认，待写实现计划
> 日期：2026-05-30
> 模块：performance-engine-center / eval 子域 + xanzc_frontend

## 1. 背景与目标

当前评价标签（`EVAL_TAG`）带 `TAG_TYPE`（1=被评价人标签 / 2=评价人标签），这个类型是"标签是被评价角色还是评价角色"的**唯一真相来源**，被以下处全链路依赖：

- 标签管理页（`Tags.vue`）：新建/筛选/列表按类型
- 人员标签页（`UserTags.vue`）：被评价角色（单选 type=1）/ 评价角色（多选 type=2）
- 评价规则（`EvalRule.beEvalTagId` 挂载 / `EvalRuleGroup.evalTagId` 匹配组）
- 评价引擎：`EvalTaskService` 按被评价人 type=1 标签匹配规则；`EvalScoreService` 按评价人 type=2 标签匹配组
- `EVAL_USER_TAG`：只有 `(USER_ID, TAG_ID)`，被评价/评价靠 JOIN `EVAL_TAG.TAG_TYPE` 得到

**目标**（用户确认）：

1. **标签管理页不区分**评价人/被评价人 —— 标签是一个无类型的扁平池
2. **人员标签页仍区分**被评价角色与评价角色
3. **评价规则同理**仍区分被评价标签与评价人组标签
4. **局部排斥**：同一人的评价角色不能包含其自身的被评价角色；同一条规则内评价人组标签不能等于该规则的被评价标签
   - 局部 = 按人/按规则；同一标签允许是 A 的被评价角色、同时是 B 的评价角色

## 2. 核心模型

"被评价/评价"不再是**标签的属性**，而是**标签被使用时所处的角色槽**：

- `EVAL_TAG` 拍平为无类型标签池
- 人员维度：`EVAL_USER_TAG` 新增 `ROLE_TYPE`（1=被评价角色 / 2=评价角色）
- 规则维度：`EVAL_RULE.BE_EVAL_TAG_ID`（被评价）与 `EVAL_RULE_GROUP.EVAL_TAG_ID`（评价）本就分列承载角色，**结构不变**

**为何不能"保留 tag_type 仅 UI 隐藏"**：一个标签行无法同时是 type=1 和 type=2；要让同一标签既能作 A 的被评价角色又能作 B 的评价角色，角色必须落到使用处。已确认采用本模型。

## 3. 数据模型变更（SQL 直接执行，遵守 Flyway 禁令）

脚本：`docs/superpowers/sql/2026-05-30-eval-tag-detype.sql`（执行前 mysqldump 备份 `EVAL_TAG`/`EVAL_USER_TAG` 到 `sql/backup/`）。

执行顺序（次序关键）：

1. `ALTER TABLE EVAL_USER_TAG ADD COLUMN ROLE_TYPE TINYINT NOT NULL DEFAULT 1 COMMENT '1=被评价角色,2=评价角色'`
2. 回填：`UPDATE EVAL_USER_TAG ut JOIN EVAL_TAG t ON t.TAG_ID = ut.TAG_ID SET ut.ROLE_TYPE = t.TAG_TYPE`
3. 换唯一键：`ALTER TABLE EVAL_USER_TAG DROP KEY UK_USER_TAG, ADD UNIQUE KEY UK_USER_TAG_ROLE (USER_ID, TAG_ID, ROLE_TYPE)`
4. 去掉默认值（回填后角色必须显式）：`ALTER TABLE EVAL_USER_TAG ALTER COLUMN ROLE_TYPE DROP DEFAULT`
5. **冲突预检**：`SELECT TAG_NAME FROM EVAL_TAG GROUP BY TAG_NAME HAVING COUNT(DISTINCT TAG_TYPE)>1`
   - 已实测 yiti = 0 冲突（6 标签 / 7 关联）。若目标库扫出冲突：合并同名 tag_id（保留较小 id，重指 `EVAL_USER_TAG`/`EVAL_RULE`/`EVAL_RULE_GROUP` 的 FK 后删除多余行），再继续
6. `ALTER TABLE EVAL_TAG DROP KEY UK_TAG_NAME_TYPE, ADD UNIQUE KEY UK_TAG_NAME (TAG_NAME)`
7. `ALTER TABLE EVAL_TAG DROP COLUMN TAG_TYPE`

同步更新基线 `docs/schema/ddl-eval.sql`：`EVAL_TAG` 去 `TAG_TYPE` + 改唯一键；`EVAL_USER_TAG` 加 `ROLE_TYPE` + 改唯一键。

> 局部排斥靠 Service 层保证；`UK_USER_TAG_ROLE` 仅防同角色精确重复，物理上允许同人同标签两角色各一行（由 Service 拦截）。

## 4. 后端变更（performance-engine-center / eval）

### 4.1 标签去类型

| 文件 | 改动 |
|---|---|
| `entity/EvalTag.java` | 删 `tagType` 字段 |
| `mapper/EvalTagMapper.java` + `EvalTagMapper.xml` | `BASE_COLUMNS` 去 `tag_type`；`selectByNameAndType`→`selectByName`；`selectByCondition`/`countByCondition`/`selectAll` 去 `tagType` 参数与 `<if>`；排序去 `tag_type` |
| `service/EvalTagService.java` | `create(tagName)` 不收 type，唯一性按 name（`selectByName`）；`update` 改名唯一性按 name；`list`/`listAll` 去 `tagType` 参数 |
| `controller/EvalTagController.java` | `list`/`listAll` 去 `tagType` query 参；`create` 去 `tagType` query 参 |

### 4.2 人员标签角色化

| 文件 | 改动 |
|---|---|
| `entity/EvalUserTag.java` | 加 `Integer roleType` |
| `mapper/EvalUserTagMapper.xml` | `selectByUserId` 带 `role_type`；`selectTagIdsByUserIdAndType` 改查 `EVAL_USER_TAG WHERE user_id=? AND role_type=?`（去 JOIN）；`selectUserTagsByUserIds` 取 `ut.role_type AS roleType`（仍 JOIN 取 tag_name）；`batchInsert` 写 `role_type` |
| `mapper/EvalUserTagMapper.java` | `selectTagIdsByUserIdAndType` 参数名 `tagType`→`roleType`（语义切换，签名兼容） |
| `dto/EvalUserTagRow.java` | `tagType`→`roleType` |
| `service/EvalUserTagService.java` | `saveUserRoles`：删除 tagType 类型校验（标签无类型，仅校验存在性）；**加局部排斥**：`beEvalTagId` 若出现在 `evalTagIds` 抛 `EVAL_ROLE_CONFLICT`；按角色写 `role_type`（beEval→1，每个 eval→2）。`assembleRows`：按 `EvalUserTagRow.roleType` 拆被评价/评价（取代 `tagType`） |
| `service/EvalUserTagImportService.java` | 被评价/评价名称都从同一**扁平** name→id（`selectAll(null,1)` 后不再按 type 拆 map）；**加局部排斥**校验；错误文案"非被评价人类型/非评价人类型"→"标签不存在" |

### 4.3 规则局部排斥

| 文件 | 改动 |
|---|---|
| `service/EvalRuleService.java` | `create`/`update`：校验任一 `groupType==1` 的 `group.evalTagId` 不得等于 `beEvalTagId`（`update` 时 beEvalTagId 取库中现值），冲突抛 `EVAL_ROLE_CONFLICT` |

### 4.4 引擎（无逻辑改动）

- `EvalTaskService`：仍调 `selectTagIdsByUserIdAndType(userId, 1)`，语义随 mapper 切到 `role_type=1`
- `EvalScoreService`：仍调 `selectTagIdsByUserIdAndType(evalUserId, 2)`，切到 `role_type=2`

### 4.5 错误码

- 新增 `EVAL_ROLE_CONFLICT("PERF-40063", "评价角色不能与被评价角色相同")`
- 移除 `EVAL_TAG_TYPE_MISMATCH("PERF-40059")`（标签拍平后无类型，不再抛出；删除枚举并清理引用）

## 5. 前端变更（xanzc_frontend）

| 文件 | 改动 |
|---|---|
| `views/eval/Tags.vue` | 删「标签类型」筛选项、表格列、新建弹窗 radio；`TAG_TYPE_LABEL` 移除；`createTag` 只传 name；页头副标题"被评价人标签 · 评价人标签"→"评价标签" |
| `api/eval.js` | `createTag(tagName)` 去掉 `tagType` 参数与 query |
| `views/eval/UserTags.vue` | 被评价角色 + 评价角色两个选择器都用全量启用标签池（删 `tagsOfType(1/2)` 按类型过滤）；评价角色多选**排除**已选的被评价角色（computed 派生选项）；保存契约不变（仍传 beEvalTagId + evalTagIds + evalEnabled） |
| `views/eval/Rules.vue` | `beEvalTagOptions`/`evalTagOptions` 都改为全量池（删 `t.tagType===1/2`）；评价人组标签下拉**排除**当前 `formData.beEvalTagId`（computed 派生）；`allTags` 注释更新 |

> 前端排斥仅为体验前置；后端 `EVAL_ROLE_CONFLICT` 为权威校验。

## 6. 范围外（不动）

- `是否参与评价`（`EVAL_USER_SETTING` 排除名单）逻辑保持不变
- 被评价角色仍单选、评价角色仍多选（沿用现状）
- 评价任务/打分的加权计算逻辑

## 7. 测试（TDD 红-绿-重构）

**新增/改写单测**：
- `EvalUserTagServiceTest`：saveUserRoles 局部排斥（beEval 在 evalIds 抛 `EVAL_ROLE_CONFLICT`）、按 role_type 写入与装配、null/空清空
- `EvalUserRoleServiceTest`：`assembleRows` 按 roleType 拆分（mapper 返回 `roleType` 而非 tagType）
- `EvalUserTagImportServiceTest`：扁平解析、局部排斥、文案
- `EvalRuleServiceTest`：group.evalTagId == beEvalTagId 抛冲突；正常通过
- `EvalTagServiceTest`：create 按 name 唯一（去 type）

**改写 IT**：
- `EvalUserTagMapper` IT：`role_type` 写入/按角色查询
- `EvalTagMapper` IT：`selectByName` 唯一

**回归**：现有 eval 测试中所有断言 `tagType` / `selectByNameAndType` / `EVAL_TAG_TYPE_MISMATCH` 的用例同步迁移到 `roleType` / `selectByName` / `EVAL_ROLE_CONFLICT`。

**验收**：
- `mvn test -pl performance-engine-center` 全绿（eval 子域）
- 前端 `npm run build` 无 UserTags/Tags/Rules/eval.js 报错
- 标签管理新建标签无类型选项；人员标签两个选择器互斥；规则评价组下拉排除被评价标签

## 8. 风险

- **迁移次序**：必须先回填 `EVAL_USER_TAG.ROLE_TYPE` 再 DROP `EVAL_TAG.TAG_TYPE`，否则丢失角色信息（脚本内固定次序 + 备份）
- **生产 onepl**：部署前同样需跑冲突预检（可能与 yiti 数据不同）
- **EVAL_USER_SETTING 旧语义**：与本特性正交，无交叉影响
