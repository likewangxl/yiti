# 机构位置能力启用清单

本文是代码能力的部署准备清单，不是 schema 已实施或真实机构已定位的证明。2026-09-08 只读复核：`yiti` 与 `yiti_test` 均无 `PT_ORG_LOCATION`，目标库也未登记新位置资源。当前目标库未执行本清单中的结构、资源或画像写入；本轮不交付 DDL 脚本。合法且非 SCREEN_MAP_DEMO 的现有坐标仍由机构画像优先提供，默认关闭新存储及地址解析。

## 功能与前置条件

- 原有机构目录和有效画像在新存储关闭、位置表缺失或仓储不可用时继续可读。
- 新位置管理接口只有在资源、数据范围及新存储准备完成后才能维护地址。403 表示权限/资源未就绪；503 表示存储或解析能力不可用，不能解释为没有地址。
- 人工坐标需要操作员明确核定；地址解析只产生候选，必须核对城市、匹配精度后显式确认。解析失败或只有城市/道路级结果不会产生支行准确点位。
- 原画像中的合法且非 SCREEN_MAP_DEMO 坐标优先；演示坐标不可直接投影，只有 MANUAL/GEOCODE_VERIFIED 的台账坐标可以覆盖其旧标记；无可用画像坐标时才使用与有效画像城市一致、状态为 VERIFIED 的位置台账。未定位机构继续保留在授权目录。

地址解析适配 [高德服务端地理编码接口](https://lbs.amap.com/api/webservice/guide/api/georegeo)，服务 Key 只在部署端配置。外联仅发生在有权限的操作员点击解析预览后；大屏刷新不调用解析服务。高德 Key 有效性、额度、地址精度及保存结果的使用条件必须在获准环境核对，单元测试不证明外部服务可用。

## DBA 模型审核材料（拟建，未实施）

auth 自有 `PT_ORG_LOCATION`，一机构一条记录；不回写 `EXT_ORG_INFO`，不把地址塞进画像备注。以下是当前实体所需逻辑字段，物理类型和约束由 DBA 在获准隔离环境核定，核实后再同步模块 05 文档。

| 字段 | 拟定类型与约束 | 用途 |
| --- | --- | --- |
| ORG_CODE | 与外部机构编码一致的字符串，主键 | 机构关联，不按名称匹配 |
| ADDRESS | 字符串，最多 255 字符，可空 | 详细营业地址 |
| ADDRESS_SOURCE | 字符串，最多 32 字符，可空 | 服务端记录资料来源 |
| CITY_CODE | 字符串，最多 12 字符，可空 | 与有效画像城市一致，定位使用标准城市编码 |
| LNG / LAT | 精度可保留七位小数的十进制数，可空且成对 | GCJ02 经度/纬度 |
| COORD_SYS | 字符串，最多 10 字符，可空 | 当前仅 GCJ02 |
| PROVIDER / MATCH_LEVEL | 字符串，分别最多 32 字符，可空 | 定位提供方及匹配级别 |
| STATUS | 字符串，最多 32 字符，非空 | 未维护、待核对或已核定状态 |
| VERSION | 非负整数，非空 | 新增请求 0，写入递增，条件更新防覆盖 |
| LOCATION_SOURCE | 字符串，最多 32 字符，可空 | MANUAL 或 GEOCODE_VERIFIED 等服务端来源 |
| CREATED_BY / UPDATED_BY | 与平台员工编码长度一致的字符串 | 变更操作员 |
| CREATED_TIME / UPDATED_TIME | 日期时间 | 变更时间 |

部署前须验证字段空值策略、编码长度、Decimal 范围、主键并发冲突及 VERSION 条件更新。不得根据此表在运行时创建结构。

## 资源登记材料（待审批，未写入）

Controller 的 `@BizAuth` 使用 `SYS_CONFIG`。拟登记独立 API 资源，并按最小权限绑定获准维护角色；不能因为能查看机构名单就自动授予位置修改或外联能力。

| 建议资源编码 | 方法 | URL 匹配模式 | BizAction | 能力 |
| --- | --- | --- | --- | --- |
| A_ORG_LOCATION_CAPS | GET | /api/admin/org-locations/capabilities | READ | 能力状态 |
| A_ORG_LOCATION_READ | GET | /api/admin/org-locations/* | READ | 单机构位置 |
| A_ORG_LOCATION_SAVE | PUT | /api/admin/org-locations/* | PERMISSION_CHANGE | 地址/坐标覆盖维护 |
| A_ORG_LOCATION_GEOCODE | POST | /api/admin/org-locations/*/geocode-preview | EXECUTE | 地址外联解析预览 |

登记前核对当前 `PT_RESOURCE` 的既有编码、方法、路径匹配顺序，避免 GET 通配资源遮蔽 capabilities 专属资源。写权限必须同时满足实体范围，`SYS_ADMIN` 不能替代目标机构范围校验。变更和解析申请由 common 的持久化审计 SPI 记录，审计失败不允许业务写入提交或外呼。

## 外部配置

以下均为 `auth.org-location` 前缀下的配置项，不向浏览器传递凭据，不在仓库内填写凭据，不改动现有数据库、端口、代理配置。

| 配置项 | 默认值 | 含义 |
| --- | --- | --- |
| storage-enabled | false | 启用新位置台账 mapper/store |
| geocoding-enabled | false | 启用地址解析适配器 |
| api-key | 无 | 高德 Web 服务 Key |
| signing-secret | 无 | 候选确认令牌签名密钥，至少 32 个 UTF-8 字节 |
| connect-timeout-millis | 3000 | 连接超时 |
| request-timeout-millis | 5000 | 请求时限 |
| max-response-bytes | 1048576 | 响应体大小上限 |
| max-candidates | 5 | 返回候选上限 |
| max-preview-requests-per-minute | 10 | 解析预览限流 |
| candidate-ttl-seconds | 300 | 候选确认有效期，须为 1–900 秒 |

关闭两个开关即可回到旧画像坐标路径；不删除新台账，也不回滚其他画像或数据源配置。若只需人工维护坐标，可保持 geocoding-enabled=false。

## 验证及启用顺序

1. 已完成对现有 `yiti_test` 的只读存在性/相关表盘点；尚未取得覆盖/克隆授权，也未进行隔离恢复演练。先明确 DBA 目标与获准动作。
2. 在获准隔离实例完成目标库备份、表行数/checksum 清单、恢复演练，隔离 Session、锁、调度、消息、对象存储、凭据和回调。
3. DBA 实施并只读核实最终结构；按独立权限登记资源。验证默认关闭、不存在新表、空记录、人工确认、地址预览、低精度拒绝、跨城拒绝、过期/篡改令牌、版本冲突、越权和审计回滚。
4. 选择有权使用的地址台账并核对经营机构范围。先核验少量真实支行，确认坐标系与地图落点；地址/城市变化后旧定位必须失效，重新核定后才能回到地图；不能批量把行政区中心当营业点。
5. 归档官方 playwright-cli 的真实地址维护/地图请求、截图和异常路径证据。无 Key 或无地址时只证明代码与禁用状态，不能标记已完成地址定位联调。
6. 将隔离验证结果与精确变更清单提交用户，按根 `AGENTS.md` 再次取得对 `yiti` 的明确执行确认后，才实施目标库写入与启用。真实数据源、机构组和发布配置另按各自候选清单审核。
