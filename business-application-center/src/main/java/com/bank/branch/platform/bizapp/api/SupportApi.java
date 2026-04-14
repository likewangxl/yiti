package com.bank.branch.platform.bizapp.api;

import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;

import java.util.List;
import java.util.Optional;

/**
 * 中场支持申请对外查询接口。
 * <p>
 * 供其他模块查询支持申请数据。
 * 实现类位于 {@code facade/SupportApiImpl}。
 * </p>
 */
public interface SupportApi {

    /**
     * 按申请ID查询支持申请。
     *
     * @param requestId 申请ID（UUID，32位）
     * @return 支持申请 DTO，不存在时返回空 Optional
     */
    Optional<SupportRequestDTO> getSupportRequest(String requestId);

    /**
     * 按流程业务键查询支持申请。
     *
     * @param businessKey 业务键，格式为 SUPPORT:{id}
     * @return 支持申请 DTO，不存在时返回空 Optional
     */
    Optional<SupportRequestDTO> getSupportRequestByBusinessKey(String businessKey);

    /**
     * 查询客户的支持申请历史列表。
     *
     * @param custId 客户ID
     * @return 支持申请 DTO 列表，无数据时返回空列表
     */
    List<SupportRequestDTO> getCustomerSupportHistory(String custId);

    /**
     * 查询同一批次提交分组下的所有支持申请。
     * <p>
     * 多产品拆单时，同批提交的申请共享 submitGroupId。
     * </p>
     *
     * @param submitGroupId 提交分组ID
     * @return 支持申请 DTO 列表，无数据时返回空列表
     */
    List<SupportRequestDTO> getBySubmitGroup(String submitGroupId);

    /**
     * 批量按ID查询支持申请。
     *
     * @param requestIds 申请ID列表
     * @return 支持申请 DTO 列表，无数据时返回空列表
     */
    List<SupportRequestDTO> getSupportRequestBatch(List<String> requestIds);
}
