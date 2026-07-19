# CLAUDE.md

后续所有回答全部使用中文，打开和编辑文件时全部使用UTF-8编码

## 项目概述

**Branch Platform (分行业务平台)** - 模块化单体架构的银行分行业务运营系统
**业务目标**: 为银行分行提供客户营销、工作流审批、绩效计算、报表分析的一体化解决方案
**核心价值**: 模块化设计、权限精细化控制、工作流集成、数据强一致性

## 技术栈

- **后端**: Spring Boot 3.2.3 + JDK 17、MyBatis 3.0.3（新增功能一律 MyBatis-Plus，见下文红线）、Flowable 7.0.1（嵌入式）、MySQL 8.0 + Druid、Spring Session JDBC（session 落 MySQL `SPRING_SESSION` 表，**2026-05 已去 Redis**）、Knife4j 4.4.0、**华为云 OBS**（`esdk-obs-java-bundle` 3.24.3，实现类 `ObsStorageClient`；早期 MinIO 已迁移废弃，代码 Javadoc 里残留的 "MinIO" 字样勿信）
- **前端** (`xanzc_frontend/`): Vue 3 + Vite 4 + Element Plus + Pinia
- **构建**: Maven 多模块项目；版本控制 Git

## 开发指令

```bash
mvn clean install -DskipTests        # 安装/刷新全部模块到本地 .m2（跨模块改动后必跑）
cd bootstrap && mvn spring-boot:run  # 启动开发服务器
mvn test                             # 运行单元测试（surefire）
mvn verify                           # 全量测试含 IT（failsafe）
mvn clean package                    # 构建打包
```

## 测试 / IT 执行注意事项

### Stale jar 处理（跨模块改动后必跑）
bootstrap 的 `@SpringBootTest` 依赖其他模块的最新 java 类时，必须先 `mvn clean install -DskipTests` 把上游模块 install 到本地 .m2，再 `mvn test -pl bootstrap`；否则旧 jar 加载旧类会引发 `ConflictingBeanDefinitionException` 等怪错，`mvn clean install` 立即解决。

### Surefire vs Failsafe 分工
- `*Test.java` / `*Tests.java` → surefire（`mvn test` 触发）
- `*IT.java` → failsafe（`mvn verify` 触发，`mvn test` 不跑）；写新集成测试按 `*IT.java` 命名

### UTF-8 编码已全局配置
pom.xml surefire/failsafe 的 argLine 已含 `-Dfile.encoding=UTF-8`，无需在 `@Sql` 注解上加 `@SqlConfig(encoding="UTF-8")`。

### 测试数据库
- 多数模块的真库测试与 bootstrap IT 连 `onepl_test_bootstrap`（**仍在用的测试基础设施，勿当历史遗留清理**）；auth/customer/business 单测用 H2 内存库；`yiti_test` 仅用于演示/验收数据
- `yiti` 是开发主库（生产语义）：任何测试、脚本不得向其写入测试数据

## 模块结构与依赖

全部模块均已交付，无尚未实现模块。各模块细节见对应模块的 CLAUDE.md，此处不重复维护。

| 模块 | 包名 | 说明 |
|------|------|------|
| `common` | com.bank.branch.platform.common.* | 公共基础设施 (web/trace/security/aop/db 5 个子模块) |
| `auth-permission-center` | com.bank.branch.platform.auth | 认证授权中心 (RBAC + 数据范围) |
| `system-governance-center` | com.bank.branch.platform.governance | 系统治理中心 (含 sys_job_conf / Quartz 集群调度、文件存储 OBS) |
| `workflow-center` | com.bank.branch.platform.workflow | 工作流中心 (Flowable 集成) |
| `customer-marketing-center` | com.bank.branch.platform.customer | 客户营销中心 |
| `business-application-center` | com.bank.branch.platform.bizapp | 业务申请中心 |
| `portal-content-center` | com.bank.branch.platform.portal | 门户与内容中心 |
| `performance-engine-center` | com.bank.branch.platform.performance | 绩效计算中心 (含 eval 考核评价/奖励分配) |
| `report-analytics-center` | com.bank.branch.platform.report | 报表分析中心 (只读，含 screen 大屏子域) |
| `red-engine-center` | com.bank.branch.platform.redengine | 红色引擎党建管理 |
| `soap-gateway-center` | com.bank.branch.platform.soap | 外部渠道 SOAP/callpu 网关：随 bootstrap 同 JVM 启动，`SoapNettyServer` 额外监听独立 Netty 端口；另提供 `POST /api/callpu` HTTP 入口 |
| `bootstrap` | com.bank.branch.platform | 唯一的 Spring Boot 启动入口 |

以上均在 Maven 聚合内。此外 `xanzc_frontend/` 为 Vue 3 + Vite 前端（npm 工程，不在 Maven 聚合内，`npm run dev` 启动）。

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
portal-content-center (依赖 auth + governance + workflow，通用域不持有核心域状态)
customer-marketing-center (依赖 auth + governance + workflow)
business-application-center (依赖 auth + governance + workflow + customer-marketing + portal)
performance-engine-center (依赖 auth + governance + workflow + customer-marketing + portal——V1.12+ 经 AddressBookApi 校验员工存在性)

report-analytics-center (只读，依赖 auth/governance/performance/customer 的 *Api，不被业务模块依赖)

red-engine-center (依赖 auth + governance；不依赖 workflow——审核流不接 Flowable，自管两级审核状态机)
soap-gateway-center (依赖 performance-engine 的 PerfApprovalQueryApi，手机端审批网关)

bootstrap (依赖所有业务模块，是唯一的 Spring Boot 启动入口)
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
- **Mapper**: `*Mapper` (接口) + `*Mapper.xml`（仅 BaseMapper 覆盖不到的自定义 SQL 才写 XML）
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
3. **无状态设计**: 所有模块无状态，Session 经 Spring Session JDBC 落 MySQL 共享（已去 Redis，分布式锁用 `LockManager`/`PT_LOCK` 表）
4. **Fail Close**: 权限缓存失效时默认拒绝访问
5. **全链路追踪**: 所有跨模块调用携带 TraceID

## 性能和安全规范

- 所有 API 响应时间 < 500ms (目标)；慢查询 > 5s 必须告警
- 数据库查询优化（索引 + 缓存）；分页懒加载（默认 pageSize=20，最大 100）
- 所有用户输入必须验证和清理；敏感数据加密存储（密码 BCrypt）
- 基于 `PT_RESOURCE` 的 RBAC 权限控制
- 高危操作必须独立 URL、单独授权、单独审计
- 敏感字段日志脱敏（手机号、身份证、账号、金额）

## 环境配置

### 开发环境

- **数据库**: MySQL 8.0 本地实例 (`localhost:3306/yiti 用户:root, 密码 djdev`)
- **对象存储**: 华为云 OBS（配置键 `obs.endPoint` / `obs.bucketName`，见 bootstrap `application.yml`；开发沙箱可能无法解析 OBS 域名，附件失败按 fail-close 处理）
- **日志级别**: DEBUG (com.bank.platform), INFO (root)

### 配置文件

- 配置: `src/main/resources/application.yml`
- Session 超时: 7200 秒 (2 小时)
- Flowable history level: `audit`
- MyBatis mapper 位置: `classpath*:mapper/**/*Mapper.xml`

### 端口与 API 文档

- **仓库基线**: 后端 `server.port` **18080**、SOAP 网关 Netty **30522**（`platform.soap.netty.port`）、前端 dev **8090**（`/api` 代理到 18080）
- **本机开发实况**: 18080/30522 被另一套遗留 java 进程占用（**勿动该进程**），本机通过 `bootstrap/src/main/resources/application.yml` 与 `xanzc_frontend/vite.config.js` 的**有意未提交改动**运行在 **18081 / 30523 / 8091**——这几处本地改动勿提交、勿回退
- Knife4j UI: `http://localhost:18081/doc.html`（按本机实际后端端口）

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
10. ✅ **接口契约同步**:凡改动 `controller/`、`api/` 包(含 DTO 字段、错误码、`@BizAuth`),**同一提交内**更新 `docs/modules/<模块>/03-接口设计*`、`04-对外API契约*`;提交前跑 `scripts/check-contract-docs.sh` 自检(比较契约代码与契约文档的提交时间,STALE 即欠账)

## 重要文件路径

### 项目规划与设计
- **设计文档**: `project_ana_技术方案与架构拆分.md`
- **功能文档**: `project_ana.md`
- **docs 目录**: 各模块详细设计文档 + DDL + 共享开发规范 (见 `docs/CLAUDE.md`)
- **示例代码索引**: [docs/code-examples.md](docs/code-examples.md) — 各类规范实现（文件上传/OBS、MyBatis-Plus、标准 Controller、资源注册 SQL、DATA_SCOPE、Flowable、Quartz、导出、分布式锁、各类测试、前端模式）的现成范例位置，**开发新功能先查此索引照着写**
- **运维 Runbook**: `docs/modules/system-governance-center/09-运维Runbook.md` — sys_job_conf / Quartz 集群调度运维权威指南

### 模块级 CLAUDE.md (开发时必须参考)
- **公共基础设施**: [common/CLAUDE.md](common/CLAUDE.md)
- **认证授权**: [auth-permission-center/CLAUDE.md](auth-permission-center/CLAUDE.md)
- **系统治理**: [system-governance-center/CLAUDE.md](system-governance-center/CLAUDE.md)
- **工作流**: [workflow-center/CLAUDE.md](workflow-center/CLAUDE.md)
- **客户营销**: [customer-marketing-center/CLAUDE.md](customer-marketing-center/CLAUDE.md)
- **业务申请**: [business-application-center/CLAUDE.md](business-application-center/CLAUDE.md)
- **门户与内容**: [portal-content-center/CLAUDE.md](portal-content-center/CLAUDE.md)
- **绩效计算**: [performance-engine-center/CLAUDE.md](performance-engine-center/CLAUDE.md)
- **报表分析**: [report-analytics-center/CLAUDE.md](report-analytics-center/CLAUDE.md)
- **红色引擎（党建管理）**: [red-engine-center/CLAUDE.md](red-engine-center/CLAUDE.md) — 不接 Flowable，自管两级审核状态机
- **外部渠道网关（SOAP/callpu）**: [soap-gateway-center/CLAUDE.md](soap-gateway-center/CLAUDE.md) — 含 SYS_415 / urlencoded 兼容过滤器
- **启动入口**: [bootstrap/CLAUDE.md](bootstrap/CLAUDE.md)
- **前端**: [xanzc_frontend/CLAUDE.md](xanzc_frontend/CLAUDE.md)

### 共享开发规范
- **[docs/common-dev-guide.md](docs/common-dev-guide.md)** — 统一响应模型、错误码规范、分页标准、鉴权链路、数据范围 SQL 模板、审计规范、事件发布、数据传输、日志规范 (所有模块必须遵守)

### 文档维护约定（2026-07-19 起，防止再次漂移）
- **每个目录的 CLAUDE.md 是该目录开发指导的唯一权威**；凡与 CLAUDE.md 同目录并存的 AGENTS.md 一律只写指向 CLAUDE.md 的指针，禁止承载实体内容（历史上两份并行维护已造成双向漂移）
- **CLAUDE.md 不维护数量与清单**：Controller/端点/类/错误码的个数与逐条列表一律不写入（历史证明必然过期，最严重处漏记 56%）。清单以源码目录为准；端点契约细节见 `docs/modules/<模块名>/03-接口设计.md`、`04-对外API契约.md`，每次接口变更同步更新这两份文档而非 CLAUDE.md（开发 Checklist 第 10 条硬约束，`scripts/check-contract-docs.sh` 可检出欠账）
- **版本历史/变更叙事不进 CLAUDE.md**：一律靠 `git log`；CLAUDE.md 只保留仍影响当下决策的"为什么"结论与踩坑
- **示例代码位置统一登记在 `docs/code-examples.md`**：新增/替换某类规范实现时同步更新该索引，CLAUDE.md 只引用不复制

### TDD (测试驱动开发) 绝对红线
- **红-绿-重构 (Red-Green-Refactor) 闭环**：一切特性的开发或者 Bug 修复，必须先写测试（让他失败，Red），再写最简代码让他通过（Green），最后重构优化（Refactor）。
- **禁止事后狂补测试**：严禁无视 TDD，先凭直觉写完一大堆业务逻辑再去凑测试的行为。

### Flyway 禁令（绝对红线）
- **本项目已彻底废弃 Flyway**：禁止引入 `flyway-core` / `flyway-mysql` 任何版本依赖；禁止在 `application*.yml` 出现 `spring.flyway.*` 配置；禁止新增 `V*__*.sql` / `U*__*.sql` 命名风格的迁移脚本；禁止编写 `*FlywayIT` / `*FlywayTestBase` 类。
- **schema 变更走 SQL 直接执行**：所有 DDL/DML 由开发或 DBA 直接在目标库执行（手工或 CI 脚本），不再依赖任何"按版本号自动 migrate"框架。
- **历史迁移脚本已全部删除**：当前 schema 即为唯一真相，未来如需 fresh deploy 请用 `mysqldump` 从生产库导出 baseline。

### MyBatis-Plus 规范（新增功能绝对红线，2026-06-10 起）
- **所有新增功能涉及的数据库访问统一使用 MyBatis-Plus**：Mapper 接口必须 `extends BaseMapper<T>`；单条 CRUD（`insert`/`selectById`/`updateById`/`deleteById` 等）直接用 BaseMapper 内置方法，**禁止**为这些方法重复写 XML；动态/简单条件查询优先 `LambdaQueryWrapper`/`LambdaUpdateWrapper`。
- **XML 只写 BaseMapper 覆盖不到的自定义 SQL**：批量插入、跨表 JOIN、GROUP BY 聚合、带乐观条件的批更新等才落 `*Mapper.xml`（命名空间指向该 BaseMapper 接口）。
- **实体**用 MyBatis-Plus 注解 `@TableName` / `@TableId(type = IdType.AUTO)`；**配置**已就绪（root 依赖 `mybatis-plus-spring-boot3-starter` 3.5.7、`map-underscore-to-camel-case: true`、各模块 `@MapperScan` + `MybatisPlusConfig`），新 Mapper 开箱即用，无需额外配置。
- 既有遗留的纯 MyBatis 写法不强制回改，但**新增**一律按本规范。

### 子代理派遣规范（绝对红线）
- **派遣任何 subagent（Agent 工具）时，model 参数必须 ≥ sonnet（即只能是 `sonnet` 或 `opus`），禁止使用 `haiku`**。
- 即使 plan 文档建议"机械任务用 cheap model"，也要降级到 sonnet 而非 haiku。
- 此规则适用于全部 subagent 类型（executor、explore、code-reviewer、debugger 等），无例外。
