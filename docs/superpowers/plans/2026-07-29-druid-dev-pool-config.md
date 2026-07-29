# Druid 开发环境连接池配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `dev` profile 增加一组有界的 Druid 连接池容量、等待、空闲检测和主动保活参数。

**Architecture:** 所有新增参数放在 `bootstrap/src/main/resources/application-dev.yml` 的 `spring.datasource.druid` 节点，复用现有 Druid Starter 1.2.21 的属性绑定。全局 `application.yml` 和其他 profile 不变，现有 Druid 监控及 SQL 日志配置保持原样。

**Tech Stack:** Spring Boot 3.2.3、Druid Spring Boot 3 Starter 1.2.21、YAML

## Global Constraints

- 只修改 `dev` profile 的 Druid 配置。
- `initial-size: 5`、`min-idle: 5`、`max-active: 20`。
- `max-wait: 60000`、`time-between-eviction-runs-millis: 60000`、`min-evictable-idle-time-millis: 300000`。
- `validation-query: SELECT 1`、`test-while-idle: true`。
- `keep-alive: true`、`keep-alive-between-time-millis: 120000`。
- 不启用 `test-on-borrow` 或 `test-on-return`。
- 主动保活只降低空闲连接失效概率，不作为 GoldenDB 事务中断的根因修复。
- 不覆盖或提交 `bootstrap/src/main/resources/application.yml` 中已有的用户修改。

---

### Task 1: 增加并验证开发环境连接池参数

**Files:**
- Modify: `bootstrap/src/main/resources/application-dev.yml:5`
- Test: `bootstrap/src/main/resources/application-dev.yml`
- Include in commit: `docs/superpowers/plans/2026-07-29-druid-dev-pool-config.md`

**Interfaces:**
- Consumes: Spring Boot `dev` profile 与 Druid Starter 的 `spring.datasource.druid.*` 属性绑定。
- Produces: 启动容量 5、最小空闲 5、最大活跃 20，并按 60 秒周期检测、5 分钟空闲阈值回收的开发数据源。

- [x] **Step 1: 运行配置存在性检查并确认当前失败**

Run:

```bash
ruby -e '
require "yaml"
druid = YAML.load_file("bootstrap/src/main/resources/application-dev.yml").dig("spring", "datasource", "druid")
expected = {
  "initial-size" => 5,
  "min-idle" => 5,
  "max-active" => 20,
  "max-wait" => 60000,
  "time-between-eviction-runs-millis" => 60000,
  "min-evictable-idle-time-millis" => 300000,
  "validation-query" => "SELECT 1",
  "test-while-idle" => true
}
abort("missing Druid pool settings") unless expected.all? { |key, value| druid[key] == value }
'
```

Expected: FAIL with `missing Druid pool settings`。

- [x] **Step 2: 添加最小配置实现**

在 `spring.datasource.druid` 下、`stat-view-servlet` 前加入：

```yaml
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000
      validation-query: SELECT 1
      test-while-idle: true
```

- [x] **Step 3: 重新运行配置存在性检查**

Run:

```bash
ruby -e '
require "yaml"
druid = YAML.load_file("bootstrap/src/main/resources/application-dev.yml").dig("spring", "datasource", "druid")
expected = {
  "initial-size" => 5,
  "min-idle" => 5,
  "max-active" => 20,
  "max-wait" => 60000,
  "time-between-eviction-runs-millis" => 60000,
  "min-evictable-idle-time-millis" => 300000,
  "validation-query" => "SELECT 1",
  "test-while-idle" => true
}
abort("missing Druid pool settings") unless expected.all? { |key, value| druid[key] == value }
puts "Druid dev pool settings OK"
'
```

Expected: PASS and print `Druid dev pool settings OK`。

- [x] **Step 4: 验证 YAML、差异和作用范围**

Run:

```bash
ruby -e 'require "yaml"; YAML.load_file("bootstrap/src/main/resources/application-dev.yml"); puts "YAML parse OK"'
git diff --check -- bootstrap/src/main/resources/application-dev.yml docs/superpowers/plans/2026-07-29-druid-dev-pool-config.md
git diff -- bootstrap/src/main/resources/application-dev.yml
```

Expected: YAML 解析成功；`git diff --check` 无输出；差异只在现有 `spring.datasource.druid` 节点新增八个参数。

- [x] **Step 5: 重启后端并验证服务**

从 `bootstrap` 目录重新执行：

```bash
mvn spring-boot:run
```

Expected: 日志包含 `Started BranchPlatformApplication`，`18080` 端口正常监听；未登录访问 `/api/auth/current-user` 返回 `401`。

- [x] **Step 6: 提交计划和配置**

```bash
git add docs/superpowers/plans/2026-07-29-druid-dev-pool-config.md bootstrap/src/main/resources/application-dev.yml
git commit -m "chore(config): 配置 dev Druid 连接池"
```

Expected: 提交只包含计划文档和 `application-dev.yml`；不包含已有修改的 `application.yml`。

---

### Task 2: 增加并验证开发环境主动保活参数

**Files:**
- Modify: `bootstrap/src/main/resources/application-dev.yml:13`
- Modify: `docs/superpowers/plans/2026-07-29-druid-dev-pool-config.md`
- Test: `bootstrap/src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: Druid Starter 1.2.21 的 `spring.datasource.druid.keep-alive` 和 `spring.datasource.druid.keep-alive-between-time-millis` 属性绑定。
- Produces: 开发数据源主动保活已启用，同一连接两次保活检测至少间隔 120000 毫秒。

- [x] **Step 1: 运行配置存在性检查并确认当前失败**

Run:

```bash
ruby -e '
require "yaml"
druid = YAML.load_file("bootstrap/src/main/resources/application-dev.yml").dig("spring", "datasource", "druid")
expected = {
  "keep-alive" => true,
  "keep-alive-between-time-millis" => 120000
}
abort("missing Druid keep-alive settings") unless expected.all? { |key, value| druid[key] == value }
'
```

Expected: FAIL with `missing Druid keep-alive settings`。

- [x] **Step 2: 添加最小配置实现**

在 `test-while-idle` 后加入：

```yaml
      keep-alive: true
      keep-alive-between-time-millis: 120000
```

- [x] **Step 3: 重新运行配置存在性检查**

Run:

```bash
ruby -e '
require "yaml"
druid = YAML.load_file("bootstrap/src/main/resources/application-dev.yml").dig("spring", "datasource", "druid")
expected = {
  "keep-alive" => true,
  "keep-alive-between-time-millis" => 120000
}
abort("missing Druid keep-alive settings") unless expected.all? { |key, value| druid[key] == value }
puts "Druid dev keep-alive settings OK"
'
```

Expected: PASS and print `Druid dev keep-alive settings OK`。

- [x] **Step 4: 验证属性元数据、YAML、差异和作用范围**

Run:

```bash
unzip -p ~/.m2/repository/com/alibaba/druid-spring-boot-3-starter/1.2.21/druid-spring-boot-3-starter-1.2.21.jar \
  META-INF/spring-configuration-metadata.json \
  | grep -E '"spring.datasource.druid.keep-alive(-between-time-millis)?"'
ruby -e 'require "yaml"; YAML.load_file("bootstrap/src/main/resources/application-dev.yml"); puts "YAML parse OK"'
git diff --check -- bootstrap/src/main/resources/application-dev.yml docs/superpowers/plans/2026-07-29-druid-dev-pool-config.md
git diff -- bootstrap/src/main/resources/application-dev.yml
```

Expected: 元数据包含两个属性；YAML 解析成功；`git diff --check` 无输出；配置差异只新增两个主动保活参数。

- [x] **Step 5: 提交计划和配置**

```bash
git add docs/superpowers/plans/2026-07-29-druid-dev-pool-config.md bootstrap/src/main/resources/application-dev.yml
git commit -m "chore(config): 启用 dev Druid 连接保活"
```

Expected: 提交只包含计划文档和 `application-dev.yml`，不包含工作区其他已有改动。
