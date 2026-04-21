package com.bank.branch.platform.customer.api;

import com.bank.branch.platform.customer.api.dto.LeadDTO;

import java.util.List;
import java.util.Optional;

/**
 * 线索查询 API。
 * <p>
 * 被调用方：workflow-center / report-analytics-center。
 * 只提供只读查询，不暴露写操作（写操作通过 LeadController 暴露）。
 * </p>
 *
 * @author customer-marketing-center
 * @since V1.0
 */
public interface LeadApi {

    /**
     * 获取线索详情。
     *
     * @param leadId 线索 ID
     * @return 包含 LeadDTO 的 Optional；不存在时返回 Optional.empty()
     */
    Optional<LeadDTO> getLead(String leadId);

    /**
     * 按业务键查询线索。
     * <p>
     * 业务键格式：LEAD:{leadId} 单条 / LEAD:IMP_{batchId} 批量（批量场景返回批次第一条作为样本）。
     * </p>
     *
     * @param businessKey 业务键
     * @return 包含 LeadDTO 的 Optional；不存在时返回 Optional.empty()
     */
    Optional<LeadDTO> getLeadByBusinessKey(String businessKey);

    /**
     * 查询批次下的线索列表。
     *
     * @param importBatchId 导入批次 ID
     * @return 线索 DTO 列表；无数据时返回空列表
     */
    List<LeadDTO> getLeadsByBatch(String importBatchId);

    /**
     * 查询线索的完整版本链（按 version_no 升序）。
     *
     * @param leadId 线索 ID（任意版本均可）
     * @return 版本链 DTO 列表，按 version_no 升序；查不到时返回空列表
     */
    List<LeadDTO> getLeadVersionChain(String leadId);

    /**
     * 校验线索名称是否可用（全行唯一, excludeLeadId 用于编辑场景排除自身）。
     *
     * @param custName      客户名称
     * @param excludeLeadId 编辑场景下排除的线索 ID，新建场景传 null
     * @return true 表示名称可用，false 表示已被占用
     */
    boolean isLeadCustNameAvailable(String custName, String excludeLeadId);
}
