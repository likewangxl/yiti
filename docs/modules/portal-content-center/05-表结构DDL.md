# 门户与内容中心 — 表结构与数据模型说明

> 本文档只描述数据模型，不是可执行 DDL，也不生成迁移 SQL。
> 真实库的建表、字段调整、历史数据清理和关系数据迁移必须由 DBA 按审批结果实施，
> 实施前后以目标库只读盘点结果为准。
>
> 通讯录模型收口（2026-08-31）：人员目录由 auth 维护，portal 不再维护员工镜像表；
> 产品负责人由 `PORTAL_USER_PRODUCT_REL` 唯一派生，不使用两侧负责人 JSON。

## 1. 数据所有权与表清单

| 数据对象 | 所有者 | portal 访问方式 | 说明 |
|---|---|---|---|
| `PT_USER` | auth-permission-center / 外部用户主数据 | 通过 `UserDirectoryApi` 读取和自助更新联系方式 | `USER_ID` 是唯一用户标识；`UPDATE_TIME` 复用为更新时间 |
| `EXT_USER_ORG` | auth-permission-center / 外部组织同步 | 通过 `UserDirectoryApi` 联查 | 用户与主机构关系 |
| `EXT_ORG_INFO` | auth-permission-center / 外部组织同步 | 通过 `UserDirectoryApi` 联查 | 机构编码与名称 |
| `PORTAL_USER_PRODUCT_REL` | portal-content-center | `UserProductRelationMapper/Service` | portal 唯一维护的用户—产品负责人关系 |
| `PRODUCT_INFO` | portal-content-center | 产品 Mapper/Service | 产品基本资料；不保存负责人 JSON |
| `PORTAL_NAV` | portal-content-center | 导航 Mapper/Service | 网址导航 |
| `PORTAL_SHORTCUT` | portal-content-center | 快捷入口 Mapper/Service | 工作台快捷入口 |
| `DOC_INFO` | portal-content-center | 文档 Mapper/Service | 文档元数据和文件对象关联 |
| `ADDRBOOK_EMPLOYEE` | 已移除 | 不访问 | 历史通讯录镜像表，不属于当前模型 |

auth 所有的三张表均为 portal 的外部只读数据源（联系方式自助更新通过公开 API 完成）；
portal 不得直接依赖 auth 的 entity、mapper 或 service，也不得在本模块复制用户、机构、
岗位或状态字段。

## 2. portal 自有业务表模型

### 2.1 `PORTAL_NAV`

保存导航名称、URL、图标、分类、排序、类型、启用状态、逻辑删除标记及创建/更新时间等
导航元数据。导航的组织/个人可见范围使用既有 owner/creator 字段和数据范围规则；本次
通讯录改造不改变该表模型。

### 2.2 `PORTAL_SHORTCUT`

保存快捷入口名称、URL、图标、入口类型（系统/自定义）、目标类型（内部/外部）、所属
用户标识、排序、启用状态、逻辑删除标记及审计时间等字段。所属用户标识沿用平台用户
ID 口径，不把用户姓名或工号冗余写入表内。

### 2.3 `PRODUCT_INFO`

保存产品编码、名称、类别、描述、是否支持中场支持、归属/维护机构、文件对象 ID、
状态、逻辑删除标记以及创建/更新时间等产品资料字段。

负责人字段明确排除：

- 不新增、不读取、不回写 `responsible_emp_ids` 或其他负责人 JSON 字段；
- 产品负责人列表由 `PORTAL_USER_PRODUCT_REL` 按 `PRODUCT_ID` 派生；
- 删除或停用产品前，Service 按关系表检查是否仍有负责人关系，按产品删除策略处理。

### 2.4 `DOC_INFO`

保存文档标题、分类、`FILE_OBJECT_ID`、状态、逻辑删除标记以及创建/更新时间等文档
元数据。文件本体由 governance 文件服务管理，portal 只保存对象引用。

## 3. 人员目录模型（auth 所有）

通讯录返回视图由以下逻辑关系组成：

| 视图字段 | 来源 | 口径 |
|---|---|---|
| `empId` | `PT_USER.USER_ID` | 唯一用户 ID，不是登录名或旧表工号副本 |
| `empName` | `PT_USER.USERCHNNAME` | 中文姓名 |
| `mobile` | `PT_USER.MOBILE` | 手机号，按调用方权限脱敏；仅本人接口可更新 |
| `email` | `PT_USER.EMAIL` | 邮箱；仅本人接口可更新 |
| `orgCode/orgName` | `EXT_USER_ORG` 联查 `EXT_ORG_INFO` | 用户主机构 |
| `position/positionDesc` | 无 | 兼容字段固定为 `null` |
| `selfDesc` | 无 | 兼容字段固定为 `null` |
| `status` | `PT_USER.ISENABLED` | 对外映射为 `ACTIVE/RESIGNED` |
| `updatedTime` | `PT_USER.UPDATE_TIME` | 不另建联系方式更新时间字段 |

portal 仅通过 auth `UserDirectoryApi` 查询和更新上述视图；不在 portal 建立人员主表、
扩展表、触发器或同步镜像。

## 4. `PORTAL_USER_PRODUCT_REL` 用户—产品负责人关系

### 4.1 关系语义

该表表示“一个用户负责一个产品”的关系：一个 `USER_ID` 可关联多个产品，一个
`PRODUCT_ID` 可关联多个用户。关系双方均使用主键标识：`USER_ID` 对应
`PT_USER.USER_ID`，`PRODUCT_ID` 对应 `PRODUCT_INFO.ID`。

### 4.2 字段模型

| 字段 | 类型约束 | 必填 | 说明 |
|---|---|---|---|
| `USER_ID` | 与 `PT_USER.USER_ID` 相同类型 | 是 | 负责人用户 ID |
| `PRODUCT_ID` | 与 `PRODUCT_INFO.ID` 相同类型 | 是 | 产品 ID |
| `ASSIGNED_TIME` | 日期时间 | 是 | 首次建立关系的时间 |
| `UPDATED_TIME` | 日期时间 | 是 | 关系最后更新时间 |
| `UPDATED_BY` | 与用户 ID 相同类型 | 是 | 最后修改关系的用户 ID |

### 4.3 主键、索引和逻辑约束

- 主键为复合键 `(USER_ID, PRODUCT_ID)`，禁止同一用户与同一产品重复关联。
- 必须有反向查询索引 `(PRODUCT_ID, USER_ID)`，支持产品详情和批量负责人装配。
- `USER_ID`、`PRODUCT_ID` 为逻辑外键；portal 不跨模块创建物理外键，不得直接修改 auth 表。
- 关系写入必须经过 `UserProductRelationService`，输入 trim、去空、去重，并支持整组替换。
- 关系查询返回的负责人用户 ID 必须再通过 `UserDirectoryApi` 装配姓名和联系方式；
  不在关系表冗余姓名、手机号、机构或状态。

## 5. 派生字段与删除边界

| 对外字段 | 派生规则 | 禁止的旧来源 |
|---|---|---|
| `EmployeeDTO.responsibleProductIds` | 按 `USER_ID` 查询关系表的 `PRODUCT_ID` | 员工 JSON、`ADDRBOOK_EMPLOYEE` |
| `ProductDTO.responsibleEmpIds` | 按 `PRODUCT_ID` 查询关系表的 `USER_ID` | 产品 JSON、`PRODUCT_INFO.responsible_emp_ids` |
| `EmployeeDTO.empId` | `PT_USER.USER_ID` | 登录名、旧表工号副本 |
| `EmployeeDTO.position/selfDesc` | 无来源，返回 `null` | POSITION 字典、旧员工表字段 |

以下对象不属于新模型，完成调用链迁移后应从代码、Mapper、XML、测试和资源配置中删除：

- `AddrbookEmployee` 实体及 `AddrbookEmployeeMapper`；
- `responsible_product_ids`、`responsible_emp_ids` JSON 映射和双向同步逻辑；
- 通讯录导入、模板、导出服务及端点；
- 用于人员复制、负责人双写或清理的旧事件与监听器。

## 6. 真实库实施说明

本文档不替代数据库变更申请，也不代表目标库已经完成变更。真实库实施至少需要：

1. DBA 只读核对 `PT_USER`、`EXT_USER_ORG`、`EXT_ORG_INFO`、`PRODUCT_INFO` 的实际字段
   类型、状态口径和现有数据；
2. DBA 评审 `PORTAL_USER_PRODUCT_REL` 的建表/补表、复合主键、反向索引及历史关系数据
   对齐方案；
3. 明确旧 `ADDRBOOK_EMPLOYEE` 和负责人 JSON 的下线、备份、回滚和验收方案；
4. 取得审批后由 DBA 在隔离测试库实施并验证，再按生产变更流程执行。

在未完成 DBA 评审、目标库实施和只读验收前，不得宣称生产 schema 已完成，也不得把本
文档中的模型描述直接复制为生产执行脚本。

---

**文档结束**
