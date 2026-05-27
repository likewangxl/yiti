package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.api.dto.CustomerFilterDTO;
import com.bank.branch.platform.customer.api.dto.RunningFlowDTO;

import java.util.List;
import java.util.Optional;

/**
 * 客户主档对外查询接口（最重要，被多个模块依赖）。
 * <p>
 * 供 business-application-center、performance-engine-center 等模块查询客户主档信息使用。
 * 提供客户存在性校验、认领关系查询、在途流程查询等常用判断方法，避免跨模块直连 Mapper。
 * 所有方法返回 DTO，禁止暴露内部实体。
 * </p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface CustomerQueryApi {

    /**
     * 获取客户详情。
     * <p>
     * 缓存策略: cust:customer:{custId}, TTL 5min。
     * </p>
     *
     * @param custId 客户ID
     * @return 客户DTO，不存在时返回 Optional.empty()
     */
    Optional<CustomerDTO> getCustomer(String custId);

    /**
     * 按客户编号 (cust_no) 获取客户详情。
     * <p>
     * 用于上游模块前端按业务编号查询客户时使用，区别于 {@link #getCustomer(String)} 的内部 ID 主键查询。
     * </p>
     *
     * @param custNo 客户编号（cust_master.cust_no 列）
     * @return 客户DTO，不存在时返回 Optional.empty()
     */
    Optional<CustomerDTO> getCustomerByCustNo(String custNo);

    /**
     * 批量获取客户信息。
     * <p>
     * custIds 最大 500，超限抛 COMMON-40000 异常。
     * 空列表/null 直接返回空列表，不查询数据库。
     * </p>
     *
     * @param custIds 客户ID列表，最大 500 个
     * @return 客户DTO列表，找不到的ID不包含在返回列表中
     * @throws com.bank.branch.platform.common.web.exception.BizException COMMON-40000 custIds 超过 500
     */
    List<CustomerDTO> listCustomers(List<String> custIds);

    /**
     * 模糊搜索客户（匹配 cust_name / cust_no / unified_credit_code）。
     * <p>
     * limit 范围 [1, 50]，超限或空关键词抛 COMMON-40000。
     * </p>
     *
     * @param keyword 搜索关键词，不能为空
     * @param limit   最大返回条数，范围 1-50
     * @return 匹配的客户DTO列表
     * @throws com.bank.branch.platform.common.web.exception.BizException COMMON-40000 参数非法
     */
    List<CustomerDTO> searchCustomers(String keyword, int limit);

    /**
     * 校验客户是否有效（status=VALID 且未逻辑删除）。
     *
     * @param custId 客户ID
     * @return true 表示客户有效，false 表示不存在或已删除
     */
    boolean isValidCustomer(String custId);

    /**
     * 校验机构是否有 CLAIMED 状态的认领记录。
     * <p>
     * 缓存策略: cust:customer:{custId}:claims, TTL 3min。
     * 用于权限校验：确保只有认领了该客户的机构才能发起相关业务申请。
     * </p>
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return true 表示该机构存在有效认领记录（CLAIMED），false 表示未认领
     */
    boolean isClaimedByOrg(String custId, String orgCode);

    /**
     * 获取客户所有 CLAIMED 状态的认领关系。
     * <p>
     * 仅返回状态为 CLAIMED 的有效认领，历史 CANCELLED 记录不包含在内。
     * </p>
     *
     * @param custId 客户ID
     * @return 有效认领DTO列表，不存在时返回空列表
     */
    List<CustClaimDTO> getCustomerClaims(String custId);

    /**
     * 判断客户是否存在指定业务类型的在途流程。
     * <p>
     * bizType 当前支持: TOUCH_TASK（触达任务）。
     * 其他 bizType 暂不支持，返回 false。
     * </p>
     *
     * @param custId  客户ID
     * @param bizType 业务类型（如 TOUCH_TASK）
     * @return true 表示存在在途流程
     */
    boolean hasRunningProcess(String custId, String bizType);

    /**
     * 获取客户所有在途流程（全类型）。
     * <p>
     * 当前仅包含 TOUCH_TASK 类型的在途触达任务。
     * TODO: 后续可扩展委托 WorkflowApi 查询线索审批在途流程。
     * </p>
     *
     * @param custId 客户ID
     * @return 在途流程DTO列表，无在途流程时返回空列表
     */
    List<RunningFlowDTO> listRunningProcesses(String custId);

    /**
     * 按过滤条件统计客户数量。
     * <p>
     * filter 为 null 时当作空过滤条件处理，统计所有未删除客户数。
     * </p>
     *
     * @param filter 过滤条件，可为 null
     * @return 符合条件的客户总数
     */
    long countCustomers(CustomerFilterDTO filter);
}
