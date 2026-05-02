# CLAUDE.md

本文件为 Claude Code 提供项目上下文和开发指导。后续所有回答全部使用中文，打开和编辑文件时全部使用UTF-8编码，当前开发环境为windows环境

## 项目概述

**Branch Platform (分行业务平台)** - 模块化单体架构的银行分行业务运营系统
**业务目标**: 为银行分行提供客户营销、工作流审批、绩效计算、报表分析的一体化解决方案
**核心价值**: 模块化设计、权限精细化控制、工作流集成、数据强一致性

## 技术栈

**后端** (Spring Boot 3.2.3 + JDK 17):

- ORM框架: MyBatis 3.0.3
- 工作流引擎: Flowable 7.0.1 (嵌入式)
- 数据库: MySQL 8.0 + Druid 连接池
- 缓存/Session: Redis 6.X (Spring Session)
- API文档: Knife4j 4.4.0
- 对象存储: MinIO 8.5.7

**工具链**:

- 构建工具: Maven
- 包管理: Maven 多模块项目
- 版本控制: Git

## 开发指令

```bash
# 安装依赖
mvn clean install

# 启动开发服务器
cd bootstrap
mvn spring-boot:run

# 运行测试
mvn test

# 运行特定模块测试
cd <module-name>
mvn test

# 构建打包
mvn clean package
```

## 测试 / IT 执行注意事项

### Stale jar 处理（跨模块改动后必跑）
当 IT（特别是 bootstrap 模块的 `@SpringBootTest`）依赖另一个模块的最新 java 类时，必须**先把上游模块 install 到本地 .m2 仓库**，否则 bootstrap test 会用旧 jar 加载到旧类，引发奇怪的 `ConflictingBeanDefinitionException` 等错误。

```bash
# 安全做法：清缓存 + 重新 install 全部模块
mvn clean install -DskipTests

# 然后跑 IT
mvn test -pl bootstrap

# 或者跑全量含 IT
mvn verify
```

**典型症状**：bootstrap test 启动时报 "ConflictingBeanDefinitionException ... bean class [com.bank.branch.platform.report.controller.SqlProbeController] conflicts with existing"。这是 stale jar 残留旧类（已重命名为 RptSqlProbeController）+ 新源码冲突。`mvn clean install` 立即解决。

### Surefire vs Failsafe 分工
- `*Test.java` / `*Tests.java` → surefire（`mvn test` 触发）
- `*IT.java` → failsafe（`mvn verify` 触发，`mvn test` 不跑）
- 写新集成测试时按 `*IT.java` 命名

### UTF-8 编码已全局配置
pom.xml `surefire/failsafe` 的 argLine 已含 `-Dfile.encoding=UTF-8`，无需在每个 `@Sql` 注解上加 `@SqlConfig(encoding="UTF-8")`。Windows JVM 默认 file.encoding=GBK 不再影响 H2 中文 fake data 加载。

## 当前已实现的模块

| 模块 | 包名 | 状态 | 说明 |
|------|------|------|------|
| `common` | com.bank.branch.platform.common.* | 已完成 | 公共基础设施层 (5 个子模块) |
| `auth-permission-center` | com.bank.branch.platform.auth | 已完成 | 认证授权中心 (RBAC + 数据范围) |
| `system-governance-center` | com.bank.branch.platform.governance | 已完成 | 系统治理中心 (7 大治理域) |
| `workflow-center` | com.bank.branch.platform.workflow | 已完成 | 工作流中心 (Flowable 7.0.1 集成) |
| `customer-marketing-center` | com.bank.branch.platform.customer | V1.8 已交付（2026-05-01）| 客户营销中心 (114 Java + 49 测试，0 UOE)；V1.8 LeadCallbackCompensation @Scheduled→Quartz 迁移 |
| `business-application-center` | com.bank.branch.platform.bizapp | 已完成 | 业务申请中心 (60 Java + 26 测试，0 UOE) |
| `portal-content-center` | com.bank.branch.platform.portal | 已完成（V1.13 # 1 解绑 yiti） | 门户与内容中心 (108 Java + 38 测试，0 UOE)；V1.13 # 1 application-test.yml 解绑 yiti → onepl_test_bootstrap |
| `performance-engine-center` | com.bank.branch.platform.performance | V1.7 + V1.13 # 1 测试库一统 | 绩效计算中心 (V1.0-V1.5 累积 + V1.6 Quartz 整合 + V1.7 指标级调度)；V1.13 # 1 application-test.yml 解绑 yiti + 启用 Quartz JDBC + 38 测试 fixture 大小写治理 |
| `report-analytics-center` | com.bank.branch.platform.report | V1.0 已交付（2026-04-25） | 报表分析中心 (25 REST + 4 表 + 跨模块只读 + 4 ExportStrategy 异步 + SQL 探查) |
| `bootstrap` | com.bank.branch.platform | 已完成 | Spring Boot 启动入口 |

**全部 9 个业务模块已交付**，无尚未实现模块。

### 当前模块依赖图

```
common (common-web → common-trace → common-security → common-aop → common-db)
  ↑
auth-permission-center (无其他业务模块依赖)
  ↑
system-governance-center (依赖 auth)  ← 被 workflow 依赖
  ↑
workflow-center (依赖 auth + governance)
  ↑
portal-content-center (依赖 auth + governance + workflow，通用域不持有核心域状态) ← 已交付
customer-marketing-center (依赖 auth + governance + workflow) ← V1.8 已交付（2026-05-01）；LeadCallbackCompensation @Scheduled → Quartz 集群调度（job_key=LEAD_CALLBACK_COMPENSATE）；CustomerSchedulingConfig 删除，@EnableScheduling 归属 PerformanceSchedulingConfig
business-application-center (依赖 auth + governance + workflow + customer-marketing + portal) ← 已交付
performance-engine-center (依赖 auth + governance + workflow + customer-marketing) ← V1.6 已交付（Quartz 整合）

report-analytics-center (只读，依赖 auth/governance/performance/customer 的 *Api，不被业务模块依赖) ← V1.0 已交付（2026-04-25）

bootstrap (依赖所有已实现模块, 是唯一的 Spring Boot 启动入口)
```

### 模块间依赖规则

**严格遵守以下规则**:

1. 模块间只通过 `*Api` 接口交互，**禁止**直接依赖 `mapper`/`entity`/`serviceImpl`
2. 所有接口必须注册到 `PT_RESOURCE` 表并使用 `@BizAuth` 注解
3. `workflow-center` 是**唯一**直接调用 Flowable API 的模块
4. 跨模块查询使用 `*QueryApi`，避免直接 join 其他模块的表
5. `auth-permission-center` 可被所有模块依赖，但不依赖任何业务模块
6. `report-analytics-center` 只读，**不允许**被业务模块依赖

### 包结构规范

强制：使用多module进行开发结构如下
```text
com.bank.branch.platform
├─ common                        存放公用组件 ✅ 已完成 (5 个子模块)
├─ auth-permission-center        认证授权中心 ✅ 已完成
├─ system-governance-center      系统治理中心 ✅ 已完成
├─ workflow-center               工作流中心 ✅ 已完成
├─ bootstrap                     启动入口 ✅ 已完成
├─ portal-content-center         门户与内容中心 ✅ 已完成（108 Java + 38 测试）
├─ customer-marketing-center     客户营销中心 ✅ V1.8 已交付（2026-05-01）（114 Java + 49 测试）；V1.8 LeadCallbackCompensation @Scheduled→Quartz 迁移
├─ business-application-center   业务申请中心 ✅ 已完成（60 Java + 26 测试）
├─ performance-engine-center     绩效计算中心 ✅ V1.7 + V1.13 # 1（V1.6 Quartz 整合：Spring `@Scheduled`/ShedLock 全部迁移到 Quartz 集群调度，QRTZ_LOCKS 行锁接管防重；JobApi 精简到 1 方法 getJobConf；JobExecutionLogger 全局 Quartz JobListener 统一写日志；V1.13 # 1 application-test.yml 解绑 yiti 开发库迁到 onepl_test_bootstrap + 显式启用 Quartz JDBC clustered）
└─ report-analytics-center       报表分析中心 ✅ V1.0 已交付（25 REST + 4 表 + 跨模块只读 + 4 ExportStrategy 异步导出 + SQL 探查 / surefire 103 + failsafe 70 = 173 全绿）
```

```
com.bank.branch.platform.<module>/
├── api/              # 对外接口 (唯一可跨模块依赖)
│   ├── *Api.java
│   ├── *QueryApi.java
│   └── dto/
├── controller/       # REST 控制器
├── facade/           # 对外编排实现
├── service/          # 业务逻辑
├── mapper/           # MyBatis Mapper (模块私有)
├── entity/           # 数据库实体 (模块私有)
├── config/           # 模块配置
└── ...
```

### 命名约定

- **接口**: `*Api`, `*QueryApi`
- **实现类**: `*Facade` (对外), `*ServiceImpl` (内部)
- **实体**: 驼峰命名，对应表名
- **Mapper**: `*Mapper` (接口) + `*Mapper.xml`
- **控制器**: `*Controller`
- **常量**: `SCREAMING_SNAKE_CASE`

### 代码风格

- 所有 Service 类和 public 方法必须有注释
- 复杂业务逻辑必须有行注释（说明"为什么"而非"做什么"）
- 禁止使用 `any` 类型的等价物（如 Object 作为通用参数）
- 所有接口必须记录入参/出参、traceId 和耗时

### 核心设计原则

1. **显式优于隐式**: 不使用魔法约定，所有配置显式声明
2. **简单优于复杂**: 使用贫血模型 (Service + DAO + Entity)，避免过度设计
3. **无状态设计**: 所有模块无状态，Session/缓存通过 Redis 实现
4. **Fail Close**: 权限缓存失效时默认拒绝访问
5. **全链路追踪**: 所有跨模块调用携带 TraceID


## 性能和安全规范

### API 性能要求

- 所有 API 响应时间 < 500ms (目标)
- 慢查询 > 5s 必须告警
- 数据库查询优化（使用索引和缓存）
- 实现分页和懒加载（默认 pageSize=20，最大 100）

### 安全规范

- 所有用户输入必须验证和清理
- 敏感数据加密存储（密码使用 BCrypt）
- 实现基于 `PT_RESOURCE` 的 RBAC 权限控制
- 高危操作必须独立 URL、单独授权、单独审计
- 敏感字段日志输出必须脱敏（手机号、身份证、账号、金额）

## 环境配置

### 开发环境

- **数据库**: MySQL 8.0 本地实例 (`localhost:3306/onepl 用户:root, 密码 123456`)
- **缓存**: Redis 6.X 本地实例 (`localhost:6379`)
- **对象存储**: MinIO 本地服务
- **日志级别**: DEBUG (com.bank.platform), INFO (root)

### 配置文件

- 配置: `src/main/resources/application.yml`
- Session 超时: 7200 秒 (2 小时)
- Flowable history level: `audit`
- MyBatis mapper 位置: `classpath*:mapper/**/*Mapper.xml`

### API 文档

- Knife4j UI: `http://localhost:8080/doc.html` (启动后访问)

## 开发 Checklist

开发新功能时必须遵守以下规则:

1. ✅ 新增接口前确定归属模块，禁止"顺手写到别的模块"
2. ✅ 每个新接口必须登记到 `PT_RESOURCE` 并声明 `@BizAuth`
3. ✅ 跨模块调用必须走 `*Api`/`*QueryApi`，禁止直连 `mapper`/`entity`
4. ✅ 所有写操作必须在 Service 层基于实体做二次权限校验
5. ✅ 所有读接口、导出接口必须应用统一 `DATA_SCOPE`
6. ✅ 高危操作必须独立 URL、单独授权、单独审计
7. ✅ 所有流程类业务必须维护 `business_key` 和 `biz_process_map`
8. ✅ 所有 Service 类与 public 方法必须补齐注释
9. ✅ 所有接口记录入参/出参、traceId 和耗时

## 重要文件路径

### 项目规划与设计
- **设计文档**: `project_ana_技术方案与架构拆分.md`
- **功能文档**: `project_ana.md`
- **docs 目录**: 各模块详细设计文档 + DDL + 共享开发规范 (见 `docs/CLAUDE.md`)
- **运维 Runbook**: `docs/modules/system-governance-center/09-运维Runbook.md` — sys_job_conf / Quartz 集群调度运维权威指南（V1.9 整合）
- **V1.10 测试库合一 spec**: `docs/superpowers/specs/2026-05-01-v1.10-test-db-unification-design.md` — 唯一 `onepl_test_bootstrap`，FlywayTestBase 真接管 perf/rpt schema
- **V1.11 # 1 spec**: `docs/superpowers/specs/2026-05-01-v1.11-5it-diagnosis-design.md` — listener 嵌套 AFTER_COMMIT 链改同步调用（方向 C），5 IT 诊断 + 修复
- **V1.11 # 1 plan**: `docs/superpowers/plans/2026-05-01-v1.11-1-5it-diagnosis-impl.md`
- **V1.12 schema 治理 spec**: `docs/superpowers/specs/2026-05-01-v1.12-schema-and-data-cleanup-design.md` — onepl_test_bootstrap schema column drift 修复 + 8 customer 小写历史表 DROP + bizapp/MetricScheduledE2EIT 数据 cleanup
- **V1.13 # 1 测试库一统 spec**: `docs/superpowers/specs/2026-05-02-v1.13-test-db-unification-design.md` — onepl_test_bootstrap 23 张其他模块小写双胞胎 DROP（V1.13 自己脚本 `docs/superpowers/sql/2026-05-02-v1.13-drop-other-lowercase-tables.sql`）+ perf/portal application-test.yml 解绑 yiti 改 onepl_test_bootstrap + perf 启用 Quartz JDBC + 38 测试文件 80+ 处 SQL 上下文 lowercase→uppercase 一致性治理（perl word-boundary sed）+ Flyway history 17 V_*.sql mark success=1 绕开生产 V_*.sql 大小写不一致 + 幂等 init v2 脚本 `docs/superpowers/sql/2026-05-02-v1.13-onepl-test-bootstrap-init-v2.sql`；customer/bizapp/portal/bootstrap surefire 全绿 + report surefire 全绿（fix SqlSafeValidatorTest 期望大写后），perf failsafe errors 27→2、残 6 fail 全部归因（V1.13 # 1b spike 2 + V1.13 # 1d 副作用 4 + V1.7 已知 baseline 3）。
- **V1.13 # 1d 生产 V_*.sql 大写化 + yiti 库清理**: 2026-05-02 增量交付；perf V_*.sql 9 文件（V1_0_1/V1_0_3/V1_2_3/V1_2_5/V1_3_0/V1_4_0 + undo U1_0_3/U1_0_4/U1_4_0）37 处 SQL 上下文 lowercase→uppercase（perl word-boundary sed），生产 fresh deploy 修复（onepl 库 lower_case_table_names=0 大小写一致）；yiti 开发库 32 张小写双胞胎 DROP（mysqldump 备份 80KB 归档；脚本 `docs/superpowers/sql/2026-05-02-v1.13-drop-yiti-lowercase-tables.sql`）；onepl_test_bootstrap V_*.sql 真跑 V1.0.0~V1.2.5 全 success（V1.3.0+ 因不幂等保留 mark skip → V1.13 # 1e）；perf failsafe errors 27→0、failures 6 全归因
- **V1.13 候选清单**：# 1 + # 1d 已交付；# 1b MetricScheduledE2EIT 业务层 Quartz JobKey 注入失败 spike（2 fail）；~~# 1e V_*.sql 幂等化~~（**已作废**：项目废弃 Flyway 后整个 V_*FlywayIT 体系已移除）；# 2 V1.8 P6 业务/数据状态；# 3 SummaryControllerIT 500 (V1.12 # 4 老登记)；# 5 WorkflowCallbackListener REQUIRES_NEW 嵌套简化；# 6 LeadRejectedEvent dead code 定调

### 模块级 CLAUDE.md (开发时必须参考)
- **公共基础设施**: [common/CLAUDE.md](common/CLAUDE.md)
- **认证授权**: [auth-permission-center/CLAUDE.md](auth-permission-center/CLAUDE.md)
- **系统治理**: [system-governance-center/CLAUDE.md](system-governance-center/CLAUDE.md)
- **工作流**: [workflow-center/CLAUDE.md](workflow-center/CLAUDE.md)
- **客户营销**: [customer-marketing-center/CLAUDE.md](customer-marketing-center/CLAUDE.md)
- **业务申请**: [business-application-center/CLAUDE.md](business-application-center/CLAUDE.md)
- **门户与内容**: [portal-content-center/CLAUDE.md](portal-content-center/CLAUDE.md)
- **绩效计算**: [performance-engine-center/CLAUDE.md](performance-engine-center/CLAUDE.md)

### 共享开发规范
- **[docs/common-dev-guide.md](docs/common-dev-guide.md)** — 统一响应模型、错误码规范、分页标准、鉴权链路、数据范围 SQL 模板、审计规范、事件发布、数据传输、日志规范 (所有模块必须遵守)

### TDD (测试驱动开发) 绝对红线
- **红-绿-重构 (Red-Green-Refactor) 闭环**：一切特性的开发或者 Bug 修复，必须先写测试（让他失败，Red），再写最简代码让他通过（Green），最后重构优化（Refactor）。
- **禁止事后狂补测试**：严禁无视 TDD，先凭直觉写完一大堆业务逻辑再去凑测试的行为。

### Flyway 禁令（绝对红线）
- **本项目已彻底废弃 Flyway**：禁止引入 `flyway-core` / `flyway-mysql` 任何版本依赖；禁止在 `application*.yml` 出现 `spring.flyway.*` 配置；禁止新增 `V*__*.sql` / `U*__*.sql` 命名风格的迁移脚本；禁止编写 `*FlywayIT` / `*FlywayTestBase` 类。
- **schema 变更走 SQL 直接执行**：所有 DDL/DML 由开发或 DBA 直接在目标库执行（手工或 CI 脚本），不再依赖任何"按版本号自动 migrate"框架。
- **历史 V1.0~V1.8 迁移脚本已全部删除**：`onepl` 与 `yiti` 当前 schema 即为唯一真相，未来如需 fresh deploy 请用 `mysqldump` 从生产库导出 baseline。

### 子代理派遣规范（绝对红线）
- **派遣任何 subagent（Agent 工具）时，model 参数必须 ≥ sonnet（即只能是 `sonnet` 或 `opus`），禁止使用 `haiku`**。
- 即使 plan 文档建议"机械任务用 cheap model"，也要降级到 sonnet 而非 haiku。
- 此规则适用于全部 subagent 类型（executor、explore、code-reviewer、debugger 等），无例外。