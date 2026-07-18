# 红色引擎并入 Branch Platform 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 redengine（Java8/Boot2.7 党建系统）升级移植为平台第 10 个业务模块 `red-engine-center`，前端并入 xanzc_frontend `/redengine/**` 路由区（保留登录页外观与红色主题），权限/系统设置改用平台 PT_* RBAC；所有数据库变更只进测试库。

**Architecture:** 模块化单体新增 Maven 模块（包名 `com.bank.branch.platform.redengine`，依赖 common + auth + governance）；认证走平台 Spring Session，鉴权走 `@BizAuth(RED_ENGINE)` + `P_RE_*` 资源；党组织树模块私有（`RE_PARTY_ORG` + `RE_USER_PARTY_MAP` 映射）；两级审核保留原 4 状态机（0 草稿/1 已提交/2 通过/3 驳回），不接 Flowable。

**Tech Stack:** Spring Boot 3.2.3 / JDK 17 / MyBatis-Plus 3.5.7 / MySQL 8（`yiti_test`）/ Vue3 + Vite4 + Element Plus / Vitest / Playwright

**Spec:** `docs/superpowers/specs/2026-07-18-redengine-merge-design.md`（已获批）

## Global Constraints

- 分支：全部改动在 `feature/redengine-merge`，不动 master
- 数据库：**所有 DDL/DML 只在 `yiti_test`（开发/验收）与 `onepl_test_bootstrap`（IT 库）执行，禁止动 `yiti` 正式库**；MySQL 账号 root/djdev
- TDD 红线：每个接口先写失败测试再实现；`*Test.java`=surefire、`*IT.java`=failsafe
- MyBatis-Plus 红线：Mapper `extends BaseMapper<T>`，单条 CRUD 用内置方法，禁止为其写 XML；实体 `@TableName`/`@TableId(type=IdType.AUTO)`
- Flyway 禁令：DDL 全部手工/脚本直执，脚本落档 `docs/superpowers/sql/`，禁止 `V*__*.sql` 命名
- 每个新端点必须：登记 `PT_RESOURCE`（否则 403 AUTH-40302）+ 标 `@BizAuth`（否则 403 AUTH-40304）；写操作标 `@AuditLog`
- 响应模型：统一 `ResponseWrapper<T>`（common-web），分页用 `PageRequest`/`PageResult`；禁止把 redengine 的 `Result`/`PageResult` 带进来
- 跨模块只走 `*Api`（文件用 governance `FileApi`，用户上下文用 auth `CurrentUserApi`）
- 前端：新增代码全部放 `xanzc_frontend/src/views/redengine/**` + `src/api/redengine.js`，样式类名加 `re-` 前缀，禁止改动平台全局样式
- 中文注释、UTF-8；跨模块改动后先 `mvn clean install -DskipTests` 再跑 bootstrap 测试（stale jar 规则）
- `redengine/` 原工程与 `backup/` 不入 git
- 子代理派遣 model ≥ sonnet

---

### Task 0: 数据库安全网（备份 + 测试库 + profile + gitignore）

**Files:**
- Create: `backup/yiti_full_20260718.sql`（不入 git）
- Create: `bootstrap/src/main/resources/application-remerge.yml`
- Modify: `.gitignore`（追加两行）

**Interfaces:**
- Produces: `yiti_test` 库（与 yiti 全量一致）；`remerge` profile（datasource 指向 yiti_test）。后续所有后端 Task 的本地启动/验收都用 `--spring.profiles.active=remerge`。

- [ ] **Step 1: 备份 yiti 全库**

```bash
mkdir -p /home/djdev/leid/yiti/backup
mysqldump -uroot -pdjdev --single-transaction --routines --triggers \
  --databases yiti > /home/djdev/leid/yiti/backup/yiti_full_20260718.sql
ls -lh /home/djdev/leid/yiti/backup/yiti_full_20260718.sql
```
Expected: 文件存在且 > 10MB（yiti 数据约 68MB，dump 文本通常更大）

- [ ] **Step 2: 创建 yiti_test 并导入**

```bash
mysql -uroot -pdjdev -e "DROP DATABASE IF EXISTS yiti_test; CREATE DATABASE yiti_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sed 's/`yiti`/`yiti_test`/g' /home/djdev/leid/yiti/backup/yiti_full_20260718.sql | mysql -uroot -pdjdev
mysql -uroot -pdjdev -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='yiti_test';"
```
Expected: 表数与 yiti 相同（当前 169，若 yiti 又有新表则以 dump 时点为准，两边一致即可）

- [ ] **Step 3: 新增 remerge profile**

`bootstrap/src/main/resources/application-remerge.yml`（完整内容）：

```yaml
# 红色引擎合并开发/验收专用 profile：数据源切到 yiti_test，其余全部继承默认配置。
# 用法：cd bootstrap && mvn spring-boot:run -Dspring-boot.run.profiles=remerge
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/yiti_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true&sessionVariables=time_zone%3D%27%2B08%3A00%27
```

- [ ] **Step 4: .gitignore 追加**

在 `.gitignore` 末尾追加：

```
# 红色引擎迁移源工程（只读参考，不入库）
redengine/
# 数据库备份
backup/
```

- [ ] **Step 5: 验证 profile 生效（冒烟）**

```bash
cd /home/djdev/leid/yiti/bootstrap && timeout 120 mvn spring-boot:run -Dspring-boot.run.profiles=remerge 2>&1 | grep -m1 "Started BranchPlatformApplication"
```
Expected: 启动成功行出现（连的是 yiti_test；Ctrl-C/timeout 结束即可）

- [ ] **Step 6: Commit**

```bash
git add .gitignore bootstrap/src/main/resources/application-remerge.yml
git commit -m "chore(redengine): 库安全网——yiti 备份/yiti_test 测试库/remerge profile/gitignore"
```

---

### Task 1: BizType 枚举新增 RED_ENGINE（TDD）

**Files:**
- Modify: `common/common-security/src/main/java/com/bank/branch/platform/common/security/enums/BizType.java`
- Test: `common/common-security/src/test/java/com/bank/branch/platform/common/security/enums/BizTypeRedEngineTest.java`

**Interfaces:**
- Produces: `BizType.RED_ENGINE`（code="RED_ENGINE"），后续所有 `@BizAuth` 使用；`PT_ROLE_BIZ_SCOPE.BIZ_TYPE` 存 'RED_ENGINE' 字符串。

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.common.security.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 红色引擎 BizType 常量存在性守护 */
class BizTypeRedEngineTest {
    @Test
    void redEngineConstantExists() {
        assertEquals("RED_ENGINE", BizType.RED_ENGINE.getCode());
        assertEquals("党建红色引擎", BizType.RED_ENGINE.getDescription());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl common/common-security -Dtest=BizTypeRedEngineTest`
Expected: 编译失败 `cannot find symbol: RED_ENGINE`

- [ ] **Step 3: 实现——枚举加一行**

在 `WORKFLOW_MONITOR("WORKFLOW_MONITOR", "工作流监控");` 前加：

```java
    WORKFLOW_MONITOR("WORKFLOW_MONITOR", "工作流监控"),
    RED_ENGINE("RED_ENGINE", "党建红色引擎");
```
（即把原最后一项的 `;` 改 `,`，新增 RED_ENGINE 作为最后一项）

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl common/common-security -Dtest=BizTypeRedEngineTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add common/common-security/src
git commit -m "feat(common): BizType 新增 RED_ENGINE 党建红色引擎业务类型"
```

---

### Task 2: red-engine-center 模块骨架

**Files:**
- Modify: `pom.xml`（根，modules 列表）
- Create: `red-engine-center/pom.xml`
- Create: `red-engine-center/src/main/java/com/bank/branch/platform/redengine/config/RedEngineMyBatisConfig.java`
- Modify: `bootstrap/pom.xml`（加依赖）

**Interfaces:**
- Produces: Maven 模块 `com.bank.branch.platform:red-engine-center`；包根 `com.bank.branch.platform.redengine`（bootstrap 的 `@SpringBootApplication` 在 `com.bank.branch.platform` 根包，自动扫到本模块，无需额外配置）。

- [ ] **Step 1: 根 pom modules 加一行**

在 `<module>report-analytics-center</module>` 之后插入：

```xml
        <module>red-engine-center</module>
```

- [ ] **Step 2: 新建 red-engine-center/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.bank.branch.platform</groupId>
        <artifactId>branch-platform</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>red-engine-center</artifactId>
    <name>Red Engine Center</name>
    <description>党建红色引擎中心：四大维度材料上报、两级审核、评分与逾期扣分、驾驶舱、红黄牌预警、年度归档、数据导出（自 redengine 独立系统移植）</description>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-trace</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-security</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-aop</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>common-db</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>auth-permission-center</artifactId>
        </dependency>
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>system-governance-center</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
        </dependency>
        <!-- 数据导出（平台既有版本，替代 redengine 的 hutool-poi） -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>easyexcel</artifactId>
            <version>3.3.4</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: MyBatis 扫描配置类**

`red-engine-center/src/main/java/com/bank/branch/platform/redengine/config/RedEngineMyBatisConfig.java`：

```java
package com.bank.branch.platform.redengine.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 红色引擎模块 MyBatis-Plus Mapper 扫描配置。
 * 分页/审计填充等拦截器由 common-db 自动配置提供，此处仅声明扫描包。
 */
@Configuration
@MapperScan("com.bank.branch.platform.redengine.mapper")
public class RedEngineMyBatisConfig {
}
```

- [ ] **Step 4: bootstrap/pom.xml 加依赖**

在 bootstrap 依赖块中 `report-analytics-center` 依赖之后插入：

```xml
        <dependency>
            <groupId>com.bank.branch.platform</groupId>
            <artifactId>red-engine-center</artifactId>
            <version>${project.version}</version>
        </dependency>
```
（若其他模块依赖未写 `<version>`，与之保持一致省略）

- [ ] **Step 5: 全量安装冒烟**

Run: `cd /home/djdev/leid/yiti && mvn clean install -DskipTests`
Expected: BUILD SUCCESS，反应堆含 `Red Engine Center`

- [ ] **Step 6: Commit**

```bash
git add pom.xml red-engine-center bootstrap/pom.xml
git commit -m "feat(redengine): 新增 red-engine-center 模块骨架并接入 bootstrap"
```

---

### Task 3: RE_* 表 DDL（只进测试库）

**Files:**
- Create: `docs/superpowers/sql/2026-07-18-redengine-tables.sql`

**Interfaces:**
- Produces: 8 张表 `RE_PARTY_ORG` / `RE_USER_PARTY_MAP` / `RE_SUBMIT` / `RE_SUBMIT_FILE` / `RE_SCORE` / `RE_MEMBER_SCORE` / `RE_OVERDUE_DEDUCTION` / `RE_ANNUAL_RESULT`（列名沿用 redengine `schema.sql` 小写蛇形，表名大写 RE_ 前缀）。后续实体 @TableName 逐一对应。

- [ ] **Step 1: 写 DDL 脚本**（完整内容；来源 `redengine/red-engine-server/src/main/resources/db/schema.sql`，做 4 处适配：表名 RE_ 前缀；`biz_submit_file` 增加 `file_object_id`（平台 OBS 文件 ID）；`RE_USER_PARTY_MAP` 全新；不迁移 sys_user/sys_role/sys_menu/sys_user_role/sys_role_menu/sys_dict_type/sys_dict_data）

```sql
-- 红色引擎业务表（2026-07-18，只在 yiti_test / onepl_test_bootstrap 执行，禁止动 yiti 正式库）
-- 幂等：IF NOT EXISTS

CREATE TABLE IF NOT EXISTS RE_PARTY_ORG (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '党组织ID',
  org_name VARCHAR(100) NOT NULL COMMENT '党组织名称',
  parent_id BIGINT COMMENT '上级党组织ID',
  org_level INT COMMENT '层级(1:分行党委 2:党支部)',
  org_code VARCHAR(50) COMMENT '党组织编码',
  org_type VARCHAR(20) DEFAULT NULL COMMENT '类型：经营单位/营销部室/中后台部门',
  principal VARCHAR(50) COMMENT '负责人姓名',
  contact_phone VARCHAR(20) COMMENT '联系电话',
  org_address VARCHAR(255) COMMENT '地址',
  secretary_id VARCHAR(50) DEFAULT NULL COMMENT '支部书记平台用户工号(PT_USER.USER_ID)',
  remark TEXT COMMENT '备注',
  deleted TINYINT DEFAULT 0 COMMENT '软删(0有效 1删除)',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_parent_id (parent_id),
  KEY idx_org_code (org_code),
  KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-党组织';

CREATE TABLE IF NOT EXISTS RE_USER_PARTY_MAP (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(50) NOT NULL COMMENT '平台用户工号(PT_USER.USER_ID)',
  party_org_id BIGINT NOT NULL COMMENT '党组织ID(RE_PARTY_ORG.id)',
  party_role VARCHAR(30) NOT NULL COMMENT '党内角色:ORG_REVIEWER/BRANCH_REVIEWER/SECRETARY/REPORTER',
  deleted TINYINT DEFAULT 0 COMMENT '软删(0有效 1删除)',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user (user_id),
  KEY idx_party_org_id (party_org_id),
  KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-平台用户党组织映射';

CREATE TABLE IF NOT EXISTS RE_SUBMIT (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '上报ID',
  org_id BIGINT NOT NULL COMMENT '党组织ID',
  submitter_id VARCHAR(50) COMMENT '提交人平台工号',
  dimension VARCHAR(20) COMMENT '考核维度(dim1~dim4)',
  item_code VARCHAR(50) COMMENT '考核项编码(如1.1)',
  item_name VARCHAR(100) COMMENT '考核项名称',
  max_score DECIMAL(5,1) COMMENT '该考核项满分上限',
  project_name VARCHAR(255) COMMENT '项目名称',
  submit_type INT COMMENT '上报类型(1月度 2季度 3年度)',
  submit_date DATE COMMENT '上报日期',
  status INT DEFAULT 0 COMMENT '状态(0草稿 1已提交 2已通过 3已驳回)',
  form_data LONGTEXT COMMENT '表单数据(JSON)',
  file_urls LONGTEXT COMMENT '附件URL列表(JSON数组,兼容迁移数据)',
  review_feedback VARCHAR(500) COMMENT '审核意见',
  reviewer_id VARCHAR(50) COMMENT '审核人平台工号',
  review_date TIMESTAMP NULL DEFAULT NULL COMMENT '审核时间',
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_org_id (org_id), KEY idx_submitter_id (submitter_id),
  KEY idx_dimension (dimension), KEY idx_item_code (item_code),
  KEY idx_status (status), KEY idx_submit_date (submit_date), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-材料上报';

CREATE TABLE IF NOT EXISTS RE_SUBMIT_FILE (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  submit_id BIGINT NOT NULL COMMENT '上报ID',
  file_object_id VARCHAR(64) DEFAULT NULL COMMENT '平台文件ID(FILE_OBJECT,经 governance FileApi 上传)',
  file_name VARCHAR(255) NOT NULL COMMENT '文件名',
  file_path VARCHAR(500) DEFAULT NULL COMMENT '旧文件路径(仅迁移数据兼容,新数据置空)',
  file_size BIGINT COMMENT '文件大小',
  file_type VARCHAR(50) COMMENT '文件类型',
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_submit_id (submit_id), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-上报附件';

CREATE TABLE IF NOT EXISTS RE_SCORE (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '党组织ID',
  submit_id BIGINT COMMENT '关联上报ID',
  item_code VARCHAR(50) COMMENT '考核项编码',
  score_year INT COMMENT '考核年度',
  score_period VARCHAR(20) COMMENT '考核期间(YYYY-MM)',
  base_score DECIMAL(10,2) DEFAULT 100.00,
  deduction_score DECIMAL(10,2) DEFAULT 0.00,
  final_score DECIMAL(10,2) COMMENT '最终得分',
  remark TEXT,
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_org_id (org_id), KEY idx_submit_id (submit_id), KEY idx_item_code (item_code),
  KEY idx_score_year (score_year), KEY idx_score_period (score_period), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-评分';

CREATE TABLE IF NOT EXISTS RE_MEMBER_SCORE (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '党组织ID',
  user_id VARCHAR(50) COMMENT '党员平台工号',
  member_name VARCHAR(50) COMMENT '党员姓名',
  score_period VARCHAR(20) COMMENT '考核期间(YYYY-MM)',
  score DECIMAL(10,2) COMMENT '党员得分',
  remark TEXT,
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_org_id (org_id), KEY idx_user_id (user_id),
  KEY idx_score_period (score_period), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-党员评分';

CREATE TABLE IF NOT EXISTS RE_OVERDUE_DEDUCTION (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '党组织ID',
  submit_id BIGINT COMMENT '上报ID',
  deduction_reason VARCHAR(255) COMMENT '扣分原因',
  deduction_points DECIMAL(10,2) COMMENT '扣分分值',
  deduction_date DATE COMMENT '扣分日期',
  remark TEXT,
  deleted TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_org_id (org_id), KEY idx_submit_id (submit_id),
  KEY idx_deduction_date (deduction_date), KEY idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-逾期扣分';

CREATE TABLE IF NOT EXISTS RE_ANNUAL_RESULT (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  org_id BIGINT NOT NULL COMMENT '党支部ID',
  dim1_score DECIMAL(5,1) DEFAULT 0 COMMENT '维度一得分(满35)',
  dim2_score DECIMAL(5,1) DEFAULT 0 COMMENT '维度二得分(满50)',
  dim3_score DECIMAL(5,1) DEFAULT 0 COMMENT '维度三得分(满10)',
  dim4_score DECIMAL(5,1) DEFAULT 0 COMMENT '维度四得分(满5)',
  total_score DECIMAL(5,1) NOT NULL COMMENT '原始总分(满100)',
  final_score DECIMAL(5,1) NOT NULL COMMENT '折算得分(total*0.4)',
  is_qualified TINYINT NOT NULL DEFAULT 1 COMMENT '评优资格:1保留 0拦截(<60)',
  eval_year INT NOT NULL COMMENT '考核年度',
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_org_year (org_id, eval_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红色引擎-年度考核结果';
```

注意两处与源 schema 的**有意差异**（不是笔误）：`secretary_id`/`submitter_id`/`reviewer_id`/`user_id` 从 BIGINT 改为 VARCHAR(50)——平台用户主键是 `PT_USER.USER_ID`（工号字符串）；`RE_SUBMIT_FILE.file_object_id` 新增。

- [ ] **Step 2: 在两个测试库执行并验证**

```bash
mysql -uroot -pdjdev yiti_test < docs/superpowers/sql/2026-07-18-redengine-tables.sql
mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-07-18-redengine-tables.sql
mysql -uroot -pdjdev -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='yiti_test' AND table_name LIKE 'RE\\_%' ORDER BY 1;"
```
Expected: 两库各 8 张 RE_* 表

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/sql/2026-07-18-redengine-tables.sql
git commit -m "feat(redengine): RE_* 8 张业务表 DDL（仅测试库执行）"
```

---

### Task 4: 权限/字典种子 SQL（只进测试库）

**Files:**
- Create: `docs/superpowers/sql/2026-07-18-redengine-seed.sql`

**Interfaces:**
- Produces: PT_RESOURCE 17 条 `P_RE_*` / `M_RE_*`；PT_ROLE 4 个党建角色（ROLE_ID `RE_ROLE_1..4`，ROLE_CODE `R_RE_ORGREV`/`R_RE_BRREV`/`R_RE_SECR`/`R_RE_REPORT`）；PT_ROLE_RESOURCE 绑定；PT_ROLE_BIZ_SCOPE（BIZ_TYPE='RED_ENGINE'，含 **R_ADMIN 补行 SOP**）；SYS_DICT/SYS_DICT_ITEM 字典 4 类 15 项；RE_PARTY_ORG 党组织树 10 行。
- 端点↔资源对照表（后续 Task 6-11 的 Controller 必须与此严格一致）：

| RESOURCE_ID | METHOD | RESOURCE_URL | 说明 | 角色（4 党建角色中的授权者） |
|---|---|---|---|---|
| P_RE_ORG_TREE | GET | /api/re/orgs/tree | 党组织树 | 全部 4 角色 |
| P_RE_ORG_GET | GET | /api/re/orgs/* | 党组织详情 | 全部 4 角色 |
| P_RE_ORG_ADD | POST | /api/re/orgs | 新增党组织 | （仅 R_ADMIN） |
| P_RE_ORG_UPD | PUT | /api/re/orgs/* | 修改党组织 | （仅 R_ADMIN） |
| P_RE_ORG_DEL | DELETE | /api/re/orgs/* | 删除党组织(高危) | （仅 R_ADMIN） |
| P_RE_MAP_LIST | GET | /api/re/user-party-maps | 映射列表 | （仅 R_ADMIN） |
| P_RE_MAP_BIND | POST | /api/re/user-party-maps | 绑定用户党组织(高危) | （仅 R_ADMIN） |
| P_RE_SUBMIT_ADD | POST | /api/re/submits | 新建上报 | R_RE_REPORT, R_RE_SECR |
| P_RE_SUBMIT_MY | GET | /api/re/submits/my | 我的上报分页 | R_RE_REPORT, R_RE_SECR, R_RE_BRREV |
| P_RE_SUBMIT_GET | GET | /api/re/submits/* | 上报详情 | 全部 4 角色 |
| P_RE_REVIEW_Q | GET | /api/re/reviews/queue | 待审队列 | R_RE_BRREV, R_RE_ORGREV |
| P_RE_REVIEW_APPR | POST | /api/re/reviews/*/approve | 审核通过+评分 | R_RE_BRREV, R_RE_ORGREV |
| P_RE_REVIEW_REJ | POST | /api/re/reviews/*/reject | 审核驳回 | R_RE_BRREV, R_RE_ORGREV |
| P_RE_CKPT_VIEW | GET | /api/re/cockpit/** | 驾驶舱全部只读(overview/ranking/overdue/warning/settlement) | R_RE_ORGREV, R_RE_SECR |
| P_RE_CKPT_EXEC | POST | /api/re/cockpit/overdue/execute | 执行逾期扣分(高危) | R_RE_ORGREV |
| P_RE_CKPT_ANNUAL | POST | /api/re/cockpit/archive/generate/* | 生成年度归档(高危) | R_RE_ORGREV |
| P_RE_EXPORT | GET | /api/re/export/* | 数据导出 | R_RE_ORGREV, R_RE_SECR |

- [ ] **Step 1: 写种子脚本**（骨架如下，编写时按上表补全全部 17 条资源与绑定；幂等用 INSERT IGNORE）

```sql
-- 红色引擎权限/字典/党组织种子（只在 yiti_test / onepl_test_bootstrap 执行）
-- 1) PT_RESOURCE：按对照表 17 条，示例（其余 16 条同构）：
INSERT IGNORE INTO PT_RESOURCE
 (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_USER, REMARK)
VALUES
 ('P_RE_ORG_TREE', '/api/re/orgs/tree', 'GET', '红色引擎-党组织树', 0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6'),
 -- ... P_RE_ORG_GET / P_RE_ORG_ADD / P_RE_ORG_UPD / P_RE_ORG_DEL
 -- ... P_RE_MAP_LIST / P_RE_MAP_BIND
 -- ... P_RE_SUBMIT_ADD / P_RE_SUBMIT_MY / P_RE_SUBMIT_GET
 -- ... P_RE_REVIEW_Q / P_RE_REVIEW_APPR / P_RE_REVIEW_REJ
 -- ... P_RE_CKPT_VIEW('/api/re/cockpit/**') / P_RE_CKPT_EXEC / P_RE_CKPT_ANNUAL / P_RE_EXPORT('/api/re/export/*')
 ('P_RE_EXPORT', '/api/re/export/*', 'GET', '红色引擎-数据导出', 0, NULL, 0, 'RE', 'redengine-merge', 'spec 2026-07-18 §6');

-- 2) PT_ROLE：4 个党建角色
INSERT IGNORE INTO PT_ROLE (ROLE_ID, ROLE_CODE, ROLE_CHNAME, RECORD_STATUS, SYS_CODE, CREATE_USER) VALUES
 ('RE_ROLE_1', 'R_RE_ORGREV', '党建组织审核员', 0, 'RE', 'redengine-merge'),
 ('RE_ROLE_2', 'R_RE_BRREV',  '党建支部审核员', 0, 'RE', 'redengine-merge'),
 ('RE_ROLE_3', 'R_RE_SECR',   '党建支部书记',   0, 'RE', 'redengine-merge'),
 ('RE_ROLE_4', 'R_RE_REPORT', '党建报送员',     0, 'RE', 'redengine-merge');

-- 3) PT_ROLE_RESOURCE：按对照表"角色"列绑定（ID 用 MD5(角色+资源) 保幂等），示例：
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', x.rid)), r.ROLE_ID, x.rid, 'RE'
FROM PT_ROLE r
JOIN (SELECT 'P_RE_ORG_TREE' rid UNION SELECT 'P_RE_ORG_GET' UNION SELECT 'P_RE_SUBMIT_GET') x
WHERE r.ROLE_CODE IN ('R_RE_ORGREV','R_RE_BRREV','R_RE_SECR','R_RE_REPORT');
-- ...按对照表补齐其余分组绑定

-- 4) R_ADMIN 补行 SOP：管理员获得全部 P_RE_* 资源 + RED_ENGINE BizScope
INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE)
SELECT MD5(CONCAT(r.ROLE_ID, '#', p.RESOURCE_ID)), r.ROLE_ID, p.RESOURCE_ID, 'RE'
FROM PT_ROLE r JOIN PT_RESOURCE p ON p.RESOURCE_ID LIKE 'P\_RE\_%'
WHERE r.ROLE_CODE = 'R_ADMIN';

-- 5) PT_ROLE_BIZ_SCOPE：BIZ_TYPE='RED_ENGINE'，数据范围模块内自管故统一 ALL
INSERT IGNORE INTO PT_ROLE_BIZ_SCOPE (ID, ROLE_ID, BIZ_TYPE, DATA_SCOPE, RECORD_STATUS, CREATE_USER)
SELECT MD5(CONCAT(r.ROLE_ID, '#RED_ENGINE')), r.ROLE_ID, 'RED_ENGINE', 'ALL', 0, 'redengine-merge'
FROM PT_ROLE r
WHERE r.ROLE_CODE IN ('R_RE_ORGREV','R_RE_BRREV','R_RE_SECR','R_RE_REPORT','R_ADMIN');

-- 6) SYS_DICT / SYS_DICT_ITEM：org_type(3)/dimension(4)/submit_status(3)/item_code(8) 共 4 类 18 项，
--    内容照抄 redengine data.sql 158-193 行（dict 表列名以 yiti_test 实际 DESCRIBE 为准，编写时先 DESCRIBE 再写）

-- 7) RE_PARTY_ORG：党组织树 10 行，内容照抄 redengine data.sql 5-22 行（去 secretary_id）
```

- [ ] **Step 2: 核对前置事实再定稿**

```bash
mysql -uroot -pdjdev -e "DESCRIBE yiti_test.SYS_DICT; DESCRIBE yiti_test.SYS_DICT_ITEM;"
mysql -uroot -pdjdev -N -e "SELECT ROLE_ID, ROLE_CODE FROM yiti_test.PT_ROLE WHERE ROLE_CODE='R_ADMIN' OR ROLE_ID IN ('RE_ROLE_1','RE_ROLE_2','RE_ROLE_3','RE_ROLE_4');"
```
Expected: 拿到 SYS_DICT 真实列名（按其补全脚本第 6 段）；确认 R_ADMIN 存在且 RE_ROLE_* 无冲突（若 R_ADMIN 码不同，以实际管理员角色码替换）

- [ ] **Step 3: 两库执行 + 验证**

```bash
mysql -uroot -pdjdev yiti_test < docs/superpowers/sql/2026-07-18-redengine-seed.sql
mysql -uroot -pdjdev onepl_test_bootstrap < docs/superpowers/sql/2026-07-18-redengine-seed.sql
mysql -uroot -pdjdev -N -e "SELECT COUNT(*) FROM yiti_test.PT_RESOURCE WHERE RESOURCE_ID LIKE 'P\\_RE\\_%'; SELECT COUNT(*) FROM yiti_test.PT_ROLE_BIZ_SCOPE WHERE BIZ_TYPE='RED_ENGINE'; SELECT COUNT(*) FROM yiti_test.RE_PARTY_ORG;"
```
Expected: 17 / 5 / 10

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/sql/2026-07-18-redengine-seed.sql
git commit -m "feat(redengine): 权限资源/党建角色/BizScope/字典/党组织树种子（仅测试库）"
```

---

### Task 5: 实体 + Mapper（TDD，Mapper IT 走 onepl_test_bootstrap）

**Files:**
- Create: `red-engine-center/src/main/java/com/bank/branch/platform/redengine/entity/` 下 8 个实体：`RePartyOrg` `ReUserPartyMap` `ReSubmit` `ReSubmitFile` `ReScore` `ReMemberScore` `ReOverdueDeduction` `ReAnnualResult`
- Create: `red-engine-center/src/main/java/com/bank/branch/platform/redengine/mapper/` 下 8 个 Mapper 接口（全部 `extends BaseMapper<T>`，无 XML）
- Test: `red-engine-center/src/test/java/com/bank/branch/platform/redengine/mapper/RePartyOrgMapperIT.java`
- Create: `red-engine-center/src/test/resources/application-test.yml`（datasource 指 onepl_test_bootstrap，参考 performance-engine-center 同名文件抄格式）

**Interfaces:**
- Produces: 8 个实体（字段=Task 3 DDL 列的驼峰形式；`deleted` 字段标 `@TableLogic`；主键 `@TableId(type = IdType.AUTO)`）；8 个 `BaseMapper` 子接口。
- 实体全貌以 `RePartyOrg` 为范式（其余 7 个按 DDL 列同构展开）：

```java
package com.bank.branch.platform.redengine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** 红色引擎-党组织（党委/党支部树） */
@Data
@TableName("RE_PARTY_ORG")
public class RePartyOrg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orgName;
    private Long parentId;
    private Integer orgLevel;
    private String orgCode;
    private String orgType;
    private String principal;
    private String contactPhone;
    private String orgAddress;
    /** 支部书记平台用户工号 */
    private String secretaryId;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 1: 写失败的 Mapper IT**

```java
package com.bank.branch.platform.redengine.mapper;

import com.bank.branch.platform.redengine.entity.RePartyOrg;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** RE_PARTY_ORG 基础 CRUD 与软删链路（真库 onepl_test_bootstrap） */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RePartyOrgMapperIT {

    @Autowired
    private RePartyOrgMapper mapper;

    @Test
    void insertSelectAndLogicDelete() {
        RePartyOrg org = new RePartyOrg();
        org.setOrgName("TEST_RE_测试支部");
        org.setOrgLevel(2);
        org.setOrgCode("TEST_RE_001");
        assertEquals(1, mapper.insert(org));
        assertNotNull(org.getId());

        RePartyOrg loaded = mapper.selectById(org.getId());
        assertEquals("TEST_RE_测试支部", loaded.getOrgName());

        assertEquals(1, mapper.deleteById(org.getId()));   // @TableLogic → UPDATE deleted=1
        assertNull(mapper.selectById(org.getId()));         // 软删后查不到
    }
}
```

注：IT 的 Spring 上下文启动方式（@SpringBootTest 挂哪个配置类/是否需要 test 专用 @SpringBootApplication）**照抄 performance-engine-center 现有 Mapper IT 的做法**（先读 `performance-engine-center/src/test/java/.../PerformanceMapperTestBase.java` 及其 test application 类，同构复制到本模块，连接串换 onepl_test_bootstrap——若该文件本就指向 onepl_test_bootstrap 则直接同款）。

- [ ] **Step 2: 运行确认失败**

Run: `mvn test-compile -pl red-engine-center`
Expected: 编译失败（实体/Mapper 不存在）

- [ ] **Step 3: 实现 8 实体 + 8 Mapper**

Mapper 全部形如：

```java
package com.bank.branch.platform.redengine.mapper;

import com.bank.branch.platform.redengine.entity.RePartyOrg;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 党组织 Mapper（单条 CRUD 全用 BaseMapper 内置，无 XML） */
public interface RePartyOrgMapper extends BaseMapper<RePartyOrg> {
}
```

其余实体字段对照（全部 @Data + @TableName + @TableId(AUTO) + deleted 标 @TableLogic）：
- `ReUserPartyMap`(RE_USER_PARTY_MAP): id, userId, partyOrgId, partyRole, deleted, createTime, updateTime
- `ReSubmit`(RE_SUBMIT): id, orgId, submitterId(String), dimension, itemCode, itemName, maxScore(BigDecimal), projectName, submitType(Integer), submitDate(LocalDate), status(Integer), formData, fileUrls, reviewFeedback, reviewerId(String), reviewDate(LocalDateTime), deleted, createTime, updateTime
- `ReSubmitFile`(RE_SUBMIT_FILE): id, submitId, fileObjectId, fileName, filePath, fileSize(Long), fileType, deleted, createTime
- `ReScore`(RE_SCORE): id, orgId, submitId, itemCode, scoreYear(Integer), scorePeriod, baseScore, deductionScore, finalScore(均 BigDecimal), remark, deleted, createTime, updateTime
- `ReMemberScore`(RE_MEMBER_SCORE): id, orgId, userId(String), memberName, scorePeriod, score(BigDecimal), remark, deleted, createTime, updateTime
- `ReOverdueDeduction`(RE_OVERDUE_DEDUCTION): id, orgId, submitId, deductionReason, deductionPoints(BigDecimal), deductionDate(LocalDate), remark, deleted, createTime, updateTime
- `ReAnnualResult`(RE_ANNUAL_RESULT): id, orgId, dim1Score..dim4Score, totalScore, finalScore(均 BigDecimal), isQualified(Integer), evalYear(Integer), remark, createTime, updateTime（无 deleted，不标 @TableLogic）

- [ ] **Step 4: 跑 IT 确认通过**

Run: `mvn clean install -DskipTests -pl common/common-security,red-engine-center -am && mvn verify -pl red-engine-center -Dit.test=RePartyOrgMapperIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add red-engine-center/src
git commit -m "feat(redengine): 8 实体 + BaseMapper 接口 + RePartyOrgMapperIT（TDD）"
```

---

### Task 6: 用户党组织映射服务（RE_USER_PARTY_MAP）

**Files:**
- Create: `red-engine-center/src/main/java/com/bank/branch/platform/redengine/service/ReUserPartyMapService.java`
- Create: `red-engine-center/src/main/java/com/bank/branch/platform/redengine/controller/ReUserPartyMapController.java`
- Create: `red-engine-center/src/main/java/com/bank/branch/platform/redengine/api/dto/ReUserPartyMapDTO.java`
- Test: `red-engine-center/src/test/java/com/bank/branch/platform/redengine/service/ReUserPartyMapServiceTest.java`

**Interfaces:**
- Produces（后续 Task 7-10 依赖）:
  - `ReUserPartyMapService.getRequiredPartyOrgId(String empId): Long` — 查当前用户映射党组织，无映射抛 `BizException("RE-40001", "当前用户未绑定党组织，请联系管理员")`
  - `ReUserPartyMapService.bind(String userId, Long partyOrgId, String partyRole): void` — upsert（uk_user 唯一）
  - `ReUserPartyMapService.list(): List<ReUserPartyMapDTO>`
- Controller 端点（资源号见 Task 4 对照表）: `GET /api/re/user-party-maps`（`@BizAuth(RED_ENGINE, LIST)`）、`POST /api/re/user-party-maps`（`@BizAuth(RED_ENGINE, CONFIG)` + `@AuditLog(action="RE_MAP_BIND", resourceType="RE_USER_PARTY_MAP", reasonRequired=false)`）

- [ ] **Step 1: 写失败单测**（Mockito mock `ReUserPartyMapMapper`，3 case：命中返回 orgId / 无映射抛 RE-40001 / bind 已存在走 update）
- [ ] **Step 2: 运行失败** `mvn test -pl red-engine-center -Dtest=ReUserPartyMapServiceTest` → 编译失败
- [ ] **Step 3: 实现 Service + Controller + DTO**（Service 方法全部加中文注释；bind 用 `LambdaQueryWrapper<ReUserPartyMap>().eq(ReUserPartyMap::getUserId, userId)` 查存在→update/insert）
- [ ] **Step 4: 运行通过** 同命令 → PASS
- [ ] **Step 5: Commit** `git commit -m "feat(redengine): 用户党组织映射服务+管理端点（TDD）"`

---

### Task 7: 党组织管理（移植 OrgController → ReOrgController）

**Files:**
- Create: `service/RePartyOrgService.java`、`controller/ReOrgController.java`（包前缀 `com.bank.branch.platform.redengine`，下同）
- Test: `service/RePartyOrgServiceTest.java`

**Interfaces:**
- Consumes: `RePartyOrgMapper`
- Produces: `RePartyOrgService.getOrgTree(): List<RePartyOrgTreeDTO>`（父子树，源逻辑照抄 redengine `PartyOrgServiceImpl.getOrgTree`）、`getById/add/update/delete`
- 端点（与 Task 4 对照表一致）：GET `/api/re/orgs/tree`(LIST) / GET `/api/re/orgs/{id}`(READ) / POST `/api/re/orgs`(WRITE+AuditLog) / PUT `/api/re/orgs/{id}`(WRITE+AuditLog) / DELETE `/api/re/orgs/{id}`(DELETE+`@AuditLog(reasonRequired=true)` 高危)

**移植变换规则 T（本 Task 起所有后端移植统一适用，标注为 T1-T7）：**
- T1 包名 `com.redengine.modules.*` → `com.bank.branch.platform.redengine.*`；实体/Mapper 用 Task 5 产物
- T2 `Result<T>`/`Result.ok/fail` → `ResponseWrapper<T>`/`ResponseWrapper.success(...)`；异常不再 try-catch 吞掉，直接抛 `BizException`（GlobalExceptionHandler 统一映射），删除所有 `catch (Exception e) { return Result.fail(...) }` 包裹
- T3 `@PreAuthorize("@ps.hasPermission('xxx')")` → `@BizAuth(bizType = BizType.RED_ENGINE, action = ...)`（action 映射：view/queue→LIST 或 READ；add/approve/reject/submit→WRITE；delete→DELETE；execute→EXECUTE；export→EXPORT；org 管理→CONFIG 或 WRITE，按 Task 4 表）
- T4 `SecurityContextHolder...LoginUser` → 注入 `CurrentUserApi`，取 `getCurrentEmpId()`；原 `loginUser.getUser().getOrgId()` → `reUserPartyMapService.getRequiredPartyOrgId(empId)`
- T5 redengine `PageResult.from(IPage)` → common-web `PageResult<T>`（字段 pageNo/pageSize/total/records，从 `IPage` 手工装配：`new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords())`——以 common-web `PageResult` 实际构造器为准，编写时先读该类）
- T6 `javax.*` import → `jakarta.*`；`java.util.Date` → `java.time.*`（实体已是 LocalDate/LocalDateTime）
- T7 所有 Service public 方法补中文注释；写方法 `@Transactional(rollbackFor = Exception.class)`

- [ ] **Step 1: 写失败单测**（mock mapper：树构建 1 父 2 子断言层级；delete 有子节点抛 `RE-40002 存在下级党组织不可删除`——源系统无此校验，**新增防呆**）
- [ ] **Step 2: 运行失败** `mvn test -pl red-engine-center -Dtest=RePartyOrgServiceTest`
- [ ] **Step 3: 按 T1-T7 移植实现**（源文件：`redengine/red-engine-server/.../system/controller/OrgController.java` + `service/impl/PartyOrgServiceImpl.java`）
- [ ] **Step 4: 运行通过**
- [ ] **Step 5: Commit** `git commit -m "feat(redengine): 党组织树管理移植（TDD，T1-T7 变换）"`

---

### Task 8: 材料上报（SubmitController → ReSubmitController）

**Files:**
- Create: `service/ReSubmitService.java`、`controller/ReSubmitController.java`、`api/dto/ReSubmitCreateReqDTO.java`
- Test: `service/ReSubmitServiceTest.java`

**Interfaces:**
- Consumes: `ReUserPartyMapService.getRequiredPartyOrgId`、`ReSubmitMapper`、`ReSubmitFileMapper`、governance `FileApi.bindFile`
- Produces: `createSubmit(ReSubmitCreateReqDTO, empId): Long`（status 置 1=已提交；附件按 fileObjectId 列表写 RE_SUBMIT_FILE + `FileApi.bindFile("RE_SUBMIT", submitId, fileObjectId, "ATTACHMENT")`）；`getMySubmits(empId, pageNo, pageSize): PageResult<ReSubmit>`（按映射党组织过滤）；`getDetail(id): ReSubmit`
- 端点：POST `/api/re/submits`(WRITE+AuditLog) / GET `/api/re/submits/my`(LIST) / GET `/api/re/submits/{id}`(READ)
- 说明：源系统"草稿 status=0"入口实际未被前端使用（createSubmit 直接置 0 但列表当已提交处理），移植时明确语义：**创建即 status=1 已提交**，草稿功能不做（YAGNI，与前端 JointView 提交行为一致）；附件上传本身走 governance 既有 `POST /api/files/upload`（前端直传拿 fileObjectId，本模块不再自建文件端点，源 FileController/uploadFile 端点废弃）

- [ ] **Step 1: 写失败单测**（3 case：create 落库 status=1 且逐附件 bindFile；my 列表按党组织过滤分页；未绑定党组织抛 RE-40001）
- [ ] **Step 2: 运行失败**
- [ ] **Step 3: 按 T1-T7 移植实现**（源：`SubmitController.java` + `BizSubmitServiceImpl.java`）
- [ ] **Step 4: 运行通过** `mvn test -pl red-engine-center -Dtest=ReSubmitServiceTest`
- [ ] **Step 5: Commit** `git commit -m "feat(redengine): 材料上报移植——附件走平台 FileApi（TDD）"`

---

### Task 9: 两级审核（ReviewController → ReReviewController）

**Files:**
- Create: `service/ReReviewService.java`、`controller/ReReviewController.java`
- Test: `service/ReReviewServiceTest.java`

**Interfaces:**
- Consumes: `ReSubmitMapper`、`ReScoreMapper`、`CurrentUserApi`
- Produces: `getReviewQueue(pageNo, pageSize): PageResult<ReSubmit>`（status=1 按 submitDate 升序）；`approve(id, score, feedback, empId)`；`reject(id, feedback, empId)`
- 端点：GET `/api/re/reviews/queue`(LIST) / GET `/api/re/reviews/{id}/preview`(READ，复用 P_RE_REVIEW_Q 资源即可，URL 写 `/api/re/reviews/**` 或单独登记——**编写 seed 时按最终选择对齐**) / POST `/api/re/reviews/{id}/approve`(WRITE+AuditLog) / POST `/api/re/reviews/{id}/reject`(WRITE+AuditLog)
- **核心业务规则原样移植**（源 `BizReviewServiceImpl.approveSubmit` 52-110 行）：
  1. 上报不存在抛 `RE-40003 提交记录不存在`
  2. score>0 时校验同 org+item_code+score_year 的累计 final_score + 本次 ≤ max_score（无 maxScore 回退 100），超限抛 `RE-40004 考核项[X]累计得分已达N分，上限M分，本次最多可计K分`
  3. 通过：插 RE_SCORE(orgId, submitId, itemCode, scoreYear=当年, finalScore=score, remark=feedback) + 更新 RE_SUBMIT(status=2, reviewFeedback, reviewerId=empId, reviewDate=now)
  4. 驳回：status=3 + feedback + reviewerId + reviewDate

- [ ] **Step 1: 写失败单测**（4 case：队列只含 status=1；approve 超限抛 RE-40004 且不落 score；approve 正常落 RE_SCORE+状态 2；reject 状态 3）
- [ ] **Step 2: 运行失败**
- [ ] **Step 3: 按 T1-T7 移植**（reviewerId 从 CurrentUserApi 取，源系统未记审核人，此处补齐）
- [ ] **Step 4: 运行通过** `mvn test -pl red-engine-center -Dtest=ReReviewServiceTest`
- [ ] **Step 5: Commit** `git commit -m "feat(redengine): 两级审核+评分上限校验移植（TDD）"`

---

### Task 10: 驾驶舱/预警/年度归档（CockpitController → ReCockpitController）

**Files:**
- Create: `service/ReCockpitService.java`、`controller/ReCockpitController.java`
- Test: `service/ReCockpitServiceTest.java`

**Interfaces:**
- Consumes: `ReSubmitMapper`、`ReScoreMapper`、`ReOverdueDeductionMapper`、`ReAnnualResultMapper`
- Produces（返回结构与源一致，Map 改为显式 DTO——遵守"禁止 Object 通用参数"红线）：`getOverview(): ReCockpitOverviewDTO{totalSubmits,approvedCount,pendingCount,rejectedCount}`；`getRanking(): List<ReRankingItemDTO{rank,orgId,finalScore,period}>`；`getOverdueList(): List<ReOverdueItemDTO>`（status∈{0,1} 且 submitDate+7 天 < 今天）；`executeOverdue(submitId, deductionPoints)`（默认 5 分，插 RE_OVERDUE_DEDUCTION）；`getRedWarning()/getYellowWarning()`（final_score<60 红 / 60≤x<80 黄——**照抄源码阈值 80**，源注释 75 与代码 80 不一致时以代码为准）。同理逾期扣分：spec §5.4 的"迟 1 天 −1 分、≥3 天清零"是源系统文档口径，Java 代码实际是"逾期列表=submitDate+7 天未结、人工执行扣分默认 5 分"——**移植以代码为准**，两处口径差异记入 Task 18 勘误；`archiveSettlement(period)`；`generateAnnualResult(year)`（按 org 聚合 dim1-4 → total → final=total*0.4 → is_qualified=total≥60，upsert uk_org_year）
- 端点：GET `/api/re/cockpit/overview|ranking|overdue|warning/red|warning/yellow|archive/settlement`（全部 `@BizAuth(RED_ENGINE, READ)`，资源 P_RE_CKPT_VIEW=`/api/re/cockpit/**` GET 一条覆盖）/ POST `/api/re/cockpit/overdue/execute`(EXECUTE+AuditLog reasonRequired=true) / POST `/api/re/cockpit/archive/generate/{year}`(EXECUTE+AuditLog)

- [ ] **Step 1: 写失败单测**（4 case：overview 计数；红黄牌阈值边界 59.9/60/79.9/80；overdue 7 天边界；annual upsert 与 is_qualified 拦截）
- [ ] **Step 2: 运行失败**
- [ ] **Step 3: 按 T1-T7 移植**（源 `BizCockpitServiceImpl.java` 全文件；generateAnnualResult 里"dimension 聚合"源码是残缺实现（只建 org 键不聚合值），移植时**补全**：按 RE_SCORE join RE_SUBMIT.dimension 分组求和——在单测里先固化该正确行为）
- [ ] **Step 4: 运行通过** `mvn test -pl red-engine-center -Dtest=ReCockpitServiceTest`
- [ ] **Step 5: Commit** `git commit -m "feat(redengine): 驾驶舱/红黄牌/年度归档移植并补全维度聚合（TDD）"`

---

### Task 11: 数据导出（ExportController → ReExportController，EasyExcel）

**Files:**
- Create: `controller/ReExportController.java`、`service/ReExportService.java`
- Test: `service/ReExportServiceTest.java`

**Interfaces:**
- Produces: GET `/api/re/export/{type}`（type∈{submit,score}，`@BizAuth(RED_ENGINE, EXPORT)` + `@AuditLog(action="RE_EXPORT", resourceType="RE_SUBMIT")`），返回 `ResponseEntity<byte[]>` xlsx；表头与源一致（submit: ID/Organization/Project/Type/Date/Status；score: ID/Organization/Period/Base Score/Deduction/Final Score）
- 实现：hutool ExcelWriter → EasyExcel（平台既有依赖）；导出行上限 10000 防 OOM（新增防呆，超限抛 `RE-40005 导出数据超过上限，请缩小范围`）

- [ ] **Step 1: 写失败单测**（2 case：type 非法抛 IllegalArgumentException→BizException RE-40006；生成字节流非空且 XLSX 魔数 `PK`）
- [ ] **Step 2: 运行失败** → **Step 3: 实现** → **Step 4: 通过** `mvn test -pl red-engine-center -Dtest=ReExportServiceTest` → **Step 5: Commit** `git commit -m "feat(redengine): 数据导出移植 hutool→EasyExcel（TDD）"`

---

### Task 12: 后端集成冒烟 IT（remerge 全链路）

**Files:**
- Test: `bootstrap/src/test/java/com/bank/branch/platform/it/RedEngineSmokeIT.java`

**Interfaces:**
- Consumes: Task 5-11 全部产物 + Task 3/4 测试库数据

- [ ] **Step 1: 写 IT**（照抄 bootstrap 现有 IT 的注解/基类模式——先读 `bootstrap/src/test/java/.../PortalWorkspaceMetricIT.java` 同构：@SpringBootTest + MockMvc；case：未登录访问 `/api/re/orgs/tree` → 401；管理员 session 访问 → 200 且 10 节点；无 P_RE 资源角色访问 → 403）
- [ ] **Step 2: stale jar 防护后运行**

```bash
cd /home/djdev/leid/yiti && mvn clean install -DskipTests && mvn verify -pl bootstrap -Dit.test=RedEngineSmokeIT
```
Expected: PASS
- [ ] **Step 3: Commit** `git commit -m "test(redengine): bootstrap 集成冒烟 IT——鉴权链路 401/403/200"`

---

### Task 13: 前端路由区 + RedEngineLayout + 登录页

**Files:**
- Modify: `xanzc_frontend/src/router/index.js`（顶层加 2 条路由）
- Create: `xanzc_frontend/src/views/redengine/layout/RedEngineLayout.vue`（移植自 `redengine/red-engine-web/src/layout/MainLayout.vue` + `Sidebar.vue` + `SidebarItem.vue` + `TopNav.vue`）
- Create: `xanzc_frontend/src/views/redengine/login/LoginView.vue`（移植自同名文件，**模板与 style 原样保留**）

**Interfaces:**
- Produces: 路由 `/redengine/login`（public）与 `/redengine/**`（RedEngineLayout 子路由，见 Task 14 清单）；菜单过滤函数 `canSee(item)`（基于 GET `/api/auth/permissions` 返回的 `resourceUrls` 集合判断 item.res 是否在内；拉取失败降级全显示，后端 403 兜底）

**前端移植变换规则 F（Task 13-15 统一适用）：**
- F1 `import request from './request'` / `@/api/request` → `import http, { call } from '@/api/http'`；所有 API 调用改经 `src/api/redengine.js`（Task 14 建）
- F2 JWT 相关全部删除：`utils/auth.js` 的 getToken/setToken、Authorization 头、`localStorage` token；登录成功依赖 session cookie（http.js 已 `withCredentials: true`）
- F3 路由路径 `/dashboard` 等 → `/redengine/dashboard`；`router.push('/login')` → `router.push('/redengine/login')`
- F4 样式类名与全局样式：组件内 style 全部 `scoped`；类名加 `re-` 前缀仅当发生与平台类名冲突时（先保真移植，冲突再改）；禁止向 `src/styles/` 全局文件添加内容
- F5 `v-permission` 指令 → 删除或改用 `canSee()`（layout 提供 provide/inject）
- F6 Element Plus 版本差异：redengine 用 2.7、平台 2.5——若用到 2.5 不存在的 API，以 2.5 语法回写

- [ ] **Step 1: 路由注册**（`/screen/:screenCode` 路由之后插入）：

```js
  // 红色引擎（党建）：独立登录页 + 独立布局路由区，风格与平台主布局隔离
  {
    path: '/redengine/login',
    name: 'RedEngineLogin',
    component: () => import('@/views/redengine/login/LoginView.vue'),
    meta: { title: '红色引擎-登录', public: true }
  },
  {
    path: '/redengine',
    component: () => import('@/views/redengine/layout/RedEngineLayout.vue'),
    redirect: '/redengine/dashboard',
    children: [] // Task 14 填充
  },
```

- [ ] **Step 2: LoginView 移植**：模板/样式整体照搬源文件；`<script setup>` 改为调平台登录（参考 `xanzc_frontend/src/views/login/Index.vue` 137 行做法）：

```js
import { login } from '@/api/auth';
// 提交处理：成功后进红色引擎工作台
async function handleLogin() {
  loading.value = true;
  try {
    await login(form.username, form.password);
    router.push('/redengine/dashboard');
  } catch (e) {
    errorMsg.value = e?.message || '登录失败';
  } finally {
    loading.value = false;
  }
}
```

- [ ] **Step 3: RedEngineLayout 移植**：菜单数据源改为本地数组（源 dynamicRoutes meta 翻译），每项带 `res` 字段（资源 URL）：

```js
const menuItems = [
  { path: '/redengine/dashboard',     title: '工作台',           icon: 'HomeFilled', res: null },
  { path: '/redengine/report',        title: '四大维度材料上报', icon: 'EditPen',    res: '/api/re/submits' },
  { path: '/redengine/records',       title: '上报记录',         icon: 'Document',   res: '/api/re/submits/my' },
  { path: '/redengine/branch-review', title: '支部审核工作台',   icon: 'Stamp',      res: '/api/re/reviews/queue' },
  { path: '/redengine/cockpit',       title: '全局数据驾驶舱',   icon: 'DataAnalysis', res: '/api/re/cockpit/**' },
  { path: '/redengine/warning',       title: '红黄牌预警池',     icon: 'WarningFilled', res: '/api/re/cockpit/**' },
  { path: '/redengine/review',        title: '沉浸式审核工作台', icon: 'Checked',    res: '/api/re/reviews/queue' },
  { path: '/redengine/archive',       title: '年度考核归档',     icon: 'Trophy',     res: '/api/re/cockpit/**' },
  { path: '/redengine/export',        title: '数据导出',         icon: 'Download',   res: '/api/re/export/*' },
  { path: '/redengine/org-manage',    title: '党组织管理',       icon: 'Setting',    res: '/api/re/orgs' },
];
// mounted 时 http.get('/api/auth/permissions') → resourceUrls Set；canSee = !res || set.has(res)
```

- [ ] **Step 4: 手动冒烟**：`npm run dev`（8090），后端 remerge 起 18081；访问 `http://localhost:8090/#/redengine/login` 检查外观与登录跳转
- [ ] **Step 5: Commit** `git commit -m "feat(fe): 红色引擎路由区+独立布局+登录页移植（保留原外观，登录走平台 session）"`

---

### Task 14: 前端 API 层 + 业务视图迁移（9 个业务视图）

**Files:**
- Create: `xanzc_frontend/src/api/redengine.js`
- Create: `xanzc_frontend/src/views/redengine/` 下按源目录结构迁 9 个业务视图：dashboard/DashboardView.vue、report/JointView.vue、records/RecordsView.vue、branch-review/BranchReviewView.vue、review/ReviewView.vue、cockpit/CockpitView.vue、warning/WarningView.vue、archive/ArchiveView.vue、export/ExportView.vue
- Modify: `xanzc_frontend/src/router/index.js`（/redengine children 填 9 条）

**Interfaces:**
- Produces `src/api/redengine.js`（完整函数清单，全部走 http.js 的 `call`；URL 与 Task 4 对照表一致）：

```js
import { call } from '@/api/http';

// 党组织
export const getOrgTree   = () => call('get', '/re/orgs/tree');
export const getOrg       = (id) => call('get', `/re/orgs/${id}`);
export const addOrg       = (data) => call('post', '/re/orgs', { data });
export const updateOrg    = (id, data) => call('put', `/re/orgs/${id}`, { data });
export const deleteOrg    = (id) => call('delete', `/re/orgs/${id}`);
// 用户党组织映射
export const listUserMaps = () => call('get', '/re/user-party-maps');
export const bindUserMap  = (data) => call('post', '/re/user-party-maps', { data });
// 上报
export const createSubmit = (data) => call('post', '/re/submits', { data });
export const getMySubmits = (pageNo = 1, pageSize = 10) => call('get', '/re/submits/my', { params: { pageNo, pageSize } });
export const getSubmit    = (id) => call('get', `/re/submits/${id}`);
// 审核
export const getReviewQueue = (pageNo = 1, pageSize = 10) => call('get', '/re/reviews/queue', { params: { pageNo, pageSize } });
export const getReviewPreview = (id) => call('get', `/re/reviews/${id}/preview`);
export const approveSubmit  = (id, data) => call('post', `/re/reviews/${id}/approve`, { data });
export const rejectSubmit   = (id, data) => call('post', `/re/reviews/${id}/reject`, { data });
// 驾驶舱
export const getCockpitOverview = () => call('get', '/re/cockpit/overview');
export const getRanking      = () => call('get', '/re/cockpit/ranking');
export const getOverdueList  = () => call('get', '/re/cockpit/overdue');
export const executeOverdue  = (params) => call('post', '/re/cockpit/overdue/execute', { params });
export const getRedWarning   = () => call('get', '/re/cockpit/warning/red');
export const getYellowWarning = () => call('get', '/re/cockpit/warning/yellow');
export const archiveSettlement = (period) => call('get', '/re/cockpit/archive/settlement', { params: { period } });
export const generateAnnual  = (year) => call('post', `/re/cockpit/archive/generate/${year}`);
// 导出（二进制直下）
export const exportData = (type) => call('get', `/re/export/${type}`, { responseType: 'blob' });
// 文件上传（复用平台 governance 端点）
export const uploadFile = (formData) => call('post', '/files/upload', { data: formData, headers: { 'Content-Type': 'multipart/form-data' } });
```

- [ ] **Step 1: 建 api/redengine.js**（上面完整内容）
- [ ] **Step 2: 逐视图迁移**（按 F1-F6；每个视图源文件在 `redengine/red-engine-web/src/views/<同名目录>`；模板+样式保真，script 里 API import 全部换成 `@/api/redengine`；分页字段注意 http.js 已解包 `body.page → {records,total}`，用 `unwrapPage` 兜底）
- [ ] **Step 3: 路由 children 填充**（9 条，path 不带 /redengine 前缀由嵌套提供，meta.title 同源）
- [ ] **Step 4: 构建冒烟** `cd xanzc_frontend && npm run build`
Expected: 构建成功无 import 报错
- [ ] **Step 5: Commit** `git commit -m "feat(fe): 红色引擎 9 业务视图迁移 + api/redengine.js（走平台 session/api 代理）"`

---

### Task 15: 前端管理视图（党组织管理 + 用户映射）+ Vitest

**Files:**
- Create: `xanzc_frontend/src/views/redengine/system/OrgManageView.vue`（源 system/OrgView.vue 移植，加"支部书记工号"编辑）
- Create: `xanzc_frontend/src/views/redengine/system/UserMapView.vue`（新写：表格列出映射 + 弹窗绑定 userId/党组织下拉/党内角色下拉）
- Modify: `xanzc_frontend/src/router/index.js`（/redengine children 追加 org-manage、user-map 两条）
- Test: `xanzc_frontend/src/views/redengine/__tests__/RedEngineLogin.spec.js`、`RedEngineMenuFilter.spec.js`

**Interfaces:**
- Consumes: `api/redengine.js` 的 org/map 函数
- 源系统 UserView/RoleView/MenuView **不迁移**（平台系统设置取代）

- [ ] **Step 0: 平台门户加入口**（spec §7）：查 `mysql -uroot -pdjdev -e "DESCRIBE yiti_test.PORTAL_SHORTCUT"`，若结构含 名称/URL/排序 类字段则在 Task 4 种子脚本追加一行"红色引擎 → /#/redengine"快捷方式（仅测试库）；若快捷方式非数据驱动，则在 `DefaultLayout.vue` 顶栏加一个跳转 `/redengine` 的链接入口（最小侵入，不动全局样式）
- [ ] **Step 1: 写失败 Vitest**（2 个：LoginView 挂载渲染登录按钮并 mock login 成功后 push `/redengine/dashboard`；菜单过滤——resourceUrls 只含 `/api/re/submits/my` 时 canSee 只放行"上报记录/工作台"）
- [ ] **Step 2: 运行失败** `cd xanzc_frontend && npx vitest run src/views/redengine/__tests__`
- [ ] **Step 3: 实现两个视图 + 路由**
- [ ] **Step 4: 运行通过** 同命令
- [ ] **Step 5: Commit** `git commit -m "feat(fe): 党组织管理/用户映射视图 + 红色引擎 vitest（TDD）"`

---

### Task 16: 演示数据导入 yiti_test（验收基准）

**Files:**
- Create: `docs/superpowers/sql/2026-07-18-redengine-demo-data-yiti-test-only.sql`

**Interfaces:**
- Consumes: `redengine/red_engine.db`（SQLite：16 submit / 16 score / 4 deduction）
- Produces: yiti_test 中可回放的演示数据（**不进 onepl_test_bootstrap、不随正式上线**，文件名带 only 后缀明示）

- [ ] **Step 1: 用 python3 + sqlite3 读出三表数据生成 INSERT**（org_id 直接沿用 1-10 对齐 Task 4 党组织树自增 ID；submitter_id 统一置 'admin'；file_urls 原样带过去；脚本头部注释注明仅测试库）
- [ ] **Step 2: 执行 + 验证**

```bash
mysql -uroot -pdjdev yiti_test < docs/superpowers/sql/2026-07-18-redengine-demo-data-yiti-test-only.sql
mysql -uroot -pdjdev -N -e "SELECT COUNT(*) FROM yiti_test.RE_SUBMIT; SELECT COUNT(*) FROM yiti_test.RE_SCORE; SELECT COUNT(*) FROM yiti_test.RE_OVERDUE_DEDUCTION;"
```
Expected: 16 / 16 / 4
- [ ] **Step 3: Commit** `git commit -m "chore(redengine): 演示数据导入脚本（仅 yiti_test 验收用）"`

---

### Task 17: 全链路联调（Playwright）+ 全量回归

**Files:**
- 无新增源码（联调脚本可临时放 scratchpad，不入库）

- [ ] **Step 1: 起后端（remerge）+ 前端 dev**，管理员账号绑定党组织映射（`POST /api/re/user-party-maps`）
- [ ] **Step 2: Playwright 回放主链路**（按项目 Playwright 约定：`login?normal` admin/123456，进程级 `env -u` 绕代理）：红色引擎登录页登录 → 工作台 → 新建上报（含附件上传）→ 审核队列通过（评分）→ 驾驶舱数字变化 → 红黄牌列表 → 导出下载。每步截图留档 scratchpad
- [ ] **Step 3: 后端全量回归**

```bash
cd /home/djdev/leid/yiti && mvn clean install -DskipTests && mvn test
```
Expected: 全绿（既有 9 模块无回归；如有既有 baseline 失败需与 master 对照确认非本次引入）
- [ ] **Step 4: 前端全量回归** `cd xanzc_frontend && npx vitest run`
Expected: 全绿
- [ ] **Step 5: Commit（如联调修复了缺陷）** `git commit -m "fix(redengine): 联调修复——<按实际>"`

---

### Task 18: 文档同步 + 收尾

**Files:**
- Create: `red-engine-center/CLAUDE.md`（模块说明：概述/包结构/8 表/17 资源/角色矩阵/状态机/与源系统差异清单——用户主键改工号、附件走 OBS、草稿态废弃、警戒阈值 80、annual 聚合补全）
- Modify: `CLAUDE.md`（根：模块表加 red-engine-center 一行、依赖图加节点）
- Modify: `docs/superpowers/specs/2026-07-18-redengine-merge-design.md`（如实现与规格有偏差，补"实现勘误"小节）

- [ ] **Step 1: 写模块 CLAUDE.md**
- [ ] **Step 2: 根 CLAUDE.md 模块表/依赖图更新**
- [ ] **Step 3: Commit** `git commit -m "docs(redengine): 模块 CLAUDE.md + 根文档模块表/依赖图同步"`

---

## 验收清单（Definition of Done）

1. `yiti` 正式库零变更（`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='yiti' AND table_name LIKE 'RE\_%'` = 0）
2. 备份文件 `backup/yiti_full_20260718.sql` 存在
3. `mvn clean install && mvn test` 全绿；`mvn verify -pl red-engine-center,bootstrap` 关键 IT 绿
4. Playwright 主链路（登录→上报→审核→评分→驾驶舱→导出）通过，登录页外观与源系统一致
5. 权限矩阵抽查：报送员角色看不到审核队列（403）；无党组织映射用户操作上报给出 RE-40001 明确提示；管理员全通
6. master 分支未被污染（所有 commit 在 feature/redengine-merge）
