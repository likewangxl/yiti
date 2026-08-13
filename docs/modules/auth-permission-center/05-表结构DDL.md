# AUTH 认证授权中心 -- 表结构 DDL

> 完整 DDL 文件位于：`docs/schema/ddl-auth.sql`
> 本文档提供约束说明、索引用途、逻辑外键关联、枚举取值等补充信息。

---

## 1. 表清单

| # | 表名 | 说明 | 主键策略 | 所属来源 | 记录数量级（预估） |
|:---|:---|:---|:---|:---|:---|
| 1 | PT_USER | 人员表 | 自然主键 USER_ID（工号） | 定制框架 | 数百 ~ 数千 |
| 2 | PT_ROLE | 角色表 | 自然主键 ROLE_ID | 定制框架 | 12（V1固定） |
| 3 | PT_RESOURCE | 资源表 | 自然主键 RESOURCE_ID | 定制框架 | 数百 |
| 4 | PT_USER_ROLE | 用户角色关联表 | 联合主键 (USER_ID, ROLE_ID) | 定制框架 | 数百 ~ 数千 |
| 5 | PT_ROLE_RESOURCE | 角色资源关联表 | UUID 主键 ID | 定制框架 | 数千 |
| 6 | PT_ROLE_BIZ_SCOPE | 角色业务范围表 | UUID 主键 ID | 新增扩展表 | 数百 |
| 7 | EXT_ORG_INFO | 机构表 | 自增主键 ID + 唯一键 ORG_CODE | 定制框架 | 数十 ~ 数百 |
| 8 | EXT_USER_ORG | 用户机构关联表 | 联合主键 (USER_ID, ORG_CODE) | 定制框架 | 数百 ~ 数千 |
| 9 | PT_ORG_PROFILE | 机构本地经营画像 | 自然主键 ORG_CODE | auth 扩展表 | 数十 ~ 数百 |
| 10 | PT_ORG_GROUP | 命名机构组 | 自增主键 ID + 唯一键 GROUP_CODE | auth 扩展表 | 数十 |
| 11 | PT_ORG_GROUP_MEMBER | 机构组直接成员 | 自增主键 ID + UK(GROUP_CODE, ORG_CODE) | auth 扩展表 | 数百 |
| 12 | PT_ROLE_ORG_GROUP | 角色-机构组授权 | 自增主键 ID + UK(ROLE_ID, GROUP_CODE) | auth 扩展表 | 数百 |

---

## 2. 完整字段清单

### 2.1 PT_USER -- 人员表

| 字段名 | 数据类型 | 允许NULL | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| USER_ID | varchar(50) | NOT NULL | - | 用户ID（工号），主键 |
| USERNAME | varchar(200) | NOT NULL | - | 用户姓名（登录名） |
| USERCHNNAME | varchar(200) | NOT NULL | - | 用户中文姓名（显示名） |
| PWD | varchar(64) | NULL | NULL | 密码（BCrypt加密存储） |
| EMAIL | varchar(100) | NULL | NULL | 邮箱 |
| ISEXPIRED | int | NULL | 0 | 1过期 / 0未过期 |
| ISLOCKED | int | NULL | 0 | 1被锁 / 0未被锁 |
| PASS_WRONG_COUNT | int | NULL | 0 | 密码错误次数（累计，登录成功重置） |
| ISENABLED | int | NULL | 1 | 0启用 / 1未启用（注意：1是未启用） |
| CREATE_TIME | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| CREATE_AUTHOR | varchar(50) | NULL | NULL | 创建者工号 |
| UPDATE_TIME | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间（自动更新） |
| UPDATE_AUTHOR | varchar(50) | NULL | NULL | 更新者工号 |
| REMARK | varchar(100) | NULL | NULL | 备注 |
| PWD_UPDATE_TIME | datetime | NULL | NULL | 密码更新时间 |

**主键**：`PRIMARY KEY (USER_ID)`

---

### 2.2 PT_ROLE -- 角色表

| 字段名 | 数据类型 | 允许NULL | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| ROLE_ID | varchar(50) | NOT NULL | - | 角色ID，主键 |
| ROLE_CODE | varchar(50) | NOT NULL | - | 角色业务编码（大写字母+下划线）；部署脚本要求全局唯一 |
| ROLE_CHNAME | varchar(100) | NOT NULL | - | 角色中文名 |
| RECORD_STATUS | int | NULL | 0 | 0可用 / 1不可用（逻辑删除） |
| SYS_CODE | varchar(10) | NULL | 'PLATFORM' | 系统编号 |
| CREATE_TIME | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| CREATE_USER | varchar(50) | NULL | NULL | 创建人工号 |
| UPDATE_TIME | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| UPDATE_USER | varchar(50) | NULL | NULL | 更新人工号 |
| REMARK | varchar(100) | NULL | NULL | 备注 |

**主键**：`PRIMARY KEY (ROLE_ID)`；机构组对齐脚本会在无重复历史数据时补充 `UNIQUE KEY UK_PT_ROLE_ROLE_CODE (ROLE_CODE)`，角色绑定表始终保存 `ROLE_ID`，外部契约始终使用规范化 `ROLE_CODE`。

---

### 2.3 PT_RESOURCE -- 资源表

| 字段名 | 数据类型 | 允许NULL | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| RESOURCE_ID | varchar(20) | NOT NULL | - | 资源ID，主键 |
| RESOURCE_URL | varchar(256) | NOT NULL | - | 资源URL（支持Ant通配符） |
| RESOURCE_METHOD | varchar(10) | NOT NULL | - | 请求方法：GET/POST/PUT/DELETE/* |
| MENU_NAME | varchar(256) | NOT NULL | - | 菜单名称 |
| MENU_ICON_URL | varchar(256) | NULL | NULL | 图标路径 |
| MENU_RANK_NO | int | NULL | 0 | 菜单排序号 |
| ISMENU | int | NULL | 0 | 0是菜单 / 1不是菜单 |
| MENU_ENDFLAG | varchar(10) | NULL | '0' | 是否叶子节点菜单 1是 / 0不是 |
| PARENT_RESOURCE_ID | varchar(60) | NULL | NULL | 上级资源ID（树形关联） |
| STATUS | int | NULL | 0 | 0启用 / 1不启用 |
| SYS_CODE | varchar(10) | NULL | 'PLATFORM' | 系统编号 |
| CREATE_TIME | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| CREATE_USER | varchar(50) | NULL | NULL | 创建人工号 |
| UPDATE_TIME | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| UPDATE_USER | varchar(50) | NULL | NULL | 更新人工号 |
| REMARK | varchar(100) | NULL | NULL | 备注 |

**主键**：`PRIMARY KEY (RESOURCE_ID)`

---

### 2.4 PT_USER_ROLE -- 用户角色关联表

| 字段名 | 数据类型 | 允许NULL | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| USER_ID | varchar(50) | NOT NULL | - | 用户ID（联合主键之一） |
| ROLE_ID | varchar(50) | NOT NULL | - | 角色ID（联合主键之一） |
| DEFAULT_ASSIGN | int | NULL | 0 | 默认分配标记 |
| INHERIT_ASSIGN | int | NULL | 0 | 用户组角色继承标记 |
| GROUP_ASSING | int | NULL | 0 | 角色组分配标记 |
| CREATE_TIME | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |

**主键**：`PRIMARY KEY (USER_ID, ROLE_ID)`

---

### 2.5 PT_ROLE_RESOURCE -- 角色资源关联表

| 字段名 | 数据类型 | 允许NULL | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| ID | varchar(32) | NOT NULL | - | 主键（UUID） |
| ROLE_ID | varchar(50) | NOT NULL | - | 角色ID |
| RESOURCE_ID | varchar(20) | NOT NULL | - | 资源ID |
| SYS_CODE | varchar(10) | NULL | 'PLATFORM' | 系统编号 |
| CREATE_TIME | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |

**主键**：`PRIMARY KEY (ID)`

---

### 2.6 PT_ROLE_BIZ_SCOPE -- 角色业务范围表

| 字段名 | 数据类型 | 允许NULL | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| ID | varchar(32) | NOT NULL | - | 主键（UUID） |
| ROLE_ID | varchar(50) | NOT NULL | - | 角色ID |
| BIZ_TYPE | varchar(50) | NOT NULL | - | 业务类型枚举值 |
| DATA_SCOPE | varchar(50) | NOT NULL | - | 数据范围枚举值 |
| RECORD_STATUS | int | NULL | 0 | 0可用 / 1不可用 |
| CREATE_TIME | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| CREATE_USER | varchar(50) | NULL | NULL | 创建人工号 |
| UPDATE_TIME | datetime | NULL | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| UPDATE_USER | varchar(50) | NULL | NULL | 更新人工号 |
| REMARK | varchar(100) | NULL | NULL | 备注 |

**主键**：`PRIMARY KEY (ID)`

---

### 2.7 EXT_ORG_INFO -- 机构表

| 字段名 | 数据类型 | 允许NULL | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| ID | int | NOT NULL | AUTO_INCREMENT | 自增主键 |
| ORG_CODE | varchar(20) | NOT NULL | - | 机构编号（唯一） |
| ORG_NAME | varchar(200) | NOT NULL | - | 机构名称 |
| ORG_LEVEL | int | NULL | NULL | 机构等级 1总行/2分行/3支行 |
| P_ID | varchar(20) | NULL | NULL | 上级机构编码（自关联） |
| ORGAN_STATE | int | NULL | 0 | 状态 0启用/1删除 |
| ADM_DIVISION_CODE | varchar(20) | NULL | NULL | 行政区划代码 |
| ADM_DIVISION_NAME | varchar(255) | NULL | NULL | 行政区划名称 |
| CREATE_TIME | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |
| CREATE_USER | varchar(50) | NULL | NULL | 创建人工号 |

**主键**：`PRIMARY KEY (ID)`

---

### 2.8 EXT_USER_ORG -- 用户机构关联表

| 字段名 | 数据类型 | 允许NULL | 默认值 | 说明 |
|:---|:---|:---|:---|:---|
| USER_ID | varchar(50) | NOT NULL | - | 用户ID（联合主键之一） |
| ORG_CODE | varchar(20) | NOT NULL | - | 机构编码（联合主键之一） |
| CREATE_TIME | datetime | NULL | CURRENT_TIMESTAMP | 创建时间 |

**主键**：`PRIMARY KEY (USER_ID, ORG_CODE)`

---

## 3. 索引说明

### 3.1 唯一索引

| 表名 | 索引名 | 字段 | 用途 |
|:---|:---|:---|:---|
| PT_RESOURCE | uk_pt_resource_url_method_sys | (RESOURCE_URL, RESOURCE_METHOD, SYS_CODE) | 同一系统内URL+Method唯一，防止重复注册资源 |
| PT_ROLE_BIZ_SCOPE | uk_pt_role_biz_scope_role_biz | (ROLE_ID, BIZ_TYPE) | 同一角色对同一BizType只能有一条配置 |
| EXT_ORG_INFO | uk_ext_org_info_org_code | (ORG_CODE) | 机构编码全局唯一 |

### 3.2 普通索引

| 表名 | 索引名 | 字段 | 用途 |
|:---|:---|:---|:---|
| PT_RESOURCE | idx_pt_resource_status | (STATUS, SYS_CODE) | 按状态和系统编号筛选资源 |
| PT_USER_ROLE | idx_role_id | (ROLE_ID) | 查询角色下所有用户 |
| PT_ROLE_RESOURCE | idx_role_id | (ROLE_ID) | 查询角色绑定的资源 |
| PT_ROLE_RESOURCE | idx_resource_id | (RESOURCE_ID) | 查询资源被哪些角色绑定 |
| PT_ROLE_BIZ_SCOPE | idx_role_id | (ROLE_ID) | 查询角色的所有BizScope配置 |
| PT_ROLE_BIZ_SCOPE | idx_biz_type | (BIZ_TYPE) | 按BizType查询所有角色配置 |
| EXT_ORG_INFO | idx_p_id | (P_ID) | 查询子机构（树形遍历） |
| EXT_USER_ORG | idx_org_code | (ORG_CODE) | 查询机构下所有用户 |

---

## 4. 逻辑外键关联

> 所有外键均为逻辑外键（不建物理外键约束），通过应用层保证引用完整性。

| 表 | 字段 | 关联表 | 关联字段 | 关联说明 |
|:---|:---|:---|:---|:---|
| PT_USER_ROLE | USER_ID | PT_USER | USER_ID | 用户-角色多对多关系 |
| PT_USER_ROLE | ROLE_ID | PT_ROLE | ROLE_ID | 用户-角色多对多关系 |
| PT_ROLE_RESOURCE | ROLE_ID | PT_ROLE | ROLE_ID | 角色-资源多对多绑定 |
| PT_ROLE_RESOURCE | RESOURCE_ID | PT_RESOURCE | RESOURCE_ID | 角色-资源多对多绑定 |
| PT_ROLE_BIZ_SCOPE | ROLE_ID | PT_ROLE | ROLE_ID | 角色-BizScope配置 |
| EXT_USER_ORG | USER_ID | PT_USER | USER_ID | 用户-机构多对多关系 |
| EXT_USER_ORG | ORG_CODE | EXT_ORG_INFO | ORG_CODE | 用户-机构多对多关系 |
| EXT_ORG_INFO | P_ID | EXT_ORG_INFO | ORG_CODE | 机构层级自关联（父子关系） |
| PT_RESOURCE | PARENT_RESOURCE_ID | PT_RESOURCE | RESOURCE_ID | 资源树形自关联 |

---

## 5. 审计字段规范

所有表统一包含审计字段（字段命名因定制框架历史原因存在差异）：

| 表名 | 创建时间 | 创建人 | 更新时间 | 更新人 |
|:---|:---|:---|:---|:---|
| PT_USER | CREATE_TIME | CREATE_AUTHOR | UPDATE_TIME | UPDATE_AUTHOR |
| PT_ROLE | CREATE_TIME | CREATE_USER | UPDATE_TIME | UPDATE_USER |
| PT_RESOURCE | CREATE_TIME | CREATE_USER | UPDATE_TIME | UPDATE_USER |
| PT_USER_ROLE | CREATE_TIME | -（无） | -（无） | -（无） |
| PT_ROLE_RESOURCE | CREATE_TIME | -（无） | -（无） | -（无） |
| PT_ROLE_BIZ_SCOPE | CREATE_TIME | CREATE_USER | UPDATE_TIME | UPDATE_USER |
| EXT_ORG_INFO | CREATE_TIME | CREATE_USER | -（无） | -（无） |
| EXT_USER_ORG | CREATE_TIME | -（无） | -（无） | -（无） |

**注意事项：**
- PT_USER 的审计字段名为 `CREATE_AUTHOR` / `UPDATE_AUTHOR`（历史原因与其他表不同）
- PT_USER_ROLE、PT_ROLE_RESOURCE、EXT_USER_ORG 只有创建时间，无更新审计字段
- 建议在 MyBatis 的 AuditFieldFiller 中统一处理这些差异

---

## 6. DATA_SCOPE 枚举取值

| 枚举值 | 数据库存储值 | 说明 | SQL 条件模板 | 适用场景 |
|:---|:---|:---|:---|:---|
| SELF_CREATED | `'SELF_CREATED'` | 本人创建 | `created_by = #{empId}` | 线索、资产投放、中场支持（发起方） |
| SELF | `'SELF'` | 本人对象 | `{selfCol} = #{empId}` | 通讯录本人信息、个人KPI |
| SELF_ASSIGNED | `'SELF_ASSIGNED'` | 本人被分配 | `{assigneeCol} = #{empId}` | 触达任务、中场支持任务（承接方） |
| ORG | `'ORG'` | 本机构 | `owner_org_id = #{orgCode}` | 认领范围、部门秘书派单范围 |
| ORG_SUBTREE | `'ORG_SUBTREE'` | 本机构及下属 | `owner_org_id IN (#{orgSubtreeCodes})` | 机构负责人视角 |
| ALL | `'ALL'` | 全行 | `1=1`（不过滤） | 系统管理员、公司部/零售部 |
| WORKFLOW_PARTICIPANT | `'WORKFLOW_PARTICIPANT'` | 流程参与者 | 需结合 workflow-center 的参与者判定能力（当前对外 Java 契约待补齐） | 授信审查/批复、公司部审批 |

---

## 7. BizType 枚举取值

| # | 枚举值 | 数据库存储值 | 对应业务域 |
|:---|:---|:---|:---|
| 1 | NAV | `'NAV'` | 网址导航 |
| 2 | ADDRBOOK | `'ADDRBOOK'` | 通讯录 |
| 3 | PRODUCT | `'PRODUCT'` | 产品资料库 |
| 4 | DOC | `'DOC'` | 常用文档 |
| 5 | TAG | `'TAG'` | 标签管理 |
| 6 | LEAD | `'LEAD'` | 线索管理 |
| 7 | CUSTOMER | `'CUSTOMER'` | 客户列表与详情 |
| 8 | CUSTOMER_POOL | `'CUSTOMER_POOL'` | 待认领客户池 |
| 9 | CLAIM | `'CLAIM'` | 认领与已认领客户操作 |
| 10 | TOUCH_TASK | `'TOUCH_TASK'` | 触达任务办理 |
| 11 | TOUCH_REPORT | `'TOUCH_REPORT'` | 触达任务一览 |
| 12 | LOAN | `'LOAN'` | 资产投放申请 |
| 13 | SUPPORT | `'SUPPORT'` | 中场支持申请（发起方） |
| 14 | SUPPORT_DEPT | `'SUPPORT_DEPT'` | 中场支持（承接部门） |
| 15 | REPORT | `'REPORT'` | 报表与分析 |
| 16 | PERF_CONFIG | `'PERF_CONFIG'` | 绩效配置 |
| 17 | SYS_CONFIG | `'SYS_CONFIG'` | 系统配置与运维 |

---

## 8. 字符集与排序规则

所有表统一使用：
- 字符集：`utf8mb4`
- 排序规则：`utf8mb4_general_ci`
- 存储引擎：`InnoDB`

---

## 9. 机构画像与命名机构组扩展表（2026-08-11）

四张扩展表及 `AUDIT_LOG` 结构化审计列的可执行手工 SQL 位于 `docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql`，不由 Flyway 或应用启动自动执行。脚本会先校验已有表/索引、角色和资源业务身份；任何残缺或冲突均 fail-fast。`EXT_ORG_INFO` 仍为外部同步只读表。

| 表 | 关键约束 |
|:---|:---|
| `PT_ORG_PROFILE` | `ORG_CODE` 主键；`ORG_NATURE` 与 `OPERATING_LEVEL` 分离；SUBORDINATE 必须指向祖先链上的 ACTIVE PRIMARY；坐标成对出现且使用 GCJ02；`STATUS` 与外部机构状态共同决定运行时有效性 |
| `PT_ORG_GROUP` | `GROUP_CODE` 唯一；用途固定 `REPORT_SCREEN`；`VERSION` 用于乐观锁 |
| `PT_ORG_GROUP_MEMBER` | `(GROUP_CODE, ORG_CODE)` 唯一；只保存直接成员，不展开组织子树 |
| `PT_ROLE_ORG_GROUP` | `(ROLE_ID, GROUP_CODE)` 唯一；仅绑定 `PT_ROLE.RECORD_STATUS=0` 的有效角色 |

运行时机构集合固定为：`PT_ORG_GROUP_MEMBER` ACTIVE 直接成员 ∩ `EXT_ORG_INFO.ORGAN_STATE=0` ∩ `PT_ORG_PROFILE.STATUS=ACTIVE`。角色授权固定为当前有效角色、屏级白名单和 `PT_ROLE_ORG_GROUP` 有效绑定的同一角色交集。
