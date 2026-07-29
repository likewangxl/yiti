# Druid SQL 监控修复设计

## 目标

修复 `dev` profile 下 Druid SQL 监控无数据的问题，使主业务数据源执行的 SQL 能进入 Druid `SQL监控`，并使 Web URI 监控显示实际 JDBC 执行次数。

## 根因

当前运行时主数据源已执行 SQL，但 `FilterClassNames` 为空，`/druid/sql.json` 无记录：

- `spring.datasource.druid.filter.stat` 未配置 `enabled: true`，因此没有创建 `StatFilter`。
- `RptReadOnlyDataSourceConfig` 手工构造普通 `DruidDataSource`，绕过了 Druid Starter 的 `DruidDataSourceWrapper.autoAddFilters(...)`，导致已经启用的 `Slf4jLogFilter` 也未挂载。

## 方案

### 主业务数据源

`RptReadOnlyDataSourceConfig.primaryDataSource` 改为创建 `DruidDataSourceWrapper`。该类型继承 `DruidDataSource`，继续接受 `spring.datasource.druid` 属性绑定，并由 Starter 自动注入所有已启用的 Druid Filter。

保持以下契约不变：

- Bean 名仍为 `primaryDataSource`，并继续标记 `@Primary`。
- 注入类型仍兼容 `DataSource` 和 `DruidDataSource`。
- JDBC URL、账号、驱动、连接池容量、超时和事务管理方式不变。
- MyBatis、Flowable、Quartz 和 Spring Session 继续使用主数据源。

### 过滤器配置

在 `application-dev.yml` 中增加：

```yaml
spring:
  datasource:
    druid:
      filter:
        stat:
          enabled: true
```

现有 `Slf4jLogFilter` 配置保持不变。修复后开发环境会继续按现有配置打印带实际参数的 SQL。

### 只读数据源

`rptReadOnlyDataSource` 保持普通 `DruidDataSource`，不挂载 SQL 统计和日志过滤器，避免 Druid 页面和应用日志记录 SQL 探查功能提交的任意 SQL。

## 行为影响

- 主数据源在 Spring Bean 初始化阶段建立连接池；数据库不可用时会更早暴露启动失败。
- SQL 统计会产生少量运行时和内存开销。
- `druid.sql.Statement=DEBUG` 与 `statement-executable-sql-log-enable=true` 会增加开发日志量，并记录 SQL 参数。
- 配置开关仅位于 `application-dev.yml`；其他 profile 不自动启用 `StatFilter`。
- 本修复只恢复 SQL 可观测性，不处理 GoldenDB 网络断开或事务结果未知问题。

## 测试与验收

1. 先增加 Spring 上下文测试，验证 `dev` profile 下主数据源：
   - 实例为 `DruidDataSourceWrapper`；
   - 挂载 `StatFilter`；
   - 挂载 `Slf4jLogFilter`。
2. 验证只读数据源未挂载上述两个过滤器。
3. 运行测试并确认修改前失败、修改后通过。
4. 重启后端并调用实际查询接口。
5. 验证 Druid JSON 接口：
   - 主数据源 `FilterClassNames` 包含 `StatFilter` 和 `Slf4jLogFilter`；
   - `/druid/sql.json` 至少出现一条 SQL；
   - Web URI 的 `JdbcExecuteCount` 大于零。

