package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.converter.CustClaimDTOConverter;
import com.bank.branch.platform.customer.api.converter.CustomerDTOConverter;
import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.api.dto.CustomerFilterDTO;
import com.bank.branch.platform.customer.api.dto.RunningFlowDTO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.M98CustMaster;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.M98CustMasterMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 客户主档对外查询接口实现。
 * <p>
 * 实现 {@link CustomerQueryApi} 接口，提供客户主档的只读查询能力。
 * 所有方法返回 DTO，不暴露内部实体；跨模块调用通过此实现类完成，禁止直连 Mapper。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerQueryApiImpl implements CustomerQueryApi {

    /** 批量查询最大客户数量限制 */
    private static final int MAX_BATCH_SIZE = 500;
    /** 模糊搜索最大返回条数限制 */
    private static final int MAX_SEARCH_LIMIT = 50;

    private final CustMasterMapper custMasterMapper;
    /** 仅供绩效等存量业务按 M98 客户号反显客户名称。 */
    private final M98CustMasterMapper m98CustMasterMapper;
    private final CustClaimMapper custClaimMapper;
    private final TouchTaskMapper touchTaskMapper;

    /**
     * 获取客户详情，不存在时返回 Optional.empty()。
     *
     * @param custId 客户ID
     * @return 客户DTO Optional，不存在时为空
     */
    @Override
    public Optional<CustomerDTO> getCustomer(String custId) {
        log.debug("[CustomerQueryApiImpl.getCustomer] custId={}", custId);
        CustMaster entity = custMasterMapper.selectById(custId);
        return Optional.ofNullable(CustomerDTOConverter.toDTO(entity));
    }

    @Override
    public Optional<CustomerDTO> getCustomerByCustNo(String custNo) {
        log.debug("[CustomerQueryApiImpl.getCustomerByCustNo] custNo={}", custNo);
        if (custNo == null || custNo.isBlank()) {
            return Optional.empty();
        }
        M98CustMaster entity = m98CustMasterMapper.selectByCustNo(custNo);
        return Optional.ofNullable(CustomerDTOConverter.toDTOFromM98(entity));
    }

    /**
     * 批量获取客户信息，custIds 最大 500，超限抛 COMMON-40000。
     *
     * @param custIds 客户ID列表
     * @return 客户DTO列表
     */
    @Override
    public List<CustomerDTO> listCustomers(List<String> custIds) {
        if (custIds == null || custIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 超过上限直接拒绝，保护数据库资源
        if (custIds.size() > MAX_BATCH_SIZE) {
            throw new BizException("COMMON-40000", "参数超限: custIds 最大 500，当前: " + custIds.size());
        }
        log.debug("[CustomerQueryApiImpl.listCustomers] custIds.size={}", custIds.size());
        List<CustMaster> entities = custMasterMapper.selectByIds(custIds);
        return CustomerDTOConverter.toDTOList(entities);
    }

    /**
     * 模糊搜索客户（匹配 cust_name / cust_no / unified_credit_code）。
     * keyword 为空或 limit 超范围抛 COMMON-40000。
     *
     * @param keyword 搜索关键词，不能为空
     * @param limit   最大返回条数，范围 1-50
     * @return 匹配的客户DTO列表
     */
    @Override
    public List<CustomerDTO> searchCustomers(String keyword, int limit) {
        // 关键词不能为空
        if (keyword == null || keyword.isBlank()) {
            throw new BizException("COMMON-40000", "参数错误: keyword 不能为空");
        }
        // limit 范围校验 [1, 50]
        if (limit < 1 || limit > MAX_SEARCH_LIMIT) {
            throw new BizException("COMMON-40000", "参数超限: limit 应在 1-50 之间，当前: " + limit);
        }
        log.debug("[CustomerQueryApiImpl.searchCustomers] keyword={}, limit={}", keyword, limit);
        List<CustMaster> entities = custMasterMapper.searchByKeyword(keyword, limit);
        return CustomerDTOConverter.toDTOList(entities);
    }

    /**
     * 校验客户是否有效（存在且未删除）。
     *
     * @param custId 客户ID
     * @return true 表示客户有效
     */
    @Override
    public boolean isValidCustomer(String custId) {
        log.debug("[CustomerQueryApiImpl.isValidCustomer] custId={}", custId);
        return custMasterMapper.selectById(custId) != null;
    }

    /**
     * 校验机构是否有 CLAIMED 状态的认领记录。
     * 只有认领状态为 CLAIMED 时才返回 true，CANCELLED 状态返回 false。
     *
     * @param custId  客户ID
     * @param orgCode 机构代码
     * @return true 表示该机构有有效认领
     */
    @Override
    public boolean isClaimedByOrg(String custId, String orgCode) {
        log.debug("[CustomerQueryApiImpl.isClaimedByOrg] custId={}, orgCode={}", custId, orgCode);
        CustClaim claim = custClaimMapper.selectByCustIdAndOrgId(custId, orgCode);
        // 必须存在且认领状态为 CLAIMED，CANCELLED 的历史记录不算有效认领
        return claim != null && ClaimStatus.CLAIMED.getCode().equals(claim.getClaimStatus());
    }

    /**
     * 获取客户所有 CLAIMED 状态的认领关系，历史 CANCELLED 记录排除。
     *
     * @param custId 客户ID
     * @return 有效认领DTO列表
     */
    @Override
    public List<CustClaimDTO> getCustomerClaims(String custId) {
        log.debug("[CustomerQueryApiImpl.getCustomerClaims] custId={}", custId);
        List<CustClaim> claims = custClaimMapper.selectByCustId(custId);
        if (claims == null) {
            return Collections.emptyList();
        }
        // 按契约要求：仅返回 CLAIMED 状态的有效认领，过滤历史取消记录
        return claims.stream()
                .filter(c -> ClaimStatus.CLAIMED.getCode().equals(c.getClaimStatus()))
                .map(CustClaimDTOConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 判断客户是否存在指定业务类型的在途流程。
     * 当前仅支持 TOUCH_TASK 类型，其他 bizType 返回 false。
     *
     * @param custId  客户ID
     * @param bizType 业务类型
     * @return true 表示存在在途流程
     */
    @Override
    public boolean hasRunningProcess(String custId, String bizType) {
        if ("TOUCH_TASK".equals(bizType)) {
            Long count = touchTaskMapper.countActiveByCust(custId);
            return count != null && count > 0L;
        }
        // 未支持的 bizType 暂时返回 false，记录 debug 日志
        log.debug("[CustomerQueryApiImpl.hasRunningProcess] unsupported bizType={}, custId={}, returning false",
                bizType, custId);
        return false;
    }

    /**
     * 获取客户所有在途流程（全类型）。
     * 当前仅包含 TOUCH_TASK 类型的在途触达任务。
     *
     * @param custId 客户ID
     * @return 在途流程DTO列表
     */
    @Override
    public List<RunningFlowDTO> listRunningProcesses(String custId) {
        log.debug("[CustomerQueryApiImpl.listRunningProcesses] custId={}", custId);
        List<TouchTask> activeTasks = touchTaskMapper.selectActiveByCust(custId);
        if (activeTasks == null) {
            return Collections.emptyList();
        }
        // 将触达任务转换为通用的在途流程 DTO
        return activeTasks.stream().map(t -> {
            RunningFlowDTO dto = new RunningFlowDTO();
            dto.setBizType("TOUCH_TASK");
            dto.setBizId(t.getId());
            dto.setBusinessKey(t.getBusinessKey());
            dto.setOrgId(t.getOrgId());
            dto.setEmpId(t.getAssigneeEmpId());
            dto.setStatus(t.getTaskStatus());
            dto.setStartedAt(t.getCreatedTime());
            // orgName、empName 需二次查询，此层暂置 null，由上层 Service 补充
            dto.setOrgName(null);
            dto.setEmpName(null);
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 按过滤条件统计客户数量。
     * filter 为 null 时当作空过滤条件处理。
     *
     * @param filter 过滤条件，可为 null
     * @return 符合条件的客户总数
     */
    @Override
    public long countCustomers(CustomerFilterDTO filter) {
        // null 安全：传 null 当作空 filter，统计所有未删除客户
        CustomerFilterDTO effectiveFilter = filter != null ? filter : new CustomerFilterDTO();
        log.debug("[CustomerQueryApiImpl.countCustomers] filter={}", effectiveFilter);
        return custMasterMapper.countByFilter(effectiveFilter);
    }
}
