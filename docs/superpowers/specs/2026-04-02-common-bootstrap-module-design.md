# Common + Bootstrap 模块设计文档

> 版本: V1.1 | 日期: 2026-04-02
> 状态: 审阅修订版

---

## 1. 目标

从零搭建 Branch Platform 后端项目骨架，完成 common 基础层（5 个子模块）和 bootstrap 启动模块的全部实现。完成后：

- `mvn clean install` 编译通过
- bootstrap 可启动（Spring Boot Application Context 加载成功）
- 所有 common 组件可被业务模块依赖使用
- TDD 驱动：每个组件先写测试再写实现

## 2. 技术栈与版本

| 依赖 | 版本 |
|:---|:---|
| JDK | 17 |
| Spring Boot | 3.2.3 |
| MyBatis Spring Boot Starter | 3.0.3 |
| Druid Spring Boot Starter | 1.2.21 |
| Knife4j OpenAPI3 Spring Boot Starter | 4.4.0 |
| Flowable Spring Boot Starter | 7.0.1 |
| Spring Boot Starter Data Redis | 3.2.3 (BOM) |
| Spring Session Data Redis | 3.2.3 (BOM) |
| MinIO | 8.5.7 |
| Lombok | BOM 管理 |
| JUnit 5 | BOM 管理 |
| Mockito | BOM 管理 |

## 3. Maven 项目结构

```
branch-platform/                            (根目录 = 当前仓库根)
├── pom.xml                                 (父 POM，BOM + 插件管理)
├── common/                                 (common 聚合模块)
│   ├── pom.xml
│   ├── common-web/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/com/bank/branch/platform/common/web/
│   │       │   ├── ResponseWrapper.java
│   │       │   ├── PageRequest.java
│   │       │   ├── PageResult.java
│   │       │   ├── RequestValidator.java
│   │       │   ├── GlobalExceptionHandler.java
│   │       │   ├── CorsConfig.java
│   │       │   ├── config/WebAutoConfiguration.java
│   │       │   └── exception/
│   │       │       ├── BizException.java
│   │       │       ├── AuthException.java
│   │       │       └── PermissionDeniedException.java
│   │       ├── test/java/com/bank/branch/platform/common/web/
│   │       │   ├── ResponseWrapperTest.java
│   │       │   ├── PageRequestTest.java
│   │       │   ├── PageResultTest.java
│   │       │   ├── RequestValidatorTest.java
│   │       │   └── GlobalExceptionHandlerTest.java
│   │       └── main/resources/META-INF/spring/
│   │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   ├── common-trace/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/com/bank/branch/platform/common/trace/
│   │       │   ├── TraceIdFilter.java
│   │       │   ├── TraceIdInterceptor.java
│   │       │   ├── TraceContext.java
│   │       │   ├── MdcUtils.java
│   │       │   └── config/TraceAutoConfiguration.java
│   │       ├── test/java/com/bank/branch/platform/common/trace/
│   │       │   ├── MdcUtilsTest.java
│   │       │   ├── TraceIdFilterTest.java
│   │       │   └── TraceIdInterceptorTest.java
│   │       └── main/resources/META-INF/spring/
│   │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   ├── common-aop/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/com/bank/branch/platform/common/aop/
│   │       │   ├── ApiLogAspect.java
│   │       │   ├── MethodTimingAspect.java
│   │       │   ├── AuditLogAspect.java
│   │       │   ├── annotation/AuditLog.java
│   │       │   ├── event/AuditLogEvent.java
│   │       │   ├── handler/AuditLogHandler.java
│   │       │   └── config/AopAutoConfiguration.java
│   │       ├── test/java/com/bank/branch/platform/common/aop/
│   │       │   ├── ApiLogAspectTest.java
│   │       │   ├── MethodTimingAspectTest.java
│   │       │   └── AuditLogAspectTest.java
│   │       └── main/resources/META-INF/spring/
│   │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   ├── common-db/
│   │   ├── pom.xml
│   │   └── src/
│   │       ├── main/java/com/bank/branch/platform/common/db/
│   │       │   ├── PageInterceptor.java
│   │       │   ├── AuditFieldFiller.java
│   │       │   ├── SlowSqlInterceptor.java
│   │       │   ├── DruidConfig.java
│   │       │   └── config/DbAutoConfiguration.java
│   │       ├── test/java/com/bank/branch/platform/common/db/
│   │       │   ├── PageInterceptorTest.java
│   │       │   ├── AuditFieldFillerTest.java
│   │       │   └── SlowSqlInterceptorTest.java
│   │       └── main/resources/META-INF/spring/
│   │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── common-security/
│       ├── pom.xml
│       └── src/
│           ├── main/java/com/bank/branch/platform/common/security/
│           │   ├── enums/
│           │   │   ├── BizType.java
│           │   │   ├── BizAction.java
│           │   │   └── DataScopeType.java
│           │   ├── annotation/BizAuth.java
│           │   ├── context/
│           │   │   ├── CurrentUserContext.java
│           │   │   └── DataScopeContext.java
│           │   ├── meta/
│           │   │   ├── ObjectMeta.java
│           │   │   └── ObjectMetaRegistry.java
│           │   ├── masker/SensitiveDataMasker.java
│           │   ├── sign/SignatureUtils.java
│           │   └── config/SecurityAutoConfiguration.java
│           ├── test/java/com/bank/branch/platform/common/security/
│           │   ├── enums/BizTypeTest.java
│           │   ├── enums/BizActionTest.java
│           │   ├── enums/DataScopeTypeTest.java
│           │   ├── context/CurrentUserContextTest.java
│           │   ├── context/DataScopeContextTest.java
│           │   ├── meta/ObjectMetaTest.java
│           │   ├── meta/ObjectMetaRegistryTest.java
│           │   ├── masker/SensitiveDataMaskerTest.java
│           │   └── sign/SignatureUtilsTest.java
│           └── main/resources/META-INF/spring/
│               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── bootstrap/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/bank/branch/platform/BranchPlatformApplication.java
        │   └── resources/
        │       ├── application.yml
        │       ├── application-dev.yml
        │       └── logback-spring.xml
        └── test/java/com/bank/branch/platform/
            └── BranchPlatformApplicationTest.java
```

## 4. 根 POM 设计

- `groupId`: `com.bank.branch.platform`
- `artifactId`: `branch-platform`
- `packaging`: `pom`
- `<modules>`: common, bootstrap
- `<dependencyManagement>`: Spring Boot BOM 3.2.3 + 第三方版本锁定
- `<pluginManagement>`: maven-compiler-plugin (JDK 17), maven-surefire-plugin
- `<properties>`: 统一版本号变量

## 5. Common 子模块依赖关系

```
common-web          (无内部依赖, 依赖 spring-boot-starter-web, lombok, jackson, jakarta.validation)
                    注意: ResponseWrapper 中获取 traceId 直接使用 org.slf4j.MDC.get("traceId")
                    而非依赖 common-trace 的 MdcUtils，保持零内部依赖
common-trace        (无内部依赖, 依赖 spring-boot-starter-web, slf4j)
common-security     ──> common-web (依赖 BizException/PermissionDeniedException)
common-aop          ──> common-trace (依赖 MdcUtils)
                    ──> common-security (依赖 SensitiveDataMasker, CurrentUserContext)
common-db           ──> common-security (依赖 CurrentUserContext 填充审计字段)
```

每个子模块的外部依赖：

| 子模块 | 外部依赖 |
|:---|:---|
| common-web | spring-boot-starter-web, lombok, jackson-databind, jakarta.validation-api, slf4j-api (用于 MDC.get) |
| common-trace | spring-boot-starter-web, slf4j-api |
| common-security | common-web, lombok |
| common-aop | common-trace, common-security, spring-boot-starter-aop |
| common-db | common-security, mybatis-spring-boot-starter, druid-spring-boot-starter |

## 6. 各子模块组件清单

### 6.1 common-web

| 类 | 职责 | 关键设计点 |
|:---|:---|:---|
| ResponseWrapper\<T\> | 统一响应包装 | code/message/traceId/data/page/timestamp；静态工厂 success()/page()/error()；traceId 通过 `org.slf4j.MDC.get("traceId")` 获取，不依赖 common-trace |
| PageRequest | 统一分页请求 | pageNo(默认1)/pageSize(默认20,最大100)/sortBy/sortDir；offset 计算；提供 `validateSortBy(Set<String> allowedFields)` 方法做排序字段白名单校验，校验失败抛出 BizException |
| PageResult\<T\> | 统一分页结果 | pageNo/pageSize/total/records；静态工厂 of()；getTotalPages() |
| RequestValidator | 参数校验增强 | 封装 javax.validation 校验结果提取；提供 `validate(Object)` 静态方法，校验失败自动抛出 BizException 并格式化所有违规字段信息 |
| BizException | 业务异常基类 | code + message |
| AuthException | 认证异常 | 默认 AUTH_001, HTTP 401 |
| PermissionDeniedException | 权限异常 | 默认 PERM_001, HTTP 403 |
| GlobalExceptionHandler | 全局异常处理 | @RestControllerAdvice；捕获 BizException(→对应HTTP状态码)/AuthException(→401)/PermissionDeniedException(→403)/MethodArgumentNotValidException(→400)/HttpRequestMethodNotSupportedException(→405)/HttpMediaTypeNotSupportedException(→415)/MissingServletRequestParameterException(→400)/Exception(→500) |
| CorsConfig | 跨域配置 | @Profile("dev") 仅 dev profile 生效 |
| WebAutoConfiguration | 自动装配 | 注册 GlobalExceptionHandler、CorsConfig |

### 6.2 common-trace

| 类 | 职责 | 关键设计点 |
|:---|:---|:---|
| MdcUtils | MDC 工具 | put/get/remove traceId；常量 TRACE_ID_KEY = "traceId" |
| TraceContext | 链路上下文 | traceId/source/startTime |
| TraceIdFilter | traceId 生成 | Filter order=Ordered.HIGHEST_PRECEDENCE+10；UUID 去横杠取前16位；请求结束清理 MDC |
| TraceIdInterceptor | 跨模块 traceId 传递 | HandlerInterceptor，检查请求头 X-Trace-Id，存在则复用、不存在则由 Filter 已生成；确保异步调用/线程池场景下 traceId 可传递 |
| TraceAutoConfiguration | 自动装配 | 注册 TraceIdFilter + TraceIdInterceptor |

### 6.3 common-security

| 类 | 职责 | 关键设计点 |
|:---|:---|:---|
| BizType | 业务类型枚举 | 17 个值 |
| BizAction | 业务操作枚举 | 15 个值 |
| DataScopeType | 数据范围枚举 | 7 个值，每个值注释 SQL 条件模板 |
| @BizAuth | 权限注解 | bizType + action, RetentionPolicy.RUNTIME |
| CurrentUserContext | 用户上下文 record | empId/mainOrgCode/roleIds/candidateGroupKeys/systemAdmin |
| DataScopeContext | 数据范围 ThreadLocal | ThreadLocal\<DataScopeContext\>，set/current/clear |
| ObjectMeta | 对象元数据 record | objectKey/tableName/各列名/supportedScopes；validateScope() |
| ObjectMetaRegistry | 元数据注册中心 | ConcurrentHashMap；register(重复注册抛 IllegalStateException)/get(返回 Optional)/getRequired(不存在抛 BizException) |
| SensitiveDataMasker | 脱敏工具 | 手机号/身份证/银行账号/金额 |
| SignatureUtils | 签名工具 | HmacSHA256 sign/verify；异常包装为 BizException("SIGN_001", "签名计算失败") 而非裸 RuntimeException |
| SecurityAutoConfiguration | 自动装配 | 注册 ObjectMetaRegistry Bean + DataScopeContext 清理 Filter |

### 6.4 common-aop

| 类 | 职责 | 关键设计点 |
|:---|:---|:---|
| @AuditLog | 审计注解 | action/resourceType/reasonRequired |
| AuditLogEvent | 审计事件模型 | record：action/resourceType/resourceId/businessKey/operatorEmpId/operatorOrgId/requestTime/ip/userAgent/before/after/reason/timestamp |
| AuditLogHandler | 审计存储接口 | `void handle(AuditLogEvent)`；由 system-governance-center 实现；common-aop 提供 NoopAuditLogHandler 默认实现（仅日志输出），避免无实现时 Bean 注入失败 |
| ApiLogAspect | 接口日志切面 | 切 @RestController；入参出参记录；敏感字段通过 SensitiveDataMasker 脱敏 |
| MethodTimingAspect | 耗时统计切面 | 切 @Service；超过 warn 阈值 (默认500ms) WARN，超过 error 阈值 (默认5000ms) ERROR；两个阈值均可通过 `platform.method-timing-warn-ms` 和 `platform.method-timing-error-ms` 配置 |
| AuditLogAspect | 审计切面 | 切 @AuditLog；采集操作信息构建 AuditLogEvent，委托 AuditLogHandler 处理 |
| AopAutoConfiguration | 自动装配 | 注册三个切面 Bean + 条件注册 NoopAuditLogHandler（@ConditionalOnMissingBean） |

### 6.5 common-db

| 类 | 职责 | 关键设计点 |
|:---|:---|:---|
| PageInterceptor | MyBatis 分页拦截器 | 拦截 query 类型的 MappedStatement；识别 PageRequest 参数；注入 COUNT + LIMIT SQL；排序字段需在此处做 SQL 注入防护（仅允许字母、数字、下划线） |
| AuditFieldFiller | 审计字段填充 | MyBatis 拦截器；INSERT 填充 created_by/created_time；UPDATE 填充 updated_by/updated_time；从 DataScopeContext 或 CurrentUserContext 获取当前用户 |
| SlowSqlInterceptor | 慢 SQL 拦截 | MyBatis 拦截器；执行时间 > 阈值 (默认5000ms，通过 `platform.slow-sql-threshold-ms` 配置) 时 WARN 日志，记录完整 SQL、参数、耗时 |
| DruidConfig | Druid 配置 | @Configuration + @ConfigurationProperties("spring.datasource.druid") |
| DbAutoConfiguration | 自动装配 | 注册三个拦截器 + DruidConfig |

## 7. Bootstrap 模块设计

### 7.1 依赖

bootstrap 依赖所有 common 子模块 + 未来各业务模块。V1 初始仅依赖 common。

额外引入（业务模块依赖但 common 不引入的）：
- spring-boot-starter-data-redis
- spring-session-data-redis
- knife4j-openapi3-jakarta-spring-boot-starter
- mysql-connector-j
- (Flowable 在 workflow-center 开发时引入并在 application.yml 补充 `flowable.history-level: audit` 配置)
- (MinIO 在 portal-content-center / system-governance-center 开发时引入)

### 7.2 application.yml 关键配置

```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  profiles:
    active: dev
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/onepl?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
  data:
    redis:
      host: localhost
      port: 6379
  session:
    store-type: redis
    timeout: 7200s

mybatis:
  mapper-locations: classpath*:mapper/**/*Mapper.xml
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.bank.branch.platform: DEBUG
    root: INFO

knife4j:
  enable: true
  setting:
    language: zh_cn

platform:
  slow-sql-threshold-ms: 5000
  method-timing-warn-ms: 500
  method-timing-error-ms: 5000
```

### 7.3 application-dev.yml

```yaml
# 开发环境专属配置
spring:
  datasource:
    druid:
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        login-username: admin
        login-password: admin123
      web-stat-filter:
        enabled: true
      filter:
        stat:
          log-slow-sql: true
          slow-sql-millis: 5000

# CORS 由 CorsConfig (@Profile("dev")) 自动生效

logging:
  level:
    com.bank.branch.platform: DEBUG
    org.mybatis: DEBUG
```

### 7.4 logback-spring.xml

- 控制台输出包含 traceId（从 MDC 读取）
- 格式：`%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n`
- dev profile 下 com.bank.branch.platform 级别为 DEBUG
- 文件滚动策略：按日切割，保留 30 天

## 8. TDD 策略

每个组件遵循 Red-Green-Refactor 闭环：

| 子模块 | 测试重点 |
|:---|:---|
| common-web | ResponseWrapper 工厂方法、PageRequest 校验/offset 计算/sortBy 白名单校验、PageResult.of()/getTotalPages()、RequestValidator 校验、GlobalExceptionHandler 各异常类型映射（含 405/415/400） |
| common-trace | MdcUtils put/get/remove、TraceIdFilter 生成并注入 MDC/请求结束清理、TraceIdInterceptor 复用请求头中的 traceId |
| common-security | 枚举值完整性（数量和编码）、CurrentUserContext hasRole/hasAnyRole、DataScopeContext ThreadLocal 隔离和清理、ObjectMeta validateScope、ObjectMetaRegistry 注册/查询/重复注册异常/getRequired 不存在异常、SensitiveDataMasker 各脱敏方法（含边界值）、SignatureUtils sign/verify/异常处理 |
| common-aop | ApiLogAspect 拦截验证、MethodTimingAspect 双阈值告警、AuditLogAspect 事件采集并委托 Handler |
| common-db | PageInterceptor SQL 改写及排序字段注入防护、AuditFieldFiller 字段注入、SlowSqlInterceptor 慢查询检测 |
| bootstrap | ApplicationContext 启动成功、自动装配组件加载验证 |

## 9. 实现顺序

按依赖关系从底向上：

1. 根 POM + common 聚合 POM
2. common-web（无内部依赖，最底层）
3. common-trace（无内部依赖）
4. common-security（依赖 common-web）
5. common-aop（依赖 common-trace + common-security）
6. common-db（依赖 common-security）
7. bootstrap（依赖全部，集成验证）

## 10. 设计决策记录

| # | 决策 | 选项 | 选择 | 理由 |
|:---|:---|:---|:---|:---|
| 1 | ResponseWrapper 实现 | 03-关键组件设计.md vs common-dev-guide.md | 03-关键组件设计.md | 更具体的模块级设计，Lombok @Data、success()/error()、Instant 时间戳 |
| 2 | Common 拆分粒度 | 5 子模块 vs 单模块 | 5 子模块 | 架构文档明确要求，职责清晰 |
| 3 | 自动装配机制 | spring.factories vs AutoConfiguration.imports | AutoConfiguration.imports | Spring Boot 3.x 推荐方式 |
| 4 | AuditLogAspect 存储 | 直接写数据库 vs 接口解耦 | 接口解耦 (AuditLogHandler) | common 不操作数据库，存储由 system-governance-center 实现；提供 NoopAuditLogHandler 兜底 |
| 5 | ObjectMetaRegistry | 静态 Map vs Spring Bean | Spring Bean (Singleton) | 便于测试和依赖注入；源文档 02-后端架构.md 中 meta/ 下仅列出 ObjectMeta，ObjectMetaRegistry 是对源文档的合理补充，为 ObjectMeta 提供集中管理和查询能力 |
| 6 | ResponseWrapper 获取 traceId | 依赖 common-trace 的 MdcUtils vs 直接用 SLF4J MDC | 直接用 `org.slf4j.MDC.get("traceId")` | 保持 common-web 零内部依赖，MDC key 与 MdcUtils.TRACE_ID_KEY 保持一致即可 |
| 7 | 排序字段白名单 | Controller 注解声明 vs PageRequest 方法校验 vs PageInterceptor 拦截 | PageRequest 提供 validateSortBy() + PageInterceptor 正则防护 | 双层校验：业务层白名单 + 底层 SQL 注入防护 |
| 8 | 日志级别包名 | CLAUDE.md 写的 com.bank.platform vs 实际 com.bank.branch.platform | com.bank.branch.platform | CLAUDE.md 中 `com.bank.platform` 少了 `.branch`，以实际包名为准 |
| 9 | SignatureUtils 异常 | 裸 RuntimeException vs BizException | BizException("SIGN_001", ...) | 安全工具类异常应纳入统一异常体系，便于 GlobalExceptionHandler 处理 |
