<!-- Generated: 2026-04-26 | Updated: 2026-08-14 -->

# AGENTS.md

本文件为 AI 编码代理提供项目上下文和开发指导。后续所有回答全部使用中文，打开和编辑文件时全部使用UTF-8编码。

## 项目概述

**Branch Platform (分行业务平台)** - 模块化单体架构的银行分行业务运营系统
**业务目标**: 为银行分行提供客户营销、工作流审批、绩效计算、报表分析的一体化解决方案
**核心价值**: 模块化设计、权限精细化控制、工作流集成、数据强一致性

## 技术栈

- **后端**: Spring Boot 3.2.3 + JDK 17、MyBatis 3.0.3（新增功能一律 MyBatis-Plus，见下文红线）、Flowable 7.0.1（嵌入式）、MySQL 8.0 + Druid、Spring Session JDBC（session 落 MySQL `SPRING_SESSION` 表，**2026-05 已去 Redis**）、Knife4j 4.4.0、MinIO 8.5.7
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

- **Stale jar**：bootstrap 的 `@SpringBootTest` 依赖其他模块最新类时，必须先 `mvn clean install -DskipTests` 再 `mvn test -pl bootstrap`，否则旧 jar 会引发 `ConflictingBeanDefinitionException` 等怪错。
- **Surefire vs Failsafe**：`*Test.java`/`*Tests.java` → surefire（`mvn test`）；`*IT.java` → failsafe（`mvn verify`）；新集成测试按 `*IT.java` 命名。
- **UTF-8 已全局配置**：surefire/failsafe argLine 已含 `-Dfile.encoding=UTF-8`，无需在 `@Sql` 上加 `@SqlConfig(encoding="UTF-8")`。

## 模块结构与依赖

全部 9 个业务模块 + bootstrap 均已交付，无尚未实现模块。各模块细节见对应模块的 AGENTS.md / CLAUDE.md，此处不重复维护。

| 模块 | 包名 | 说明 |
|------|------|------|
| `common` | com.bank.branch.platform.common.* | 公共基础设施 (web/trace/security/aop/db 5 个子模块) |
| `auth-permission-center` | com.bank.branch.platform.auth | 认证授权中心 (RBAC + 数据范围) |
| `system-governance-center` | com.bank.branch.platform.governance | 系统治理中心 (含 sys_job_conf / Quartz 集群调度) |
| `workflow-center` | com.bank.branch.platform.workflow | 工作流中心 (Flowable 集成) |
| `customer-marketing-center` | com.bank.branch.platform.customer | 客户营销中心 |
| `business-application-center` | com.bank.branch.platform.bizapp | 业务申请中心 |
| `portal-content-center` | com.bank.branch.platform.portal | 门户与内容中心 |
| `performance-engine-center` | com.bank.branch.platform.performance | 绩效计算中心 (含 eval 考核评价/奖励分配) |
| `report-analytics-center` | com.bank.branch.platform.report | 报表分析中心 (只读) |
| `bootstrap` | com.bank.branch.platform | 唯一的 Spring Boot 启动入口 |

此外还有两个工程：

- `soap-gateway-center/` — 外部渠道 SOAP/callpu 网关（包名 `...platform.soap`）：随 bootstrap 同 JVM 启动，`SoapNettyServer` 额外监听独立 Netty 端口；另提供 `POST /api/callpu` HTTP 入口
- `xanzc_frontend/` — Vue 3 + Vite 前端（npm 工程，不在 Maven 聚合内，`npm run dev` 启动）

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
performance-engine-center (依赖 auth + governance + workflow + customer-marketing)

report-analytics-center (只读，依赖 auth/governance/performance/customer 的 *Api，不被业务模块依赖)

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
- **对象存储**: MinIO 本地服务
- **日志级别**: DEBUG (com.bank.platform), INFO (root)

### 配置文件

- 配置: `src/main/resources/application.yml`
- Session 超时: 7200 秒 (2 小时)
- Flowable history level: `audit`
- MyBatis mapper 位置: `classpath*:mapper/**/*Mapper.xml`

### 端口与 API 文档

- 后端 `server.port`: **18081**；SOAP 网关 Netty 端口: 30523（`platform.soap.netty.port`）
- 前端 dev server: 8090（`/api` 代理到 `http://localhost:18081`）
- Knife4j UI: `http://localhost:18081/doc.html` (启动后访问)

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
- **docs 目录**: 各模块详细设计文档 + DDL + 共享开发规范 (见 `docs/AGENTS.md`)
- **运维 Runbook**: `docs/modules/system-governance-center/09-运维Runbook.md` — sys_job_conf / Quartz 集群调度运维权威指南（V1.9 整合）

### 模块级 AGENTS.md (开发时必须参考)
- **公共基础设施**: [common/AGENTS.md](common/AGENTS.md)
- **认证授权**: [auth-permission-center/AGENTS.md](auth-permission-center/AGENTS.md)
- **系统治理**: [system-governance-center/AGENTS.md](system-governance-center/AGENTS.md)
- **工作流**: [workflow-center/AGENTS.md](workflow-center/AGENTS.md)
- **客户营销**: [customer-marketing-center/AGENTS.md](customer-marketing-center/AGENTS.md)
- **业务申请**: [business-application-center/AGENTS.md](business-application-center/AGENTS.md)
- **门户与内容**: [portal-content-center/AGENTS.md](portal-content-center/AGENTS.md)
- **绩效计算**: [performance-engine-center/AGENTS.md](performance-engine-center/AGENTS.md)
- **报表分析**: [report-analytics-center/AGENTS.md](report-analytics-center/AGENTS.md)
- **启动入口**: [bootstrap/AGENTS.md](bootstrap/AGENTS.md)
- **外部渠道网关（SOAP/callpu）**: [soap-gateway-center/AGENTS.md](soap-gateway-center/AGENTS.md)
- **前端**: [xanzc_frontend/AGENTS.md](xanzc_frontend/AGENTS.md)

### 共享开发规范
- **[docs/common-dev-guide.md](docs/common-dev-guide.md)** — 统一响应模型、错误码规范、分页标准、鉴权链路、数据范围 SQL 模板、审计规范、事件发布、数据传输、日志规范 (所有模块必须遵守)

### TDD (测试驱动开发) 绝对红线
- **红-绿-重构 (Red-Green-Refactor) 闭环**：一切特性的开发或者 Bug 修复，必须先写测试（让他失败，Red），再写最简代码让他通过（Green），最后重构优化（Refactor）。
- **禁止事后狂补测试**：严禁无视 TDD，先凭直觉写完一大堆业务逻辑再去凑测试的行为。

### Flyway 禁令（绝对红线）
- **本项目已彻底废弃 Flyway**：禁止引入 `flyway-core` / `flyway-mysql` 任何版本依赖；禁止在 `application*.yml` 出现 `spring.flyway.*` 配置；禁止新增 `V*__*.sql` / `U*__*.sql` 命名风格的迁移脚本；禁止编写 `*FlywayIT` / `*FlywayTestBase` 类。
- **schema 变更走 SQL 直接执行**：所有 DDL/DML 由开发或 DBA 直接在目标库执行（手工或 CI 脚本），不再依赖任何"按版本号自动 migrate"框架。
- **历史迁移脚本已全部删除**：`onepl` 与 `yiti` 当前 schema 即为唯一真相，未来如需 fresh deploy 请用 `mysqldump` 从生产库导出 baseline。

### MyBatis-Plus 规范（新增功能绝对红线，2026-06-10 起）
- **所有新增功能涉及的数据库访问统一使用 MyBatis-Plus**：Mapper 接口必须 `extends BaseMapper<T>`；单条 CRUD（`insert`/`selectById`/`updateById`/`deleteById` 等）直接用 BaseMapper 内置方法，**禁止**为这些方法重复写 XML；动态/简单条件查询优先 `LambdaQueryWrapper`/`LambdaUpdateWrapper`。
- **XML 只写 BaseMapper 覆盖不到的自定义 SQL**：批量插入、跨表 JOIN、GROUP BY 聚合、带乐观条件的批更新等才落 `*Mapper.xml`（命名空间指向该 BaseMapper 接口）。
- **实体**用 MyBatis-Plus 注解 `@TableName` / `@TableId(type = IdType.AUTO)`；**配置**已就绪（root 依赖 `mybatis-plus-spring-boot3-starter` 3.5.7、`map-underscore-to-camel-case: true`、各模块 `@MapperScan` + `MybatisPlusConfig`），新 Mapper 开箱即用，无需额外配置。
- 既有遗留的纯 MyBatis 写法不强制回改，但**新增**一律按本规范。

### Sol 主代理与 Luna 子代理协作规范（绝对红线）

非简单开发任务必须采用 **Sol 主代理分析规划、Luna 子代理执行开发、Sol 主代理最终验收** 的协作方式：

1. **Sol 先分析和制定计划**：主代理必须先阅读本文件、目标模块的 `AGENTS.md` 及相关代码，明确需求、影响范围、风险、验收标准和可执行计划，再开始派遣开发任务。
2. **拆分可验证子任务**：Sol 将计划拆成边界清晰、范围受控、可以独立完成并验证的开发子任务；架构决策、跨模块契约、需求歧义、冲突裁决及高风险操作仍由 Sol 负责。
3. **Luna 负责子任务开发**：清晰、窄范围、低歧义或重复性的编码与测试任务，必须优先派给 `luna_worker`（`gpt-5.6-luna`，`max`）执行。
4. **明确文件所有权**：每个 Luna 子代理必须获得明确的文件或模块所有权，并被告知其并非独自在代码库中工作；不得回退他人改动，发现并行修改时必须主动适配。
5. **谨慎并行写入**：仅当子任务互不依赖且修改文件不重叠时才允许并行派遣多个 Luna；存在共享文件、契约依赖、数据库迁移或集成顺序时必须顺序执行，不得为了并行而强行拆分。
6. **等待并审查结果**：Sol 必须等待所有 Luna 子代理返回，审查实际 diff，确认没有遗漏、越界或无关改动，并按本项目 TDD、前端及数据库验证门禁完成适用的集成验证。
7. **Sol 最终验收**：最终由 Sol 汇总计划完成情况、实际变更、测试结果、未验证项和剩余风险；不得直接转发 Luna 的结论作为最终验收结果。
8. **聚焦提交**：每完成一个可独立验证的小节点，应在适用测试通过后创建聚焦的 Git commit；提交不得夹带用户已有改动或无关文件。

### 前端与数据库验证门禁（绝对红线）
- 任何前端功能、交互、样式或 API 契约改动，除单元测试和构建外，必须使用官方 `playwright-cli` 对真实运行页面验证关键流程；不得用 Vitest、Playwright Test 或 MCP 替代 CLI 浏览器验收。
- 每次 CLI 验收必须归档真实执行命令、路由/拦截器注册清单、原始 console 输出、原始 requests/response 摘要及截图。无 mock 验收须同时证明未注册 mock route、请求实际到达目标服务；开发态 mock 必须逐条列出 route 与响应、显著标注“仅开发态 mock，非联调”，不得以占位伪代码、手工改写日志或截图替代原始证据。
- 任何 DDL、DML 或数据库脚本开始前，必须先对现有 `yiti_test` 做只读盘点。覆盖、清空、重建或从 `yiti` 克隆到 `yiti_test` 均属于破坏性操作，必须先取得明确确认；不得把“测试验证”或“备份”视为隐含授权，更不得直接改动 `yiti`。
- 备份仅针对获准操作的目标库：须保存受控访问的备份文件、表/行数清单与 checksum 清单，并在独立隔离实例完成可恢复性演练和校验。备份、diff、日志、截图和工单不得暴露生产敏感数据；需要时先脱敏、最小化导出并限定留存与访问人员。
- 获准克隆后，`yiti_test` 必须隔离 `SPRING_SESSION`、`PT_LOCK`、Quartz/`sys_job_conf` 调度、消息消费者/生产者、缓存命名空间、对象存储和所有外联凭据/回调；禁用调度和外发，使用独立实例标识。仅可在该隔离环境验证结构、数据、约束、真实查询、幂等复跑、执行前后 diff 与恢复演练。
- `yiti_test` 验证失败不得进入 `yiti`；验证通过后须先报告完整证据，并再次取得对 `yiti` 的明确执行确认，方可执行。任何未确认、隔离不完整或证据缺失均为停止条件。
