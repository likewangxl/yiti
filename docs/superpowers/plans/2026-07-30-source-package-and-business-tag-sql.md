# 前后端源码包与业务标签部署 SQL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在同一发布目录生成前端源码 ZIP、后端源码 ZIP 和包含业务标签建表、菜单资源及最小权限授权的单一 SQL 文件。

**Architecture:** 使用最终态 DDL 和幂等资源种子生成独立部署 SQL；角色授权不硬编码角色 ID，而是从业务标签既有参照资源及 `M_PERF_KPI_RULES` 菜单动态复制。源码包使用 `git archive` 从当前 `HEAD` 生成，只收录已跟踪文件并按前后端目录拆分。

**Tech Stack:** Git、ZIP、Bash、MySQL 8、项目现有 `PT_RESOURCE`/`PT_ROLE_RESOURCE` 权限模型

## Global Constraints

- 发布目录固定为 `release/yiti-20260730/`。
- 前端文件名固定为 `yiti-frontend-src-20260730.zip`。
- 后端文件名固定为 `yiti-backend-src-20260730.zip`。
- SQL 文件名固定为 `yiti-business-tag-deploy-20260730.sql`。
- 压缩包不得包含 `node_modules`、`dist`、`target`、`coverage`、日志目录及 `.git`。
- SQL 使用 UTF-8 与 MySQL 8 语法，不依赖 Flyway。
- KPI 规则角色只获得 `G_PTAG_LIST`，不获得业务标签菜单或写接口权限。

---

### Task 1: 生成业务标签部署 SQL

**Files:**
- Create: `release/yiti-20260730/yiti-business-tag-deploy-20260730.sql`
- Reference: `docs/superpowers/sql/2026-07-20-person-tag-tables-and-menu.sql`
- Reference: `docs/superpowers/sql/2026-07-24-person-tag-dim-org.sql`
- Reference: `docs/superpowers/sql/2026-07-24-person-tag-rename-to-business-tag.sql`
- Reference: `docs/superpowers/sql/2026-07-21-grant-person-tags-readonly-to-back-finance.sql`

**Interfaces:**
- Consumes: `PT_RESOURCE`、`PT_ROLE_RESOURCE`、`M_SYS_DICT`、`G_DICT_CREATE`、`M_PERF_KPI_RULES`
- Produces: `PERSON_TAG`、`PERSON_TAG_REL`、`M_SYS_PERSON_TAGS`、十二个 `G_PTAG_*` 资源及其角色绑定

- [ ] **Step 1: 创建发布目录**

Run:

```bash
mkdir -p release/yiti-20260730
```

Expected: `release/yiti-20260730` 存在且为空，或仅含本次可覆盖交付物。

- [ ] **Step 2: 写入最终态建表语句**

在 SQL 中直接创建包含机构维度的最终结构：

```sql
CREATE TABLE IF NOT EXISTS `PERSON_TAG` (
  `TAG_ID` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `TAG_NAME` varchar(100) NOT NULL COMMENT '标签名称',
  `REMARK` varchar(500) DEFAULT NULL COMMENT '备注',
  `CREATE_BY` varchar(50) DEFAULT NULL COMMENT '创建人工号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_BY` varchar(50) DEFAULT NULL COMMENT '更新人工号',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`TAG_ID`),
  UNIQUE KEY `UK_PERSON_TAG_NAME` (`TAG_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务标签（全平台通用）';

CREATE TABLE IF NOT EXISTS `PERSON_TAG_REL` (
  `ID` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `TAG_ID` bigint NOT NULL COMMENT '标签ID（PERSON_TAG.TAG_ID）',
  `DIM_TYPE` varchar(8) NOT NULL DEFAULT 'EMP' COMMENT '成员维度：EMP=员工 / ORG=机构',
  `USERNAME` varchar(200) DEFAULT NULL COMMENT '员工工号（PT_USER.USERNAME），DIM_TYPE=EMP 时有值',
  `ORG_DEPT_NO` varchar(60) DEFAULT NULL COMMENT '机构业务编号（EXT_ORG_INFO.DEPT_NO），DIM_TYPE=ORG 时有值',
  `CREATE_BY` varchar(50) DEFAULT NULL COMMENT '创建人工号',
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UK_PTR_TAG_USER` (`TAG_ID`, `USERNAME`),
  UNIQUE KEY `UK_PTR_TAG_ORG` (`TAG_ID`, `ORG_DEPT_NO`),
  KEY `IDX_PTR_USERNAME` (`USERNAME`),
  KEY `IDX_PTR_ORG_DEPT_NO` (`ORG_DEPT_NO`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务标签成员关联';
```

- [ ] **Step 3: 写入菜单和十二个接口资源**

使用 `INSERT ... ON DUPLICATE KEY UPDATE` 登记：

```text
M_SYS_PERSON_TAGS
G_PTAG_LIST
G_PTAG_CREATE
G_PTAG_UPDATE
G_PTAG_DELETE
G_PTAG_MEMBERS
G_PTAG_MEM_ADD
G_PTAG_MEM_UPD
G_PTAG_MEM_DEL
G_PTAG_IMPORT
G_PTAG_TPL
G_PTAG_MEM_IMP
G_PTAG_MEM_TPL
```

资源 URL、HTTP 方法和父资源必须与 `AdminPersonTagController` 及现有资源定义一致；菜单名和接口名称统一使用“业务标签”。

- [ ] **Step 4: 写入最小角色授权**

使用 `NOT EXISTS` 防重复，写入三组授权：

```sql
-- 系统管理员参照既有字典管理权限：菜单沿用 M_SYS_DICT，全部接口沿用 G_DICT_CREATE。
-- 资财部经办人只读：沿用当前 BACK_FINANCE 对业务标签菜单、列表、成员列表的现状。
-- KPI 规则页：将 M_PERF_KPI_RULES 的角色复制到 G_PTAG_LIST，不复制菜单和写接口。
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), rr.ROLE_ID, 'G_PTAG_LIST', 'PLATFORM', NOW()
  FROM PT_ROLE_RESOURCE rr
 WHERE rr.RESOURCE_ID = 'M_PERF_KPI_RULES'
   AND NOT EXISTS (
       SELECT 1 FROM PT_ROLE_RESOURCE x
        WHERE x.ROLE_ID = rr.ROLE_ID
          AND x.RESOURCE_ID = 'G_PTAG_LIST'
   );
```

完整 SQL 同时包含授权后自检查询，分别核对资源数量、业务标签管理权限和 KPI 规则查询权限。

- [ ] **Step 5: 静态检查 SQL**

Run:

```bash
rg -n "CREATE TABLE IF NOT EXISTS|M_SYS_PERSON_TAGS|G_PTAG_|M_PERF_KPI_RULES|NOT EXISTS" release/yiti-20260730/yiti-business-tag-deploy-20260730.sql
```

Expected: 两个建表语句、一个菜单、十二个接口资源和三组权限逻辑均可检索到。

### Task 2: 生成前后端源码压缩包

**Files:**
- Create: `release/yiti-20260730/yiti-frontend-src-20260730.zip`
- Create: `release/yiti-20260730/yiti-backend-src-20260730.zip`

**Interfaces:**
- Consumes: 当前 Git `HEAD`
- Produces: 可独立解压的前端与后端源码树

- [ ] **Step 1: 生成前端源码包**

Run:

```bash
git archive \
  --format=zip \
  --prefix=yiti-frontend-src-20260730/ \
  --output=release/yiti-20260730/yiti-frontend-src-20260730.zip \
  HEAD:xanzc_frontend
```

Expected: ZIP 根目录为 `yiti-frontend-src-20260730/`，包含 `package.json` 和 `src/`。

- [ ] **Step 2: 生成后端源码包**

Run:

```bash
git archive \
  --format=zip \
  --prefix=yiti-backend-src-20260730/ \
  --output=release/yiti-20260730/yiti-backend-src-20260730.zip \
  HEAD -- . ':(exclude)xanzc_frontend'
```

Expected: ZIP 根目录为 `yiti-backend-src-20260730/`，包含根 `pom.xml` 和全部 Maven 模块，不包含 `xanzc_frontend/`。

### Task 3: 验证并交付

**Files:**
- Verify: `release/yiti-20260730/yiti-frontend-src-20260730.zip`
- Verify: `release/yiti-20260730/yiti-backend-src-20260730.zip`
- Verify: `release/yiti-20260730/yiti-business-tag-deploy-20260730.sql`

**Interfaces:**
- Consumes: Task 1 与 Task 2 的三个交付物
- Produces: 完整性、边界和校验和结果

- [ ] **Step 1: 校验 ZIP 完整性**

Run:

```bash
unzip -t release/yiti-20260730/yiti-frontend-src-20260730.zip
unzip -t release/yiti-20260730/yiti-backend-src-20260730.zip
```

Expected: 两次均输出 `No errors detected`。

- [ ] **Step 2: 检查禁止目录和前后端边界**

Run:

```bash
unzip -Z1 release/yiti-20260730/yiti-frontend-src-20260730.zip |
  rg '(^|/)(node_modules|dist|target|coverage|logs|\.git)(/|$)'

unzip -Z1 release/yiti-20260730/yiti-backend-src-20260730.zip |
  rg '(^|/)(node_modules|dist|target|coverage|logs|\.git|xanzc_frontend)(/|$)'
```

Expected: 两条检查命令均无输出。

- [ ] **Step 3: 核对关键源码文件**

Run:

```bash
unzip -Z1 release/yiti-20260730/yiti-frontend-src-20260730.zip |
  rg '/(package.json|src/views/system/PersonTags.vue)$'

unzip -Z1 release/yiti-20260730/yiti-backend-src-20260730.zip |
  rg '/(pom.xml|system-governance-center/src/main/java/.*/AdminPersonTagController.java)$'
```

Expected: 前后端各两个关键文件均存在。

- [ ] **Step 4: 生成校验和并记录交付信息**

Run:

```bash
sha256sum release/yiti-20260730/*
du -h release/yiti-20260730/*
git status --short --branch
```

Expected: 三个交付文件均有 SHA-256；仅计划文档或发布目录显示为本次新增内容。
