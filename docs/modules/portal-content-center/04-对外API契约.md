# 门户与内容中心 — 对外 API 契约

## 1. 契约范围

本文只记录 `portal-content-center/src/main/java/.../portal/api/` 中当前存在、供其他模块注入调用的公开接口。调用方只能依赖这些接口及 `api/dto`，不能引用 portal 的 Mapper、Entity、Controller DTO 或内部 Service；接口实现和 DTO 字段以源码为最终依据。

当前公开契约包括 `PortalApi`、`ProductApi`、`AddressBookApi`、`DocumentApi` 和 `NavApi`。所有方法均为只读查询，门户写操作仍走门户 REST；工作台的 workflow/metric 适配器是 portal 内部边界，不作为调用方 API。

## 2. `PortalApi`

```java
WorkspaceDTO getWorkspace(String empId);
int getTodoCount(String empId);
int getUnreadNotificationCount(String empId);
```

`getTodoCount` 和 `getUnreadNotificationCount` 分别读取 workflow 待办和治理通知，异常/上游缺失按工作台降级语义返回 `0`。`getWorkspace` 返回工作台聚合 DTO；当前 Facade 内部按认证上下文取得当前用户，`empId` 必须与有效请求上下文一致，不能借此查询任意用户。

`WorkspaceDTO` 当前字段：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| `todoCount` | `int` | 待办数量 |
| `unreadNotificationCount` | `int` | 未读通知数量 |
| `recentTodos` | `List<TodoItemDTO>` | 最近待办 |
| `recentNotifications` | `List<NotificationItemDTO>` | 最近通知 |
| `metricCards` | `List<PortalMetricCard>` | 指标卡片，缺失时空列表 |
| `shortcuts` | `List<ShortcutDTO>` | 系统/个人快捷入口 |
| `aggregateErrors` | `Map<String,String>` | 聚合分支错误摘要 |

`TodoItemDTO` 字段为 `taskId`、`processInstanceId`、`processName`、`taskTitle`、`initiatorName`、`initiatedTime`、`lightStatus`、`overdueInfo`、`bizDetailUrl`；`NotificationItemDTO` 字段为 `notificationId`、`title`、`summary`、`sentTime`、`readStatus`、`bizType`、`bizId`、`bizDetailUrl`；`PortalMetricCard` 字段为 `metricCode`、`metricName`、`currentValue`、`targetValue`、`completionRate`、`trend`、`changeRate`、`dataTime`、`unit`。这些集合不承诺固定条数。

## 3. `ProductApi`

```java
Optional<ProductDTO> getProduct(String productId);
List<ProductDTO> getProducts(List<String> productIds);
List<ProductDTO> listSupportAvailableProducts();
List<ProductDTO> listProductsByDept(String productDeptOrgCode);
List<String> getProductResponsibleEmpIds(String productId);
```

语义：

- `getProduct` 查询不到（包括门户逻辑删除记录）返回 `Optional.empty()`；
- `getProducts` 入参为空或未命中返回空列表，不保证与入参顺序一致；
- `listSupportAvailableProducts` 只返回 `ACTIVE`、支持中场支持且未逻辑删除的产品；
- `listProductsByDept` 按维护机构查询未逻辑删除产品，当前不额外承诺只返回 `ACTIVE`；
- `getProductResponsibleEmpIds` 查询不到产品或无关系时返回空列表。

`ProductDTO` 当前字段：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| `id` | `String` | 产品 ID |
| `productCode` | `String` | 产品代码 |
| `productName` | `String` | 名称 |
| `productCategory` | `String` | 类别代码 |
| `productCategoryDesc` | `String` | 类别显示名，基础 Facade 可为空 |
| `description` | `String` | 产品说明 |
| `supportForSupportRequest` | `Boolean` | 是否支持中场支持 |
| `productDeptOrgCode` | `String` | 维护机构编码 |
| `productDeptOrgName` | `String` | 维护机构名称，基础 Facade 可为空 |
| `fileObjectId` | `String` | 治理文件对象 ID |
| `fileName` | `String` | 文件名，基础 Facade 可为空 |
| `responsibleEmpIds` | `List<String>` | 负责人 ID |
| `responsibleEmpNames` | `String` | 展示用负责人姓名，基础 Facade 可为空 |
| `status` | `String` | `ACTIVE`/`DISABLED` |
| `createdTime` | `LocalDateTime` | 创建时间 |
| `updatedTime` | `LocalDateTime` | 更新时间 |

产品 ID 是应用生成的字符串，不是 `Long`。负责人关系来自门户关系表；调用方不得假定产品 DTO 中含有可写的负责人对象或完整组织信息。

## 4. `AddressBookApi`

```java
Optional<EmployeeDTO> getEmployee(String empId);
List<EmployeeDTO> getEmployees(List<String> empIds);
List<EmployeeDTO> searchEmployees(String keyword, int limit);
List<EmployeeDTO> listEmployeesByOrg(String orgCode);
boolean isCustomerManager(String empId);
```

员工标识是 auth `PT_USER.USER_ID` 的规范字符串；用户、主机构和联系方式通过 auth 公开目录能力取得，负责人产品 ID 由门户关系查询补充。空入参/未命中按 `Optional.empty()` 或空列表处理，查询异常由调用方按业务需要降级。

`EmployeeDTO` 字段为 `empId`、`empName`、`mobile`、`email`、`orgCode`、`orgName`、`position`、`positionDesc`、`selfDesc`、`responsibleProductIds`、`status`、`updatedTime`。跨模块 `mobile` 已脱敏；当前 auth 三表不能提供岗位说明和自我描述，`positionDesc`/`selfDesc` 可为 `null`。`status` 以 auth 在职目录口径返回，不能自行解释为旧通讯录表状态。

`isCustomerManager` 通过 auth `UserApi.getUserRoleCodes` 检查角色编码（当前认可客户营销经理/客户经理角色编码）；它不是对岗位文本的猜测，也不是权限授予接口。

## 5. `DocumentApi`

```java
Optional<DocumentDTO> getDocument(String docId);
List<DocumentDTO> listDocumentsByCategory(String category);
```

`listDocumentsByCategory` 只查询 `ACTIVE` 文档；`getDocument` 当前 Facade 按 ID 直接查询 Mapper，返回不存在时为空，调用方不能假定该方法自动排除 `DISABLED`。下载 URL 不属于本 Java API，REST 下载经治理 `FileApi` 完成。

`DocumentDTO` 字段为 `id`、`docTitle`、`docCategory`、`docCategoryDesc`、`fileObjectId`、`fileName`、`status`、`fileSize`、`updatedBy`、`updatedTime`。分类显示名、文件名和大小是否装配取决于调用路径；基础 `DocumentFacade` 只保证实体可映射字段。

## 6. `NavApi`

```java
List<NavDTO> listActiveNavs();
List<NavDTO> listActiveNavsByCategory(String category);
```

两者只返回 `ACTIVE` 导航并按排序号升序；空分类或无命中返回空列表。`NavDTO` 字段为字符串 `id`、`navName`、`navUrl`、`navIcon`、`navCategory`、`Integer sortOrder` 和 `status`。

## 7. 调用和降级规则

- 门户 Facade 不返回可写 Entity；调用方不得修改 DTO 后期待回写门户。
- 单条资源缺失采用 `Optional.empty()`（或计数接口的 `0`），集合缺失采用空列表；不要用 `null` 表示“关系未知”。
- 上游 API Bean 缺失或适配器捕获异常时，工作台待办/指标/通知按各自的空值契约降级；产品、通讯录、文档和导航 Facade 的系统异常通常向调用方抛出，由调用方决定页面或流程降级。
- 数据范围由门户/上游服务各自按公开契约执行；调用方不得直接 JOIN portal 或 auth 私有表补查询。
- 文件对象仅能经治理 `FileApi` 使用，DTO 中的 `fileObjectId` 不能被当成本地路径或 OBS 客户端凭据。

## 8. 依赖方向与演进

当前主要消费者是 business-application-center（产品及通讯录）和 performance-engine-center（通讯录员工校验）；实际引用以消费者源码和 POM 为准。portal 不反向依赖这些消费者。

新增字段应保持旧调用方可反序列化，改变字段语义或 ID 类型必须同步所有消费者和测试；删除/重命名方法前先完成消费者迁移。本文不维护版本流水、调用次数、限流数字或未实现接口。
