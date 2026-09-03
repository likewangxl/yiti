# 门户与内容中心 — 数据模型说明

## 1. 使用边界

本文只记录当前代码所依赖的数据模型语义，不是可执行 DDL、迁移脚本或建库顺序。结构变更由 DBA 按审批在目标库实施；实际列、类型、索引和约束以目标库只读盘点及 Mapper 映射为准。

## 2. 数据所有权

| 数据 | 归属 | 门户使用方式 |
| --- | --- | --- |
| `PT_USER`、`EXT_USER_ORG`、`EXT_ORG_INFO` | auth | 通过 `UserDirectoryApi` 读取用户、联系方式和主机构 |
| `PRODUCT_INFO` | portal | 产品资料及逻辑删除标记 |
| `PORTAL_USER_PRODUCT_REL` | portal | 用户与产品负责人关系 |
| `PORTAL_NAV`、`PORTAL_SHORTCUT` | portal | 导航和工作台入口 |
| `DOC_INFO` | portal | 文档元数据和文件对象 ID |
| `ANNOUNCEMENT`、`ANNOUNCEMENT_FILE` | portal | 公告、置顶/删除状态和附件关联 |
| `zh_guarantee_info` | 既有业务表，门户读写当前页面所需记录 | 担保查询、维护和导出 |
| `ccms_business_contract`、`clms_ed_credit_info` | 外部/其他业务数据 | 合同命中查询、担保同步只读来源 |

门户不创建旧的通讯录镜像，也不把客户、申请、绩效或报表核心域状态复制到自身表。

## 3. 门户自有模型

### 3.1 导航 `PORTAL_NAV`

一条记录表示一个网址入口。业务字段为名称、URL、图标、分类、排序号和审计字段；`status` 为 `ACTIVE`/`DISABLED`。前台默认只读取 `ACTIVE`，删除通过停用完成；同一分类的启用名称需唯一。主键由应用生成字符串 ID。

### 3.2 快捷入口 `PORTAL_SHORTCUT`

记录包含名称、URL、图标、`shortcut_type`（`SYSTEM`/`CUSTOM`）、`target_type`（`INTERNAL`/`EXTERNAL`）、所属用户、排序、状态和审计字段。系统入口的 `emp_id` 为空或不参与当前用户过滤，自定义入口绑定一个用户。表没有 `deleted` 语义；自定义保存是物理删除后批量插入。

### 3.3 产品 `PRODUCT_INFO`

产品标识为字符串 ID，核心字段为全局唯一的 `product_code`、名称、分类、说明、是否支持中场支持、维护机构 `product_dept_org_code`、归属机构 `owner_org_id`、文件对象 ID、状态和审计字段。`status` 为 `ACTIVE`/`DISABLED`，`deleted=0/1` 实现逻辑删除；查询和唯一性检查均排除逻辑删除记录。当前编辑维护机构时同步归属机构。

负责人不存于产品 JSON 或用户主表，而由 `PORTAL_USER_PRODUCT_REL` 派生。产品详情/列表中的负责人姓名、机构名和附件名属于查询装配字段，不改变产品主表所有权。

### 3.4 文档 `DOC_INFO`

文档元数据包括标题、分类、治理文件对象 ID、状态和审计字段。`status=ACTIVE` 表示可用，`DISABLED` 表示逻辑停用；表没有独立 `deleted` 字段。删除文档只更新状态，不删除治理文件对象。

### 3.5 公告及附件

`ANNOUNCEMENT` 保存标题、内容、发布人、发布日期、置顶标记 `is_pinned`、删除标记 `is_deleted` 和审计字段。`ANNOUNCEMENT_FILE` 保存公告 ID、原始文件名、文件大小、上传人/时间及文件对象 ID（复用 `file_path` 列存储）。对象内容由治理 `FileApi` 写入华为云 OBS；`file_path` 不是本地路径契约。

## 4. 用户—产品关系

`PORTAL_USER_PRODUCT_REL` 是用户和产品的多对多关系，复合键语义为 `(USER_ID, PRODUCT_ID)`；两侧均引用规范字符串 ID。新增/编辑产品和本人通讯录维护都先规范化、去重 ID，再由关系服务按集合差异替换；空集合表示清空关系。

REST 端把关系投影为 `responsibleProductIds`，产品端还可装配负责人姓名。查询用户或产品时，关系不存在即返回空列表，不用 NULL 表示“未知关系”。

## 5. 担保数据模型

### 5.1 `zh_guarantee_info`

页面使用客户号、客户名称、业务额度、剩余额度、融资额度、授信到期日、经办人、创建时间和变更时间等语义字段；主键为数据库自增 `Long`。金额和部分日期在既有表中以字符串承载，门户 DTO 也按字符串传输。

手工新增/编辑的金额按“万元”接收，去除 `¥` 和千分位后乘以 10000，以“元”字符串保存；读取时由转换器按“元→万元”展示。合同表金额单位为“元”，命中合同批量映射时不再换算。

### 5.2 合同和同步来源

`ccms_business_contract` 仅在新增时按客户号+名称查询；命中后按合同记录批量生成担保记录，前端手工金额不落库。`clms_ed_credit_info` 是 Quartz 同步的只读来源，任务更新既有客户并补入新客户，同时归集相应合同记录。门户不维护这两个来源表的结构。

## 6. 删除和派生边界

| 对象 | 当前删除语义 | 派生/外部数据处理 |
| --- | --- | --- |
| 导航 | `status=DISABLED` | 不删除导航记录 |
| 快捷入口 | 自定义记录物理删除后重建 | 系统入口不受保存请求影响 |
| 产品 | `deleted=1` | 有负责人关系时拒绝删除 |
| 文档 | `status=DISABLED` | 不删除 OBS 文件对象 |
| 公告 | `is_deleted=1` | 附件对象不会随公告自动删除 |
| 担保 | 批量物理删除 | 不回写合同或同步来源 |

任何新增字段、索引或表变更都必须先核对实体、Mapper、目标库和审批结果；本文不提供 DDL 片段。
