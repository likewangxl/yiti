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


## 当前已实现的模块

| 模块 | 包名 | 状态 | 说明 |
|------|------|------|------|
| `common` | com.bank.branch.platform.common.* | 已完成 | 公共基础设施层 (5 个子模块) |
| `auth-permission-center` | com.bank.branch.platform.auth | 已完成 | 认证授权中心 (RBAC + 数据范围) |
| `system-governance-center` | com.bank.branch.platform.governance | 已完成 | 系统治理中心 (7 大治理域) |
| `workflow-center` | com.bank.branch.platform.workflow | 已完成 | 工作流中心 (Flowable 7.0.1 集成) |
| `bootstrap` | com.bank.branch.platform | 已完成 | Spring Boot 启动入口 |

**尚未实现的模块** (代码骨架和 DDL 已存在):
- `portal-content-center` (门户与内容中心)
- `customer-marketing-center` (客户营销中心)
- `business-application-center` (业务申请中心)
- `performance-engine-center` (绩效计算中心)
- `report-analytics-center` (报表分析中心)

### 当前模块依赖图

```
common (common-web → common-trace → common-security → common-aop → common-db)
  ↑
auth-permission-center (无其他业务模块依赖)
  ↑
system-governance-center (依赖 auth)  ← 被 workflow 依赖
  ↑
workflow-center (依赖 auth + governance)

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
├─ portal-content-center         门户与内容中心 ⏳ 骨架
├─ customer-marketing-center     客户营销中心 ⏳ 骨架
├─ business-application-center   业务申请中心 ⏳ 骨架
├─ performance-engine-center     绩效计算中心 ⏳ 骨架
└─ report-analytics-center       报表分析中心 ⏳ 骨架
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

### 模块级 CLAUDE.md (开发时必须参考)
- **公共基础设施**: [common/CLAUDE.md](common/CLAUDE.md)
- **认证授权**: [auth-permission-center/CLAUDE.md](auth-permission-center/CLAUDE.md)
- **系统治理**: [system-governance-center/CLAUDE.md](system-governance-center/CLAUDE.md)
- **工作流**: [workflow-center/CLAUDE.md](workflow-center/CLAUDE.md)

### 共享开发规范
- **[docs/common-dev-guide.md](docs/common-dev-guide.md)** — 统一响应模型、错误码规范、分页标准、鉴权链路、数据范围 SQL 模板、审计规范、事件发布、数据传输、日志规范 (所有模块必须遵守)

### TDD (测试驱动开发) 绝对红线
- **红-绿-重构 (Red-Green-Refactor) 闭环**：一切特性的开发或者 Bug 修复，必须先写测试（让他失败，Red），再写最简代码让他通过（Green），最后重构优化（Refactor）。
- **禁止事后狂补测试**：严禁无视 TDD，先凭直觉写完一大堆业务逻辑再去凑测试的行为。