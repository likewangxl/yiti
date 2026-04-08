# 系统治理中心接口一致性修复设计方案

> 创建日期: 2026-04-08
> 目标: 将 governance 模块 Controller 实现与 03-接口设计与报文.md 文档对齐

## 一、背景说明

当前 governance 模块的 Controller 实现与接口设计文档存在多处不一致，主要体现在：
1. 工作日历接口路径和参数不符合文档
2. 审计日志路径缺少 sys/ 前缀
3. 文件管理接口路径不符合 REST 规范
4. 缺少部分文档定义的接口

## 二、修复范围

### 2.1 高优先级（必须修复）

| # | 模块 | 问题描述 | 修复方案 |
|---|------|----------|----------|
| 1 | Calendar | B.1 文档定义 `GET /api/sys/calendar?year&month` (公共,按月)，实际 `/api/admin/sys/calendar?year` (需鉴权,按年) | 新增公共按月查询接口 |
| 2 | Calendar | B.2 文档定义 `PUT /api/admin/sys/calendar/{date}` (RequestBody)，实际 `/toggle` | 改为文档定义 |
| 3 | Calendar | B.4 批量导入节假日文档定义但未实现 | 新增 Excel 导入接口 |
| 4 | AuditLog | 文档 `/api/admin/sys/audit-logs/*`，实际 `/api/admin/audit-logs/*` | 路径补全 sys/ |
| 5 | File | G.2 文档 302 重定向，实际返回 URL | 改为 302 重定向 |
| 6 | File | G.3 文档 `GET /api/files?bizType&bizId`，实际 `/api/files/biz/{bizType}/{bizId}` | 改为 Query 参数 |
| 7 | File | G.4 删除文件文档定义但未实现 | 新增删除接口 |

### 2.2 低优先级（建议修复）

| # | 模块 | 问题描述 | 修复方案 |
|---|------|----------|----------|
| 8 | Job | C.3 trigger reason 文档 RequestBody，实际 @RequestParam | 改为 RequestBody |
| 9 | SqlProbe | H.1 execute sql/reason 文档 RequestBody，实际 @RequestParam | 改为 RequestBody |
| 10 | Notification | E.5 通知详情文档定义但未实现 | 新增详情接口 |

## 三、详细设计

### 3.1 工作日历接口设计

#### B.1 新增：GET /api/sys/calendar -- 查询日历（按月）

- **权限**：无 @BizAuth（公共只读数据）
- **说明**：查询指定年月的日历数据，返回该月每天的工作日/休息日状态

**Query 参数：**

| 参数 | Java类型 | 必填 | 校验注解 | 说明 |
|:---|:---|:---|:---|:---|
| year | Integer | 是 | `@NotNull @Min(2020)` | 年份 |
| month | Integer | 是 | `@NotNull @Min(1) @Max(12)` | 月份 |

**响应体：** `ResponseWrapper<List<CalendarDayRespDTO>>`

**CalendarDayRespDTO：**
| 字段 | Java类型 | 说明 |
|:---|:---|:---|
| day | String | 日期（yyyy-MM-dd 格式） |
| isWorkday | Boolean | 是否工作日 |
| dayOfWeek | Integer | 星期几（1=周一 ... 7=周日） |
| remark | String | 备注（如"国庆节"） |

#### B.2 修改：PUT /api/admin/sys/calendar/{date} -- 切换工作/休息状态

**路径参数：**
| 参数 | Java类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| date | String | 是 | 日期（yyyy-MM-dd 格式） |

**请求体 CalendarDayUpdateReqDTO：**
| 字段 | Java类型 | 必填 | 校验注解 | 说明 |
|:---|:---|:---|:---|:---|
| isWorkday | Boolean | 是 | `@NotNull` | true=工作日，false=休息日 |
| remark | String | 否 | `@Size(max=500)` | 备注（如"国庆调休"） |

**响应体：** `ResponseWrapper<Void>`

**业务规则：**
1. 日期 <= 今天，返回 `GOV-40902`（已过去日期不允许修改）
2. 日期对应记录不存在时自动创建
3. 更新后清除该年份的日历缓存

#### B.4 新增：POST /api/admin/sys/calendar/import -- 批量导入节假日

- **权限**：`@BizAuth(bizType = SYS_CONFIG, action = IMPORT)`
- **说明**：通过 Excel 文件批量导入节假日安排。属于 IMPORT 高危动作，需审计。

**请求格式**：`Content-Type: multipart/form-data`

| 参数 | Java类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| file | MultipartFile | 是 | Excel 文件（.xlsx） |

**Excel 格式要求：**

| 列 | 表头 | 类型 | 说明 |
|:---|:---|:---|:---|
| A | 日期 | yyyy-MM-dd | 日期 |
| B | 是否工作日 | 1/0 | 1=工作日，0=休息日 |
| C | 备注 | 文本 | 备注（如"国庆节"） |

**响应体：** `ResponseWrapper<CalendarImportRespDTO>`

| 字段 | Java类型 | 说明 |
|:---|:---|:---|
| totalRows | Integer | 导入总行数 |
| successRows | Integer | 成功行数 |
| skippedRows | Integer | 跳过行数（已过去的日期） |

### 3.2 审计日志路径修复

所有 AuditLogController 路径统一为 `/api/admin/sys/audit-logs/*`

| 接口 | 方法 | 路径 |
|------|------|------|
| D.1 | GET | /api/admin/sys/audit-logs |
| D.2 | GET | /api/admin/sys/audit-logs/{id} |
| D.3 | POST | /api/admin/sys/audit-logs/export |

### 3.3 文件管理接口设计

#### G.2 修改：GET /api/files/{fileId}/download -- 下载文件

**变更**：原返回预签名 URL 改为 302 重定向

**路径参数：**
| 参数 | Java类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| fileId | String | 是 | 文件对象ID |

**响应**：`302 Redirect` 到 MinIO 预签名下载 URL（有效期 1 小时）

#### G.3 修改：GET /api/files -- 按业务查询关联文件

**变更**：原路径 `/api/files/biz/{bizType}/{bizId}` 改为 Query 参数

**Query 参数：**
| 参数 | Java类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| bizType | String | 是 | 业务类型 |
| bizId | String | 是 | 业务ID |

#### G.4 新增：DELETE /api/files/{fileId} -- 删除文件

**路径参数：**
| 参数 | Java类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| fileId | String | 是 | 文件对象ID |

**响应体：** `ResponseWrapper<Void>`

**业务规则：**
1. 文件不存在返回 `GOV-40404`
2. 先解除所有 biz_file_rel 关联
3. 查询是否还有其他 biz_file_rel 引用该 file_object_id
4. 无其他引用则从 MinIO 删除实际文件 + 删除 file_object 记录

## 四、错误码定义

| 错误码 | 含义 |
|--------|------|
| GOV-40002 | 字典编码已存在 |
| GOV-40003 | 字典项不存在 |
| GOV-40401 | 配置项不存在 |
| GOV-40402 | 调度任务不存在 |
| GOV-40403 | 通知不存在或无权限 |
| GOV-40404 | 文件不存在 |
| GOV-40901 | 调度任务状态冲突 |
| GOV-40902 | 已过去日期不允许修改 |
| GOV-40903 | 文件格式不在白名单 |
| GOV-40904 | 文件大小超过限制 |
| GOV-40905 | 配置值类型校验失败 |
| GOV-42201 | SQL语法校验失败（非SELECT） |
| GOV-42202 | SQL执行超时 |
| GOV-42203 | 无权访问指定表 |
| GOV-50001 | MinIO上传失败 |

## 五、实施顺序

1. **第一批（高优先级，核心功能）**
   - 审计日志路径修复（影响范围小，改动明确）
   - 工作日历 B.1 公共查询接口
   - 工作日历 B.2 修改
   - 工作日历 B.4 批量导入

2. **第二批（文件管理）**
   - G.3 路径修改
   - G.4 删除接口
   - G.2 改为 302 重定向

3. **第三批（低优先级）**
   - Job C.3 RequestBody 改造
   - SqlProbe H.1 RequestBody 改造
   - Notification E.5 详情接口

## 六、测试策略

每个接口修改后需验证：
1. 接口文档定义的参数、返回值与实际一致
2. 权限注解配置正确（需鉴权 vs 公共）
3. 审计日志正确记录
4. 单元测试覆盖

---
