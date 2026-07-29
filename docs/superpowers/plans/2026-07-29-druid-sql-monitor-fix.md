# Druid SQL 监控修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 恢复 `dev` profile 主业务数据源的 Druid SQL 统计与 SQL 日志过滤器，使 SQL监控页面产生真实数据。

**Architecture:** 主业务数据源继续由 `RptReadOnlyDataSourceConfig` 显式声明，但构造类型从普通 `DruidDataSource` 切换为 Starter 的 `DruidDataSourceWrapper`，由 Wrapper 自动挂载 Spring 中已启用的 Filter。`application-dev.yml` 显式启用 `StatFilter`；独立只读 SQL 探查数据源保持无过滤器。

**Tech Stack:** Java 17、Spring Boot 3.2.3、Druid Spring Boot 3 Starter 1.2.21、JUnit 5、AssertJ、YAML

## Global Constraints

- 主 Bean 名和 `@Primary` 语义不变。
- JDBC URL、账号、驱动、连接池容量、超时与事务配置不变。
- `filter.stat.enabled` 仅配置在 `application-dev.yml`。
- `Slf4jLogFilter` 继续使用现有带实际参数的 SQL 日志配置。
- `rptReadOnlyDataSource` 不挂载 `StatFilter` 或 `Slf4jLogFilter`。
- 不修改或提交工作区中已有的其他用户改动。
- 本修复不宣称解决 GoldenDB 网络断开或事务结果未知问题。

---

### Task 1: 恢复主数据源 SQL 过滤器并验证运行时监控

**Files:**
- Modify: `report-analytics-center/src/test/java/com/bank/branch/platform/report/config/RptReadOnlyDataSourceConfigTest.java`
- Modify: `report-analytics-center/src/main/java/com/bank/branch/platform/report/config/RptReadOnlyDataSourceConfig.java:60`
- Modify: `bootstrap/src/main/resources/application-dev.yml:23`
- Include in commit: `docs/superpowers/plans/2026-07-29-druid-sql-monitor-fix.md`

**Interfaces:**
- Consumes: `DruidDataSourceWrapper` 的 `@Autowired(required=false) autoAddFilters(List<Filter>)` 和 `spring.datasource.druid.filter.*.enabled` 条件 Bean。
- Produces: 主数据源挂载 `StatFilter`、`Slf4jLogFilter`；只读数据源过滤器列表保持不含这两个类型。

- [x] **Step 1: 增加主/只读数据源过滤器边界测试**

为 `RptReadOnlyDataSourceConfigTest` 的 `@SpringBootTest` 增加测试属性：

```java
@SpringBootTest(
        classes = ReportTestApplication.class,
        properties = {
                "spring.datasource.druid.filter.stat.enabled=true",
                "spring.datasource.druid.filter.slf4j.enabled=true"
        }
)
```

增加主数据源注入：

```java
@Autowired
private DataSource primaryDataSource;
```

增加测试：

```java
@Test
void primaryDataSourceShouldUseWrapperAndAttachMonitoringFilters() {
    assertThat(primaryDataSource).isInstanceOf(DruidDataSourceWrapper.class);
    DruidDataSource druid = (DruidDataSource) primaryDataSource;
    assertThat(druid.getProxyFilters().stream().anyMatch(StatFilter.class::isInstance)).isTrue();
    assertThat(druid.getProxyFilters().stream().anyMatch(Slf4jLogFilter.class::isInstance)).isTrue();
}

@Test
void rptReadOnlyDataSourceShouldNotAttachMonitoringFilters() {
    DruidDataSource druid = (DruidDataSource) rptReadOnlyDataSource;
    assertThat(druid.getProxyFilters().stream().anyMatch(StatFilter.class::isInstance)).isFalse();
    assertThat(druid.getProxyFilters().stream().anyMatch(Slf4jLogFilter.class::isInstance)).isFalse();
}
```

- [x] **Step 2: 运行目标测试并确认 RED**

Run:

```bash
mvn -pl report-analytics-center \
  -Dtest=RptReadOnlyDataSourceConfigTest \
  test
```

Expected: FAIL，`primaryDataSource` 实际类型为普通 `DruidDataSource`，不是 `DruidDataSourceWrapper`。

- [x] **Step 3: 将主数据源切换为 Starter Wrapper**

在 `RptReadOnlyDataSourceConfig` 中导入：

```java
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceWrapper;
```

将主数据源构造类型改为：

```java
DataSource ds = properties.initializeDataSourceBuilder()
        .type(DruidDataSourceWrapper.class)
        .build();
```

不修改 `rptReadOnlyDataSource` 的构造代码。

- [x] **Step 4: 在 dev profile 显式启用 StatFilter**

将以下属性加入 `application-dev.yml` 的 `filter.stat` 节点：

```yaml
          enabled: true
```

- [x] **Step 5: 运行目标测试并确认 GREEN**

Run:

```bash
mvn -pl report-analytics-center \
  -Dtest=RptReadOnlyDataSourceConfigTest \
  test
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`，构建成功。

- [x] **Step 6: 验证 YAML 与修改范围**

Run:

```bash
ruby -e '
require "yaml"
druid = YAML.load_file("bootstrap/src/main/resources/application-dev.yml").dig("spring", "datasource", "druid")
abort("StatFilter disabled") unless druid.dig("filter", "stat", "enabled") == true
puts "Druid StatFilter enabled"
'
git diff --check -- \
  bootstrap/src/main/resources/application-dev.yml \
  report-analytics-center/src/main/java/com/bank/branch/platform/report/config/RptReadOnlyDataSourceConfig.java \
  report-analytics-center/src/test/java/com/bank/branch/platform/report/config/RptReadOnlyDataSourceConfigTest.java \
  docs/superpowers/plans/2026-07-29-druid-sql-monitor-fix.md
```

Expected: YAML 检查通过，`git diff --check` 无输出。

- [x] **Step 7: 重启后端并验证实际 SQL 统计**

先通过 `ss -ltnp` 与 `/proc/<pid>/cwd` 确认 `18080` 的现有 yiti 后端进程，然后发送 `SIGTERM`，等待端口释放。从 `bootstrap` 目录运行：

```bash
mvn -pl report-analytics-center -DskipTests install
cd bootstrap
mvn spring-boot:run
```

先安装更新后的 `report-analytics-center`，确保 `bootstrap` 使用的本地 Maven 依赖包含本次主数据源修改。

启动完成后，携带一个不存在的 Spring Session cookie 请求：

```bash
curl --noproxy '*' -sS \
  -H 'Cookie: SESSION=druid-monitor-probe' \
  http://127.0.0.1:18080/api/auth/current-user
```

登录 Druid JSON 接口并验证：

- 主数据源 `FilterClassNames` 包含 `StatFilter` 与 `Slf4jLogFilter`；
- `/druid/sql.json` 的 `Content` 非空；
- `/druid/weburi.json` 至少一个业务 URI 的 `JdbcExecuteCount` 大于零。

- [x] **Step 8: 提交实现**

Run:

```bash
git add \
  bootstrap/src/main/resources/application-dev.yml \
  report-analytics-center/src/main/java/com/bank/branch/platform/report/config/RptReadOnlyDataSourceConfig.java \
  report-analytics-center/src/test/java/com/bank/branch/platform/report/config/RptReadOnlyDataSourceConfigTest.java \
  docs/superpowers/plans/2026-07-29-druid-sql-monitor-fix.md
git commit -m "fix(config): 恢复 Druid SQL 监控"
```

Expected: 提交只包含上述四个文件。
