package com.bank.branch.platform.customer.service.marketing;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerProfileUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerQuery;
import com.bank.branch.platform.customer.dto.marketing.customer.MarketingCustomerVO;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 营销客户主档查询和资料维护服务。
 *
 * <p>页面一使用管理员/业务数据范围列表，页面二固定按主办人工号查询。
 * 所有写操作均重读主档，并以 profile_version + lock_version 或 lock_version CAS
 * 保护并发更新。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingCustomerService {

    private static final String CUSTOMER_PROFILE_FORBIDDEN = "CUST-40301";
    private static final String CUSTOMER_VERSION_CONFLICT = "CUST-40903";
    private static final String CUSTOMER_PROFILE_INVALID = "CUST-40000";

    private final MarketingCustomerInfoMapper customerMapper;
    private final UserApi userApi;
    private final OrgApi orgApi;
    /** 保留为服务层权限边界依赖；Controller 也会先解析当前用户数据范围。 */
    private final BizScopeApi bizScopeApi;

    /**
     * 分页查询营销客户总列表。
     *
     * @param query 查询条件
     * @param operatorEmpId 当前员工工号
     * @param operatorOrgId 当前主机构编码
     * @param scopeType 已由 BizScopeApi 解析的数据范围
     * @return 客户主档分页
     */
    public PageResult<MarketingCustomerVO> listAll(MarketingCustomerQuery query,
                                                   String operatorEmpId,
                                                   String operatorOrgId,
                                                   DataScopeType scopeType) {
        MarketingCustomerQuery normalized = normalize(query);
        Set<String> orgCodes = resolveOrgCodes(scopeType, operatorOrgId);
        int offset = (normalized.getPageNo() - 1) * normalized.getPageSize();
        String scope = scopeType == null ? DataScopeType.SELF.getCode() : scopeType.getCode();
        List<MarketingCustomerInfo> records = customerMapper.selectPage(normalized, scope, orgCodes,
                null, offset, normalized.getPageSize());
        long total = customerMapper.countPage(normalized, scope, orgCodes, null);
        return PageResult.of(normalized.getPageNo(), normalized.getPageSize(), total,
                toVOList(records));
    }

    /**
     * 查询当前登录人主办的客户。
     * 页面传入的主办人筛选条件会被后端覆盖，不能借此查看其他员工客户。
     *
     * @param query 页面筛选条件
     * @param operatorEmpId 当前员工工号
     * @param operatorOrgId 当前主机构编码
     * @return 本人主办客户分页
     */
    public PageResult<MarketingCustomerVO> listMine(MarketingCustomerQuery query,
                                                    String operatorEmpId,
                                                    String operatorOrgId) {
        MarketingCustomerQuery normalized = normalize(query);
        normalized.setRecordStatus("ACTIVE");
        normalized.setOwnershipStatus("ASSIGNED");
        normalized.setMainManagerId(operatorEmpId);
        int offset = (normalized.getPageNo() - 1) * normalized.getPageSize();
        List<MarketingCustomerInfo> records = customerMapper.selectPage(normalized,
                DataScopeType.SELF.getCode(), Set.of(), operatorEmpId, offset, normalized.getPageSize());
        long total = customerMapper.countPage(normalized, DataScopeType.SELF.getCode(), Set.of(), operatorEmpId);
        return PageResult.of(normalized.getPageNo(), normalized.getPageSize(), total,
                toVOList(records));
    }

    /**
     * 查询详情并执行统一数据范围校验。
     * 无主办客户按业务规则对客户经理可见，但仍受接口本身的业务权限控制。
     */
    public MarketingCustomerVO getDetail(Long id, String operatorEmpId, String operatorOrgId,
                                         DataScopeType scopeType, boolean systemAdmin) {
        MarketingCustomerInfo customer = requireActive(id);
        if (!isVisible(customer, operatorEmpId, operatorOrgId, scopeType, systemAdmin)) {
            throw new BizException(CUSTOMER_PROFILE_FORBIDDEN, "无权查看该营销客户");
        }
        return toVO(customer);
    }

    /**
     * 修改客户主档允许维护的资料字段。
     * 主键、客户号、统一社会信用代码、开户状态和主办权只能由专用流程修改。
     */
    @Transactional
    @AuditLog(action = "UPDATE_CUSTOMER_PROFILE", resourceType = "MARKETING_CUSTOMER", reasonRequired = true)
    public MarketingCustomerVO updateProfile(Long id, MarketingCustomerProfileUpdateRequest request,
                                             String operatorEmpId, String operatorOrgId,
                                             boolean operatorAdmin) {
        if (!operatorAdmin) {
            throw new BizException(CUSTOMER_PROFILE_FORBIDDEN, "仅营销管理员可以修改客户资料");
        }
        if (request == null || request.getProfileVersion() == null || request.getLockVersion() == null
                || !StringUtils.hasText(request.getReason())) {
            throw new BizException(CUSTOMER_PROFILE_INVALID, "资料版本、锁版本、修改原因不能为空");
        }
        if (!request.hasProfileChanges()) {
            throw new BizException(CUSTOMER_PROFILE_INVALID, "至少修改一项客户资料");
        }
        MarketingCustomerInfo customer = requireActive(id);
        if (!sameVersion(request.getProfileVersion(), customer.getProfileVersion())
                || !sameVersion(request.getLockVersion(), customer.getLockVersion())) {
            throw versionConflict();
        }
        LocalDateTime now = LocalDateTime.now();
        int affected = customerMapper.updateProfileByVersions(id, request.getProfileVersion(),
                request.getLockVersion(), request, operatorEmpId, now);
        if (affected != 1) {
            throw versionConflict();
        }
        MarketingCustomerInfo latest = requireActive(id);
        return toVO(latest);
    }

    private MarketingCustomerInfo requireActive(Long id) {
        MarketingCustomerInfo customer = customerMapper.selectActiveById(id);
        if (customer == null) {
            throw new BizException("CUST-40403", "客户不存在");
        }
        return customer;
    }

    private boolean isVisible(MarketingCustomerInfo customer, String operatorEmpId, String operatorOrgId,
                              DataScopeType scopeType, boolean systemAdmin) {
        if (systemAdmin || scopeType == DataScopeType.ALL) {
            return true;
        }
        if ("UNASSIGNED".equals(customer.getOwnershipStatus())) {
            return true;
        }
        if (StringUtils.hasText(operatorEmpId) && operatorEmpId.equals(customer.getMainManagerId())) {
            return true;
        }
        if (scopeType == DataScopeType.ORG && StringUtils.hasText(operatorOrgId)) {
            return operatorOrgId.equals(customer.getMainOrgId());
        }
        if (scopeType == DataScopeType.ORG_SUBTREE && StringUtils.hasText(operatorOrgId)) {
            Set<String> orgCodes = orgApi.getOrgSubtreeCodes(operatorOrgId);
            return orgCodes != null && orgCodes.contains(customer.getMainOrgId());
        }
        return false;
    }

    private Set<String> resolveOrgCodes(DataScopeType scopeType, String operatorOrgId) {
        if (scopeType == DataScopeType.ORG && StringUtils.hasText(operatorOrgId)) {
            return Set.of(operatorOrgId);
        }
        if (scopeType == DataScopeType.ORG_SUBTREE && StringUtils.hasText(operatorOrgId)) {
            Set<String> codes = orgApi.getOrgSubtreeCodes(operatorOrgId);
            return codes == null ? Set.of() : codes;
        }
        return Set.of();
    }

    private MarketingCustomerQuery normalize(MarketingCustomerQuery query) {
        MarketingCustomerQuery normalized = query == null ? new MarketingCustomerQuery() : query;
        normalized.setPageNo(Math.max(1, normalized.getPageNo()));
        normalized.setPageSize(Math.min(Math.max(1, normalized.getPageSize()), 100));
        if (!StringUtils.hasText(normalized.getRecordStatus())) {
            normalized.setRecordStatus("ACTIVE");
        }
        return normalized;
    }

    private List<MarketingCustomerVO> toVOList(List<MarketingCustomerInfo> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<String, UserDTO> users = loadUsers(records);
        Map<String, String> orgNames = loadOrgNames(records);
        return records.stream().map(item -> toVO(item, users, orgNames)).toList();
    }

    private MarketingCustomerVO toVO(MarketingCustomerInfo customer) {
        return toVO(customer, loadUsers(List.of(customer)), loadOrgNames(List.of(customer)));
    }

    private MarketingCustomerVO toVO(MarketingCustomerInfo customer, Map<String, UserDTO> users,
                                     Map<String, String> orgNames) {
        MarketingCustomerVO vo = MarketingCustomerVO.fromEntity(customer);
        if (StringUtils.hasText(customer.getMainManagerId())) {
            UserDTO manager = users.get(customer.getMainManagerId());
            if (manager != null) {
                vo.setMainManagerName(manager.getDisplayName());
                if (!StringUtils.hasText(vo.getMainOrgName())) {
                    vo.setMainOrgName(manager.getMainOrgName());
                }
            }
        }
        if (!StringUtils.hasText(vo.getMainOrgName()) && StringUtils.hasText(customer.getMainOrgId())) {
            vo.setMainOrgName(orgNames.get(customer.getMainOrgId()));
        }
        return vo;
    }

    private Map<String, UserDTO> loadUsers(List<MarketingCustomerInfo> records) {
        List<String> empIds = records.stream().map(MarketingCustomerInfo::getMainManagerId)
                .filter(StringUtils::hasText).distinct().toList();
        if (empIds.isEmpty()) {
            return Map.of();
        }
        List<UserDTO> users = userApi.getUserByEmpIds(empIds);
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Map<String, UserDTO> result = new HashMap<>();
        users.forEach(user -> {
            if (user != null && StringUtils.hasText(user.getEmpId())) {
                result.put(user.getEmpId(), user);
            }
        });
        return result;
    }

    private Map<String, String> loadOrgNames(List<MarketingCustomerInfo> records) {
        List<String> orgIds = records.stream().map(MarketingCustomerInfo::getMainOrgId)
                .filter(StringUtils::hasText).distinct().toList();
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        List<OrgDTO> orgs = orgApi.getOrgsByCodes(orgIds);
        if (orgs == null || orgs.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        orgs.forEach(org -> {
            if (org != null && StringUtils.hasText(org.getOrgCode())) {
                result.put(org.getOrgCode(), org.getOrgName());
            }
        });
        return result;
    }

    private boolean sameVersion(Integer expected, Integer actual) {
        int expectedValue = expected == null ? 0 : expected;
        int actualValue = actual == null ? 0 : actual;
        return expectedValue == actualValue;
    }

    private BizException versionConflict() {
        return new BizException(CUSTOMER_VERSION_CONFLICT, "客户资料或锁版本已变化，请刷新后重新确认");
    }
}
