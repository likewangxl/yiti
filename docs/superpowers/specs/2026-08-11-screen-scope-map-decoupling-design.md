# 大屏查看视角、业务条线、机构范围与复合地图解耦设计规格

> - 日期：2026-08-11
> - 状态：设计定稿（业务决策已确认，初始机构成员与人员授权属于上线配置数据）
> - 归属模块：`auth-permission-center` + `report-analytics-center` + `xanzc_frontend`
> - 前置规格：`2026-07-12-screen-dashboard-design.md`、`2026-07-12-screen-canvas-designer-design.md`、`2026-07-17-screen-designer-expansion-design.md`
> - 文档性质：增量规格；不改写历史规格，冲突处以本文为准

## 1. 背景与问题定义

现有大屏把多个独立概念压在 `view_level=PROVINCE/BRANCH/PERSON` 上：

1. `PROVINCE/BRANCH/PERSON` 实际表示查看视角，不是绩效指标的 `EMP/ORG/CUST` 维度；
2. 对公、零售属于业务条线，不能扩展成新的 `view_level`；
3. 对公部门需要跨多个兄弟机构查看对公数据，零售条线需要跨西安本地一级经营机构与异地二级分行查看零售数据；既有 `ORG_SUBTREE` 无法表达该范围，直接授予 `ALL` 又会过度授权；
4. 省级屏地图硬编码为陕西省，不能表达“西安本地机构为主、宝鸡/渭南/咸阳/榆林作为异地经营节点”的实际经营分布；
5. `EXT_ORG_INFO.ORG_LEVEL=2` 同时包含部门、西安本地支行和异地分行，不能只按层级推导经营机构；
6. `EXT_ORG_INFO` 是外部同步表，人工维护的经营属性不能直接写入该表；
7. 现有地图点位是所有省级屏共享的全局集合，无法按屏、机构组和地图模式配置。

本文将以下四条契约解耦：

| 契约 | 回答的问题 | 权威来源 |
|---|---|---|
| 查看视角 | 以全辖、机构还是个人为主体查看 | `RPT_SCREEN.view_level`，兼容保留 |
| 业务条线 | 数据属于公司、零售还是共用 | 大屏与数据源的业务条线字段；数据源字段为数据归属真相 |
| 机构范围 | 当前屏统计哪些机构 | 认证授权模块的命名机构组 |
| 地图表现 | 使用地理地图还是复合示意图 | 画布地图组件配置 |

### 1.1 核实依据与判断边界

截至 2026-08-11，本规格将内容明确分为三类：

| 类别 | 已核实内容 | 依据/处理方式 |
|---|---|---|
| 代码事实 | 新建大屏仅提供 `PROVINCE/BRANCH/PERSON`；`MapCenter` 读取陕西 GeoJSON；地图属性面板没有业务地图配置；平台已有当前用户角色编码与有效角色列表 API | `xanzc_frontend/src/views/screen/designer/DesignerV2.vue`、`xanzc_frontend/src/views/screen/components/MapCenter.vue`、`xanzc_frontend/src/views/screen/designer/widgets/map-center/Attr.vue`、auth `CurrentUserApi`/`RoleApi` |
| 资产事实 | 仓库 `shaanxi.json` 的实施记录指向阿里 DataV `610000_full.json`；阿里云官方说明 DataV 地图组件主要使用 GCJ-02，GeoAtlas 数据来自高德且版权需参照高德开放平台 | `docs/superpowers/plans/2026-07-12-screen-dashboard-impl.md`；[阿里云 DataV 地图数据格式](https://help.aliyun.com/zh/datav/datav-7-0/user-guide/map-data-format-1)；[DataV GeoAtlas 说明](https://help.aliyun.com/en/datav/datav-7-0/user-guide/datav-geoatlas-widgets/) |
| 数据事实 | 开发库当前机构同层混有部门、本地支行和异地分行；机构行政区划字段不足以直接生成本方案点位；当前地图点位不完整 | 以本次设计审查时的本地 `yiti` 数据库快照为准；这些事实只证明现状缺口，不作为生产种子数据来源 |
| 已确认业务决策 | 不建独立岗位模型；使用 RBAC 角色；新增本地机构画像和命名机构组；异地分行直接读取自身指标行；数据源显式分条线；采用西安复合地图 | 本次需求确认，属于目标规则，不是从现有代码推导出的事实 |
| 上线待配置 | 对公部门完整成员、全辖一级经营机构完整成员、人员角色分配、全部西安点位、每个数据源条线 | 必须由业务负责人/权限管理员复核，不得按机构名、角色名或组织层级自动推测 |

系统“没有可用岗位主数据”是已核实的当前事实：auth 的人员目录契约明确 `position` 无来源且恒为 `null`。因此本期用角色承载查看能力是有意的产品取舍，不代表“角色”和“岗位”在概念上等价；未来若接入权威岗位主数据，应另行设计岗位同步、任职有效期和岗位到角色映射。

## 2. 已确认业务决策

### 2.1 人员授权

- 本期不建设独立岗位模型；系统当前也没有可用的岗位主数据。
- 使用指定 RBAC 角色表达“大屏查看岗位”。给员工分配相应角色后获得访问资格。
- 建议新增能力角色：
  - `R_SCREEN_CORP_VIEWER`：对公大屏查看角色；
  - `R_SCREEN_RETAIL_VIEWER`：零售大屏查看角色。
- 既有角色也可由管理员绑定机构组与大屏资源，不强制所有环境使用上述两个角色码。
- 角色回答“谁能看”，机构组回答“能看哪些机构”；两者必须同时通过。

### 2.2 经营机构口径

- 统一业务术语为“一级经营机构”，不再将所有节点笼统称为一级支行。
- 西安分行直属、承担独立经营职责的机构属于一级经营机构。
- 宝鸡分行、渭南分行、咸阳分行、榆林分行在组织名称上是二级分行，但经营比较等级等同一级经营机构。
- 异地分行下属营业部、小微支行、社区支行只汇总归属到对应异地分行，不作为全辖主屏独立比较节点。
- 西安社区支行、小微支行归属其上级一级经营机构；挂在“零售客户一部”等部门下的网点归属该部门。
- “零售客户一部”等节点可以同时具有“部门”性质和“一级经营机构”属性；机构性质与经营等级不能设计为一个互斥枚举。
- 上述一级经营机构在全辖汇总中直接读取自身 `ORG_INDEX_RESULT` 行，不在大屏查询引擎中对子树求和，避免父级汇总与子级明细重复计算。

### 2.3 业务条线

- 业务条线由数据源显式声明：
  - `CORP`：公司/对公；
  - `RETAIL`：零售；
  - `COMMON`：共用。
- 禁止根据屏名称、机构名称、角色名称或指标中文名称推断业务条线。
- `COMMON` 数据源可以被对公或零售大屏复用。

### 2.4 复合地图

- 主图只展示西安市外轮廓，不要求区县边界。
- 西安本地一级经营机构使用真实经纬度光点。
- 宝鸡、渭南、咸阳、榆林使用示意位置节点：
  - 宝鸡：主图左侧；
  - 渭南：主图右侧；
  - 咸阳：主图上方；
  - 榆林：比咸阳更远的上方。
- 四个异地节点仅作为导航入口，不以颜色、大小或波纹强度表达指标值。
- 地图必须显示“组织分布示意，非地理比例”。
- 点击任一经营机构节点跳转机构详情屏并携带 `orgCode`。

### 2.5 兼容策略

- 数据库存量继续保留 `PROVINCE/BRANCH/PERSON`，不做破坏性枚举迁移。
- 管理端将字段标签由“层级”改为“查看视角”。
- `PROVINCE` 的中文标签改为“全辖”，既有数据仍可显示兼容名称。
- 省级种子屏改名为“西安分行全辖经营总览”。
- 业务条线、机构范围和地图模式作为独立字段/配置新增，不继续扩张 `view_level`。

## 3. 目标与非目标

### 3.1 目标

1. 对公与零售查看人员通过角色获得精确的大屏访问权；
2. 支持跨兄弟机构的命名机构组授权，不向用户授予全平台 `ALL`；
3. 在机构列表中维护本地经营属性、城市和坐标，不污染外部同步表；
4. 对公、零售、共用数据源可被明确识别并在保存、发布、运行时三处校验；
5. 提供可配置的“西安主图 + 四个异地分行节点”复合地图；
6. 存量三屏、存量画布 JSON 和存量陕西地图继续可用；
7. 所有权限、机构组和地图点位变更可审计、Fail Close。

### 3.2 非目标

- 不建设独立岗位、职务或岗位同步体系；
- 不改变 `EXT_ORG_INFO` 的外部同步契约；
- 不根据机构名称自动识别对公、零售或经营机构；
- 不自动对子树汇总 `ORG_INDEX_RESULT`；
- 不建设地图模板市场、在线上传任意 GeoJSON 或互联网地图服务；
- 不展示西安区县边界；
- 不以地图节点样式编码经营指标；
- 不将命名机构组扩展为所有业务模块通用的 `DataScopeType`，本期只提供独立的机构组授权契约。

## 4. 术语模型

### 4.1 查看视角

| 存量值 | 新中文标签 | 语义 |
|---|---|---|
| `PROVINCE` | 全辖 | 西安分行全辖或跨机构组总览 |
| `BRANCH` | 机构 | 单一机构详情；不限名称中是否含“支行” |
| `PERSON` | 个人 | 单一员工详情 |

`view_level` 不决定地图底图、不决定数据源业务条线，也不直接决定机构组。

### 4.2 机构性质与经营等级

机构本地画像至少表达两个正交属性：

- `orgNature`：机构的组织性质，如 `DEPARTMENT/LOCAL_BRANCH/SECONDARY_BRANCH/OUTLET/OTHER`；
- `operatingLevel`：经营管理等级，如 `PRIMARY/SUBORDINATE/NONE`。

示例：

| 机构 | orgNature | operatingLevel |
|---|---|---|
| 零售客户一部 | `DEPARTMENT` | `PRIMARY` |
| 延兴门西路支行 | `LOCAL_BRANCH` | `PRIMARY` |
| 宝鸡分行 | `SECONDARY_BRANCH` | `PRIMARY` |
| 宝鸡分行下属社区支行 | `OUTLET` | `SUBORDINATE` |

### 4.3 命名机构组

命名机构组是一份由管理员显式维护的机构编码集合，具有稳定编码、名称、用途、状态和审计信息。

本期至少需要：

| 建议编码 | 名称 | 用途 |
|---|---|---|
| `ORG_GRP_PRIMARY_OPERATING_UNITS` | 全辖一级经营机构 | 全辖/零售大屏的比较与地图节点范围 |
| `ORG_GRP_CORP_DEPARTMENTS` | 对公部门组 | 对公部门业务数据范围 |

机构组成员不通过名称匹配或 `ORG_LEVEL` 自动变化；机构调整后由管理员审核并显式更新成员。

## 5. 总体架构

```text
员工 ──分配角色──> PT_ROLE / PT_USER_ROLE
                       │
                       ├──资源授权──> 大屏查看接口
                       ├──屏级授权──> RPT_SCREEN_ACCESS_ROLE
                       └──机构组授权──> PT_ROLE_ORG_GROUP
                                              │
RPT_SCREEN ──org_group_code──> PT_ORG_GROUP ──成员──> PT_ORG_GROUP_MEMBER
      │                                                 │
      └──允许角色──> RPT_SCREEN_ACCESS_ROLE             └──画像──> PT_ORG_PROFILE

RPT_SCREEN_DATASOURCE ──biz_line──> CORP / RETAIL / COMMON
RPT_SCREEN_BLOCK ──绑定──> 数据源
地图组件 ──读取屏机构组 + 机构画像──> 西安真实点位 + 异地示意节点
```

模块职责：

| 模块 | 职责 |
|---|---|
| `auth-permission-center` | 机构画像、机构组、组成员、角色-机构组授权、跨模块 `OrgGroupApi` |
| `report-analytics-center` | 屏查看视角/业务条线/机构组绑定、屏级角色白名单、数据源条线、运行时授权、查询注入、地图渲染包 |
| `xanzc_frontend` | 机构属性与机构组管理、大屏创建/绑定、数据源条线、复合地图配置与渲染 |

`report-analytics-center` 禁止直连 auth 的 Mapper/Entity，只能调用 `OrgGroupApi`/`OrgApi`。

## 6. 认证授权模块设计

### 6.1 `PT_ORG_PROFILE`：机构本地画像

不修改 `EXT_ORG_INFO`。新增本地扩展表：

| 字段 | 类型建议 | 必填 | 说明 |
|---|---|---|---|
| `ORG_CODE` | varchar(20) PK | 是 | 对应 `EXT_ORG_INFO.ORG_CODE`，不建数据库外键 |
| `ORG_NATURE` | varchar(30) | 是 | `DEPARTMENT/LOCAL_BRANCH/SECONDARY_BRANCH/OUTLET/OTHER` |
| `OPERATING_LEVEL` | varchar(20) | 是 | `PRIMARY/SUBORDINATE/NONE` |
| `OWNER_OPERATING_ORG_CODE` | varchar(20) | 条件必填 | `SUBORDINATE` 下属网点必须显式填写归属的一级经营机构 |
| `CITY_CODE` | varchar(20) | 否 | 行政区划代码；地图点位启用时必填 |
| `CITY_NAME` | varchar(100) | 否 | 城市名称 |
| `LNG` | decimal(10,6) | 否 | 真实经度；西安地图节点启用时必填 |
| `LAT` | decimal(10,6) | 否 | 真实纬度；西安地图节点启用时必填 |
| `COORD_SYS` | varchar(10) | 条件必填 | 经纬度坐标系；本期地图点位固定 `GCJ02` |
| `STATUS` | varchar(10) | 是 | `ACTIVE/DISABLED` |
| `VERSION` | int | 是 | 乐观锁版本 |
| `CREATED_BY/TIME` | 通用列 | 是 | 创建审计 |
| `UPDATED_BY/TIME` | 通用列 | 是 | 更新审计 |
| `REMARK` | varchar(500) | 否 | 口径说明 |

约束：

1. `ORG_CODE` 必须能通过 `OrgMapper` 查询到有效 `EXT_ORG_INFO`；
2. `OPERATING_LEVEL=SUBORDINATE` 时 `OWNER_OPERATING_ORG_CODE` 必填，目标必须是有效 `PRIMARY` 机构且位于当前机构的祖先链上；运行时不得按名称或“最近祖先”自动猜测；
3. `LNG` 范围 `[-180,180]`，`LAT` 范围 `[-90,90]`；
4. 经纬度存在时 `COORD_SYS` 必须为 `GCJ02`；禁止在未声明、未转换的情况下混用 WGS-84、GCJ-02 或 BD-09；
5. `SECONDARY_BRANCH + PRIMARY` 是合法组合；
6. `DEPARTMENT + PRIMARY` 是合法组合，不能因机构性质是部门而拒绝；
7. 外部机构停用时画像不物理删除，运行时与 `EXT_ORG_INFO.ORGAN_STATE` 共同 Fail Close。

### 6.2 `PT_ORG_GROUP`：命名机构组

| 字段 | 类型建议 | 必填 | 说明 |
|---|---|---|---|
| `ID` | bigint AUTO PK | 是 | 主键 |
| `GROUP_CODE` | varchar(64) UK | 是 | 稳定业务编码 |
| `GROUP_NAME` | varchar(100) | 是 | 中文名称 |
| `GROUP_PURPOSE` | varchar(30) | 是 | 本期固定 `REPORT_SCREEN` |
| `STATUS` | varchar(10) | 是 | `ACTIVE/DISABLED` |
| `VERSION` | int | 是 | 乐观锁版本 |
| 通用审计列 | - | 是 | 创建/更新人及时间 |
| `REMARK` | varchar(500) | 否 | 口径说明 |

### 6.3 `PT_ORG_GROUP_MEMBER`：机构组成员

| 字段 | 类型建议 | 必填 | 说明 |
|---|---|---|---|
| `ID` | bigint AUTO PK | 是 | 主键 |
| `GROUP_CODE` | varchar(64) | 是 | 机构组编码 |
| `ORG_CODE` | varchar(20) | 是 | 直接成员机构编码 |
| `STATUS` | varchar(10) | 是 | `ACTIVE/DISABLED` |
| 通用审计列 | - | 是 | 创建/更新人及时间 |

唯一约束：`(GROUP_CODE, ORG_CODE)`。

本期成员语义固定为“直接机构行”，不自动展开组织子树。下属网点的业绩已由上游汇总到一级经营机构自身 `ORG_INDEX_RESULT` 行。

### 6.4 `PT_ROLE_ORG_GROUP`：角色机构组授权

| 字段 | 类型建议 | 必填 | 说明 |
|---|---|---|---|
| `ID` | bigint AUTO PK | 是 | 主键 |
| `ROLE_ID` | varchar(50) | 是 | `PT_ROLE.ROLE_ID` |
| `GROUP_CODE` | varchar(64) | 是 | 允许访问的机构组 |
| `STATUS` | varchar(10) | 是 | `ACTIVE/DISABLED` |
| 通用审计列 | - | 是 | 创建/更新人及时间 |

唯一约束：`(ROLE_ID, GROUP_CODE)`。

一名员工多角色同时生效；只要任一有效角色绑定目标机构组，即具有该组授权。机构组授权不扩大其他报表端点的 `REPORT` DataScope。

### 6.5 不扩展 `DataScopeType`

本期不新增 `ORG_GROUP` 到全局 `DataScopeType`，原因：

1. 现有 DataScope 合并使用单一优先级最大值，命名集合不存在稳定的全序关系；
2. 把 `ORG_GROUP` 硬塞入 `ALL > ORG_SUBTREE > ...` 会产生错误并集；
3. 命名机构组当前只服务大屏跨兄弟机构场景，不应无评估地改变所有模块的数据权限语义。

大屏采用四项门禁：

```text
RBAC 资源允许
AND 屏级角色白名单有效且非空
AND 屏绑定机构组有效
AND 存在同一个当前有效角色，同时位于屏级角色白名单并绑定该机构组
```

运行时判定为 `当前有效角色 ∩ 屏级角色白名单 ∩ 机构组绑定角色 ≠ ∅`。不允许角色 A 只提供屏访问、角色 B 只提供机构组，再拼接成单个角色未被授予的组合权限；这项相关性约束不恢复激活角色或单角色切换模型，员工的全部有效角色仍参与求交集。

`SYS_ADMIN` 只是在第 1 项中按 RBAC 获得资源的一个角色，绝不是大屏数据范围旁路。其运行时
请求同样必须通过屏级角色白名单、有效机构组与同一角色三方交集；缺任一门均拒绝。不得以
`isSystemAdmin()`、管理员菜单或 SQL 探查资源替代后续三项门禁。

### 6.6 `OrgGroupApi` 跨模块契约

新增 API，report 只依赖 DTO：

```java
public interface OrgGroupApi {
    OrgGroupDTO getGroup(String groupCode);
    Set<String> listActiveMemberCodes(String groupCode);
    OrgGroupScopeDTO resolveAuthorizedScope(
        String empId,
        String groupCode,
        Collection<String> allowedRoleCodes
    );
    OrgGroupRoleCheckDTO checkRoleBindings(String groupCode, Collection<String> roleCodes);
    Map<String, OrgProfileDTO> getActiveProfiles(Collection<String> orgCodes);
}
```

`OrgGroupScopeDTO` 至少包含：

- `groupCode`；
- `authorized`；
- `memberOrgCodes`；
- `deniedReasonCode`，仅内部审计使用，不向普通用户暴露角色细节。

`allowedRoleCodes` 由 report 从屏级白名单读取，不接受前端传值。auth 必须用它与员工当前有效角色、机构组有效绑定角色求三方交集；交集为空即 `authorized=false`。

`OrgGroupRoleCheckDTO` 供大屏保存/发布校验屏级角色配置，至少返回：

- `groupCode`；
- `validRoleCodes`；
- `unboundRoleCodes`；
- `invalidRoleCodes`；
- `satisfiable`：是否至少存在一个有效屏级角色已绑定该机构组。

Fail Close：组不存在、停用、无成员、角色未绑定、机构失效、API 异常均不得返回全量数据。

### 6.7 管理端 REST

建议端点：

| 方法 | URL | 说明 |
|---|---|---|
| `GET` | `/api/admin/org-profiles` | 机构列表并附带本地画像 |
| `PUT` | `/api/admin/org-profiles/{orgCode}` | 更新单机构画像 |
| `GET` | `/api/admin/org-groups` | 机构组列表 |
| `POST` | `/api/admin/org-groups` | 新建机构组 |
| `PUT` | `/api/admin/org-groups/{groupCode}` | 修改机构组 |
| `PUT` | `/api/admin/org-groups/{groupCode}/members` | 覆盖式保存成员 |
| `PUT` | `/api/admin/org-groups/{groupCode}/roles` | 覆盖式绑定角色 |

机构组成员、角色绑定、机构坐标和经营等级变更均属于高影响权限配置：独立 URL、独立资源、独立审计，不与普通机构名称编辑共用权限。

## 7. 报表大屏数据模型

### 7.1 `RPT_SCREEN` 增量字段

| 字段 | 类型建议 | 默认值 | 说明 |
|---|---|---|---|
| `BIZ_LINE` | varchar(20) | `COMMON` | 屏级绑定约束：`CORP/RETAIL/COMMON` |
| `ORG_SCOPE_MODE` | varchar(20) | `LEGACY_CONTEXT` | `LEGACY_CONTEXT/NAMED_GROUP` |
| `ORG_GROUP_CODE` | varchar(64) | null | `NAMED_GROUP` 时必填；逻辑引用 auth 机构组，不建跨模块数据库外键 |

屏级 `BIZ_LINE` 不是数据归属真相，只用于发布前与数据源的 `BIZ_LINE` 做兼容校验。

数据库默认值仅用于兼容存量调用；新建大屏时后端必须要求客户端显式提交 `BIZ_LINE` 与 `ORG_SCOPE_MODE`，不能因列有默认值而省略业务选择。

绑定矩阵：

| 屏 BIZ_LINE | 允许的数据源 BIZ_LINE |
|---|---|
| `CORP` | `CORP`、`COMMON` |
| `RETAIL` | `RETAIL`、`COMMON` |
| `COMMON` | `COMMON` |

若将来确需一屏混合公司与零售，必须单独设计 `MIXED` 高权限场景；本期不通过放宽 `COMMON` 实现。

### 7.2 `RPT_SCREEN_DATASOURCE` 增量字段

| 字段 | 类型建议 | 默认值 | 说明 |
|---|---|---|---|
| `BIZ_LINE` | varchar(20) | `COMMON` | 数据归属：`CORP/RETAIL/COMMON` |

规则：

1. 新建数据源必须显式选择业务条线；
2. 存量数据源迁移为 `COMMON`，不得根据名称批量猜测；
3. 只要任一当前发布包或发布归档引用数据源，禁止原地修改其查询语义（`dsType`、`sourceKind`、
   `bizLine`、`configJson`、`timeParamJson`、`status`）或删除；拒绝响应必须返回完整引用屏编码；
4. 名称、备注等纯展示元数据可更正，但仍留审计；
5. 需要调整查询语义时，必须调用既有“新建数据源”契约创建副本，再修改草稿绑定并重新发布，
   不得借更新接口改变已发布读数；
6. 试跑返回条线元数据，便于管理员核对；
7. 数据源创建、更新、删除、试跑和独立列探测均写结构化审计。
8. 数据源状态只允许 `ACTIVE/DISABLED`：存储一律规范为大写；新建省略时默认 `ACTIVE`，更新省略时
   保持原值，显式非法值拒绝。屏状态使用同一枚举和规范化规则。

### 7.3 `RPT_SCREEN_ACCESS_ROLE`：屏级查看角色

机构组权限只能回答“用户是否可访问这组机构”，不能区分绑定同一机构组的不同大屏。新增屏级角色白名单：

| 字段 | 类型建议 | 必填 | 说明 |
|---|---|---|---|
| `ID` | bigint AUTO PK | 是 | 主键 |
| `SCREEN_ID` | bigint | 是 | 所属 `RPT_SCREEN.id` |
| `ROLE_CODE` | varchar(50) | 是 | 允许查看该屏的 `PT_ROLE.ROLE_CODE`；通过 auth `RoleApi` 校验，不建跨模块数据库外键 |
| `STATUS` | varchar(10) | 是 | `ACTIVE/DISABLED` |
| 通用审计列 | - | 是 | 创建/更新人及时间 |

唯一约束：`(SCREEN_ID, ROLE_CODE)`。

- `NAMED_GROUP` 屏发布前至少配置一个有效查看角色；
- 屏级角色白名单与 `PT_ROLE_ORG_GROUP` 分别校验，不能只配其一；
- 保存/发布通过 `OrgGroupApi.checkRoleBindings` 校验白名单中的角色是否有效并已绑定目标机构组；
- 存量 `LEGACY_CONTEXT` 保持其既有机构上下文兼容；一旦配置白名单也必须命中，不能由管理员身份绕过；
- `SYS_ADMIN` 不跳过白名单、机构组或同一角色门禁。管理员如需查看命名机构组屏，也须拥有同一条
  已获白名单和机构组绑定的有效角色。

### 7.4 地图配置进入组件树

新地图仍可沿用组件标识 `MapCenter`，通过 `propValue.schemaVersion=2` 区分新模式，避免旧发布包无法识别。

```json
{
  "schemaVersion": 2,
  "mode": "XIAN_COMPOSITE",
  "baseRegion": "XIAN_OUTLINE",
  "localSelector": {
    "cityCode": "610100",
    "operatingLevel": "PRIMARY"
  },
  "satelliteNodes": [
    {"orgCode": "128", "anchor": "LEFT", "targetScreenCode": "SCR_BRANCH"},
    {"orgCode": "191", "anchor": "RIGHT", "targetScreenCode": "SCR_BRANCH"},
    {"orgCode": "169", "anchor": "TOP", "targetScreenCode": "SCR_BRANCH"},
    {"orgCode": "129", "anchor": "FAR_TOP", "targetScreenCode": "SCR_BRANCH"}
  ],
  "disclaimer": "组织分布示意，非地理比例"
}
```

约束：

- `orgGroupCode` 不由组件 JSON 提供，统一取所属 `RPT_SCREEN.ORG_GROUP_CODE`，防止客户端换组；
- `satelliteNodes.orgCode` 必须属于屏绑定机构组；
- `LEFT/RIGHT/TOP/FAR_TOP` 是示意锚点，不转换为伪造经纬度；
- 本期固定四个锚点各最多一个节点；
- `disclaimer` 可改文案但不可为空或隐藏；
- 本地真实点位取机构画像的经纬度；缺坐标的一级经营机构使发布失败，不在线上静默遗漏。
- 机构组内每个有效 `PRIMARY` 成员必须恰好落入“西安本地节点”或“四个异地节点”之一；未落位或重复落位均拒绝发布。

### 7.5 旧地图点位表

`RPT_SCREEN_MAP_POINT` 在兼容期保留：

- schemaVersion 1 的陕西地图继续读取旧表；
- schemaVersion 2 的复合地图只读取 `OrgGroupApi` 返回的机构画像；
- 新管理端不再向旧表写入新模式点位；
- 完成所有旧屏迁移前不删除旧表、不改旧发布包。

### 7.6 初始大屏配置矩阵

| 屏 | 查看视角 | BIZ_LINE | 机构组 | 地图默认值 | 说明 |
|---|---|---|---|---|---|
| `SCR_PROVINCE`（改名“西安分行全辖经营总览”） | `PROVINCE/全辖` | `COMMON` | `ORG_GRP_PRIMARY_OPERATING_UNITS` | `XIAN_COMPOSITE` | 沿用稳定屏编码；只允许共用数据源 |
| `SCR_CORP_OVERVIEW`（新增） | `PROVINCE/全辖` | `CORP` | `ORG_GRP_CORP_DEPARTMENTS` | 默认不放地图 | 对公部门是业务责任范围，不应被误画成地理网点；可使用列表、排行等组件 |
| `SCR_RETAIL_OVERVIEW`（新增） | `PROVINCE/全辖` | `RETAIL` | `ORG_GRP_PRIMARY_OPERATING_UNITS` | `XIAN_COMPOSITE` | 展示西安本地一级经营机构真实点位与四个异地导航节点 |

不得通过 SQL 直接把正在使用的 `SCR_PROVINCE` 发布版本原地切成 schemaVersion 2。应先生成新草稿、补齐机构组/角色/点位、完成预览与验收，再发布新版本；仅含可信不可变 `bindSnapshots` 的旧发布归档可回滚。

## 8. 运行时授权与取数

### 8.1 整屏查看

`GET /api/screen/view/{screenCode}` 执行顺序：

1. 平台认证、PT_RESOURCE、`@BizAuth(REPORT, READ)`；
2. 读取 ACTIVE 屏；
3. 通过既有 `CurrentUserApi.getCurrentRoleCodes()` 取得当前用户全部有效角色；已配置白名单必须有交集，
   `SYS_ADMIN` 也不例外；
4. `ORG_SCOPE_MODE=LEGACY_CONTEXT`：保持既有机构上下文，但不得以管理员身份跳过已配置白名单；
5. `ORG_SCOPE_MODE=NAMED_GROUP`：调用 `OrgGroupApi.resolveAuthorizedScope(currentEmpId, orgGroupCode, screenAllowedRoleCodes)`，由 auth 校验同一角色同时命中屏白名单和机构组绑定；
6. 未授权统一返回大屏数据范围拒绝，不返回屏名称、画布 JSON、数据源 ID或机构成员；
7. 授权后才合成渲染包和复合地图点位；
8. 草稿预览除上述检查外，还必须具备画布管理读取资源，修复“普通查看者可读取草稿”的既有遗留。

### 8.2 区块取数请求防篡改

现有运行时请求由客户端直接提交 `dsId`，不足以证明该数据源属于当前屏。`schemaVersion`、`blockId`
和 `dsId` 都是 HTTP JSON 边界的严格原生整数契约：v2 只能精确为 `2`，v1 只能精确为 `1`；字符串、
浮点、缺失、重复、其他值或企图以未知版本降级均 fail-close。v2 请求必须改为：

```json
{
  "screenCode": "SCR_RETAIL_OVERVIEW",
  "blockId": 123,
  "period": "LATEST",
  "dateFrom": null,
  "dateTo": null,
  "contextParams": {}
}
```

服务端先按 `screenCode` 查得**恰好一条** ACTIVE 屏，再根据 `screenCode + blockId` 从当前发布快照
解析真实 `dsId`；客户端附带的 `dsId` 不参与解析：

1. 校验用户有权查看该屏及其机构组；
2. 校验 block 属于屏的当前发布版本；
3. 校验屏与数据源业务条线兼容；
4. 从授权机构组获得服务端机构编码集合；
5. 执行参数化查询。

v1 旧屏也必须同时提交 `screenCode + dsId`，并且仅由该屏**当前** `canvasPublishedJson` 中可信、不可变
的 `bindSnapshots` 证明。服务端先精确查得一条 ACTIVE `screenCode`，再校验发布包内 ChartWidget 的
`blockId` 与快照键双向一一对应、快照中的 `dsId` 与请求一致；不得读取当前草稿、当前 `RPT_SCREEN_BLOCK`，
也不得把归档当作当前运行身份。当前包缺少/损坏/重复 `bindSnapshots` 时统一返回 `RPT-43023`；身份不匹配
或屏不唯一时返回既有运行身份拒绝码。发布归档只参与回滚和数据源引用冻结：任一无可信快照的归档不能
直接回滚，并使数据源查询语义更新/删除保守拒绝，避免错误宣称“未被历史引用”。

上线前必须对当前发布包和归档做**只读盘点**。仅在能由可信发布历史、审计记录或备份复原原始不可变
绑定时，才可经业务核对后受控回填；没有可信来源的包不得从当前 block 自动迁移，业务方须核对后重新发布。

### 8.3 有效机构集合

命名机构组屏的有效集合为：

```text
屏配置机构组直接成员
∩ 当前用户有效角色获授权的同一机构组成员
∩ EXT_ORG_INFO 有效机构
∩ PT_ORG_PROFILE ACTIVE 机构
```

本期一个屏只绑定一个机构组。客户端不得传 `orgGroupCode`、`orgCodes` 或机构集合覆盖参数。

### 8.4 宽表取数

对 `WIDE_TABLE + NAMED_GROUP`：

- 主体列固定来自服务端已校验的 `subjectCol`；
- 生成 `subjectCol IN (?, ?, ...)`，每个机构编码使用 PreparedStatement 参数；
- `groupBy=SUBJECT` 返回每个一级经营机构自身行；
- `groupBy=NONE` 对机构组成员自身行聚合；
- 禁止同时读取父级汇总行与其下属明细行；
- 机构组为空直接拒绝，不生成无 WHERE 条件查询。

### 8.5 CUSTOM_SQL 与设计器列探测

`NAMED_GROUP + CUSTOM_SQL` 本期延期，保存、发布、运行和试跑一律拒绝；不以 SQL 注释标记、主体列
声明或字符串改写提供“已支持”的错觉。命名机构组当前仅允许服务端固定生成的
`WIDE_TABLE + ORG_INDEX_RESULT + org_code` 参数化谓词。`LEGACY_CONTEXT` 的既有 `CUSTOM_SQL` 继续
按原 SQL 白名单和数据范围契约运行，不受本条延期影响。

设计器如需查看已保存数据源列，必须调用独立的
`POST /api/screen/admin/datasources/{id}/probe-columns`：该入口拥有独立 `EXECUTE_SQL` 资源、非空
`reason`、目标/前后快照/操作人/traceId 结构化审计；不得借 `/api/screen/data` 的 v1 兼容支路探测
任意 `dsId`。命名机构组探测同样只使用服务端解析的测试机构组成员。

## 9. 复合地图渲染设计

### 9.1 地理与示意坐标分离

```text
                  榆林分行（FAR_TOP）
                           ●
                  咸阳分行（TOP）
                           ●
宝鸡分行（LEFT） ●   [西安市外轮廓 + 本地真实点位]   ● 渭南分行（RIGHT）

                    组织分布示意，非地理比例
```

- 西安主图内节点使用 ECharts `geo + effectScatter` 与真实经纬度；
- 四个异地节点使用组件内部绝对/弹性布局锚点；
- 两类坐标不得共用一个经纬度数组；
- 异地节点与主图之间可使用装饰连线，但连线不表达距离、流量或上下级关系；
- 所有节点固定视觉权重，不编码指标；
- 节点必须键盘可聚焦并提供可读名称，不能只依赖波纹动画识别。

### 9.2 西安轮廓资产

- 运行时不访问互联网地图服务；
- 可从仓库现有 `shaanxi.json` 中提取西安市 Feature，生成静态 `xian-outline.json`；
- 提取结果需保留来源 URL、获取日期、内容哈希、行政区名称、`coordinateSystem=GCJ02` 和资产校验测试；
- 点位统一使用 GCJ-02，与当前 DataV 边界资产保持一致；其他坐标系必须在入库前由受控工具转换并记录来源，本期运行时不做隐式转换；
- 仓库已有文件不等于已取得生产使用授权。DataV 官方说明 GeoAtlas 数据来自高德且版权问题需参照高德开放平台，因此上线前必须取得并归档可用于本项目的授权依据；
- 若现有 Feature 的质量或授权不满足要求，由项目方提供有授权、坐标系明确的权威 GeoJSON；实现者不得自行抓取不明来源地图数据。

### 9.3 本地节点选择

西安本地节点必须同时满足：

1. 属于屏绑定机构组；
2. `PT_ORG_PROFILE.OPERATING_LEVEL=PRIMARY`；
3. `CITY_CODE=610100`；
4. 机构和画像均 ACTIVE；
5. 经纬度完整合法。

“零售客户一部”等部门只要满足上述条件，就作为一级经营机构显示；其下属社区/小微支行不显示。

### 9.4 异地节点

固定业务映射：

| 机构编码 | 机构 | 锚点 |
|---|---|---|
| `128` | 宝鸡分行 | `LEFT` |
| `191` | 渭南分行 | `RIGHT` |
| `169` | 咸阳分行 | `TOP` |
| `129` | 榆林分行 | `FAR_TOP` |

发布时必须校验四个编码均属于屏绑定机构组且机构有效。映射是当前业务配置，不把城市名称写死在渲染组件判断中。

### 9.5 点击与导航

- 本地和异地节点统一跳转 `/screen/{targetScreenCode}?orgCode={orgCode}`；
- 默认目标屏 `SCR_BRANCH`，允许组件配置覆盖目标屏编码；
- 跳转前端只负责导航，目标屏后端重新执行资源与机构组权限检查；
- 禁止因来源屏已授权而信任目标 `orgCode`。

## 10. 设计器与管理端交互

### 10.1 新建/编辑大屏

新建弹框字段调整为：

1. 屏名称；
2. 查看视角：全辖/机构/个人；
3. 业务条线：公司/零售/共用；
4. 机构范围模式：传统上下文/命名机构组；
5. 命名机构组：仅范围模式为命名机构组时显示。

保存时后端通过 `OrgGroupApi` 校验组存在且 ACTIVE。前端下拉结果不是安全边界。

大屏属性区增加“允许查看角色”多选，候选项通过 auth `RoleApi` 获取。角色列表只用于配置体验，保存与发布仍由后端重新校验角色有效性。

### 10.2 数据源管理

- 新建、编辑数据源时业务条线必选；
- 列表新增“业务条线”列和筛选项；
- 已被屏引用时展示引用关系；
- 修改条线导致引用不兼容时，后端拒绝并返回冲突屏列表；
- `COMMON` 明确显示为“共用”，不能用空值表示。

### 10.3 机构列表

在现有机构管理列表增加本地扩展列：

- 机构性质；
- 经营管理等级；
- 归属一级经营机构；
- 所属城市；
- 经度、纬度；
- 坐标系（本期固定 GCJ-02，并在录入控件旁明确提示）；
- 画像状态。

外部同步字段只读，本地扩展字段通过独立编辑入口修改，并显示“本地经营属性，不回写上游机构系统”。

### 10.4 机构组管理

提供独立机构组页面：

- 左侧机构组列表；
- 中部机构树/列表，可按机构性质、经营等级、城市筛选；
- 右侧已选直接成员；
- 角色绑定页签；
- 保存前显示新增/移除机构及新增/移除角色差异；
- 覆盖式保存必须二次确认；
- 禁止通过筛选条件自动长期跟随，保存的是明确成员快照。

### 10.5 地图组件

- `MapCenter` 进入可拖拽组件面板；
- 属性面板支持地图模式：陕西兼容地图/西安复合经营地图；
- 西安复合模式显示四个异地节点配置及目标屏；
- 机构点位不在画布 JSON 中手填经纬度，只从机构画像读取；
- 设计态使用已授权/已配置机构画像预览；缺配置显示明确清单，不用假点位兜底；
- 新建全辖屏不再依赖历史种子才能获得地图组件。

## 11. 保存、发布与运行时三道校验

### 11.1 保存草稿

- 校验字段枚举和机构组存在；
- 校验地图组件结构与四个锚点唯一性；
- 校验屏/数据源业务条线兼容；
- 允许机构坐标暂缺，但草稿预览必须显示缺口提示。

### 11.2 发布

- 重做全部保存校验，不信任历史草稿；
- 机构组必须 ACTIVE 且非空；
- 屏级查看角色至少一个有效；
- 地图引用的全部机构必须有效；
- 西安本地一级经营机构必须有合法经纬度；
- 四个异地节点必须存在于组内；
- 机构组内全部有效 `PRIMARY` 成员均已唯一落位，不能因城市或点位配置缺失被静默省略；
- 至少一个有效屏级查看角色同时绑定目标机构组；运行时按员工全部有效角色与这两个集合求三方交集；
- block 与数据源业务条线兼容；
- 任一条件失败拒绝发布，不允许“先发布空图、后补权限”。

### 11.3 运行时

- 每次整屏读取和区块取数重新校验角色-机构组授权；
- 权限或机构组变更下一请求立即生效；
- auth API 异常 Fail Close；
- 已发布快照不能固化机构成员和人员权限，防止配置变更后旧权限继续有效；
- 地图样式进入发布快照，机构画像与组成员运行时读取。

## 12. 安全与审计

### 12.1 权限边界

- RBAC 资源授权、屏级角色白名单与机构组授权缺一不可；`SYS_ADMIN` 不存在旁路；
- 屏访问和机构组授权必须由同一个有效角色同时满足，禁止跨角色权限拼接；
- 角色名称或角色码本身不携带可信的业务条线语义，不能仅凭 `CORP/RETAIL` 命名放行；
- 用户不因拥有通用大屏 URL 资源而自动获得任一机构组或屏级访问权；
- 已授权的 `CORP` 屏不能绑定或查询 `RETAIL` 数据源，已授权的 `RETAIL` 屏不能绑定或查询 `CORP` 数据源；
- `COMMON` 数据源只能在屏绑定矩阵允许的范围内复用；
- 客户端传入的 `dsId/orgGroupCode/orgCodes/bizLine` 均不得成为运行时授权依据；
- 所有运行时主体集合由服务端屏配置和 `OrgGroupApi` 解析。

### 12.2 审计事件

至少记录：

| 事件 | 必要内容 |
|---|---|
| 机构画像变更 | orgCode、前后经营属性/城市/坐标、操作人、traceId |
| 机构组变更 | groupCode、新增/移除成员、原因、操作人、traceId |
| 角色组绑定变更 | groupCode、新增/移除角色、原因、操作人、traceId |
| 屏级角色变更 | target、before/after、新增/移除角色、expectedVersion、原因、操作人、traceId |
| 元数据范围变更 | target、允许字段前后差异、expectedVersion、原因、操作人、traceId |
| 数据源创建/更新/删除/试跑/列探测 | target、before/after、完整发布引用屏、原因、操作人、traceId |
| 大屏发布/回滚/放弃草稿 | target、before/after、block 新增/移除、expectedVersion、原因、操作人、traceId |
| 运行时拒绝 | empId、screenCode、blockId/dsId、真实 URL/方法、拒绝原因码、target、traceId；不记录敏感业务数据 |

成员或角色批量覆盖保存必须要求 `reason`，并将差异摘要写入治理审计。

## 13. 错误码与资源

具体序号实施时从当前 `AuthErrorCode`/`RptErrorCode` 最大已用序号后顺延，禁止在规格中伪造可能冲突的最终编号。语义至少包括：

### 13.1 AUTH

- 机构画像不存在/非法；
- 机构组不存在或停用；
- 机构组成员非法；
- 角色未绑定机构组；
- 机构组保存冲突。

### 13.2 RPT

- 屏业务条线与数据源不兼容；
- 屏机构组缺失/无效/空；
- 当前用户无机构组授权；
- 当前用户不在屏级角色白名单；
- 地图机构坐标缺失；
- 复合地图异地节点配置非法；
- 运行时 block 不属于屏发布版本；
- 命名机构组 SQL 未应用安全机构谓词。

新增管理端接口全部登记 `PT_RESOURCE` 并标注 `@BizAuth`。机构组成员、角色绑定、大屏发布继续使用独立高危资源。

## 14. 数据与脚本策略

遵守 Flyway 禁令，实施时新增手工对齐脚本，不修改历史脚本：

1. `docs/superpowers/sql/2026-08-11-auth-org-profile-group.sql`：auth 三张配置表 + 角色组关联表 + 资源；
2. `docs/superpowers/sql/2026-08-11-screen-scope-map-align.sql`：RPT_SCREEN/RPT_SCREEN_DATASOURCE 增量列、RPT_SCREEN_ACCESS_ROLE、资源与存量默认值；
3. `docs/superpowers/sql/2026-08-11-screen-scope-map-seed.sql`：机构组壳、建议角色、复合地图屏配置；具体生产成员和人员角色绑定须由业务负责人复核后填写。

任何 DDL/DML 前必须先对现有 `yiti_test` 做只读盘点；克隆、备份、覆盖、清空和执行均须明确授权。
隔离 `yiti_test` 验证证据完整后，对 `yiti` 的执行仍须再次明确确认。脚本应幂等检查列/索引/资源
是否已存在，但不得包装为自动迁移框架。

## 15. 兼容与迁移

### 15.1 存量默认值

- 所有存量 `RPT_SCREEN.BIZ_LINE=COMMON`；
- 所有存量 `RPT_SCREEN.ORG_SCOPE_MODE=LEGACY_CONTEXT`；
- 所有存量 `RPT_SCREEN_DATASOURCE.BIZ_LINE=COMMON`；
- 不根据名称把现有数据源自动改成 CORP/RETAIL；由管理员逐项核对后修改。

### 15.2 JSON 兼容

- 画布/地图 schemaVersion 1：陕西地图 + 旧点位表；
- schemaVersion 2：地图配置进入组件树 + 机构组/画像；
- 读取集中适配，禁止散落版本判断；
- 运行取数只接受精确整数 schemaVersion 1 或 2，未知/缺失版本不降级；
- 发布新版本后不再降级为 schemaVersion 1；
- 仅含可信不可变 `bindSnapshots` 的历史发布归档可回滚并渲染；无快照归档不得直接回滚，也不得从当前
  可变 block 自动补证。上线前按 §8.2 只读盘点、可信来源受控回填或业务核对后重新发布。

### 15.3 路由兼容

- `/screen/{screenCode}?orgCode=&empId=` 保留；
- 命名机构组全辖屏忽略客户端传入的集合参数；
- 机构详情屏继续使用单一 `orgCode`，并在目标屏重新鉴权。

## 16. TDD 测试策略

所有实现按 Red-Green-Refactor，先写失败测试。

### 16.1 auth 单元测试

1. 部门可以标记为 `PRIMARY`；
2. 二级分行可以标记为 `PRIMARY`；
3. 下属网点必须显式归属有效的祖先 `PRIMARY` 机构；
4. 坐标边界校验；
5. 机构组成员去重、失效机构拒绝；
6. 多角色场景仅同一角色同时命中 `allowedRoleCodes` 与组绑定时授权；
7. 角色 A 命中屏白名单、角色 B 命中组绑定时拒绝权限拼接；
8. 无角色绑定、组停用、空组、API 异常 Fail Close；
9. 角色组覆盖保存乐观锁与差异审计；
10. `EXT_ORG_INFO` 不被画像写接口更新。

### 16.2 report 单元测试

1. 屏/数据源业务条线绑定矩阵；
2. `NAMED_GROUP` 缺 groupCode 拒绝；
3. 保存、发布、运行时三处均校验机构组；
4. schemaVersion 2 由 `screenCode+blockId` 服务端解析数据源；
5. 篡改 `dsId/orgGroupCode/orgCodes` 无效或被拒绝；
6. WIDE_TABLE 生成参数化机构组谓词；
7. 空组不退化为无 WHERE；
8. `NAMED_GROUP + CUSTOM_SQL` 在保存、发布、运行、试跑均拒绝；`LEGACY_CONTEXT` CUSTOM_SQL 回归通过；
9. CORP 屏上下文不能取 RETAIL 数据源，RETAIL 屏上下文不能取 CORP 数据源；
10. 草稿预览需要管理读取资源；
11. 屏级角色白名单与机构组授权分别 Fail Close；
12. 不同角色分别命中屏白名单和机构组时拒绝访问；
13. 复合地图对全部 `PRIMARY` 组成员执行唯一落位校验；
14. schemaVersion 1 兼容路径只能接受当前发布包的可信不可变 `bindSnapshots`：无快照、字符串/浮点身份
    节点、同屏未发布 block、跨屏/任意 dsId、重复 screenCode 均拒绝；无快照归档不可直接回滚；
15. 元数据 allowlist CAS、明确清空 orgGroupCode、发布/草稿绑定复核以及审计失败回滚；
16. 放弃草稿 CAS、发布 block 恢复、并发冲突；
17. 已发布数据源查询语义更新/删除拒绝并返回完整引用屏；
18. SYS_ADMIN 仍须通过白名单、机构组和同一角色门禁。

### 16.3 前端测试

1. 新建弹框展示“查看视角”及业务条线/机构组；
2. 数据源条线必选与列表筛选；
3. MapCenter 可拖入且支持两种地图模式；
4. 复合地图四个锚点位置语义正确；
5. 西安点位读取真实坐标；
6. 下属社区/小微支行不作为主图独立节点；
7. “组织分布示意，非地理比例”不可隐藏；
8. 点击节点携带正确 orgCode；
9. 设计态缺坐标展示配置缺口，不生成假数据；
10. 旧陕西地图发布包继续渲染。

### 16.4 集成测试

- auth `OrgGroupApi` 与 report 跨模块契约；
- 角色资源有、屏级角色无 → 拒绝；
- 屏级角色有、机构组无 → 拒绝；
- 机构组有、角色资源无 → 拒绝；
- 角色 A 只在屏白名单、角色 B 只绑定机构组 → 拒绝；
- 零售查看角色 + 一级经营机构组 → 只返回组内机构自身 `ORG_INDEX_RESULT` 行；
- 对公屏 + 对公部门组 + 有效查看角色 → 不能读取零售数据源；
- 宝鸡/渭南/咸阳/榆林均可点击进入自身机构屏；
- 权限撤销后下一请求立即失败；
- 现有 `SCR_PROVINCE/SCR_BRANCH/SCR_PERSON` 回归通过。

## 17. 验收标准

### 17.1 权限

- 给员工分配已绑定机构组与大屏资源的角色后，无需新增岗位记录即可访问；
- 未命中屏级白名单或机构组授权的员工不能读取屏配置、地图机构列表或区块数据；
- 屏白名单和机构组绑定由不同角色分别命中时仍不得访问；
- 对公、零售屏不能交叉绑定或读取对方专属数据源；
- 不需要给条线查看角色配置全平台 `REPORT=ALL`。

### 17.2 机构范围

- “一级经营机构”由本地画像与显式机构组共同确定，不依赖 `ORG_LEVEL` 或名称；
- 零售客户一部等部门可作为一级经营机构；
- 异地分行下属网点不在全辖主图独立出现；
- 异地分行和本地一级经营机构均读取自身机构指标行；
- 选取西安本地与异地分行样本，将自身 `ORG_INDEX_RESULT` 与上游已确认汇总口径对账通过；
- 机构组成员变更经审计后下一请求生效。

### 17.3 地图

- 主图仅显示西安市外轮廓；
- 西安本地一级经营机构使用真实经纬度；
- 宝鸡左、渭南右、咸阳上、榆林更远上；
- 四个异地节点固定视觉权重，只用于导航；
- 明确展示“组织分布示意，非地理比例”；
- 缺失坐标或缺失四个异地机构时禁止发布。

### 17.4 兼容

- 原有三种 `view_level` 数据无须迁移；
- 原陕西地图可继续打开；旧发布归档仅在具有可信不可变 `bindSnapshots` 时可回滚，无快照归档按 §8.2
  盘点并受控迁移或重新发布；
- 存量数据源默认 COMMON，不发生自动误分类；
- 新建全辖屏可以从组件面板添加地图，不依赖历史种子 JSON。

## 18. 风险、替代解释与控制

| 风险/易错前提 | 独立判断 | 控制与替代方案 |
|---|---|---|
| “数据源声明为 CORP 就一定只有对公数据” | 不成立。标签是治理元数据，不是对 SQL 内容的数学证明，错标或 CUSTOM_SQL 越界仍可能泄露数据 | 数据源负责人逐项复核、条线变更审计、发布绑定矩阵和抽样验数；本期拒绝按名称推断。后续若已有可靠指标目录，可增加指标到条线的数据血缘校验 |
| “直接读异地分行自身行就必然等于完整汇总” | 这是已确认的上游业务契约，但不是大屏查询本身能证明的事实 | 上线前对西安与四家异地分行做样本对账；发现缺口应修复上游 `ORG_INDEX_RESULT`，不能临时改成子树求和，否则可能重复计算 |
| “机构组建好后会自动保持正确” | 不成立。组织调整、机构停用或画像变化会造成配置漂移 | 管理端显示失效成员/未落位机构健康状态；发布和每次运行 Fail Close；定期核查只告警，不自动增删成员 |
| “角色就是岗位” | 不成立。本期只是用角色承载查看能力，因为现系统没有权威岗位数据 | 角色命名表达能力而非职务，人员分配走审批并可撤销；未来接入岗位主数据时另建任职有效期和岗位到角色映射，不把本期角色表反向解释为 HR 岗位 |
| “多角色都合法，权限拼接也必然合法” | 不成立。跨角色组合可能形成单个角色从未获批的权限 | 强制同一有效角色同时命中屏白名单和机构组绑定，三方交集为空即拒绝 |
| “仓库已有地图文件即可直接生产使用” | 不成立。文件存在不能证明授权，也不能证明点位坐标系一致 | 上线前归档地图授权、来源和哈希；边界与点位统一 GCJ-02；授权或坐标系不明即阻断发布 |
| “示意节点位置可以被理解为实际空间关系” | 有误读风险，尤其榆林仅因布局需要位于更远上方 | 四个异地节点不编码距离/数值/方向关系，固定展示“组织分布示意，非地理比例”，并为节点提供可访问文本 |

## 19. 上线前配置清单

以下是部署配置，不是待定设计，不得由代码按名称猜测：

1. 业务负责人确认 `ORG_GRP_CORP_DEPARTMENTS` 的完整机构编码成员；
2. 业务负责人确认 `ORG_GRP_PRIMARY_OPERATING_UNITS` 的完整一级经营机构成员；
3. 为全部西安本地一级经营机构维护城市、GCJ-02 经纬度和 `PRIMARY` 标识；
4. 确认宝鸡 `128`、渭南 `191`、咸阳 `169`、榆林 `129` 仍为有效权威机构编码；
5. 将每个存量/新增数据源逐项标记为 CORP、RETAIL 或 COMMON；
6. 创建或选定对公、零售查看角色，绑定相应屏资源、屏级角色白名单和机构组；
7. 将查看角色分配给经业务审批的员工；
8. 归档西安边界资产的生产使用授权、来源、哈希与 GCJ-02 坐标系说明；
9. 抽样核对点位坐标和一级经营机构自身 `ORG_INDEX_RESULT` 汇总口径；
10. 完成生产数据快照备份、权限矩阵复核和验收记录。

上述任一项未完成，相应新屏不得发布。
