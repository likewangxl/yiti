package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.converter.CustomerDTOConverter;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.governance.api.DictApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 客户池业务服务。
 * <p>
 * 负责分页查询审批通过且全行公开、当前员工尚未认领的客户，供客户经理浏览并认领。
 * 不直接操作 CUSTOMER_MARKET_CUSTOMER 表，借助 {@link CustClaimMapper#selectPoolPage} 的
 * LEFT JOIN 查询剔除已认领记录。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPoolService {

    private final CustClaimMapper claimMapper;
    private final DictApi dictApi;
    private final OrgApi orgApi;

    /**
     * 分页查询当前员工可认领的全行公开客户。
     * <p>
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword  搜索关键词（模糊匹配 cust_name），可为 null
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页的全行公开待认领客户主档列表
     */
    public PageResult<CustMaster> listPool(String keyword, String empId, int pageNo, int pageSize) {
        log.info("[CustomerPoolService.listPool] keyword={}, pageNo={}, pageSize={}", keyword, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<CustMaster> list = claimMapper.selectPoolPage(keyword, empId, offset, pageSize);
        long total = claimMapper.countPoolPage(keyword, empId);

        log.info("[CustomerPoolService.listPool] total={}", total);
        return PageResult.of(pageNo, pageSize, total, list);
    }

    /**
     * 分页查询客户池并转换为 DTO，供 REST 出口使用，避免实体字段泄露。
     *
     * @param keyword  搜索关键词（模糊匹配 cust_name），可为 null
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页的未认领客户 DTO 列表（CustomerDTO）
     */
    public PageResult<CustomerDTO> listPoolAsDTO(String keyword, String empId, int pageNo, int pageSize) {
        log.info("[CustomerPoolService.listPoolAsDTO] keyword={}, pageNo={}, pageSize={}", keyword, pageNo, pageSize);
        PageResult<CustMaster> raw = listPool(keyword, empId, pageNo, pageSize);
        PageResult<CustomerDTO> out = new PageResult<>();
        out.setPageNo(raw.getPageNo());
        out.setPageSize(raw.getPageSize());
        out.setTotal(raw.getTotal());
        List<CustomerDTO> records = CustomerDTOConverter.toDTOList(raw.getRecords());
        fillDisplayNames(records);
        out.setRecords(records);
        return out;
    }

    /**
     * 回填客户池页面所需的中文显示值。
     * <p>
     * 字典 API 在编码不存在时会原样返回编码，原样值不能作为中文展示，因此统一转为空；
     * 机构名称则一次批量查询，避免逐行调用 OrgApi 产生 N+1 请求。
     * </p>
     */
    private void fillDisplayNames(List<CustomerDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        Map<String, String> orgNames = loadOrgNames(records);
        Map<String, String> industryNames = new HashMap<>();
        Map<String, String> customerTypeNames = new HashMap<>();
        records.forEach(dto -> {
            String industry = dto.getIndustry();
            if (StringUtils.hasText(industry)) {
                if (!industryNames.containsKey(industry)) {
                    industryNames.put(industry, translatedLabel("INDUSTRY", industry));
                }
            }
            String customerType = dto.getCustomerType();
            if (StringUtils.hasText(customerType)) {
                if (!customerTypeNames.containsKey(customerType)) {
                    customerTypeNames.put(customerType, translatedLabel("CUSTOMER_TYPE", customerType));
                }
            }
            dto.setIndustryName(industryNames.get(industry));
            dto.setCustomerTypeName(customerTypeNames.get(customerType));
            dto.setOwnerOrgName(orgNames.get(dto.getOwnerOrgId()));
        });
    }

    private Map<String, String> loadOrgNames(List<CustomerDTO> records) {
        List<String> orgCodes = records.stream()
                .map(CustomerDTO::getOwnerOrgId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (orgCodes.isEmpty()) {
            return Map.of();
        }
        List<OrgDTO> orgs = orgApi.getOrgsByCodes(orgCodes);
        if (orgs == null || orgs.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        orgs.stream()
                .filter(Objects::nonNull)
                .filter(org -> StringUtils.hasText(org.getOrgCode()))
                .forEach(org -> names.put(org.getOrgCode(), org.getOrgName()));
        return names;
    }

    private String translatedLabel(String dictType, String code) {
        String label = dictApi.getDictLabel(dictType, code);
        return StringUtils.hasText(label) && !code.equals(label) ? label : null;
    }
}
