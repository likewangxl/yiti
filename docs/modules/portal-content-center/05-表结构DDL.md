# 门户与内容中心 — 表结构 DDL

> 模块：portal-content-center
> 版本：V1.0
> 最后更新：2026-04-10
> 数据库：MySQL 8.0（字符集 utf8mb4，排序规则 utf8mb4_general_ci，引擎 InnoDB）

---

## 1. 表清单

| 序号 | 表名 | 说明 | 主键策略 | 预估数据量 | 读写特征 |
|---|---|---|---|---|---|
| 1 | `PORTAL_NAV` | 网址导航 | UUID(32) | 百条级 | 读多写少，高缓存命中 |
| 2 | `PORTAL_SHORTCUT` | 工作台快捷入口 | UUID(32) | 万条级（系统级 + 个人级） | 读多写少 |
| 3 | `ADDRBOOK_EMPLOYEE` | 通讯录员工 | 自然主键（emp_id 32） | 万条级 | 读多写少，模糊查询频繁 |
| 4 | `PRODUCT_INFO` | 产品信息 | UUID(64) | 千条级 | 读多写少，状态变更偶发 |
| 5 | `DOC_INFO` | 文档信息 | UUID(32) | 万条级 | 读多写少 |

**主键策略说明：**
- `UUID(32)`：使用 `com.bank.branch.platform.common.util.IdUtil#generateShortUuid()` 生成（32 位去连字符 UUID）
- `UUID(64)`：产品表主键预留业务前缀（如 `PROD_` + 时间戳 + 随机串），便于运维识别
- `自然主键`：`addrbook_employee.emp_id` 直接复用 `PT_USER.emp_id`，保持与员工主数据一致

---

## 2. 完整 DDL

### 2.1 portal_nav — 网址导航表

```sql
DROP TABLE IF EXISTS `PORTAL_NAV`;
CREATE TABLE `PORTAL_NAV` (
    `id`            VARCHAR(32)   NOT NULL                COMMENT '主键，UUID(32)',
    `nav_name`      VARCHAR(100)  NOT NULL                COMMENT '导航名称，如"总行 CCRM"',
    `nav_url`       VARCHAR(500)  NOT NULL                COMMENT '导航链接 URL（http/https 完整路径或站内相对路径）',
    `nav_icon`      VARCHAR(100)  DEFAULT NULL            COMMENT '导航图标 key（前端图标库或 file_object_id）',
    `nav_category`  VARCHAR(50)   DEFAULT NULL            COMMENT '导航分类，关联 DICT_ITEM.NAV_CATEGORY（如 HQ_SYSTEM/BRANCH_SYSTEM/EXTERNAL）',
    `sort_order`    INT           NOT NULL DEFAULT 0      COMMENT '同分类下的排序权重（升序），相同权重按 created_time 升序',
    `status`        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 启用 / DISABLED 停用',
    `created_by`    VARCHAR(32)   NOT NULL                COMMENT '创建人 emp_id',
    `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`    VARCHAR(32)   DEFAULT NULL            COMMENT '最后更新人 emp_id',
    `updated_time`  DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category_sort` (`nav_category`, `sort_order`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='门户网址导航表';
```

### 2.2 portal_shortcut — 工作台快捷入口表

```sql
DROP TABLE IF EXISTS `PORTAL_SHORTCUT`;
CREATE TABLE `PORTAL_SHORTCUT` (
    `id`             VARCHAR(32)   NOT NULL               COMMENT '主键，UUID(32)',
    `shortcut_name`  VARCHAR(100)  NOT NULL               COMMENT '快捷入口名称',
    `shortcut_url`   VARCHAR(500)  NOT NULL               COMMENT '目标 URL（内部路由或外链）',
    `shortcut_icon`  VARCHAR(100)  DEFAULT NULL           COMMENT '图标 key',
    `shortcut_type`  VARCHAR(16)   NOT NULL               COMMENT '快捷入口类型：SYSTEM 系统级（全体可见）/ CUSTOM 个人自定义',
    `target_type`    VARCHAR(16)   NOT NULL DEFAULT 'INTERNAL' COMMENT '跳转类型：INTERNAL 内部路由 / EXTERNAL 外部链接（需新开标签页）',
    `emp_id`         VARCHAR(32)   DEFAULT NULL           COMMENT '所属员工 emp_id；shortcut_type=CUSTOM 时必填；SYSTEM 时为 NULL',
    `sort_order`     INT           NOT NULL DEFAULT 0     COMMENT '排序权重（升序）',
    `status`         VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    `created_by`     VARCHAR(32)   NOT NULL               COMMENT '创建人 emp_id',
    `created_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`     VARCHAR(32)   DEFAULT NULL           COMMENT '最后更新人 emp_id',
    `updated_time`   DATETIME      DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_emp_id`  (`emp_id`),
    KEY `idx_type`    (`shortcut_type`),
    KEY `idx_emp_type_sort` (`emp_id`, `shortcut_type`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='门户工作台快捷入口表';
```

### 2.3 addrbook_employee — 通讯录员工表

```sql
DROP TABLE IF EXISTS `ADDRBOOK_EMPLOYEE`;
CREATE TABLE `ADDRBOOK_EMPLOYEE` (
    `emp_id`                   VARCHAR(32)  NOT NULL       COMMENT '员工工号（自然主键，同 PT_USER.emp_id）',
    `emp_name`                 VARCHAR(100) NOT NULL       COMMENT '员工姓名',
    `mobile`                   VARCHAR(20)  DEFAULT NULL   COMMENT '手机号（日志输出需脱敏）',
    `email`                    VARCHAR(100) DEFAULT NULL   COMMENT '邮箱',
    `org_code`                 VARCHAR(50)  DEFAULT NULL   COMMENT '所属机构编码（关联 EXT_ORG_INFO.org_code）',
    `org_name`                 VARCHAR(200) DEFAULT NULL   COMMENT '所属机构名称（冗余字段，避免跨库 join）',
    `position`                 VARCHAR(100) DEFAULT NULL   COMMENT '岗位（关联 DICT_ITEM.POSITION.itemCode）',
    `self_desc`                TEXT         DEFAULT NULL   COMMENT '自我介绍（员工本人维护）',
    `responsible_product_ids`  TEXT         DEFAULT NULL   COMMENT '负责产品 ID 列表，JSON 数组格式：["PROD_001","PROD_002"]',
    `status`                   VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 在职 / RESIGNED 离职',
    `maintainer_emp_id`        VARCHAR(32)  DEFAULT NULL   COMMENT '维护人 emp_id（60 天提醒发送对象）',
    `created_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`             DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted`                  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`emp_id`),
    KEY `idx_org_code`    (`org_code`),
    KEY `idx_maintainer`  (`maintainer_emp_id`),
    KEY `idx_deleted`     (`deleted`),
    KEY `idx_emp_name`    (`emp_name`),
    KEY `idx_update_time` (`updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通讯录员工表';
```

### 2.4 product_info — 产品信息表

```sql
DROP TABLE IF EXISTS `PRODUCT_INFO`;
CREATE TABLE `PRODUCT_INFO` (
    `id`                          VARCHAR(64)  NOT NULL     COMMENT '主键，格式：PROD_ + UUID',
    `product_code`                VARCHAR(64)  NOT NULL     COMMENT '产品编码（业务唯一，用户输入）',
    `product_name`                VARCHAR(255) NOT NULL     COMMENT '产品名称',
    `product_category`            VARCHAR(64)  DEFAULT NULL COMMENT '产品分类（关联 DICT_ITEM.PRODUCT_CATEGORY.itemCode）',
    `description`                 TEXT         DEFAULT NULL COMMENT '产品描述（长文本，支持 Markdown）',
    `support_for_support_request` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否支持发起中场支持：0 否 / 1 是',
    `owner_org_id`                VARCHAR(50)  DEFAULT NULL COMMENT '业务归属机构 ID（用于 DATA_SCOPE 过滤）',
    `product_dept_org_code`       VARCHAR(50)  DEFAULT NULL COMMENT '维护部门机构编码（关联 EXT_ORG_INFO.org_code，决定编辑权）',
    `file_object_id`              VARCHAR(32)  DEFAULT NULL COMMENT '主附件 ID（关联 file_object.id，多文件用关系表扩展）',
    `responsible_emp_ids`         TEXT         DEFAULT NULL COMMENT '产品负责人列表，JSON 数组：["E10001","E10002"]',
    `status`                      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：DRAFT 草稿 / ACTIVE 上架 / DISABLED 下架 / ARCHIVED 归档',
    `created_by`                  VARCHAR(32)  NOT NULL     COMMENT '创建人 emp_id',
    `updated_by`                  VARCHAR(32)  DEFAULT NULL COMMENT '最后更新人 emp_id',
    `created_time`                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`                DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted`                     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_code_deleted` (`product_code`, `deleted`),
    KEY `idx_product_code` (`product_code`),
    KEY `idx_category`     (`product_category`),
    KEY `idx_status`       (`status`),
    KEY `idx_deleted`      (`deleted`),
    KEY `idx_dept_org`     (`product_dept_org_code`),
    KEY `idx_owner_org`    (`owner_org_id`),
    KEY `idx_update_time`  (`updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='产品信息表';
```

### 2.5 doc_info — 文档信息表

```sql
DROP TABLE IF EXISTS `DOC_INFO`;
CREATE TABLE `DOC_INFO` (
    `id`             VARCHAR(32)  NOT NULL               COMMENT '主键，UUID(32)',
    `doc_title`      VARCHAR(200) NOT NULL               COMMENT '文档标题',
    `doc_category`   VARCHAR(50)  DEFAULT NULL           COMMENT '文档分类（关联 DICT_ITEM.DOC_CATEGORY.itemCode）',
    `file_object_id` VARCHAR(32)  NOT NULL               COMMENT '文件对象 ID（关联 file_object.id，MinIO 存储）',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    `created_by`     VARCHAR(32)  NOT NULL               COMMENT '创建人 emp_id',
    `updated_by`     VARCHAR(32)  DEFAULT NULL           COMMENT '最后更新人 emp_id',
    `created_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`doc_category`),
    KEY `idx_status`   (`status`),
    KEY `idx_title`    (`doc_title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='门户文档信息表';
```

---

## 3. 索引说明

### 3.1 portal_nav

| 索引名 | 类型 | 字段 | 用途 | 覆盖场景 |
|---|---|---|---|---|
| `PRIMARY` | 主键索引 | `id` | 主键查询 | 编辑/删除 |
| `idx_category_sort` | 普通联合 | `nav_category, sort_order` | 按分类列表排序 | `listActiveNavs` |
| `idx_status` | 普通 | `status` | 状态过滤 | 管理台筛选 |

### 3.2 portal_shortcut

| 索引名 | 类型 | 字段 | 用途 | 覆盖场景 |
|---|---|---|---|---|
| `PRIMARY` | 主键 | `id` | 主键查询 | 编辑/删除 |
| `idx_emp_id` | 普通 | `emp_id` | 按员工查询个人快捷入口 | 工作台聚合 |
| `idx_type` | 普通 | `shortcut_type` | 系统级/个人级分流 | 工作台聚合 |
| `idx_emp_type_sort` | 联合 | `emp_id, shortcut_type, sort_order` | 最常用三字段组合（性能最优） | 工作台聚合 |

**说明**：`idx_emp_type_sort` 为主要工作台查询提供最佳覆盖。当 `shortcut_type=SYSTEM` 时 `emp_id` 为 NULL，MySQL 仍可使用联合索引的前缀。

### 3.3 addrbook_employee

| 索引名 | 类型 | 字段 | 用途 | 覆盖场景 |
|---|---|---|---|---|
| `PRIMARY` | 主键 | `emp_id` | 主键查询 | 通讯录详情 |
| `idx_org_code` | 普通 | `org_code` | 按机构过滤/组织树拉取 | 通讯录列表 |
| `idx_maintainer` | 普通 | `maintainer_emp_id` | 查找维护人所管理的员工 | 60 天提醒任务 |
| `idx_deleted` | 普通 | `deleted` | 逻辑删除过滤 | 所有查询 |
| `idx_emp_name` | 普通 | `emp_name` | 姓名模糊查询前缀匹配 | `searchEmployees` |
| `idx_update_time` | 普通 | `updated_time` | 60 天未更新扫描 | 定时任务 |

**模糊查询说明**：`emp_name LIKE 'xx%'` 可使用 `idx_emp_name`；`LIKE '%xx%'` 无法使用索引，需结合 `org_code` 联合过滤后再匹配。

### 3.4 product_info

| 索引名 | 类型 | 字段 | 用途 | 覆盖场景 |
|---|---|---|---|---|
| `PRIMARY` | 主键 | `id` | 主键查询 | 详情页 |
| `uk_product_code_deleted` | 唯一联合 | `product_code, deleted` | 防重复（允许软删后同 code 复用） | 新增校验 |
| `idx_product_code` | 普通 | `product_code` | 按编码查询 | 列表/搜索 |
| `idx_category` | 普通 | `product_category` | 按分类过滤 | 列表 |
| `idx_status` | 普通 | `status` | 状态过滤 | 列表 |
| `idx_deleted` | 普通 | `deleted` | 逻辑删除过滤 | 所有查询 |
| `idx_dept_org` | 普通 | `product_dept_org_code` | 维护部门权限过滤 | 写前校验/数据范围 |
| `idx_owner_org` | 普通 | `owner_org_id` | DATA_SCOPE 过滤 | 列表查询 |
| `idx_update_time` | 普通 | `updated_time` | 60 天未更新扫描 | 定时任务 |

### 3.5 doc_info

| 索引名 | 类型 | 字段 | 用途 | 覆盖场景 |
|---|---|---|---|---|
| `PRIMARY` | 主键 | `id` | 主键查询 | 下载/删除 |
| `idx_category` | 普通 | `doc_category` | 分类过滤 | 列表 |
| `idx_status` | 普通 | `status` | 状态过滤 | 列表 |
| `idx_title` | 普通 | `doc_title` | 标题前缀查询 | 搜索 |

---

## 4. 逻辑外键

> 本项目不使用物理外键（InnoDB FK），仅在业务层和代码层保持引用一致性，所有关联关系通过 Service 层校验与事件同步维护。

| 源表 | 源字段 | 目标表 | 目标字段 | 约束类型 | 维护策略 |
|---|---|---|---|---|---|
| `ADDRBOOK_EMPLOYEE` | `emp_id` | `PT_USER`（auth） | `emp_id` | 1:1 严格对应 | PT_USER 新增 → 事件驱动同步 addrbook_employee |
| `ADDRBOOK_EMPLOYEE` | `org_code` | `EXT_ORG_INFO`（auth） | `org_code` | N:1 | 机构变更 → 事件驱动同步 `org_code` + `org_name` |
| `ADDRBOOK_EMPLOYEE` | `maintainer_emp_id` | `PT_USER`（auth） | `emp_id` | N:1 | 离职时需重新指定维护人 |
| `PORTAL_SHORTCUT` | `emp_id` | `PT_USER`（auth） | `emp_id` | N:1（仅 CUSTOM 类型） | 员工离职时级联软删个人快捷入口 |
| `PORTAL_SHORTCUT` | `created_by` / `updated_by` | `PT_USER`（auth） | `emp_id` | 审计字段 | 无级联 |
| `PORTAL_NAV` | `created_by` / `updated_by` | `PT_USER`（auth） | `emp_id` | 审计字段 | 无级联 |
| `PRODUCT_INFO` | `product_dept_org_code` | `EXT_ORG_INFO`（auth） | `org_code` | N:1 | 机构变更时不自动迁移，需管理台手动调整 |
| `PRODUCT_INFO` | `owner_org_id` | `EXT_ORG_INFO`（auth） | `org_id` | N:1 | 数据范围过滤依据 |
| `PRODUCT_INFO` | `file_object_id` | `FILE_OBJECT`（governance） | `id` | N:1 | 删除产品前需解除或级联删除附件 |
| `PRODUCT_INFO` | `created_by` / `updated_by` | `PT_USER`（auth） | `emp_id` | 审计字段 | 无级联 |
| `DOC_INFO` | `file_object_id` | `FILE_OBJECT`（governance） | `id` | N:1 必须 | 删除文档时级联解除文件关联 |
| `DOC_INFO` | `created_by` / `updated_by` | `PT_USER`（auth） | `emp_id` | 审计字段 | 无级联 |

**级联策略：**
- **PT_USER 停用/离职**：触发 `addrbook_employee.status=RESIGNED`；`PORTAL_SHORTCUT` 中 `emp_id` 对应的个人快捷入口保留（供重新启用时恢复），但不出现在新查询中。
- **file_object 删除**：禁止直接物理删，需通过 `FileApi.deleteFile(fileId)` 级联检查，若有 `PRODUCT_INFO` 或 `DOC_INFO` 引用则拒绝。
- **EXT_ORG_INFO 机构撤并**：`addrbook_employee.org_code` 需重新映射到存续机构，由 auth 模块发布机构变更事件后消费。

---

## 5. 审计字段规范

除 `ADDRBOOK_EMPLOYEE` 外，其余业务表均包含以下 4 个审计字段：

| 字段 | 类型 | 默认值 | 是否必填 | 说明 |
|---|---|---|---|---|
| `created_by` | VARCHAR(32) | 无 | 是 | 创建人 emp_id；系统写入固定填当前操作人 |
| `created_time` | DATETIME | `CURRENT_TIMESTAMP` | 是 | 创建时间，由数据库默认值保证 |
| `updated_by` | VARCHAR(32) | `NULL` | 否 | 最后更新人 emp_id；由 Service 层在 update 时显式设置 |
| `updated_time` | DATETIME | `NULL ON UPDATE CURRENT_TIMESTAMP` | 否 | 最后更新时间，由 MySQL `ON UPDATE` 自动维护 |

**portal 当前实现约定：**
- `PORTAL_NAV` / `PORTAL_SHORTCUT` / `PRODUCT_INFO` / `DOC_INFO` 由 Service 层显式设置 `created_by` / `updated_by`
- `ADDRBOOK_EMPLOYEE` 与当前 MySQL 基线一致，仅保留 `created_time` / `updated_time`，不持有 `created_by` / `updated_by`

**逻辑删除字段：**
- `addrbook_employee.deleted`、`product_info.deleted` 使用 `TINYINT` 类型（0/1）
- `PORTAL_NAV`、`PORTAL_SHORTCUT`、`DOC_INFO` 通过 `status=DISABLED` 表达软删，不保留 deleted 字段
- 所有查询 SQL 必须带 `deleted = 0` 条件或 `status = 'ACTIVE'` 条件

---

## 6. JSON 字段说明

### 6.1 addrbook_employee.responsible_product_ids

**格式：** JSON 字符串数组（MySQL `JSON` 类型或 `TEXT`，本表使用 `TEXT` 兼容旧版本）

**示例：**
```json
["PROD_0001abcd", "PROD_0002efgh", "PROD_0003ijkl"]
```

**维护规则：**
- 员工主动声明"我负责哪些产品"时写入
- 可为空字符串 `""` 或 `NULL`（未声明负责任何产品）
- 数组内 `product_id` 必须在 `product_info.id` 中存在且 `status=ACTIVE` 且 `deleted=0`
- 最大长度 50 个 ID（Service 层校验）
- 去重：写入前 Service 层去重

**查询方式：**
```sql
-- 查找负责某产品的所有员工
SELECT * FROM addrbook_employee
WHERE deleted = 0
  AND JSON_CONTAINS(responsible_product_ids, '"PROD_0001abcd"');

-- 如使用 TEXT 类型则用 LIKE（需 Service 层配合校验边界）
SELECT * FROM addrbook_employee
WHERE deleted = 0
  AND responsible_product_ids LIKE '%"PROD_0001abcd"%';
```

### 6.2 product_info.responsible_emp_ids

**格式：** JSON 字符串数组

**示例：**
```json
["E10001", "E10002", "E10003"]
```

**维护规则：**
- 产品维护人在产品编辑页指定负责人
- 可为空（产品暂无负责人）
- 数组内 `emp_id` 必须在 `addrbook_employee.emp_id` 中存在且 `status=ACTIVE`
- 最大长度 20 个（Service 层校验）
- 去重：写入前 Service 层去重

**查询方式：**
```sql
-- 查找某员工负责的所有产品
SELECT * FROM product_info
WHERE deleted = 0
  AND status = 'ACTIVE'
  AND JSON_CONTAINS(responsible_emp_ids, '"E10001"');
```

**双向同步一致性：**
- 两个 JSON 字段必须保持逻辑一致：若 `E10001` 出现在 `product_info[PROD_001].responsible_emp_ids`，则 `PROD_001` 必须出现在 `addrbook_employee[E10001].responsible_product_ids`
- 一致性由 `portal.product.responsible-updated.v1` 事件驱动，详见第 7 节

---

## 7. 数据同步规则

### 7.1 addrbook_employee 与 PT_USER 同步

**同步来源**：auth-permission-center 的 `PT_USER` + `EXT_USER_ORG` + `EXT_ORG_INFO`

**同步方式**：事件驱动 + 定时任务兜底

**事件订阅**：
| 事件 | 触发源 | portal 处理动作 |
|---|---|---|
| `auth.user.created.v1` | 新员工入职 | INSERT addrbook_employee |
| `auth.user.updated.v1` | 员工信息变更（姓名/手机/邮箱） | UPDATE addrbook_employee（仅非自维护字段） |
| `auth.user.disabled.v1` | 停用/离职 | UPDATE status='RESIGNED' |
| `auth.user.org-changed.v1` | 机构变动 | UPDATE org_code + org_name |

**字段归属（避免互相覆盖）：**

| 字段 | 归属 | 谁可以写 |
|---|---|---|
| `emp_id` | PT_USER | 仅同步 |
| `emp_name` | PT_USER | 仅同步 |
| `mobile` | PT_USER | 同步为主，员工本人可覆盖并通过 Service 标记 `dirty` |
| `email` | PT_USER | 同步为主，员工本人可覆盖 |
| `org_code` / `org_name` | EXT_USER_ORG + EXT_ORG_INFO | 仅同步 |
| `position` | PT_USER（预留） | 同步为主 |
| `self_desc` | portal 自维护 | 仅员工本人 |
| `responsible_product_ids` | portal 自维护 | 员工本人或上级 |
| `status` | PT_USER | 仅同步 |
| `maintainer_emp_id` | portal 自维护 | 管理台 |

**兜底任务**：`ADDR_BOOK_FULL_SYNC`，每日凌晨 2 点全量对账（比对 PT_USER 增量并补齐缺失），仅作为事件丢失的补救机制。

### 7.2 product_info.responsible_emp_ids ↔ addrbook_employee.responsible_product_ids 双向同步

**触发时机 1：产品维护人在产品编辑页调整负责人列表**

1. 校验新的 `emp_id` 列表合法性（存在、在职、数量 ≤20）
2. 事务内执行：
   - 计算 `removed = 旧列表 - 新列表`，`added = 新列表 - 旧列表`
   - `UPDATE product_info SET responsible_emp_ids = 新列表 WHERE id = ?`
   - 对 `removed` 中每个 emp_id：从其 `responsible_product_ids` 中移除该 productId
   - 对 `added` 中每个 emp_id：追加该 productId 到其 `responsible_product_ids`
   - 注册 `afterCommit` 记录事件 `portal.product.responsible-updated.v1`，payload：`{productId, removed, added}`

**触发时机 2：员工本人在通讯录页调整"我负责的产品"**

1. 校验 `productId` 列表合法性
2. 事务内执行：
   - 计算 `removed` / `added`
   - `UPDATE addrbook_employee SET responsible_product_ids = 新列表 WHERE emp_id = ?`
   - 对 `removed`/`added` 中每个 productId 同步更新 `product_info.responsible_emp_ids`
   - 注册 `afterCommit` 记录事件 `portal.employee.responsible-products-updated.v1`

**幂等与冲突处理：**
- 同步更新阶段使用乐观锁 / 行锁保护，冲突时快速失败并由前端重试
- `afterCommit` 监听器仅做记录，不再反向写业务表，因此不存在二次补偿链路

**一致性校验任务**：`PRODUCT_RESPONSIBLE_CONSISTENCY_CHECK`，每日凌晨 3 点扫描不一致项并告警（不自动修复），详见 06 文档。

### 7.3 file_object 与 product_info/doc_info 关联规则

- `product_info.file_object_id` 允许 NULL（产品可无主附件）
- `doc_info.file_object_id` 必填（文档必须绑定文件）
- 上传流程：先 `FileApi.upload()` 获取 `fileObjectId` → 再写业务表（必须同事务保证一致）
- 删除业务记录前必须调用 `FileApi.detachBizRelation(fileObjectId, bizType, bizId)` 解除关联
- 孤儿文件清理由 governance 模块的 `FILE_ORPHAN_CLEANUP` 任务负责，portal 不介入

---

**文档结束**
