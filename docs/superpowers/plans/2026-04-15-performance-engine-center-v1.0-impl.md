# performance-engine-center V1.0 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 performance-engine-center V1.0（配置与版本骨架）：13 张表 + 35 个 REST 端点 + 7 个对外 Api（21 实现 + 13 UOE 占位）+ 完整测试，可 `mvn package` 通过、bootstrap 启动、Knife4j 可联调。

**Architecture:** 单一 Maven 模块，严格分层（Controller → Facade → Service → Mapper → Entity），对外契约按 `04-对外API契约.md` 定型；子代理四阶段交付（P0 骨架串行 → P1×6 子域并行 → P2 集成收敛串行 → P3 代码审查）。

**Tech Stack:** Spring Boot 3.2.3 / JDK 17 / MyBatis 3.0.3 / MySQL 8 / Redis 6 / JUnit 5 / Mockito / AssertJ / Knife4j 4.4.0 / Lombok

**Spec 引用:** `docs/superpowers/specs/2026-04-15-performance-engine-center-v1.0-design.md`（v1.1）

---

## 0. 执行策略总览

| 阶段 | 模式 | 执行者 | 任务数 | 预期墙钟 |
|---|---|---|---|---|
| P0 骨架搭建 | 串行 | 主代理 | 18 | 2-3 h |
| P1 子域全栈 | 并行 ×6 | 6 个独立子代理 | 12-20 / 每代理 | 4-6 h |
| P2 集成收敛 | 串行 | 主代理 | 9 | 1 h |
| P3 代码审查 | 串行 | code-reviewer 子代理 | 5 | 1 h |

**TDD 绝对纪律（CLAUDE.md 红线）**：
- 每个功能按"红→绿→重构"三步独立提交
- 禁止先写实现再补测试
- code-reviewer 将审查 git 历史是否符合 TDD 节奏

**并发测试例外**：涉及多线程的 Mapper IT 必须使用 `PerformanceConcurrentTestBase`（**不含 `@Transactional`**），通过数据前缀 + `@Sql(AFTER_TEST_METHOD)` 清理（详见 spec §8.2）。

**工作目录**：`C:/Users/52140/Desktop/yiti/`（所有 bash 命令假定此为 CWD）

---

## 1. 文件结构映射

### 1.1 新建的文件（共计 ~135 个源文件 + 9 个 XML + 3 个 SQL）

**阶段 0 产出**（~35 个文件）：

```
performance-engine-center/
├── pom.xml                                       ← Task 0.2
├── CLAUDE.md                                     ← Task 0.16
└── src/
    ├── main/
    │   ├── java/com/bank/branch/platform/performance/
    │   │   ├── enums/
    │   │   │   ├── PerfErrorCode.java            ← Task 0.5
    │   │   │   ├── PerfBizType.java              ← Task 0.4
    │   │   │   ├── BaseDimEnum.java              ← Task 0.4
    │   │   │   ├── MetricLevelEnum.java          ← Task 0.4
    │   │   │   ├── CalcLogicTypeEnum.java        ← Task 0.4
    │   │   │   ├── CycleTypeEnum.java            ← Task 0.4
    │   │   │   ├── MetricStatusEnum.java         ← Task 0.4
    │   │   │   └── RunTaskStatusEnum.java        ← Task 0.4
    │   │   ├── exception/
    │   │   │   └── PerfException.java            ← Task 0.6
    │   │   ├── api/
    │   │   │   ├── MetricApi.java                ← Task 0.7
    │   │   │   ├── MetricQueryApi.java           ← Task 0.7
    │   │   │   ├── KpiApi.java                   ← Task 0.7
    │   │   │   ├── TargetApi.java                ← Task 0.7
    │   │   │   ├── PerfCalcApi.java              ← Task 0.7
    │   │   │   ├── DataTaskApi.java              ← Task 0.7
    │   │   │   ├── AllocApi.java                 ← Task 0.7
    │   │   │   └── dto/                          ← Task 0.8
    │   │   │       ├── MetricDefDTO.java
    │   │   │       ├── MetricCardDTO.java
    │   │   │       ├── EmpMetricSnapshotDTO.java
    │   │   │       ├── OrgMetricSnapshotDTO.java
    │   │   │       ├── CustMetricSnapshotDTO.java
    │   │   │       ├── KpiResultDTO.java
    │   │   │       ├── KpiSchemeDTO.java
    │   │   │       ├── KpiItemDTO.java
    │   │   │       ├── TargetPlanDTO.java
    │   │   │       ├── TargetValueDTO.java
    │   │   │       ├── PerfRunTaskDTO.java
    │   │   │       ├── CustAllocRelationDTO.java
    │   │   │       ├── AllocSummaryDTO.java
    │   │   │       ├── AllocVersionDTO.java
    │   │   │       └── cmd/
    │   │   │           └── DataTaskStatusCmd.java
    │   │   └── config/
    │   │       ├── PerformanceAutoConfiguration.java  ← Task 0.12
    │   │       ├── PerformanceMyBatisConfig.java      ← Task 0.13
    │   │       └── PerformanceRedisConfig.java        ← Task 0.14
    │   └── resources/
    │       └── sql/
    │           ├── V1_0_0__performance_ddl.sql        ← Task 0.9
    │           ├── V1_0_1__performance_resources.sql  ← Task 0.10
    │           └── V1_0_2__performance_dicts.sql      ← Task 0.11
    └── test/
        ├── java/com/bank/branch/platform/performance/support/  ← Task 0.15
        │   ├── PerfTestApp.java
        │   ├── PerformanceMapperTestBase.java
        │   ├── PerformanceConcurrentTestBase.java
        │   ├── PerformanceControllerTestBase.java
        │   ├── PerformanceServiceTestBase.java
        │   ├── TestDataBuilder.java
        │   ├── MockCurrentUserHelper.java
        │   └── TestDbCleaner.java
        └── resources/
            └── application-test.yml                    ← Task 0.15
```

**阶段 1 产出**（~100 个文件，按子代理细分见 §3）

**阶段 2 产出**（修改 bootstrap、root pom；无新文件）

---

## 2. 阶段 0：骨架搭建（主代理串行）

---

### Task 0.0: 环境探针（**先执行，再启动后续任务**）

**目的**：在编写任何 SQL 脚本前，验证外部依赖的真实表结构（PT_RESOURCE、gov_dict、既有 perf_* 表），将 plan 中的模板 SQL 替换为已验证的列名。

**Files:** 不创建文件，产出探针报告到 `/tmp/perf-env-probe.md`

- [ ] **Step 1: 检查 onepl 中是否已存在 perf_* 表**

```bash
mysql -u root -p123456 onepl -e "
SELECT table_name FROM information_schema.tables
WHERE table_schema='onepl'
  AND (table_name LIKE 'perf_%' OR table_name IN ('sys_control','cust_alloc_relation','emp_index_result','org_index_result','cust_index_result','kpi_result'))
ORDER BY table_name;" > /tmp/perf-env-probe-existing.txt
cat /tmp/perf-env-probe-existing.txt
```

**决策分支**：
- 若**全部不存在**：Task 0.9 可直接 CREATE TABLE，无需 DROP 预处理
- 若**部分已存在**：在 Task 0.9 之前加 `DROP TABLE IF EXISTS ...;`（备份后）

- [ ] **Step 2: 验证 PT_RESOURCE 实际列结构**

```bash
mysql -u root -p123456 onepl -e "DESC PT_RESOURCE;" > /tmp/perf-env-probe-ptresource.txt
cat /tmp/perf-env-probe-ptresource.txt
```

**检查点**：
- 列名是大写 `RESOURCE_ID/URL_PATH/HTTP_METHOD/BIZ_TYPE/ACTION/MODULE/STATUS` 还是小写或混合？
- 是否有 `BIZ_TYPE` 字段？（spec §6.3 使用此字段）
- `RESOURCE_ID` 长度限制（应为 20）

```bash
# 采样现有资源记录（如 auth / governance 的）查看风格
mysql -u root -p123456 onepl -e "SELECT * FROM PT_RESOURCE WHERE MODULE IN ('auth','governance','workflow') LIMIT 3\G" > /tmp/perf-env-probe-ptresource-sample.txt
cat /tmp/perf-env-probe-ptresource-sample.txt
```

- [ ] **Step 3: 验证 PT_ROLE_RESOURCE 列结构**

```bash
mysql -u root -p123456 onepl -e "DESC PT_ROLE_RESOURCE;" > /tmp/perf-env-probe-ptroleresource.txt
cat /tmp/perf-env-probe-ptroleresource.txt
```

- [ ] **Step 4: 验证字典表实际结构**

```bash
mysql -u root -p123456 onepl -e "SHOW TABLES LIKE '%dict%';" > /tmp/perf-env-probe-dict-tables.txt
cat /tmp/perf-env-probe-dict-tables.txt
```

**决策分支**：
- **双表** `gov_dict_type` + `gov_dict_item`：沿用 Task 0.11 模板
- **单表** `gov_dict`（type+code 合并）：重写 Task 0.11 SQL 为单表模式
- **其他命名**：以实际为准调整

```bash
# 对实际存在的字典表做 DESC，采样数据
mysql -u root -p123456 onepl -e "DESC gov_dict_type; DESC gov_dict_item;" 2>/dev/null || \
mysql -u root -p123456 onepl -e "DESC gov_dict;" 2>/dev/null
```

- [ ] **Step 5: 验证 PT_RESOURCE 现有 MODULE 枚举值**

```bash
mysql -u root -p123456 onepl -e "SELECT DISTINCT MODULE FROM PT_RESOURCE;" > /tmp/perf-env-probe-modules.txt
cat /tmp/perf-env-probe-modules.txt
```

Expected: 看到 `auth/governance/workflow` 等；确认 `perf` 可以作为新枚举值使用（若 MODULE 字段带长度限制或校验，需适配）。

- [ ] **Step 6: 汇总探针结果并更新 Task 0.9/0.10/0.11 模板**

根据探针结果，若发现 plan 中模板 SQL 列名/表名与实际不符，**主代理在 Task 0.9/0.10/0.11 执行前现场修正模板**，并在本 plan 对应 Task 下增加注释记录"实际使用的列名/表名"。

探针完成，可以继续 Task 0.1。

---

### Task 0.1: 创建模块目录骨架

**Files:**
- Create: `performance-engine-center/` 目录树

- [ ] **Step 1: 创建目录结构**

```bash
mkdir -p performance-engine-center/src/main/java/com/bank/branch/platform/performance/{api/dto/cmd,config,controller,facade,service,mapper,entity,enums,exception,listener}
mkdir -p performance-engine-center/src/main/resources/{mapper,sql}
mkdir -p performance-engine-center/src/test/java/com/bank/branch/platform/performance/{controller,facade,service,mapper,support}
mkdir -p performance-engine-center/src/test/resources/test-data
```

- [ ] **Step 2: 验证目录结构**

```bash
find performance-engine-center -type d | sort
```

Expected: 列出上面所有 18 个目录。

- [ ] **Step 3: Commit 空骨架**

```bash
# 暂不提交，等 pom.xml 写好后一起提交（Task 0.2）
```

---

### Task 0.2: 编写 performance-engine-center/pom.xml

**Files:**
- Create: `performance-engine-center/pom.xml`

- [ ] **Step 1: 参考 workflow-center/pom.xml 的结构**

```bash
# 读取 workflow-center/pom.xml 作为模板
cat workflow-center/pom.xml
```

- [ ] **Step 2: 编写 performance-engine-center/pom.xml**

需要声明的依赖：
- `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`（5 个 common 子模块）
- `auth-permission-center`, `system-governance-center`（2 个上游模块）
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-redis`
- `org.mybatis.spring.boot:mybatis-spring-boot-starter`
- `com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter`
- `org.projectlombok:lombok` (provided)
- `org.springframework.boot:spring-boot-starter-test` (test)
- `org.junit.jupiter:junit-jupiter-api` (test)
- `org.mockito:mockito-junit-jupiter` (test)
- `org.assertj:assertj-core` (test)

版本由 root pom `<dependencyManagement>` 统一管理。

artifactId：`performance-engine-center`
groupId：`com.bank.branch.platform`
version：继承 root

- [ ] **Step 3: 验证 pom 语法**

```bash
cd performance-engine-center && mvn help:effective-pom -q -N 2>&1 | head -20
```

Expected: 无 XML 解析错误。

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/pom.xml performance-engine-center/src
git commit -m "feat(perf): scaffold performance-engine-center module skeleton

- create directory structure per spec §2
- declare pom with common/auth/governance dependencies"
```

---

### Task 0.3: 根 pom 注册 module

**Files:**
- Modify: `pom.xml` (根目录)

- [ ] **Step 1: 读取当前根 pom 的 `<modules>` 段**

```bash
grep -n "<module>" pom.xml
```

- [ ] **Step 2: 在 `<modules>` 段追加 performance-engine-center**

在现有 modules 列表末尾（`bootstrap` 之前）添加：

```xml
        <module>performance-engine-center</module>
```

**注意**：`bootstrap` 必须在最后，因为它依赖所有业务模块。

- [ ] **Step 3: 验证根 pom 聚合构建**

```bash
mvn validate -q
```

Expected: 看到 `Reactor Build Order` 包含 performance-engine-center，位于 bootstrap 之前。

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "feat(root): register performance-engine-center in root pom modules"
```

---

### Task 0.4: 编写 7 个业务枚举

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/BaseDimEnum.java` 等 7 个

- [ ] **Step 1: 编写全部 7 个枚举**

```java
// BaseDimEnum.java
package com.bank.branch.platform.performance.enums;

/** 指标基础维度。 */
public enum BaseDimEnum {
    EMP, ORG, CUST;
    public static boolean isValid(String v) {
        if (v == null) return false;
        for (BaseDimEnum e : values()) if (e.name().equals(v)) return true;
        return false;
    }
}
```

```java
// MetricLevelEnum.java
public enum MetricLevelEnum {
    L1(1), L2(2), L3(3);
    public final int level;
    MetricLevelEnum(int level) { this.level = level; }
    public static MetricLevelEnum of(int level) {
        for (var e : values()) if (e.level == level) return e;
        throw new IllegalArgumentException("invalid metric level: " + level);
    }
}
```

```java
// CalcLogicTypeEnum.java
public enum CalcLogicTypeEnum { SQL, PROC, EXPR, SUMMARY }
```

```java
// CycleTypeEnum.java
public enum CycleTypeEnum { MONTHLY, QUARTERLY, YEARLY }
```

```java
// MetricStatusEnum.java
public enum MetricStatusEnum { DRAFT, PUBLISHED, DISABLED }
```

```java
// RunTaskStatusEnum.java
public enum RunTaskStatusEnum { PENDING, RUNNING, SUCCESS, FAILED, CANCELLED }
```

```java
// PerfBizType.java
public enum PerfBizType {
    PERF_METRIC_CONFIG, PERF_KPI_CONFIG, PERF_TARGET_CONFIG,
    PERF_TARGET_VALUE, PERF_ALLOC_QUERY, PERF_RUN_TASK_QUERY, PERF_SYS_CONTROL;
}
```

- [ ] **Step 2: 编译验证**

```bash
cd performance-engine-center && mvn compile -q
```

Expected: BUILD SUCCESS，无编译错误。

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums
git commit -m "feat(perf): add 7 business enums (BaseDim/MetricLevel/CalcLogic/Cycle/MetricStatus/RunTaskStatus/PerfBizType)"
```

---

### Task 0.5: 编写 PerfErrorCode

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java`

- [ ] **Step 1: 先看 common-web 的 ErrorCode 接口**

```bash
find common -name "ErrorCode.java" | xargs grep -l "interface ErrorCode"
```

- [ ] **Step 2: 编写 PerfErrorCode**

```java
package com.bank.branch.platform.performance.enums;

import com.bank.branch.platform.common.web.error.ErrorCode;

/**
 * performance-engine-center 模块错误码定义.
 *
 * <p>格式: PERF-{HTTP_STATUS_LAST_TWO}{SEQ_3DIGIT}
 */
public enum PerfErrorCode implements ErrorCode {

    // 400 参数校验
    PARAM_INVALID("PERF-40001", "请求参数非法: %s"),
    PAGE_OUT_OF_RANGE("PERF-40002", "分页参数越界"),

    // 404 资源不存在
    METRIC_NOT_FOUND("PERF-40401", "指标不存在: %s"),
    KPI_SCHEME_NOT_FOUND("PERF-40402", "KPI方案不存在: %s"),
    TARGET_PLAN_NOT_FOUND("PERF-40403", "目标方案不存在: %s"),

    // 409 冲突
    METRIC_SLOT_CONFLICT("PERF-40901", "槽位已被占用: baseDim=%s, slot=%d"),
    METRIC_CYCLE_DETECTED("PERF-40902", "指标引用形成环路: %s"),
    METRIC_CODE_DUP("PERF-40903", "指标编码已存在: %s"),
    SYS_CONTROL_CONFLICT("PERF-40904", "版本切换并发冲突"),
    INVALID_STATE("PERF-40905", "当前状态不允许此操作: %s"),
    PLAN_NOT_PUBLISHED("PERF-40906", "方案未发布不可绑定目标: %s"),
    TARGET_BATCH_TOO_BIG("PERF-40910", "批量目标值最多500条, 当前: %d"),
    METRIC_LEVEL_INVALID("PERF-40911", "指标层级引用违规: %s"),

    // 500 服务端错误
    INTERNAL_ERROR("PERF-50001", "未预期的服务端错误"),
    DOWNSTREAM_ERROR("PERF-50002", "下游依赖异常: %s");

    private final String code;
    private final String messageTemplate;

    PerfErrorCode(String code, String messageTemplate) {
        this.code = code;
        this.messageTemplate = messageTemplate;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return messageTemplate; }

    /** 格式化消息, 填充占位符. */
    public String format(Object... args) {
        return args.length == 0 ? messageTemplate : String.format(messageTemplate, args);
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd performance-engine-center && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java
git commit -m "feat(perf): add PerfErrorCode enum with 15 error codes per spec §7.1"
```

---

### Task 0.6: 编写 PerfException

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/exception/PerfException.java`

- [ ] **Step 1: 查看 common-web BizException**

```bash
find common -name "BizException.java"
```

- [ ] **Step 2: 编写 PerfException**

```java
package com.bank.branch.platform.performance.exception;

import com.bank.branch.platform.common.web.error.BizException;
import com.bank.branch.platform.performance.enums.PerfErrorCode;

/** performance 模块统一业务异常. */
public class PerfException extends BizException {

    private final PerfErrorCode errorCode;

    public PerfException(PerfErrorCode errorCode, Object... args) {
        super(errorCode.getCode(), errorCode.format(args));
        this.errorCode = errorCode;
    }

    public PerfException(PerfErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.getCode(), errorCode.format(args), cause);
        this.errorCode = errorCode;
    }

    public PerfErrorCode getErrorCode() { return errorCode; }
}
```

- [ ] **Step 3: 编译验证 + commit**

```bash
cd performance-engine-center && mvn compile -q
cd .. && git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/exception
git commit -m "feat(perf): add PerfException extending common BizException"
```

---

### Task 0.7: 定义 7 个对外 Api 接口文件

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/*.java` (7 个)

对应 spec §5.2.1-5.2.7，严格按 `docs/modules/performance-engine-center/04-对外API契约.md` 的权威签名。

- [ ] **Step 1: 编写 MetricApi.java**

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.MetricCardDTO;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 指标查询对外 API.
 * <p>V1.0 实现: getMetricDef / getMetricDefs / listMetrics (配置查询).
 * <p>V1.1 实现: getUserMetricCards / getEmpMetricValues / getOrgMetricValues / getCustMetricValues (结果查询).
 */
public interface MetricApi {
    List<MetricCardDTO> getUserMetricCards(String empId);
    Optional<MetricDefDTO> getMetricDef(String metricCode);
    List<MetricDefDTO> getMetricDefs(List<String> metricCodes);
    List<MetricDefDTO> listMetrics(String baseDim, Integer metricLevel);
    Map<String, BigDecimal> getEmpMetricValues(String empId, LocalDate dataDate, List<String> metricCodes);
    Map<String, BigDecimal> getOrgMetricValues(String orgCode, LocalDate dataDate, List<String> metricCodes);
    Map<String, BigDecimal> getCustMetricValues(String custId, LocalDate dataDate, List<String> metricCodes);
}
```

- [ ] **Step 2: 编写 MetricQueryApi.java**

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.CustMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.OrgMetricSnapshotDTO;
import java.time.LocalDate;
import java.util.List;

/** 报表模块专用的指标批量查询 API. V1.0 全部方法抛 UnsupportedOperationException. */
public interface MetricQueryApi {
    List<EmpMetricSnapshotDTO> batchQueryEmpSnapshots(List<String> empIds, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);
    List<OrgMetricSnapshotDTO> batchQueryOrgSnapshots(List<String> orgCodes, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);
    List<CustMetricSnapshotDTO> batchQueryCustSnapshots(List<String> custIds, LocalDate dateFrom, LocalDate dateTo, List<String> metricCodes);
}
```

- [ ] **Step 3: 编写 KpiApi.java**

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.KpiResultDTO;
import com.bank.branch.platform.performance.api.dto.KpiSchemeDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** KPI 查询对外 API. V1.0 实现: getKpiScheme / getKpiSchemeById. */
public interface KpiApi {
    BigDecimal getCurrentKpiTotal(String empId, String cycleType);
    Optional<KpiResultDTO> getCurrentKpiResult(String empId, String cycleType);
    List<KpiResultDTO> getKpiHistory(String empId, String cycleType, LocalDate from, LocalDate to);
    Optional<KpiSchemeDTO> getKpiScheme(String schemeCode);
    Optional<KpiSchemeDTO> getKpiSchemeById(String schemeId);  // v1.2: String 对齐 DDL
}
```

- [ ] **Step 4: 编写 TargetApi.java**（**v1.2：planId 统一 String**）

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 目标查询对外 API. V1.0 全部实现. planId 为 String (v1.2 对齐生产 DDL varchar(32)). */
public interface TargetApi {
    Optional<TargetPlanDTO> getTargetPlan(String planCode);
    Optional<TargetPlanDTO> getTargetPlanById(String planId);
    Optional<BigDecimal> getTargetValue(String planId, String subjectType, String subjectId, String cycleKey, String metricCode);
    List<TargetValueDTO> listTargetValues(String planId, String subjectType, String subjectId, String cycleKey);
}
```

- [ ] **Step 5: 编写 PerfCalcApi.java**

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.PerfRunTaskDTO;
import java.time.LocalDate;
import java.util.Optional;

/** 绩效计算触发对外 API. V1.0 实现: getRunTask. */
public interface PerfCalcApi {
    String triggerKpiCalc(LocalDate dataDate);
    String triggerRecalc(String cycleType, LocalDate from, LocalDate to, String reason, String operator);
    Optional<PerfRunTaskDTO> getRunTask(String taskId);
}
```

- [ ] **Step 6: 编写 DataTaskApi.java**

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.cmd.DataTaskStatusCmd;

/** 外部数据任务状态上报 API. V1.0 抛 UnsupportedOperationException. */
public interface DataTaskApi {
    void reportDataTaskStatus(DataTaskStatusCmd cmd);
}
```

- [ ] **Step 7: 编写 AllocApi.java**（**10 方法**，V1.0 全部实现；与 04 契约精确一致）

```java
package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.AllocSummaryDTO;
import com.bank.branch.platform.performance.api.dto.AllocVersionDTO;
import com.bank.branch.platform.performance.api.dto.CustAllocRelationDTO;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 客户业绩分配关系查询 API. V1.0 全部只读查询. */
public interface AllocApi {
    List<CustAllocRelationDTO> getCurrentAllocations(String custId, String bizKind);
    List<CustAllocRelationDTO> getAllocationHistory(String custId, LocalDate asOfDate);
    List<CustAllocRelationDTO> listCustomersByEmp(String empId, String bizKind);
    Map<String, List<CustAllocRelationDTO>> batchGetCurrentAllocations(Set<String> custIds, String bizKind);
    Map<String, Long> countCustomersByEmps(Set<String> empIds);
    List<AllocSummaryDTO> batchSummaryByEmps(Set<String> empIds, String bizKind, LocalDate asOfDate);
    boolean hasAllocation(String empId, String custId, String bizKind);
    long countCustomersOfEmp(String empId, String bizKind);
    AllocVersionDTO getLatestAllocVersion(String bizKind);
    AllocVersionDTO getAllocVersionAt(String bizKind, LocalDate asOfDate);
}
```

- [ ] **Step 8: 跳过编译，先完成 DTO 定义（Task 0.8），再统一编译**

**重要**：本 Task 先只创建 Api 接口文件，不执行编译（会因 DTO 缺失失败）。必须先完成 Task 0.8 的全部 DTO，再在 Task 0.8 Step 3 做统一编译验证。

本 Task 不独立 commit，与 Task 0.8 合并为单次提交。

---

### Task 0.8: 定义 15 个 DTO 类（完成后与 Task 0.7 统一编译+提交）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/dto/*.java` (14 个 DTO + 1 个 cmd)

- [ ] **Step 1: 编写 MetricDefDTO.java**（按 04 契约 §8.1）

```java
package com.bank.branch.platform.performance.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetricDefDTO {
    private String metricCode;
    private String metricName;
    private String metricNameEn;
    private String description;
    private String baseDim;
    private Integer metricLevel;
    private String calcFreq;
    private String calcMode;
    private String calcLogicType;
    private Integer valSlot;
    private String status;
    private String ownerDept;
    private Integer version;
}
```

- [ ] **Step 2: 编写其余 DTO**

按 spec §2.2 与 04 契约定义，使用 `@Data @Builder` 模式。以下为字段清单（代码由子代理/主代理按规范补齐）：

**MetricCardDTO**: metricCode, metricName, currentValue(BigDecimal), previousValue, targetValue, baseValue, achievementRate, unit, sortNo, dataDate(LocalDate)

**EmpMetricSnapshotDTO**: empId, dataDate, version, metricValues(Map<String, BigDecimal>)

**OrgMetricSnapshotDTO**: orgCode, dataDate, version, metricValues(Map)

**CustMetricSnapshotDTO**: custId, dataDate, version, metricValues(Map)

**KpiResultDTO**: id(Long), empId, empName, cycleType, cycleDate, asOfDate, dataVersion, schemeCode, schemeName, kpiTotalScore(BigDecimal), detailJson, createTime(LocalDateTime) — **kpi_result 是宽表，id 保持 Long**

**KpiSchemeDTO**: id(String), schemeCode, schemeName, cycleType, openDetail(Boolean), status, version, items(List<KpiItemDTO>) — v1.2: id String

**KpiItemDTO**: id(String), metricCode, metricName, weight, multiplier, minScore, maxScore, sortNo — v1.2: id String

**TargetPlanDTO**: id(String), planCode, planName, kpiSchemeId(String), targetDim, targetCycle, effectiveDate, expireDate, status — v1.2: id/kpiSchemeId 统一 String

**TargetValueDTO**: id(String), planId(String), subjectType, subjectId, cycleKey, metricCode, targetValue(BigDecimal), baseValue(BigDecimal) — v1.2: planId String 对齐 DDL

**PerfRunTaskDTO**: id(String), taskNo, taskType, dataDate, status, totalCount, successCount, errorCount, startedBy, createdTime, durationMs, errorMsg — v1.2: id String

**CustAllocRelationDTO**: id(String), custId, custName, allocDim, bizKind, accountNo, empId, empName, allocRatio, allocAmount, effectiveDate, expireDate — v1.2: id String

**AllocSummaryDTO**: empId, empName, orgCode, bizKind, custCount(Long), totalAllocAmount(BigDecimal), avgAllocRatio(BigDecimal), asOfDate, sysControlVersion

**AllocVersionDTO**: bizKind, scopeDim, currentVersion, latestDataDate, publishedAt(LocalDateTime), publishedBy

**DataTaskStatusCmd** (api/dto/cmd/)：taskId, dataType, dataDate, version, status, rowCount(Integer), errorMsg, sourceSystem, reportedAt(Instant)

- [ ] **Step 3: 编译验证**

```bash
cd performance-engine-center && mvn compile -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 4: Commit（合并 Task 0.7 + 0.8）**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/api
git commit -m "feat(perf): define 7 public Apis + 15 DTOs per spec §5.2 (aligned with 04-对外API契约.md)"
```

---

### Task 0.9: 编写 V1_0_0__performance_ddl.sql（v1.2：基线副本，不做调整）

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_0_0__performance_ddl.sql`

**v1.2 决策**：基于环境探针确认 onepl 库中 13 张表已存在且来自权威 DDL，本脚本不做任何结构变更，仅作为新环境部署基线副本。

- [ ] **Step 1: 复制权威 DDL 作为基线**

```bash
cp docs/schema/ddl-performance.sql performance-engine-center/src/main/resources/sql/V1_0_0__performance_ddl.sql
```

- [ ] **Step 2: 文件头添加注释说明**

在 `V1_0_0__performance_ddl.sql` 顶部插入：

```sql
-- =====================================================================
-- performance-engine-center V1.0 DDL Script (baseline copy, v1.2)
-- Version: V1_0_0
-- Date: 2026-04-15
-- Source: docs/schema/ddl-performance.sql (unchanged - verified against onepl)
--
-- v1.2 note:
--   This script is a baseline copy of the authoritative schema.
--   For existing onepl database (where 13 tables are already deployed),
--   this script is effectively a no-op (relies on CREATE TABLE IF NOT EXISTS).
--   For fresh environments, it creates the 13 core tables.
--
--   No structural changes applied:
--   - perf_target_plan.id stays varchar(32) (aligned with TargetApi String planId)
--   - No `deleted` column added (logical delete by status='DISABLED')
--   - UKs remain as in authoritative DDL
--   - Slot uniqueness enforced by Redis lock + Service check (not DB UK)
-- =====================================================================
```

若权威 DDL 不包含 `IF NOT EXISTS`，可**选择性**添加该子句以提高幂等性（不强制）。

- [ ] **Step 3: 验证 onepl 当前表结构可用**

```bash
mysql -u root -p123456 onepl -e "
SELECT COUNT(*) AS cnt FROM information_schema.tables
WHERE table_schema='onepl' AND (
  table_name IN ('sys_control','perf_metric_def','perf_metric_ref','perf_kpi_scheme','perf_kpi_item',
                 'perf_target_plan','perf_target_value','perf_run_task','cust_alloc_relation',
                 'emp_index_result','org_index_result','cust_index_result','kpi_result'));"
```

Expected: `cnt = 13`（本次探针已确认）。

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/main/resources/sql/V1_0_0__performance_ddl.sql
git commit -m "feat(perf): add V1_0_0 DDL baseline (copy of authoritative ddl-performance.sql, v1.2 no-op for onepl)"
```

---

### Task 0.10: 编写 V1_0_1__performance_resources.sql（v1.2：按实际 PT_RESOURCE 列）

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_0_1__performance_resources.sql`

**实际 PT_RESOURCE 列结构**（环境探针已确认）：
- `RESOURCE_ID varchar(20)` / `RESOURCE_URL varchar(256)` / `RESOURCE_METHOD varchar(10)` / `MENU_NAME varchar(256)`
- `MENU_ICON_URL / MENU_RANK_NO / ISMENU / MENU_ENDFLAG / PARENT_RESOURCE_ID`
- `STATUS int`（0=启用）/ `SYS_CODE varchar(10)`（用 `PERF` 表示模块）
- `CREATE_TIME/CREATE_USER/UPDATE_TIME/UPDATE_USER/REMARK`
- **无 BIZ_TYPE/ACTION 字段**（BizType 存 `pt_role_biz_scope`，Action 是 @BizAuth 注解参数不入库）

- [ ] **Step 1: 编写 PT_RESOURCE INSERT（35 条）**

```sql
-- =====================================================================
-- performance-engine-center V1.0 Resources Registration (v1.2)
-- Version: V1_0_1
-- 35 REST endpoints, prefix P_PERF_*, ID length <= 20
-- Schema: see PT_RESOURCE actual columns (no BIZ_TYPE/ACTION/MODULE — use SYS_CODE)
-- =====================================================================

INSERT INTO pt_resource (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO, ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
-- MetricDef (10)
('P_PERF_METRIC_LIST', '/api/perf/metrics',                      'GET',    '指标列表',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_GET',  '/api/perf/metrics/*',                    'GET',    '指标详情',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_ADD',  '/api/perf/metrics',                      'POST',   '新增指标',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_UPD',  '/api/perf/metrics/*',                    'PUT',    '编辑指标',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_DEL',  '/api/perf/metrics/*',                    'DELETE', '删除指标',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_STAT', '/api/perf/metrics/*/status',             'PUT',    '指标状态流转',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_REFS', '/api/perf/metrics/*/refs',               'GET',    '查指标上游依赖', NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_RBY',  '/api/perf/metrics/*/ref-by',             'GET',    '查谁引用了我',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_SLOT', '/api/perf/metrics/val-slots',            'GET',    '槽位占用查询',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_METRIC_SREL', '/api/perf/metrics/*/slot/release',       'POST',   '强制释放槽位',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- KpiScheme (9)
('P_PERF_KPI_LIST',    '/api/perf/kpi-schemes',                  'GET',    'KPI方案列表',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_GET',     '/api/perf/kpi-schemes/*',                'GET',    'KPI方案详情',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_ADD',     '/api/perf/kpi-schemes',                  'POST',   '新增KPI方案',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_UPD',     '/api/perf/kpi-schemes/*',                'PUT',    '编辑KPI方案',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_DEL',     '/api/perf/kpi-schemes/*',                'DELETE', '删除KPI方案',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_PUB',     '/api/perf/kpi-schemes/*/publish',        'POST',   '发布KPI方案',    NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_IADD',    '/api/perf/kpi-schemes/*/items',          'POST',   '添加指标项',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_IUPD',    '/api/perf/kpi-schemes/*/items/*',        'PUT',    '编辑指标项',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_KPI_IDEL',    '/api/perf/kpi-schemes/*/items/*',        'DELETE', '删除指标项',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- TargetPlan (4)
('P_PERF_TGT_P_LIST',  '/api/perf/target-plans',                 'GET',    '目标方案列表',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_P_GET',   '/api/perf/target-plans/*',               'GET',    '目标方案详情',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_P_ADD',   '/api/perf/target-plans',                 'POST',   '新增目标方案',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_P_UPD',   '/api/perf/target-plans/*',               'PUT',    '编辑目标方案',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- TargetValue (3)
('P_PERF_TGT_V_LIST',  '/api/perf/target-values',                'GET',    '目标值查询',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_V_ADD',   '/api/perf/target-values',                'POST',   '目标值upsert',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_TGT_V_BAT',   '/api/perf/target-values/batch',          'POST',   '目标值批量',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- Alloc (3)
('P_PERF_ALLOC_CUR',   '/api/perf/alloc-relations',              'GET',    '当前分配关系',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_ALLOC_HIS',   '/api/perf/alloc-relations/history',      'GET',    '历史分配关系',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_ALLOC_SUM',   '/api/perf/alloc-relations/summary',      'GET',    '分配关系汇总',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- RunTask (2)
('P_PERF_RT_LIST',     '/api/perf/run-tasks',                    'GET',    '任务日志列表',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_RT_GET',      '/api/perf/run-tasks/*',                  'GET',    '任务日志详情',   NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
-- SysControl (4)
('P_PERF_SC_GET',      '/api/perf/sys-control',                  'GET',    '版本查询',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_SC_HIS',      '/api/perf/sys-control/history',          'GET',    '版本历史',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_SC_INIT',     '/api/perf/sys-control/init',             'POST',   '版本初始化',     NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0'),
('P_PERF_SC_SW',       '/api/perf/sys-control/switch-version',   'POST',   '版本切换',       NULL, 0, 0, '0', NULL, 0, 'PERF', NOW(), 'seed', 'v1.0')
ON DUPLICATE KEY UPDATE
  RESOURCE_URL    = VALUES(RESOURCE_URL),
  RESOURCE_METHOD = VALUES(RESOURCE_METHOD),
  MENU_NAME       = VALUES(MENU_NAME),
  STATUS          = VALUES(STATUS),
  UPDATE_TIME     = NOW(),
  UPDATE_USER     = 'seed',
  REMARK          = VALUES(REMARK);

-- 授权 R_ADMIN 和 R_BACK_TECH 对 PERF 模块 35 条资源的访问
-- pt_role_resource 列: ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME
INSERT INTO pt_role_resource (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT CONCAT(r.ROLE_ID, '_', res.RESOURCE_ID) AS ID, r.ROLE_ID, res.RESOURCE_ID, 'PERF', NOW()
FROM (SELECT 'R_ADMIN' AS ROLE_ID UNION ALL SELECT 'R_BACK_TECH') r
CROSS JOIN pt_resource res
WHERE res.SYS_CODE = 'PERF' AND res.RESOURCE_ID LIKE 'P_PERF_%'
ON DUPLICATE KEY UPDATE CREATE_TIME = CREATE_TIME;

-- BizType 数据范围配置 (pt_role_biz_scope): 7 个 BizType × 2 个管理角色 = 14 条
-- R_ADMIN / R_BACK_TECH 全部 ALL 范围
INSERT INTO pt_role_biz_scope (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_TIME, CREATE_USER, REMARK)
VALUES
(UUID(), 'R_ADMIN',      'PERF_METRIC_CONFIG',   'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_ADMIN',      'PERF_KPI_CONFIG',      'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_ADMIN',      'PERF_TARGET_CONFIG',   'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_ADMIN',      'PERF_TARGET_VALUE',    'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_ADMIN',      'PERF_ALLOC_QUERY',     'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_ADMIN',      'PERF_RUN_TASK_QUERY',  'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_ADMIN',      'PERF_SYS_CONTROL',     'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_BACK_TECH',  'PERF_METRIC_CONFIG',   'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_BACK_TECH',  'PERF_KPI_CONFIG',      'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_BACK_TECH',  'PERF_TARGET_CONFIG',   'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_BACK_TECH',  'PERF_TARGET_VALUE',    'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_BACK_TECH',  'PERF_ALLOC_QUERY',     'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_BACK_TECH',  'PERF_RUN_TASK_QUERY',  'ALL', 0, NOW(), 'seed', 'perf v1.0'),
(UUID(), 'R_BACK_TECH',  'PERF_SYS_CONTROL',     'ALL', 0, NOW(), 'seed', 'perf v1.0');
```

- [ ] **Step 2: 本地执行并幂等验证**

```bash
mysql -u root -p123456 onepl < performance-engine-center/src/main/resources/sql/V1_0_1__performance_resources.sql
mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM pt_resource WHERE SYS_CODE='PERF';"
```

Expected: 35。

```bash
mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM pt_role_biz_scope WHERE BIZ_TYPE LIKE 'PERF_%';"
```

Expected: 14（7 BizType × 2 管理角色）。

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/src/main/resources/sql/V1_0_1__performance_resources.sql
git commit -m "feat(perf): add V1_0_1 resources registration (35 P_PERF_* + 14 biz_scope; align with actual pt_resource schema)"
```

---

### Task 0.11: 编写 V1_0_2__performance_dicts.sql（v1.2：sys_dict + sys_dict_item 实际列）

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_0_2__performance_dicts.sql`

**实际字典表**（环境探针已确认）：
- `sys_dict`: id / dict_type / dict_code / dict_label / dict_value / sort_order / status / remark / 审计字段
- `sys_dict_item`: id / dict_type / item_code / item_label / item_value / sort_order / status / remark / 审计字段

**使用约定**（与 governance 保持一致）：
- `sys_dict` 存 **字典类型元数据**（dict_type 作为类型 ID，dict_code 对应字典类型中文名）
- `sys_dict_item` 存 **字典项**（每个 type 下的具体值）

- [ ] **Step 1: 编写 SQL**

```sql
-- =====================================================================
-- performance-engine-center V1.0 Dictionary Seed (v1.2)
-- Version: V1_0_2
-- 10 dict types (PERF_*) + items
-- Tables: sys_dict (types) + sys_dict_item (items)
-- =====================================================================

('PERF_DICT_001', 'PERF_BASE_DIM',          'PERF_BASE_DIM',          '指标维度',       'EMP/ORG/CUST',             1,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_002', 'PERF_METRIC_LEVEL',      'PERF_METRIC_LEVEL',      '指标级次',       '1/2/3',                    2,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_003', 'PERF_METRIC_CALC_LOGIC', 'PERF_METRIC_CALC_LOGIC', '计算逻辑类型',   'SQL/PROC/EXPR/SUMMARY',    3,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_004', 'PERF_CALC_FREQ',         'PERF_CALC_FREQ',         '计算频率',       'DAY/MONTH/QUARTER/YEAR',   4,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_005', 'PERF_CYCLE_TYPE',        'PERF_CYCLE_TYPE',        '考核周期类型',   'MONTHLY/QUARTERLY/YEARLY', 5,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_006', 'PERF_TASK_TYPE',         'PERF_TASK_TYPE',         '任务类型',       'METRIC_RUN/KPI_RUN/...',   6,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_007', 'PERF_TASK_STATUS',       'PERF_TASK_STATUS',       '任务状态',       'PENDING/RUNNING/...',      7,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_008', 'PERF_METRIC_STATUS',     'PERF_METRIC_STATUS',     '指标状态',       'DRAFT/PUBLISHED/DISABLED', 8,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_009', 'PERF_APPLY_STATUS',      'PERF_APPLY_STATUS',      '申请状态',       '(V1.2使用)',               9,  'ACTIVE', 'perf v1.0', 'seed', NOW()),
('PERF_DICT_010', 'PERF_ALLOC_DIM',         'PERF_ALLOC_DIM',         '分配维度',       'RULE/ACCOUNT',             10, 'ACTIVE', 'perf v1.0', 'seed', NOW())
ON DUPLICATE KEY UPDATE dict_label=VALUES(dict_label), updated_time=NOW();

-- 字典项（sys_dict_item）
INSERT INTO sys_dict_item (id, dict_type, item_code, item_label, item_value, sort_order, status, remark, created_by, created_time) VALUES
-- PERF_BASE_DIM
(UUID(), 'PERF_BASE_DIM',          'EMP',          '员工',        'EMP',          1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_BASE_DIM',          'ORG',          '机构',        'ORG',          2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_BASE_DIM',          'CUST',         '客户',        'CUST',         3,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_METRIC_LEVEL
(UUID(), 'PERF_METRIC_LEVEL',      '1',            '一级',        '1',            1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_METRIC_LEVEL',      '2',            '二级',        '2',            2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_METRIC_LEVEL',      '3',            '三级',        '3',            3,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_METRIC_CALC_LOGIC
(UUID(), 'PERF_METRIC_CALC_LOGIC', 'SQL',          'SQL查询',     'SQL',          1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_METRIC_CALC_LOGIC', 'PROC',         '存储过程',    'PROC',         2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_METRIC_CALC_LOGIC', 'EXPR',         '表达式',      'EXPR',         3,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_METRIC_CALC_LOGIC', 'SUMMARY',      '汇总规则',    'SUMMARY',      4,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_CALC_FREQ
(UUID(), 'PERF_CALC_FREQ',         'DAY',          '日',          'DAY',          1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_CALC_FREQ',         'MONTH',        '月',          'MONTH',        2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_CALC_FREQ',         'QUARTER',      '季',          'QUARTER',      3,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_CALC_FREQ',         'YEAR',         '年',          'YEAR',         4,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_CYCLE_TYPE
(UUID(), 'PERF_CYCLE_TYPE',        'MONTHLY',      '月度',        'MONTHLY',      1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_CYCLE_TYPE',        'QUARTERLY',    '季度',        'QUARTERLY',    2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_CYCLE_TYPE',        'YEARLY',       '年度',        'YEARLY',       3,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_METRIC_STATUS
(UUID(), 'PERF_METRIC_STATUS',     'DRAFT',        '草稿',        'DRAFT',        1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_METRIC_STATUS',     'PUBLISHED',    '已发布',      'PUBLISHED',    2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_METRIC_STATUS',     'DISABLED',     '已停用',      'DISABLED',     3,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_TASK_TYPE
(UUID(), 'PERF_TASK_TYPE',         'METRIC_TRIAL', '指标试运行',  'METRIC_TRIAL', 1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_TASK_TYPE',         'METRIC_RUN',   '指标计算',    'METRIC_RUN',   2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_TASK_TYPE',         'KPI_RUN',      'KPI计算',     'KPI_RUN',      3,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_TASK_TYPE',         'RECALC',       '历史回算',    'RECALC',       4,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_TASK_TYPE',         'DATA_IMPORT',  '数据导入',    'DATA_IMPORT',  5,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_TASK_STATUS
(UUID(), 'PERF_TASK_STATUS',       'PENDING',      '待执行',      'PENDING',      1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_TASK_STATUS',       'RUNNING',      '执行中',      'RUNNING',      2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_TASK_STATUS',       'SUCCESS',      '成功',        'SUCCESS',      3,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_TASK_STATUS',       'FAILED',       '失败',        'FAILED',       4,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_TASK_STATUS',       'CANCELLED',    '已取消',      'CANCELLED',    5,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_APPLY_STATUS (V1.2 用, 先预置)
(UUID(), 'PERF_APPLY_STATUS',      'DRAFT',        '草稿',        'DRAFT',        1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_APPLY_STATUS',      'IN_APPROVAL',  '审批中',      'IN_APPROVAL',  2,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_APPLY_STATUS',      'APPROVED',     '审批通过',    'APPROVED',     3,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_APPLY_STATUS',      'REJECTED',     '已驳回',      'REJECTED',     4,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_APPLY_STATUS',      'CANCELLED',    '已撤回',      'CANCELLED',    5,  'ACTIVE', NULL, 'seed', NOW()),
-- PERF_ALLOC_DIM
(UUID(), 'PERF_ALLOC_DIM',         'RULE',         '规则维度',    'RULE',         1,  'ACTIVE', NULL, 'seed', NOW()),
(UUID(), 'PERF_ALLOC_DIM',         'ACCOUNT',      '账号维度',    'ACCOUNT',      2,  'ACTIVE', NULL, 'seed', NOW())
ON DUPLICATE KEY UPDATE item_label=VALUES(item_label), updated_time=NOW();
```

**注意**：`sys_dict_item` 的 UK 推测为 `(dict_type, item_code)`；若实际不是 UK 则 `ON DUPLICATE KEY UPDATE` 可能不生效，后续可改为先 DELETE 再 INSERT 模式。

- [ ] **Step 2: 本地执行**

```bash
mysql -u root -p123456 onepl < performance-engine-center/src/main/resources/sql/V1_0_2__performance_dicts.sql
```

- [ ] **Step 3: 验证**

```bash
mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM sys_dict WHERE dict_type LIKE 'PERF_%';"
mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM sys_dict_item WHERE dict_type LIKE 'PERF_%';"
```

Expected: `sys_dict` 10 条，`sys_dict_item` 39 条（3+3+4+4+3+3+5+5+5+2+2=39）。

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/main/resources/sql/V1_0_2__performance_dicts.sql
git commit -m "feat(perf): add V1_0_2 dictionary seed (10 types + 39 items in sys_dict/sys_dict_item)"
```

---

### Task 0.12-0.14: 编写 3 个 Spring 配置类

- [ ] **Step 1: PerformanceAutoConfiguration.java**

```java
package com.bank.branch.platform.performance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.bank.branch.platform.performance")
@EnableConfigurationProperties
public class PerformanceAutoConfiguration {}
```

- [ ] **Step 2: PerformanceMyBatisConfig.java**

```java
package com.bank.branch.platform.performance.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.bank.branch.platform.performance.mapper")
public class PerformanceMyBatisConfig {}
```

- [ ] **Step 3: PerformanceRedisConfig.java**

```java
package com.bank.branch.platform.performance.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** 启用 Spring Cache (Redis 后端由 bootstrap 配置). */
@Configuration
@EnableCaching
public class PerformanceRedisConfig {}
```

- [ ] **Step 4: 编译验证 + commit**

```bash
cd performance-engine-center && mvn compile -q
cd .. && git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/config
git commit -m "feat(perf): add 3 Spring config classes (AutoConfig/MyBatis/Redis)"
```

---

### Task 0.15: 编写测试基础设施

**Files:**
- Create: 8 个测试支撑文件

- [ ] **Step 1: PerfTestApp.java**

```java
package com.bank.branch.platform.performance.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.bank.branch.platform.performance")
public class PerfTestApp {
    public static void main(String[] args) { SpringApplication.run(PerfTestApp.class, args); }
}
```

- [ ] **Step 2: PerformanceMapperTestBase.java**（单线程 IT，含事务回滚）

```java
package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@ExtendWith(SpringExtension.class)
@MybatisTest
@AutoConfigureTestDatabase(replace = NONE)
@SpringBootTest(classes = PerfTestApp.class)
@ActiveProfiles("test")
@Transactional
@Rollback(true)
public abstract class PerformanceMapperTestBase {}
```

- [ ] **Step 3: PerformanceConcurrentTestBase.java**（并发 IT，**不含 @Transactional**）

```java
package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** 并发 IT 基类: 不使用 @Transactional（Spring 事务与多线程不兼容）.
 *  测试数据必须以 CONCUR_<子代理>_* 前缀命名, 由 @AfterEach 或 @Sql(AFTER_TEST_METHOD) 清理. */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PerfTestApp.class)
@ActiveProfiles("test")
public abstract class PerformanceConcurrentTestBase {}
```

- [ ] **Step 4: PerformanceControllerTestBase.java**

```java
package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = PerfTestApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Rollback(true)
public abstract class PerformanceControllerTestBase {
    @Autowired protected MockMvc mockMvc;
}
```

- [ ] **Step 5: PerformanceServiceTestBase.java**（纯 UT，无 Spring）

```java
package com.bank.branch.platform.performance.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class PerformanceServiceTestBase {}
```

- [ ] **Step 6: TestDataBuilder.java（分子代理私有，避免共享冲突）**

**重要**：为避免 6 个并行子代理同时修改 `TestDataBuilder.java` 引发 git 冲突，采用**每子代理独立 Builder 类**的模式：
- P1-A 创建 `SysControlTestDataBuilder.java`
- P1-B 创建 `MetricTestDataBuilder.java`
- P1-C 创建 `KpiTestDataBuilder.java`
- P1-D 创建 `TargetTestDataBuilder.java`
- P1-E 创建 `RunTaskTestDataBuilder.java`
- P1-F 创建 `AllocTestDataBuilder.java`

骨架阶段仅创建**空接口父类**（供未来统一接入）：

```java
package com.bank.branch.platform.performance.support;

/** 测试数据 Builder 标记接口（空接口，供各子代理扩展）.
 *  各子代理在 test/support/ 下创建自己的 XxxTestDataBuilder 实现类, 避免共享冲突. */
public interface TestDataBuilder {
}
```

- [ ] **Step 7: MockCurrentUserHelper.java**

```java
package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import org.mockito.Mockito;

public class MockCurrentUserHelper {
    public static CurrentUserApi mockAdmin() {
        CurrentUserApi m = Mockito.mock(CurrentUserApi.class);
        Mockito.when(m.getCurrentEmpId()).thenReturn("admin");
        Mockito.when(m.getCurrentOrgCode()).thenReturn("HQ");
        Mockito.when(m.isSystemAdmin()).thenReturn(true);
        return m;
    }
    public static CurrentUserApi mockEmp(String empId, String orgCode) {
        CurrentUserApi m = Mockito.mock(CurrentUserApi.class);
        Mockito.when(m.getCurrentEmpId()).thenReturn(empId);
        Mockito.when(m.getCurrentOrgCode()).thenReturn(orgCode);
        Mockito.when(m.isSystemAdmin()).thenReturn(false);
        return m;
    }
}
```

- [ ] **Step 8: TestDbCleaner.java**

```java
package com.bank.branch.platform.performance.support;

import org.springframework.jdbc.core.JdbcTemplate;

public class TestDbCleaner {
    /** 按表名 + 前缀批量删除. */
    public static void cleanByPrefix(JdbcTemplate jdbc, String table, String codeColumn, String prefix) {
        jdbc.update("DELETE FROM " + table + " WHERE " + codeColumn + " LIKE ?", prefix + "%");
    }
}
```

- [ ] **Step 9: application-test.yml**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/onepl?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3000ms
  main:
    allow-bean-definition-overriding: true
  cache:
    type: none  # 测试环境默认禁用缓存; 需要测缓存的用例自行 @ImportAutoConfiguration(RedisCacheAutoConfiguration.class)

mybatis:
  mapper-locations: classpath*:mapper/**/*Mapper.xml
  configuration:
    map-underscore-to-camel-case: true

logging:
  level:
    com.bank.branch.platform.performance: DEBUG
    org.mybatis: INFO
    org.springframework.jdbc: INFO
```

- [ ] **Step 10: 编译测试代码 + commit**

```bash
cd performance-engine-center && mvn test-compile -q
cd .. && git add performance-engine-center/src/test
git commit -m "feat(perf): add test infrastructure (5 base classes + 3 helpers + application-test.yml)"
```

---

### Task 0.16: 编写模块级 CLAUDE.md

**Files:**
- Create: `performance-engine-center/CLAUDE.md`

- [ ] **Step 1: 参考 workflow-center/CLAUDE.md**

```bash
cat workflow-center/CLAUDE.md | head -100
```

- [ ] **Step 2: 编写 performance-engine-center/CLAUDE.md**

参照 workflow-center 风格，关键章节：
- 模块定位（绩效计算中心，核心域）
- V1.0 范围（引用 spec）
- 7 个对外 Api 概览 + UOE 占位说明
- 依赖关系（common 5 + auth + governance，不依赖 workflow/customer）
- 包结构
- 关键设计原则（TDD 红线、配置缓存、Redis 锁 Facade 层、DB UK 兜底）
- V1.1/V1.2 预告

总长度 100-150 行即可，不要复述 spec。

- [ ] **Step 3: Commit**

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "docs(perf): add module-level CLAUDE.md"
```

---

### Task 0.17: 骨架验收

- [ ] **Step 1: 全量 mvn install**

```bash
cd performance-engine-center && mvn clean install -DskipTests -q
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 验证本地 MySQL 13 张表存在**

```bash
mysql -u root -p123456 onepl -e "
SELECT COUNT(*) AS cnt FROM information_schema.tables
WHERE table_schema='onepl' AND (
  table_name IN ('sys_control','perf_metric_def','perf_metric_ref','perf_kpi_scheme','perf_kpi_item',
                 'perf_target_plan','perf_target_value','perf_run_task','cust_alloc_relation',
                 'emp_index_result','org_index_result','cust_index_result','kpi_result'));"
```

Expected: `cnt = 13`。

- [ ] **Step 3: 验证 PT_RESOURCE 35 条记录**

```bash
mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM PT_RESOURCE WHERE MODULE='perf';"
```

Expected: 35。

- [ ] **Step 4: 标记阶段 0 完成**

```bash
git log --oneline performance-engine-center/ | head -20
```

至此阶段 0 结束。创建阶段 0 里程碑 tag（可选）：

```bash
git tag -a perf-v1.0-phase0 -m "performance-engine-center V1.0 phase 0 skeleton completed"
```

---

## 3. 阶段 1：子域全栈并行（6 个独立子代理）

---

### 3.1 子代理启动方式

主代理使用 `Agent` 工具并行调度 6 个 `general-purpose` 子代理，每个子代理独立执行 TDD 循环。建议**分两批并行调度**（前 3 个 + 后 3 个），避免一次性过多并行 MySQL 写入导致冲突。

**每个子代理的通用 prompt 模板**：

```
你是 performance-engine-center V1.0 的 [子域名] 子代理（P1-X）。阶段 0 骨架已完成，你在已存在的模块上添加本子域的全部代码。

**强制要求**：
1. 严格 TDD（红→绿→重构），每步独立 commit，禁止批量提交
2. 所有代码中文注释 + UTF-8 编码
3. 测试数据前缀必须是 TEST_[前缀]_*（单线程）/ CONCUR_[前缀]_*（并发）
4. 每个 Service public 方法 @Transactional(rollbackFor=Exception.class)
5. Controller 每个端点必标 @BizAuth，高危操作标 @AuditLog
6. 跨模块调用走 @Autowired 的对方 *Api 接口
7. 禁止修改 pom.xml、bootstrap、其他子代理的文件
8. 在 PerfErrorCode 枚举末尾追加本子域需要的错误码（如有）

**测试标准**：
- Service UT 覆盖率 ≥ 80% 行、70% 分支
- 每个 Mapper public 方法至少一个正向 IT + 关键异常场景
- Controller 端点: 200 + 403/401 + 409/404 各至少一个
- Facade UT: 每个方法正常/null 输入/Optional.empty 三种场景
- UOE 占位方法: 每个都有契约测试验证抛出

[此处插入具体任务清单 - 见下文]

完成后, 执行 `cd performance-engine-center && mvn clean test -q`, 输出必须全绿。
```

---

### 3.2 P1-A: SysControl 子代理

**负责表**：`sys_control`  
**数据前缀**：`TEST_SC_*` / `CONCUR_SC_*`

**Files:**
- Create: 
  - `entity/SysControl.java`
  - `mapper/SysControlMapper.java` + `resources/mapper/SysControlMapper.xml`
  - `service/SysControlService.java`
  - `facade/SysControlFacade.java`（**独立 facade，承担 Redis 锁**）
  - `controller/SysControlController.java`
  - `controller/dto/SwitchVersionReqDTO.java` + `InitSysControlReqDTO.java`
- Test:
  - `mapper/SysControlMapperIT.java`（单线程 @Transactional）
  - `mapper/SysControlConcurrentIT.java`（**不含 @Transactional**）
  - `service/SysControlServiceTest.java`
  - `facade/SysControlFacadeTest.java`（**验证锁时序**）
  - `controller/SysControlControllerIT.java`

**TDD 里程碑**（建议 Task 顺序）：

- [ ] **T1: SysControl Entity**
  - 红：`SysControlTest.shouldMapAllFields` (字段 getter/setter)
  - 绿：编写 Entity（7 字段：id/scopeDim/latestDataDate/currentVersion/isValid/createdTime/updatedTime）
  - **注意**：sys_control 是**版本控制基础设施表**，不遵循业务表的审计字段规范，故无 createdBy/updatedBy/deleted/version 列（Task 0.9 DDL 保持与 docs/schema/ddl-performance.sql 一致，不为此表补齐审计字段）
  - commit: `test/feat: SysControl entity`

- [ ] **T2: SysControlMapper 基础 CRUD**
  - 红：`SysControlMapperIT.insert_then_selectById_shouldMatch`（TEST_SC_001）
  - 绿：Mapper 接口 + XML（insert/selectById/selectByScopeAndValid/updateById）
  - 重构：补 selectByCondition + countByCondition
  - commit: 3 步独立

- [ ] **T3: SysControlService.getCurrentVersion**
  - 红：Service UT mock Mapper 返回 is_valid=1 的记录
  - 绿：实现（含 null 防御）
  - commit: 独立

- [ ] **T4: SysControlService.initIfAbsent**
  - 红：Service UT — `when_notExists_shouldInsert`, `when_exists_shouldSkip`
  - 绿：实现幂等逻辑
  - commit: 独立

- [ ] **T5: SysControlService.doSwitchVersion（@Transactional）**
  - 红：Service UT — `concurrentSwitch_ukViolation_shouldThrow`（mock 抛 DuplicateKeyException）
  - 绿：实现事务内 `UPDATE is_valid=0` + `INSERT is_valid=1`；捕获 DuplicateKeyException 转 `PERF-40904`
  - commit: 独立

- [ ] **T6: Mapper IT 验证 UK 约束**
  - `SysControlMapperIT.switchVersion_whenUkViolation_shouldThrowDuplicateKey`
  - 利用 `EMP` 维度 + 同 `latest_data_date` 插入 2 条 is_valid=1，验证第 2 条抛异常
  - commit: 独立

- [ ] **T7: SysControlConcurrentIT（并发场景）**
  - 使用 `CONCUR_SC_*` 前缀
  - `switchVersion_concurrentTwoThreads_onlyOneWins`：启动 2 个线程同时 switchVersion，断言最终只有 1 个成功
  - `@BeforeEach` / `@AfterEach` 手工清理 `CONCUR_SC_*` 前缀的记录
  - **不要**加 `@Transactional`
  - commit: 独立

- [ ] **T8: SysControlFacade（Redis 锁时序）**
  - 红：Facade UT — `switchVersion_whenLockAcquireFailed_shouldThrow40904`（mock `RedisTemplate.opsForValue().setIfAbsent` 返回 false）
  - 绿：实现 Facade（参考 spec §4.1 伪代码）：
    ```java
    String lockKey = "perf:sys_control:switch:" + cmd.getScopeDim();
    String token = UUID.randomUUID().toString();
    Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(30));
    if (!Boolean.TRUE.equals(locked)) {
        throw new PerfException(PerfErrorCode.SYS_CONTROL_CONFLICT);
    }
    try {
        return sysControlService.doSwitchVersion(cmd);
    } finally {
        // Lua: compare token and del
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1])==ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
        redisTemplate.execute(script, java.util.Collections.singletonList(lockKey), token);
    }
    ```
  - 补 UT：`switchVersion_whenSuccess_shouldReleaseLock`, `switchVersion_whenServiceThrows_shouldStillReleaseLock`
  - commit: 独立

- [ ] **T9: SysControlController + IT**
  - 4 个端点：GET /, GET /history, POST /init, POST /switch-version
  - 每端点 Controller IT：200 成功 + 401 未登录 + 403 无权限
  - commit: 独立

- [ ] **T10: 子代理验收**
  - `mvn clean test -q` 全绿
  - 查看 git log 验证 TDD 节奏
  - 交付报告（简短）：列出交付文件 + 测试数量 + 覆盖率

**交付产物清单**（预期）：
- Java 源文件 ~10 个
- XML 1 个
- 测试文件 ~5 个
- commit 数 ~15（TDD 节奏）

---

### 3.3 P1-B: Metric 子代理（最复杂子代理）

**负责表**：`perf_metric_def`, `perf_metric_ref`  
**数据前缀**：`TEST_METRIC_*` / `CONCUR_METRIC_*`

**Files:**
- Create:
  - `entity/PerfMetricDef.java`, `entity/PerfMetricRef.java`
  - 2 套 Mapper + XML
  - Services: `MetricDefService`, `MetricRefService`, `MetricSlotService`, `MetricCycleDetectService`
  - Facades: `MetricApiImpl`, `MetricQueryApiImpl`, `MetricAssembler`
  - Controller: `MetricDefController`（10 端点）
  - DTO (内部): `CreateMetricReqDTO`, `UpdateMetricReqDTO`, `MetricQueryReqDTO`, `SlotReleaseReqDTO`, `ChangeMetricStatusReqDTO`
- Test: 对应 UT/IT，含并发测试

**TDD 里程碑**：

- [ ] **T1-T2: Entity + Mapper（2 对）**
  - PerfMetricDef 字段清单（含 deleted）：见 DDL
  - PerfMetricRef 字段：id/metricCode/refMetricCode/createdTime

- [ ] **T3: MetricCycleDetectService.checkNoCycle（纯函数，Service UT 驱动）**
  - 红：`checkNoCycle_simpleSelfRef_shouldThrow40902`（M1 引用 M1）
  - 红：`checkNoCycle_indirectRef_A_B_A_shouldThrow40902`（M1→M2→M1）
  - 红：`checkNoCycle_indirectRef_A_B_C_A_shouldThrow40902`（M1→M2→M3→M1）
  - 红：`checkNoCycle_linearChain_shouldPass`（M1→M2→M3，无环）
  - 绿：DFS 实现，借助 Mapper 读现有 `perf_metric_ref` + 临时叠加新引用
  - commit: 分步

- [ ] **T4: MetricCycleDetectService.checkLevelConstraint**
  - 红：`checkLevelConstraint_L2RefL3_shouldThrow40911`
  - 红：`checkLevelConstraint_L3RefL1_shouldThrow40911`
  - 红：`checkLevelConstraint_L2RefL1_shouldPass`
  - 红：`checkLevelConstraint_L3RefL2_shouldPass`
  - 绿：实现层级校验

- [ ] **T5: MetricSlotService.allocSlot（v1.2: Redis 锁 + Service 业务校验）**
  - **v1.2 调整**：现有 DDL `perf_metric_def.val_slot` 仅是 KEY（非 UNIQUE），DB UK 兜底不可行，改用 Redis 分布式锁保障唯一性
  - 红：`allocSlot_l1_noUsed_shouldReturn1`
  - 红：`allocSlot_l2_whenSlot_101_105_used_shouldReturn106`
  - 红：`allocSlot_preferredSlot_whenAvailable_shouldReturnPreferred`
  - 红：`allocSlot_preferredSlot_whenOccupied_shouldThrow40901`
  - 绿：**Facade 层**（非 Service）申请 Redis 锁 `perf:slot-alloc:{baseDim}` (TTL 30s, Lua 脚本释放) → 调 **Service `@Transactional`** 方法：查当前占用 slot 集合 → 按 level 范围找最小可用 → INSERT → 返回；finally 释放锁

- [ ] **T6: MetricSlotConcurrentIT**
  - `allocSlot_concurrentTwoThreads_shouldGetDifferentSlots`：2 线程同时 allocSlot(EMP, L1, null)，断言最终 2 个槽位不重复（Redis 锁生效）
  - 使用 `PerformanceConcurrentTestBase`

- [ ] **T7: MetricRefService.setRefs（双写一致性）**
  - 红：`setRefs_emptyList_shouldUpdateJsonAndClearTable`
  - 红：`setRefs_newRefs_shouldUpdateBothJsonAndTable`
  - 红：`setRefs_whenExisting_shouldDeleteOldAndInsertNew`
  - 绿：同事务内 JSON + 独立表双写
  - Mapper IT 验证双写一致

- [ ] **T8: MetricDefService.create / update / publish / disable / delete**
  - 红：`create_whenCodeDup_shouldThrow40903`
  - 红：`create_whenLevelInvalid_shouldThrow40911`
  - 红：`create_whenCycleInRefs_shouldThrow40902`
    - **测试场景**：create 新指标 M_NEW（level=2），`refMetricCodes = ["M_EXISTING"]`；M_EXISTING 已存在且引用了 M_NEW（预置测试数据）→ 形成环路
    - 环路检测在 `MetricCycleDetectService.checkNoCycle(metricCode, refCodes)` 中，需先把 "待创建 metricCode→refs" 加入临时图再 DFS；metricCode 尚未持久化不影响纯函数检测
  - 红：`publish_fromDraft_shouldSucceed`
  - 红：`publish_fromDisabled_shouldThrow40905`
  - 红：`disable_fromPublished_shouldSucceed_slotNotReleased`
  - 红：`delete_whenRefByOthers_shouldThrow`
  - 绿：编排 create（校验→层级→环路→槽位→insert→setRefs 双写）等

- [ ] **T9: MetricSlotService.releaseSlot（语义约束）**
  - 红：`releaseSlot_whenMetricNotDisabled_shouldThrow40905`
  - 红：`releaseSlot_whenDisabled_shouldAllow`
  - 绿：实现

- [ ] **T10: MetricApiImpl（Facade）**
  - 实现方法：getMetricDef, getMetricDefs, listMetrics（配置查询，含缓存注解）
  - UOE 占位：getUserMetricCards, getEmpMetricValues, getOrgMetricValues, getCustMetricValues
  - Facade UT 每个方法：正常/null/UOE 场景

- [ ] **T11: MetricQueryApiImpl（Facade）**
  - 3 个方法全部 UOE 占位
  - Facade UT：每个方法契约测试

- [ ] **T12: MetricDefController（10 端点 + IT）**
  - Controller IT：关键端点 200/403/409

- [ ] **T13: 子代理验收**

**关键代码示例**（环路检测 DFS）：

```java
public void checkNoCycle(String metricCode, List<String> newRefs) {
    if (newRefs == null || newRefs.isEmpty()) return;
    // 构造临时图：现有 perf_metric_ref + 假设加入 metricCode -> newRefs
    Map<String, Set<String>> graph = loadGraph();  // 从独立表
    graph.computeIfAbsent(metricCode, k -> new HashSet<>()).addAll(newRefs);
    Set<String> visiting = new HashSet<>();
    Set<String> done = new HashSet<>();
    dfs(metricCode, graph, visiting, done);
}

private void dfs(String node, Map<String, Set<String>> graph, Set<String> visiting, Set<String> done) {
    if (done.contains(node)) return;
    if (!visiting.add(node)) {
        throw new PerfException(PerfErrorCode.METRIC_CYCLE_DETECTED, node);
    }
    for (String child : graph.getOrDefault(node, Set.of())) {
        dfs(child, graph, visiting, done);
    }
    visiting.remove(node);
    done.add(node);
}
```

---

### 3.4 P1-C: Kpi 子代理

**负责表**：`perf_kpi_scheme`, `perf_kpi_item`  
**数据前缀**：`TEST_KPI_*`

**Files:**
- `entity/PerfKpiScheme.java`, `entity/PerfKpiItem.java`
- 2 Mapper + XML
- `service/KpiSchemeService.java`, `service/KpiItemService.java`
- `facade/KpiApiImpl.java`, `facade/KpiAssembler.java`
- `controller/KpiSchemeController.java`（9 端点）
- 内部 DTO: Create/Update/Query/AddItem/ChangeItem/ChangeStatus ReqDTO

**TDD 里程碑**：

- [ ] **T1: Entity + Mapper（2 对）**
- [ ] **T2: KpiSchemeService.create/update**
  - 校验 cycleType 字典值合法
  - UK `scheme_code` 冲突抛 `PERF-40903`（需 PerfErrorCode 追加）
- [ ] **T3: KpiItemService.addItem**
  - 校验 metricCode 存在 + 未重复
- [ ] **T4: KpiSchemeService.publish**
  - 红：`publish_whenItemMetricMissing_shouldThrow40401`
  - 红：`publish_whenItemMetricDraft_shouldThrow40905`
  - 红：`publish_whenAllMetricPublished_shouldSucceed`
- [ ] **T5: KpiApiImpl（Facade）**
  - V1.0 实现：`getKpiScheme`, `getKpiSchemeById`
  - UOE 占位：`getCurrentKpiTotal`, `getCurrentKpiResult`, `getKpiHistory`
- [ ] **T6: KpiSchemeController（9 端点 + IT）**
- [ ] **T7: 子代理验收**

---

### 3.5 P1-D: Target 子代理

**负责表**：`perf_target_plan`（**id varchar(32) — v1.2**）, `perf_target_value`（**plan_id varchar(32) — v1.2**）  
**数据前缀**：`TEST_TGT_*`

**Files:**
- `entity/PerfTargetPlan.java`（**id String**）, `entity/PerfTargetValue.java`（**planId String**）
- 2 Mapper + XML（Mapper 方法参数用 String）
- `service/TargetPlanService.java`, `service/TargetValueService.java`
- `facade/TargetApiImpl.java`, `facade/TargetAssembler.java`
- `controller/TargetPlanController.java`（4 端点）, `controller/TargetValueController.java`（3 端点）

**TDD 里程碑**：

- [ ] **T1-T2: Entity + Mapper（v1.2: id/planId String 类型）**
- [ ] **T3: TargetPlanService.create/update**
  - 校验 kpiSchemeId 存在（通过 KpiSchemeService 查询）
  - 校验 `effective_date <= expire_date`
- [ ] **T4: TargetValueService.upsert**
  - 使用 `INSERT ... ON DUPLICATE KEY UPDATE`（Mapper XML）
- [ ] **T5: TargetValueService.upsertBatch**
  - 红：`upsertBatch_whenSizeGT500_shouldThrow40910`
  - 红：`upsertBatch_whenSize500_shouldSucceed`
- [ ] **T6: TargetApiImpl（全部实现，planId 用 Long）**
- [ ] **T7: 2 Controller + IT（v1.2: planId 路径参数用 String）**
- [ ] **T8: 子代理验收**

---

### 3.6 P1-E: RunTask 子代理

**负责表**：`perf_run_task`  
**数据前缀**：`TEST_RT_*`

**Files:**
- `entity/PerfRunTask.java`
- Mapper + XML（仅查询方法，**禁止写入**）
- `service/PerfRunTaskService.java`（仅查询）
- `facade/PerfCalcApiImpl.java`（getRunTask 实现 + 2 UOE）
- `controller/PerfRunTaskController.java`（2 端点）

**TDD 里程碑**：

- [ ] **T1: Entity**（字段清单见 §1.1 数据层）
- [ ] **T2: Mapper 仅查询**
  - 接口只包含 selectById/selectByTaskNo/selectByCondition/countByCondition
  - Mapper IT 造测试数据用 SQL 直写（**不经过 Mapper 写入**）
- [ ] **T3: PerfRunTaskService 查询 + 数据范围**
  - `page` 方法需读 `DataScopeContext`：管理员全见，普通用户仅 started_by=当前 empId
- [ ] **T4: PerfCalcApiImpl**
  - V1.0 实现：`getRunTask`
  - UOE 占位：`triggerKpiCalc`, `triggerRecalc`
  - Facade UT：契约测试
- [ ] **T5: PerfRunTaskController（2 端点 + IT）**
- [ ] **T6: 子代理验收**

---

### 3.7 P1-F: Alloc 子代理

**负责表**：`cust_alloc_relation`（仅读）  
**数据前缀**：`TEST_AR_*`

**Files:**
- `entity/CustAllocRelation.java`
- Mapper + XML（仅查询，11 个业务方法）
- `service/AllocRelationService.java`（10 方法）
- `facade/AllocApiImpl.java` + `DataTaskApiImpl.java` + `AllocAssembler.java`
- `controller/AllocRelationController.java`（3 端点）

**TDD 里程碑**：

- [ ] **T1: Entity + Mapper（仅读方法）**
- [ ] **T2: AllocRelationService.getCurrentAllocations**
  - 红：`getCurrentAllocations_withEffectiveDateLE_now_andEndDateGE_now_shouldReturn`
  - 红：`getCurrentAllocations_whenEndDateBefore_now_shouldExclude`
- [ ] **T3: AllocRelationService.getAllocationHistory**
  - 按 asOfDate 历史快照查询
- [ ] **T4: listCustomersByEmp + hasAllocation + countCustomersOfEmp**
- [ ] **T5: batchGetCurrentAllocations + countCustomersByEmps + batchSummaryByEmps**
  - 输入 500 条上限校验 → `PERF-40001`
- [ ] **T6: getLatestAllocVersion / getAllocVersionAt**
  - 从 `sys_control` 的 `scope_dim=CUST` 派生
- [ ] **T7: AllocApiImpl（全部实现 + 缓存注解）**
- [ ] **T8: DataTaskApiImpl（1 方法 UOE 占位）**
- [ ] **T9: AllocRelationController（3 端点 + IT）**
- [ ] **T10: 子代理验收**

---

### 3.8 阶段 1 汇总验收（主代理执行）

- [ ] **Step 1: 全量测试**

```bash
cd performance-engine-center && mvn clean test -q
```

Expected: 所有测试绿灯。

- [ ] **Step 2: 检查 git history TDD 节奏**

```bash
git log --oneline performance-engine-center/ | head -80
```

Expected: 看到大量独立的 `test:` / `feat:` / `refactor:` 提交，不是单一巨型 commit。

- [ ] **Step 3: 阶段 1 tag**

```bash
git tag -a perf-v1.0-phase1 -m "performance-engine-center V1.0 phase 1 sub-domains completed"
```

---

## 4. 阶段 2：集成收敛（主代理串行）

---

### Task 2.1: 修改 bootstrap/pom.xml

**Files:**
- Modify: `bootstrap/pom.xml`

- [ ] **Step 1: 在 bootstrap 的 `<dependencies>` 中添加**

```xml
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>performance-engine-center</artifactId>
            <version>${project.version}</version>
        </dependency>
```

位置：放在 workflow-center 依赖之后。

- [ ] **Step 2: 验证**

```bash
cd bootstrap && mvn dependency:tree -q | grep performance
```

Expected: 看到 `com.bank.branch.platform:performance-engine-center:jar`。

---

### Task 2.2: 确认 bootstrap Application 扫描范围

**Files:**
- Modify: `bootstrap/src/main/java/com/bank/branch/platform/Application.java`（若需要）

- [ ] **Step 1: 读取 Application.java**

```bash
cat bootstrap/src/main/java/com/bank/branch/platform/Application.java
```

- [ ] **Step 2: 确认 @ComponentScan / @MapperScan 覆盖 performance 包**

如果 bootstrap 的 `@SpringBootApplication(scanBasePackages = "com.bank.branch.platform")` 能够覆盖 performance 子包，**无需修改**。如果用了显式白名单，追加 performance 包。

- [ ] **Step 3: 若修改则 commit**

```bash
git add bootstrap/src/main/java/com/bank/branch/platform/Application.java
git commit -m "chore(bootstrap): ensure scan covers performance module"
```

---

### Task 2.3: 审查并合并 PerfErrorCode

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java`

- [ ] **Step 1: 检查各子代理是否追加了新错误码**

```bash
grep -n "PERF-" performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java
```

- [ ] **Step 2: 审查是否有重复 code（`PERF-409xx`）**

- [ ] **Step 3: 整理（必要时重新分配序号）**

- [ ] **Step 4: 全量 compile 验证**

```bash
cd performance-engine-center && mvn clean compile -q
```

---

### Task 2.4: 执行 V1_0_1 与 V1_0_2 SQL（幂等模式）

- [ ] **Step 1: 执行（幂等）**

```bash
mysql -u root -p123456 onepl < performance-engine-center/src/main/resources/sql/V1_0_1__performance_resources.sql
mysql -u root -p123456 onepl < performance-engine-center/src/main/resources/sql/V1_0_2__performance_dicts.sql
```

- [ ] **Step 2: 核对**

```bash
mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM PT_RESOURCE WHERE MODULE='perf';"  # 35
mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM gov_dict_type WHERE dict_type LIKE 'PERF_%';"  # 10
```

---

### Task 2.5: mvn clean package 验收

- [ ] **Step 1: 根目录全量构建**

```bash
cd /c/Users/52140/Desktop/yiti && mvn clean package -DskipTests -q
```

Expected: 所有模块 BUILD SUCCESS。

- [ ] **Step 2: 检查 bootstrap jar 包含 performance 类**

```bash
ls bootstrap/target/*.jar
jar tf bootstrap/target/bootstrap-*.jar | grep performance | head -5
```

Expected: 看到 `BOOT-INF/lib/performance-engine-center-*.jar`。

---

### Task 2.6: 启动 bootstrap 验证

- [ ] **Step 1: 启动应用（后台，记录 PID）**

```bash
cd bootstrap && mvn spring-boot:run -q > /tmp/bootstrap.log 2>&1 &
echo $! > /tmp/bootstrap.pid
sleep 15
```

- [ ] **Step 2: 检查启动状态**

```bash
grep -E "Started|ERROR|Exception" /tmp/bootstrap.log | head -20
```

Expected: 看到 `Started Application in X seconds`，无 ERROR。

- [ ] **Step 3: 验证 Knife4j 端点**

```bash
curl -s http://localhost:8080/v3/api-docs | jq '.paths | keys | map(select(startswith("/api/perf/"))) | length'
```

Expected: 35。

- [ ] **Step 4: 查看具体端点**

```bash
curl -s http://localhost:8080/v3/api-docs | jq '.paths | keys | map(select(startswith("/api/perf/")))'
```

Expected: 35 条路径。

---

### Task 2.7: 冒烟测试 5 个关键端点

**前置**：已登录（通过已有测试账户获取 Cookie/Token）。

- [ ] **Step 1: 登录获取 Session**

```bash
# 依 auth-permission-center 的登录方式；假设 POST /api/auth/login 返回 Cookie
curl -s -c /tmp/cookie.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | jq .code
```

Expected: `"0"`。

- [ ] **Step 2: 5 个冒烟端点**

```bash
# 1. 查询 sys_control
curl -s -b /tmp/cookie.txt "http://localhost:8080/api/perf/sys-control?scopeDim=EMP" | jq .code

# 2. 指标列表
curl -s -b /tmp/cookie.txt "http://localhost:8080/api/perf/metrics?pageNo=1&pageSize=10" | jq .code

# 3. KPI 方案列表
curl -s -b /tmp/cookie.txt "http://localhost:8080/api/perf/kpi-schemes?pageNo=1&pageSize=10" | jq .code

# 4. 目标方案列表
curl -s -b /tmp/cookie.txt "http://localhost:8080/api/perf/target-plans?pageNo=1&pageSize=10" | jq .code

# 5. sys_control init
curl -s -b /tmp/cookie.txt -X POST "http://localhost:8080/api/perf/sys-control/init" \
  -H 'Content-Type: application/json' \
  -d '{"dataDate":"2026-04-15","reason":"V1.0 initial deployment"}' | jq .code
```

Expected: 全部返回 `"0"`。

- [ ] **Step 3: 验证 init 后 sys_control 表有 3 条记录**

```bash
mysql -u root -p123456 onepl -e "SELECT scope_dim, latest_data_date, current_version, is_valid FROM sys_control WHERE is_valid=1;"
```

Expected: 3 行，scope_dim 覆盖 EMP/ORG/CUST。

- [ ] **Step 4: 停止应用（Windows 兼容）**

```bash
# Windows Git Bash 环境：
taskkill //F //FI "WINDOWTITLE eq *spring-boot*" 2>/dev/null || \
  taskkill //F //IM java.exe 2>/dev/null || true

# 或者记录 PID 后直接 kill（推荐：Task 2.6 Step 1 启动时用 $! 记录 PID）
# 参考：
# mvn spring-boot:run -q > /tmp/bootstrap.log 2>&1 & echo $! > /tmp/bootstrap.pid
# 然后这里：kill $(cat /tmp/bootstrap.pid) 2>/dev/null || true
```

---

### Task 2.8: 全量 mvn test

- [ ] **Step 1: 运行全模块测试**

```bash
cd /c/Users/52140/Desktop/yiti && mvn clean test -q
```

Expected: 所有模块测试全绿。

- [ ] **Step 2: 核对 performance 测试数量**

```bash
find performance-engine-center/target/surefire-reports -name 'TEST-*.xml' | wc -l
```

Expected: ≥ 30 个测试类。

---

### Task 2.9: 阶段 2 commit + tag

- [ ] **Step 1: 确认所有阶段 2 修改**

```bash
git status
git diff --stat HEAD~
```

- [ ] **Step 2: Commit（如有未提交的）**

```bash
git add bootstrap/pom.xml bootstrap/src performance-engine-center
git commit -m "feat(perf): integrate performance-engine-center V1.0 into bootstrap

- register performance module in bootstrap pom
- smoke test 5 key endpoints pass
- all 35 REST endpoints visible in knife4j
- sys_control init creates EMP/ORG/CUST records"
```

- [ ] **Step 3: 阶段 2 tag**

```bash
git tag -a perf-v1.0-phase2 -m "performance-engine-center V1.0 phase 2 integration completed"
```

---

## 5. 阶段 3：代码审查（code-reviewer 子代理）

---

### Task 3.1-3.5: 调度 code-reviewer 子代理

- [ ] **Step 1: 调度 code-reviewer 子代理**

使用 `Agent` 工具，subagent_type=`superpowers:code-reviewer`，prompt 模板：

```
你是 performance-engine-center V1.0 的代码审查员。阶段 0-2 已完成，现在需要你全面审查代码质量。

审查目标路径：performance-engine-center/
Spec 文档：docs/superpowers/specs/2026-04-15-performance-engine-center-v1.0-design.md
Plan 文档：docs/superpowers/plans/2026-04-15-performance-engine-center-v1.0-impl.md

审查维度（逐项评级 PASS / MINOR / MAJOR）：
1. **TDD 节奏**：git 历史是否展现红→绿→重构三步提交？检查命令：
   `git log --oneline --format='%s' performance-engine-center/ | grep -E '^(test|feat|refactor)' | head -60`
2. **CLAUDE.md 规范**：
   - 每个 Controller 方法都有 @BizAuth？`grep -c "@BizAuth" performance-engine-center/src/main/java/.../controller/*.java`
   - 高危操作都有 @AuditLog？
   - 中文注释 + UTF-8 编码？`file performance-engine-center/src/main/java/**/*.java | grep -v utf-8`
3. **Spec 对照**：
   - 7 个对外 Api 全部签名与 04 契约一致？（逐个方法对照）
   - UOE 占位方法数 = 13？
   - 35 个 REST 端点全部存在？
4. **错误码完整性**：所有 throw 语句都用 PerfException + PerfErrorCode 枚举，无裸 RuntimeException
5. **缓存一致性**：所有 @CacheEvict 都在事务提交后（TransactionSynchronizationManager.registerSynchronization）
6. **SQL 安全**：Mapper XML 全部使用 #{} 而非 ${}：
   `grep -rn '\${' performance-engine-center/src/main/resources/mapper/ | grep -v 'scope' | head`（除 scope 片段外不应有）
7. **空值处理**：Facade Optional 返回方法对 null 入参的处理正确
8. **测试质量**：
   - 并发测试类是否使用 PerformanceConcurrentTestBase（不含 @Transactional）？
   - 所有 UOE 占位方法是否都有契约测试？
   - 测试覆盖率（`mvn jacoco:report` 后查看 `target/site/jacoco/index.html`）

请产出：
- 结构化审查报告，每个维度 PASS/MINOR/MAJOR
- Must Fix 清单（MAJOR 级问题）
- Should Fix 清单（MINOR 级问题）
- 整体评价
- 报告文件保存到 docs/superpowers/sessions/2026-04-15-perf-v1.0-code-review.md
```

- [ ] **Step 2: 若发现 Must Fix**

- 相关子代理修复 → 重新提交 → 重新 review
- 最多 3 轮；超过则上升人类决策

- [ ] **Step 3: 最终 tag + merge**

```bash
git tag -a perf-v1.0 -m "performance-engine-center V1.0 completed (phase 0-3 all passed)"
```

---

## 6. 附录

### 6.1 各阶段完成 Checklist

**阶段 0 完成标准**：
- [ ] `mvn clean install -pl performance-engine-center -DskipTests` 通过
- [ ] 本地 MySQL `onepl` 13 张表存在
- [ ] 35 条 `P_PERF_*` PT_RESOURCE 已插入
- [ ] 10 类 `PERF_*` 字典已插入
- [ ] 7 个 Api 接口 + 15 个 DTO 全部编写
- [ ] 测试基础设施 8 个文件编写
- [ ] 模块级 CLAUDE.md 编写

**阶段 1 完成标准（每子代理）**：
- [ ] `mvn clean test -pl performance-engine-center` 全绿
- [ ] Service 覆盖率 ≥ 80%
- [ ] Mapper IT 每方法至少一正向 + 关键异常
- [ ] git 历史体现 TDD 红-绿-重构三步提交
- [ ] 测试数据使用正确前缀
- [ ] 并发测试使用 PerformanceConcurrentTestBase

**阶段 2 完成标准**：
- [ ] `mvn clean package` 通过
- [ ] Knife4j 显示 35 端点
- [ ] 5 个冒烟端点返回 200
- [ ] `sys_control` init 创建 3 条记录
- [ ] 全量 `mvn test` 通过

**阶段 3 完成标准**：
- [ ] code-reviewer 报告无 MAJOR
- [ ] MINOR 清单记录在案

### 6.2 常用命令速查

```bash
# 启动应用
cd bootstrap && mvn spring-boot:run

# 单模块测试
cd performance-engine-center && mvn clean test

# 本地 MySQL 连接
mysql -u root -p123456 onepl

# Knife4j
open http://localhost:8080/doc.html
```

### 6.3 与 spec 的章节映射

| Plan 阶段 | Spec 章节 |
|---|---|
| P0 Task 0.2-0.3 | spec §2.1 |
| P0 Task 0.4-0.6 | spec §2.4 + §7.1 |
| P0 Task 0.7-0.8 | spec §5.2 |
| P0 Task 0.9 | spec §3 |
| P0 Task 0.10 | spec §6.3 |
| P0 Task 0.11 | spec §附录 |
| P0 Task 0.12-0.14 | spec §2.1 |
| P0 Task 0.15 | spec §8 |
| P1-A SysControl | spec §4.1 |
| P1-B Metric | spec §4.2 |
| P1-C Kpi | spec §4.3 |
| P1-D Target | spec §4.4 |
| P1-E RunTask | spec §4.5 |
| P1-F Alloc | spec §4.6 |
| P2 Task 2.1-2.9 | spec §9.4 |
| P3 | spec §9.5 |

### 6.4 风险追踪（spec §10 映射）

- **阶段 1 并发修改 PerfErrorCode** → Task 2.3 统一去重
- **Mapper IT 并行数据冲突** → 前缀隔离 + 并发测试独立基类
- **Redis 未就绪** → application-test.yml 允许 Redis 降级
- **DDL/Entity 不一致** → Mapper IT 强制映射验证
- **bootstrap 启动失败** → Task 2.6 启动冒烟测试
- **TDD 节奏被忽略** → Task 3.1 code-reviewer 审查 git 历史

---

**Plan 完成。保存路径：`docs/superpowers/plans/2026-04-15-performance-engine-center-v1.0-impl.md`**
