# 红色引擎并入平台——会话交接文档（2026-07-18）

> 新会话续接入口。读完本文档 + 台账后，从「六、下一步行动」的第 1 步开始执行。

## 一、项目概况

- **目标**：将 `redengine/`（独立党建系统，Java8/Boot2.7 + Vue3，只读参考、已 gitignore）升级移植为平台第 10 个业务模块 `red-engine-center`，前端并入 `xanzc_frontend` 的 `/redengine/**` 路由区（保留红色引擎登录页外观与风格），权限/系统设置改用平台 PT_* RBAC。
- **分支**：`feature/redengine-merge`（全部改动在此，不动 master）。
- **三份权威文档**（均已提交）：
  - 规格：`docs/superpowers/specs/2026-07-18-redengine-merge-design.md`
  - 实施计划（19 任务 Task0-18，任务全文/代码/命令都在这里）：`docs/superpowers/plans/2026-07-18-redengine-merge-impl.md`
  - 进度台账：`.superpowers/sdd/progress.md`（git-ignored scratch，找「2026-07-18 redengine-merge-impl」段）
- **执行方法**：superpowers:subagent-driven-development——每任务派 general-purpose 实施子代理 → 控制器证据验收 → review-package → 派审查子代理 → 台账记账。**用户已选定此方式，不要改为本会话直接实施。**

## 二、当前进度（Task 0-4 完成，Task 4 审查被中断未做）

| 任务 | 提交 | 状态 |
|---|---|---|
| Task 0 库安全网 | `27178e80` | ✅ 完成+审查 Approved |
| Task 1 BizType.RED_ENGINE | `30876c99` | ✅ 完成+审查 Approved |
| Task 2 模块骨架 | `d6940885`+`233a6720` | ✅ 完成+审查 Approved |
| Task 3 RE_* 8 表 DDL | `acc10c37` | ✅ 完成+审查 Approved |
| Task 4 权限/字典种子 | `37025bf0`+修复`48d0ae2a` | ✅ 实施+修复+控制器核验完成；**审查员未派（用户中断），续接第一步补做** |
| Task 5-18 | — | 未开始 |

## 三、关键决策与纠偏（后续任务必须遵守）

1. **管理员角色码是 `SYS_ADMIN` 不是 `R_ADMIN`**（PT_ROLE.ROLE_CODE；yiti_test 的 ROLE_ID='1'，onepl 的 ROLE_ID 恰为字符串'R_ADMIN' 但 ROLE_CODE 同为 SYS_ADMIN）。种子/后续脚本一律 `WHERE ROLE_CODE='SYS_ADMIN'` 定位。
2. **字典拍平**：governance `DictApi` 只读 `SYS_DICT`（实体 @TableName("SYS_DICT")），`SYS_DICT_ITEM` 全平台无代码读取。红色引擎字典已拍平落 SYS_DICT，dict_type 带 RE_ 前缀：`RE_ORG_TYPE`(3)/`RE_DIMENSION`(4)/`RE_SUBMIT_STATUS`(3)/`RE_ITEM_CODE`(8) 共 18 行。前端读字典走 `GET /api/sys/dicts/{dictType}/items`。
3. **.gitignore 已锚定 `/redengine/`**（提交 d6940885）：未锚定会把新模块包路径 `.../platform/redengine/` 整个静默忽略。若发现新建 Java 文件 git add 被拒，先查这条。
4. **用户主键是工号字符串**：RE_ 表的 secretary_id/submitter_id/reviewer_id/user_id 均 VARCHAR(50)（对齐 PT_USER.USER_ID），实体用 String，非 Long。
5. **审核流不接 Flowable**（用户确认）：RE_SUBMIT.status 0草稿/1已提交/2通过/3驳回 四态机；创建即 status=1（草稿态废弃，YAGNI）。
6. **业务规则以 Java 源码为准**（非源系统文档口径）：红黄牌阈值 <60 红 / 60≤x<80 黄（代码 80，文档 75 弃用）；逾期=submitDate+7 天未结、人工执行扣分默认 5 分（文档"-1/天、≥3 天清零"弃用）。计划 Task 18 要求把这些口径差异记入勘误。
7. **BizType 单档**：全部 Controller 用 `@BizAuth(bizType = BizType.RED_ENGINE, action = ...)`，细粒度靠 PT_RESOURCE `P_RE_*`（17 条，URL 契约见计划 Task 4 对照表——**后续 Controller 的 @RequestMapping 必须与之逐字一致，否则 403 AUTH-40302**）。
8. **附件走平台**：governance `FileApi`（华为云 OBS，非 MinIO）；前端上传复用既有 `POST /api/files/upload`；`RE_SUBMIT_FILE.file_object_id` 关联。

## 四、数据库状态快照（交接时点）

- **yiti（生产）零变更**：无 RE_ 表、无 P_RE_ 资源、无 RE_ 字典（每个 DB 任务后必须跑守护 SELECT 确认，红线）。
- **备份**：`backup/yiti_full_20260718.sql`（78M，gitignored）。
- **yiti_test**（=yiti 全量副本 169 表 + 本项目落库）：RE_* 8 表、P_RE_* 资源 17、党建角色 RE_ROLE_1..4、PT_ROLE_RESOURCE 46（ORGREV10/BRREV7/SECR7/REPORT5/SYS_ADMIN17）、PT_ROLE_BIZ_SCOPE RED_ENGINE 5 行(ALL)、SYS_DICT RE_ 前缀 18 行、RE_PARTY_ORG 10 行。
- **onepl_test_bootstrap**（IT 库）：与 yiti_test 同步落了全部上述内容。
- 三份 SQL 脚本（均幂等可重跑）：`docs/superpowers/sql/2026-07-18-redengine-tables.sql`、`2026-07-18-redengine-seed.sql`。
- MySQL 账号 root/djdev。`bootstrap` 开发/验收启动用 `-Dspring-boot.run.profiles=remerge`（datasource→yiti_test）。

## 五、本环境执行坑（不遵守会白跑）

1. **子代理最终回复常被吞**（含同步派遣）：实施/审查子代理的派遣词必须要求**把完整报告 Write 到指定文件**（`.superpowers/sdd/task-N-report.md` / `task-N-review.md`），最终回复只写状态行。验收一律读文件+git+库实测，不信自述。
2. **Explore 类子代理没有 Write 工具**，报告类任务用 general-purpose。
3. **子代理 model 必须 ≥ sonnet**（项目红线，禁 haiku）；实施/审查目前都用 sonnet，终审全分支审查用 opus。
4. **工作区有用户未提交文件**：`AGENTS.md`、`bootstrap/src/main/resources/application.yml`、`xanzc_frontend/vite.config.js`——绝不许改/暂存/提交。所有派遣词必须带"显式路径 git add + `git commit -- <pathspec>`，禁 -A/./裸 commit"红线（本会话曾两次被裸 commit 卷入，已修复）。
5. mvn 输出别管道 tail（吞退出码）；跨模块改动后先 `mvn clean install -DskipTests` 再跑 bootstrap 测试（stale jar）。
6. 机器上有个无关遗留 java 进程（`~/lijh/yiti`，端口 18080/30522）——别误杀、别误认为是本项目的。
7. 技能脚本路径：`/home/djdev/.claude/plugins/cache/superpowers-marketplace/superpowers/6.1.1/skills/subagent-driven-development/scripts/task-brief <计划文件> <N>` 生成任务简报；`.../scripts/review-package <BASE> <HEAD>` 生成审查包。BASE=派实施者前记录的 HEAD（多提交任务勿用 HEAD~1）。
8. `.superpowers/sdd/` 下的 task-N-brief/report 文件名会与旧项目残留同名——直接覆盖即可（旧项目成果已固化在台账+git）。

## 六、下一步行动（按序执行）

1. **补 Task 4 审查**（中断点恢复）：审查包已生成好 `.superpowers/sdd/review-acc10c37..48d0ae2a.diff`（BASE acc10c37, HEAD 48d0ae2a, 2 commits）。派 general-purpose(sonnet) 审查员：简报 `.superpowers/sdd/task-4-brief.md`、报告 `task-4-report.md`（含尾部修复追加段）、审查主体=17 条 PT_RESOURCE 与计划对照表逐行核对+角色绑定分组核算(46=10+7+7+5+17)+幂等清理段次序；授权偏离=SYS_ADMIN 纠偏与字典拍平修复（控制器裁决，见本文档三.1/三.2）；报告 Write 到 `task-4-review.md`。控制器已亲验落库计数（17/4/5/46/10/18/ITEM=0/守护0），审查员无需连库。
2. Approved 后台账记 `Task 4: complete (commits 37025bf0+48d0ae2a, review Approved)`，然后按计划继续 **Task 5（实体+Mapper IT）**：task-brief 5 → 记录 BASE → 派实施者（红线段照抄前几个任务的派遣词——报告文件、显式路径提交、库红线、Co-Authored-By）→ 证据验收（报告+git show --stat+`mvn verify -pl red-engine-center -Dit.test=RePartyOrgMapperIT` 结果在报告里）→ review-package → 审查员 → 台账。Task 5 简报里注明：IT 基座照抄 performance-engine-center 的 PerformanceMapperTestBase 模式，test application-test.yml 指 onepl_test_bootstrap。
3. Task 6-11 逐个同流程（计划里每任务有 Interfaces/变换规则 T1-T7/测试用例要求）；Task 12 集成 IT；Task 13-15 前端（变换规则 F1-F6）；Task 16 演示数据；Task 17 联调回归（Playwright 走 `login?normal` admin/123456、进程级 env -u 绕代理）；Task 18 文档。
4. 全部任务后：**终审全分支审查**（opus，review-package `03cb9036..HEAD`，即 merge-base 起）→ 修复终审 findings（一个 fixer 带全清单）→ superpowers:finishing-a-development-branch。
5. 验收 DoD 见计划末尾清单（yiti 零变更/备份在/全量测试绿/Playwright 主链路/权限矩阵抽查/master 干净）。

## 七、快速自检命令（新会话开场跑一遍）

```bash
cd /home/djdev/leid/yiti && git branch --show-current   # feature/redengine-merge
git log --oneline -8                                     # 应见 48d0ae2a..27178e80 六个本项目提交
git status --short                                       # 仅 3 个用户未提交文件 M（AGENTS.md/application.yml/vite.config.js）
cat .superpowers/sdd/progress.md | tail -20              # 台账末段
mysql -uroot -pdjdev -N -e "SELECT COUNT(*) FROM yiti.PT_RESOURCE WHERE RESOURCE_ID LIKE 'P\\_RE\\_%'"  # 守护=0
```
