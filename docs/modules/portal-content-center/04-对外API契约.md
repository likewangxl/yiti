# 门户与内容中心 — 对外 API 契约

> 文档版本: v1.0
> 对应模块: `portal-content-center`
> 目标读者: 依赖本模块的其他模块开发者

## 概述

本文档定义 `portal-content-center` 向其他模块提供的跨模块调用接口。所有接口定义在 `com.bank.branch.platform.portal.api` 包下，其他模块通过 Maven 依赖 `portal-content-center-api` 来调用。

### 依赖方清单
| 模块 | 调用的接口 | 主要场景 |
|---|---|---|
| customer-marketing-center | ProductApi, AddressBookApi | 线索/触达任务关联产品查询、员工选择 |
| business-application-center | ProductApi | 中场支持申请的产品选择 |

### 调用规则
1. **只能调用 `api` 包下的接口**：禁止直接依赖本模块的 `mapper`、`entity`、`service` 实现类
2. **所有接口都是只读的**：本模块对外提供的接口均为查询类，不提供写操作（写操作由本模块内部的 Controller 处理）
3. **所有 DTO 都是不可变的**：跨模块传输对象只包含 getter，没有 setter，避免被调用方修改
4. **返回 Optional 或 List**：单条查询返回 `Optional<T>`，多条查询返回 `List<T>`，避免 null 判断

---

## 1. PortalApi — 门户聚合接口

### 1.1 接口定义
```java
package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.WorkspaceDTO;

/**
 * 门户聚合对外接口。
 * 提供工作台数据聚合能力。
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface PortalApi {

    /**
     * 工作台数据聚合。
     * 一次调用返回工作台需要的全部数据（待办/通知/指标/快捷入口）。
     *
     * @param empId 员工工号
     * @return 工作台数据传输对象
     */
    WorkspaceDTO getWorkspace(String empId);

    /**
     * 获取用户待办数量。
     * 被其他模块调用，用于显示角标计数。
     *
     * @param empId 员工工号
     * @return 待办总数
     */
    int getTodoCount(String empId);

    /**
     * 获取用户未读通知数量。
     * 被其他模块调用，用于显示角标计数。
     *
     * @param empId 员工工号
     * @return 未读通知数量
     */
    int getUnreadNotificationCount(String empId);
}
```

### 1.2 调用说明
- 被调用方：工作台页面、移动端首页、SDK 客户端
- 调用频率：中频（每次用户打开工作台页面）
- 降级策略：`getWorkspace()` 内部对各子调用 try-catch，部分失败不影响整体返回

---

## 2. ProductApi — 产品资料对外接口（高频调用）

### 2.1 接口定义
```java
package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.ProductDTO;

import java.util.List;
import java.util.Optional;

/**
 * 产品资料对外接口。
 * 被 customer-marketing-center 和 business-application-center 依赖。
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface ProductApi {

    /**
     * 获取产品详情。
     * 用于线索、触达任务、业务申请等关联产品展示。
     *
     * @param productId 产品ID
     * @return 产品详情，不存在时返回 Optional.empty()
     */
    Optional<ProductDTO> getProduct(String productId);

    /**
     * 批量获取产品信息。
     * 用于列表页展示多个产品的简要信息，避免 N+1 查询。
     *
     * @param productIds 产品ID列表
     * @return 产品DTO列表（顺序不保证与入参一致，不存在的产品不返回）
     */
    List<ProductDTO> getProducts(List<String> productIds);

    /**
     * 查询支持中场支持的产品列表。
     * 供 business-application-center 的"中场支持申请"页面调用。
     * 本方法高频调用，建议缓存 TTL 5 分钟。
     *
     * @return 所有 status=ACTIVE 且 support_for_support_request=true 的产品列表
     */
    List<ProductDTO> listSupportAvailableProducts();

    /**
     * 按部门查询产品。
     * 用于显示某机构维护的全部产品。
     *
     * @param productDeptOrgCode 产品部门机构编码
     * @return 该部门维护的全部产品列表（不含已删除）
     */
    List<ProductDTO> listProductsByDept(String productDeptOrgCode);

    /**
     * 查询产品负责人工号列表。
     * 从通讯录反向关联，返回 responsible_emp_ids 字段。
     *
     * @param productId 产品ID
     * @return 负责人工号列表（可能为空）
     */
    List<String> getProductResponsibleEmpIds(String productId);
}
```

### 2.2 调用示例
```java
// 在 customer-marketing-center 中调用
@Service
public class ClueServiceImpl {

    @Resource
    private ProductApi productApi;

    public ClueDetailDTO getClueDetail(Long clueId) {
        Clue clue = clueMapper.selectById(clueId);
        ClueDetailDTO dto = new ClueDetailDTO();
        // ... 其他字段赋值

        // 查询关联产品
        if (clue.getProductId() != null) {
            Optional<ProductDTO> productOpt = productApi.getProduct(clue.getProductId());
            productOpt.ifPresent(dto::setProduct);
        }
        return dto;
    }
}

// 在 business-application-center 中调用
@Service
public class SupportRequestServiceImpl {

    @Resource
    private ProductApi productApi;

    public List<ProductDTO> getAvailableProducts() {
        return productApi.listSupportAvailableProducts();
    }
}
```

### 2.3 性能约束
- `listSupportAvailableProducts()`：本方法高频调用，本模块内部已缓存 5 分钟
- `getProducts()`：建议调用方一次性批量传入，避免循环调用
- 单次 `getProducts()` 入参产品 ID 数量上限 200

---

## 3. AddressBookApi — 通讯录对外接口

### 3.1 接口定义
```java
package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.EmployeeDTO;

import java.util.List;
import java.util.Optional;

/**
 * 通讯录对外接口。
 * 被 customer-marketing-center 等业务模块依赖。
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface AddressBookApi {

    /**
     * 获取员工通讯录信息。
     *
     * @param empId 员工工号
     * @return 员工详情，不存在时返回 Optional.empty()
     */
    Optional<EmployeeDTO> getEmployee(String empId);

    /**
     * 批量获取员工信息。
     * 用于列表展示，避免 N+1 查询。
     *
     * @param empIds 员工工号列表
     * @return 员工DTO列表
     */
    List<EmployeeDTO> getEmployees(List<String> empIds);

    /**
     * 模糊搜索员工。
     * 用于前端员工选择器（如转派、指派、@提及等）。
     *
     * @param keyword 关键词（工号/姓名）
     * @param limit 返回数量上限（最大 50）
     * @return 员工列表
     */
    List<EmployeeDTO> searchEmployees(String keyword, int limit);

    /**
     * 按机构查询员工列表。
     *
     * @param orgCode 机构编码
     * @return 该机构下的全部员工列表（仅 status=ACTIVE）
     */
    List<EmployeeDTO> listEmployeesByOrg(String orgCode);

    /**
     * 校验员工是否为客户经理角色。
     * 用于线索转派校验、触达任务分配校验等。
     *
     * @param empId 员工工号
     * @return true 表示是客户经理
     */
    boolean isCustomerManager(String empId);
}
```

### 3.2 调用示例
```java
// 在 customer-marketing-center 中的转派校验场景
@Service
public class ClueTransferServiceImpl {

    @Resource
    private AddressBookApi addressBookApi;

    public void transferClue(Long clueId, String targetEmpId) {
        // 校验目标员工存在
        Optional<EmployeeDTO> targetEmpOpt = addressBookApi.getEmployee(targetEmpId);
        if (targetEmpOpt.isEmpty()) {
            throw new BizException(MarketingErrorCode.EMPLOYEE_NOT_FOUND);
        }

        // 校验目标员工是客户经理
        if (!addressBookApi.isCustomerManager(targetEmpId)) {
            throw new BizException(MarketingErrorCode.TARGET_NOT_CUSTOMER_MANAGER);
        }

        // 执行转派
        // ...
    }
}
```

### 3.3 性能约束
- `searchEmployees()`：`limit` 参数最大 50
- `getEmployees()`：入参 `empIds` 数量上限 200
- 所有接口返回脱敏后的手机号

---

## 4. DocumentApi — 文档对外接口

### 4.1 接口定义
```java
package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.DocumentDTO;

import java.util.List;
import java.util.Optional;

/**
 * 文档对外接口。
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface DocumentApi {

    /**
     * 获取文档详情。
     *
     * @param docId 文档 ID
     * @return 文档详情，不存在时返回 Optional.empty()
     */
    Optional<DocumentDTO> getDocument(String docId);

    /**
     * 按分类查询文档列表。
     *
     * @param category 文档分类
     * @return 该分类下的全部文档列表（仅 status=ACTIVE）
     */
    List<DocumentDTO> listDocumentsByCategory(String category);
}
```

---

## 5. NavApi — 导航对外接口

### 5.1 接口定义
```java
package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.NavDTO;

import java.util.List;

/**
 * 网址导航对外接口。
 * 供工作台聚合等场景调用。
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface NavApi {

    /**
     * 获取所有启用的导航列表。
     *
     * @return 所有 status=ACTIVE 的导航列表
     */
    List<NavDTO> listActiveNavs();

    /**
     * 按分类查询启用的导航列表。
     *
     * @param category 导航分类
     * @return 该分类下的启用导航列表
     */
    List<NavDTO> listActiveNavsByCategory(String category);
}
```

---

## 6. DTO 定义

### 6.1 ProductDTO
```java
package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品信息传输对象（不可变）。
 */
@Value
@Builder
public class ProductDTO {
    /** 产品ID */
    String id;

    /** 产品代码 */
    String productCode;

    /** 产品名称 */
    String productName;

    /** 产品类别代码 */
    String productCategory;

    /** 产品类别显示名 */
    String productCategoryDesc;

    /** 产品描述 */
    String description;

    /** 是否支持中场支持 */
    Boolean supportForSupportRequest;

    /** 产品部门机构编码（维护组织） */
    String productDeptOrgCode;

    /** 产品部门机构名称 */
    String productDeptOrgName;

    /** 附件对象ID */
    String fileObjectId;

    /** 产品负责人工号列表 */
    List<String> responsibleEmpIds;

    /** 状态 ACTIVE/DISABLED */
    String status;

    /** 创建时间 */
    LocalDateTime createdTime;

    /** 最后更新时间 */
    LocalDateTime updatedTime;
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| id | String | 产品 ID |
| productCode | String | 产品代码 |
| productName | String | 产品名称 |
| productCategory | String | 产品类别代码 |
| productCategoryDesc | String | 产品类别显示名 |
| description | String | 产品描述 |
| supportForSupportRequest | Boolean | 是否支持中场支持 |
| productDeptOrgCode | String | 维护部门机构编码 |
| productDeptOrgName | String | 维护部门机构名称 |
| fileObjectId | String | 附件对象 ID |
| responsibleEmpIds | List&lt;String&gt; | 负责人工号列表 |
| status | String | 状态 |
| createdTime | LocalDateTime | 创建时间 |
| updatedTime | LocalDateTime | 更新时间 |

### 6.2 EmployeeDTO
```java
package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工通讯录信息传输对象（不可变）。
 */
@Value
@Builder
public class EmployeeDTO {
    /** 员工工号 */
    String empId;

    /** 员工姓名 */
    String empName;

    /** 手机号（脱敏后） */
    String mobile;

    /** 邮箱 */
    String email;

    /** 机构编码 */
    String orgCode;

    /** 机构名称 */
    String orgName;

    /** 岗位代码 */
    String position;

    /** 岗位显示名 */
    String positionDesc;

    /** 自我描述 */
    String selfDesc;

    /** 负责产品ID列表 */
    List<String> responsibleProductIds;

    /** 状态 ACTIVE/RESIGNED */
    String status;

    /** 最后更新时间 */
    LocalDateTime updatedTime;
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| empId | String | 工号 |
| empName | String | 姓名 |
| mobile | String | 手机号（已脱敏） |
| email | String | 邮箱 |
| orgCode | String | 机构编码 |
| orgName | String | 机构名称 |
| position | String | 岗位代码 |
| positionDesc | String | 岗位显示名 |
| selfDesc | String | 自我描述 |
| responsibleProductIds | List&lt;String&gt; | 负责产品 ID 列表 |
| status | String | 状态 |
| updatedTime | LocalDateTime | 更新时间 |

### 6.3 WorkspaceDTO
```java
@Value
@Builder
public class WorkspaceDTO {
    /** 待办总数 */
    int todoCount;

    /** 未读通知数量 */
    int unreadNotificationCount;

    /** 最近待办列表 */
    List<TodoItemDTO> recentTodos;

    /** 最近通知列表 */
    List<NotificationItemDTO> recentNotifications;

    /** 指标卡片 */
    List<MetricCardDTO> metricCards;

    /** 新增流程快捷入口 */
    List<ShortcutDTO> shortcuts;

    /** 聚合子调用错误信息 */
    Map<String, String> aggregateErrors;
}
```

### 6.4 TodoItemDTO
```java
@Value
@Builder
public class TodoItemDTO {
    /** 任务ID */
    String taskId;

    /** 流程实例ID */
    String processInstanceId;

    /** 流程名称 */
    String processName;

    /** 任务标题 */
    String taskTitle;

    /** 发起人姓名 */
    String initiatorName;

    /** 发起时间 */
    LocalDateTime initiatedTime;

    /** 红绿灯状态 GREEN/YELLOW/RED */
    String lightStatus;

    /** 超时信息 */
    String overdueInfo;

    /** 业务详情页URL */
    String bizDetailUrl;
}
```

### 6.5 NotificationItemDTO
```java
@Value
@Builder
public class NotificationItemDTO {
    /** 通知ID */
    String notificationId;

    /** 标题 */
    String title;

    /** 摘要 */
    String summary;

    /** 发送时间 */
    LocalDateTime sentTime;

    /** 读状态 READ/UNREAD */
    String readStatus;

    /** 关联业务类型 */
    String bizType;

    /** 关联业务ID */
    String bizId;

    /** 业务详情页URL */
    String bizDetailUrl;
}
```

### 6.6 MetricCardDTO
```java
@Value
@Builder
public class MetricCardDTO {
    /** 指标代码 */
    String metricCode;

    /** 指标名称 */
    String metricName;

    /** 当前值 */
    String currentValue;

    /** 目标值 */
    String targetValue;

    /** 完成率（0-1） */
    BigDecimal completionRate;

    /** 趋势 UP/DOWN/FLAT */
    String trend;

    /** 单位 */
    String unit;
}
```

### 6.7 ShortcutDTO
```java
@Value
@Builder
public class ShortcutDTO {
    /** ID */
    Long id;

    /** 名称 */
    String shortcutName;

    /** 跳转URL */
    String shortcutUrl;

    /** 图标 */
    String shortcutIcon;

    /** 类型 SYSTEM/CUSTOM */
    String shortcutType;

    /** 跳转类型 INTERNAL/EXTERNAL */
    String targetType;

    /** 排序号 */
    Integer sortOrder;
}
```

### 6.8 NavDTO
```java
@Value
@Builder
public class NavDTO {
    /** 导航ID */
    Long id;

    /** 名称 */
    String navName;

    /** URL */
    String navUrl;

    /** 图标 */
    String navIcon;

    /** 分类 */
    String navCategory;

    /** 排序号 */
    Integer sortOrder;

    /** 状态 */
    String status;
}
```

### 6.9 DocumentDTO
```java
@Value
@Builder
public class DocumentDTO {
    /** 文档ID */
    String id;

    /** 文档标题 */
    String docTitle;

    /** 分类代码 */
    String docCategory;

    /** 分类显示名 */
    String docCategoryDesc;

    /** 文件对象ID */
    String fileObjectId;

    /** 文件名 */
    String fileName;

    /** 状态 */
    String status;

    /** 更新时间 */
    LocalDateTime updatedTime;
}
```

---

## 7. 调用约束与限流

### 7.1 调用频率约束
| 接口 | 预期频率 | 缓存策略 |
|---|---|---|
| `ProductApi.listSupportAvailableProducts()` | 高频 | 模块内缓存 5 分钟 |
| `ProductApi.getProduct(id)` | 中频 | 调用方自行缓存 |
| `ProductApi.getProducts(ids)` | 中频 | 调用方自行缓存 |
| `AddressBookApi.searchEmployees(kw, limit)` | 中频 | `limit` 最大 50 |
| `AddressBookApi.getEmployee(id)` | 高频 | 调用方自行缓存 |
| `AddressBookApi.getEmployees(ids)` | 中频 | 一次最多 200 个 |
| `PortalApi.getTodoCount()` | 高频 | 内部缓存 1 分钟 |
| `PortalApi.getUnreadNotificationCount()` | 高频 | 内部缓存 1 分钟 |

### 7.2 调用超时约定
- 所有跨模块接口调用默认超时 500ms
- 超时后调用方需要有降级逻辑（例如返回空列表或使用旧数据）

### 7.3 调用方自缓存建议
- 高频查询（如产品详情、员工详情）建议调用方使用本地 Guava Cache 或 Caffeine 缓存
- 缓存 TTL 建议 1-5 分钟，避免数据滞后
- 对于配置类数据（产品列表、员工列表），缓存失效可以靠事件驱动（订阅本模块发布的事件）

---

## 8. 领域事件

### 8.1 portal.product.responsible-updated.v1

#### 8.1.1 事件定义
```java
package com.bank.branch.platform.portal.event;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 产品负责人变更事件。
 * 发布时机：
 * 1. 通讯录员工修改"负责产品"字段后
 * 2. 员工离职被清理负责产品时
 * 3. 产品被逻辑删除时
 */
@Value
public class ProductResponsibleUpdatedEvent {
    /** 产品ID */
    String productId;

    /** 产品代码 */
    String productCode;

    /** 变更前负责人列表 */
    List<String> beforeEmpIds;

    /** 变更后负责人列表 */
    List<String> afterEmpIds;

    /** 变更人工号 */
    String operatorEmpId;

    /** 变更时间 */
    LocalDateTime occurredAt;
}
```

#### 8.1.2 事件字段
| 字段 | 类型 | 说明 |
|---|---|---|
| productId | String | 产品 ID |
| productCode | String | 产品代码 |
| beforeEmpIds | List&lt;String&gt; | 变更前负责人工号列表 |
| afterEmpIds | List&lt;String&gt; | 变更后负责人工号列表 |
| operatorEmpId | String | 操作人工号 |
| occurredAt | LocalDateTime | 事件发生时间 |

#### 8.1.3 发布时机
1. **通讯录员工修改"负责产品"字段后**：事件由 `AddressBookService.updateEmployee()` 在事务提交前发布
2. **员工离职事件触发的清理**：`UserResignedEventListener` 清理关联负责产品时发布
3. **产品逻辑删除时**：`ProductService.deleteProduct()` 清理所有员工对该产品的引用后发布

#### 8.1.4 消费方
- **product_info 表自身**：触发缓存失效 `portal:product:support-available:*`
- **后续扩展**：`customer-marketing-center` 可订阅该事件以刷新其本地产品缓存

---

### 8.2 portal.addrbook.updated.v1

#### 8.2.1 事件定义
```java
package com.bank.branch.platform.portal.event;

import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工通讯录更新事件。
 * 发布时机：员工信息更新后
 */
@Value
public class AddrbookUpdatedEvent {
    /** 员工工号 */
    String empId;

    /** 变更字段列表 */
    List<String> changedFields;

    /** 操作人工号 */
    String operatorEmpId;

    /** 变更时间 */
    LocalDateTime occurredAt;
}
```

#### 8.2.2 事件字段
| 字段 | 类型 | 说明 |
|---|---|---|
| empId | String | 员工工号 |
| changedFields | List&lt;String&gt; | 变更字段名列表（例如 ["mobile", "position"]） |
| operatorEmpId | String | 操作人工号 |
| occurredAt | LocalDateTime | 事件发生时间 |

#### 8.2.3 发布时机
员工通讯录信息更新后，由 `AddressBookService.updateEmployee()` 在事务提交后发布。

#### 8.2.4 消费方
- 本模块内部的审计日志服务（记录详细变更）
- 本模块内部的缓存失效处理
- 后续扩展可供其他模块订阅

---

## 9. 版本演进约定

### 9.1 向后兼容原则
- **DTO 只能新增字段**，不能删除或修改已有字段类型
- **接口方法只能新增**，不能删除或修改已有方法签名
- 若必须引入破坏性变更，通过新增方法或事件版本号实现（例如 `portal.product.responsible-updated.v2`）

### 9.2 废弃标记
- 计划废弃的方法先标记 `@Deprecated` 并注释替代方法
- 至少保留 2 个版本后才可以真正删除

### 9.3 调用方对接流程
1. 依赖方在 `pom.xml` 中添加 `portal-content-center-api`
2. 依赖方通过 `@Resource` 注入所需的 `Api` 接口
3. 依赖方的集成测试必须包含对 `Api` 调用的用例
4. 依赖方发布前需要回归本模块的变更日志

---

## 10. 错误处理约定

### 10.1 异常传递
- 跨模块调用不抛出业务异常（`BizException`），而是通过返回值表达结果
- 单条查询不存在时返回 `Optional.empty()`，而非抛异常
- 批量查询不存在的项不包含在返回列表中，不报错

### 10.2 系统异常
- 系统异常（如数据库连接失败）直接抛出 `RuntimeException`
- 调用方必须在关键调用点使用 `try-catch` 并降级

### 10.3 调用方降级示例
```java
// 推荐的降级调用模式
public ProductDTO getProductSafely(String productId) {
    try {
        return productApi.getProduct(productId).orElse(null);
    } catch (Exception e) {
        log.warn("调用 productApi.getProduct 失败, productId={}, 降级返回 null", productId, e);
        return null;
    }
}
```
