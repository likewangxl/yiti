package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.mapper.StatShowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * 财务统计展示表只读查询服务（XAN_M9B_EMP_STAT_SHOW3 / XAN_M98_CUST_STAT_SHOW3）.
 *
 * <p>仅做条件分页查询，行以 {@link LinkedHashMap} 投影返回（保留数仓列顺序），不含任何写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatShowService {

    /** 单页最大行数，防止前端误传超大 pageSize 拖垮宽表查询. */
    private static final int MAX_PAGE_SIZE = 200;

    private final StatShowMapper statShowMapper;

    /** 客户主档查询（跨模块 *Api）：客户名称按编号反显改从 CUST_MASTER 取数. */
    private final CustomerQueryApi customerQueryApi;

    /**
     * 分页查询员工维度财务统计展示表.
     *
     * @param statisDt 统计日期（可空）
     * @param branchNo 机构号（可空）
     * @param empId    员工号（可空）
     * @param indType  指标类型（可空）
     * @param keyword  EMP_NAME 模糊关键字（可空）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小（上限 200）
     * @return 行投影分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<LinkedHashMap<String, Object>> pageEmpStat(String statisDt, String branchNo,
                                                                 String empId, String indType,
                                                                 String keyword, int pageNo, int pageSize) {
        int safeSize = normalizePageSize(pageSize);
        int safeNo = Math.max(pageNo, 1);
        int offset = (safeNo - 1) * safeSize;
        long total = statShowMapper.countEmpStat(statisDt, branchNo, empId, indType, keyword);
        List<LinkedHashMap<String, Object>> rows = total == 0
                ? List.of()
                : statShowMapper.pageEmpStat(statisDt, branchNo, empId, indType, keyword, offset, safeSize);
        log.debug("[StatShowService.pageEmpStat] statisDt={}, branchNo={}, empId={}, indType={}, "
                + "keyword={}, total={}", statisDt, branchNo, empId, indType, keyword, total);
        return PageResult.of(safeNo, safeSize, total, rows);
    }

    /**
     * 分页查询客户维度财务统计展示表.
     *
     * @param statisDt 统计日期（可空）
     * @param branchNo 机构号（可空）
     * @param custId   客户号（可空）
     * @param custType 客户类型 CUST_TYPE_CD（可空）
     * @param keyword  CUST_NAME 模糊关键字（可空）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小（上限 200）
     * @return 行投影分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<LinkedHashMap<String, Object>> pageCustStat(String statisDt, String branchNo,
                                                                  String custId, String custType,
                                                                  String keyword, int pageNo, int pageSize) {
        int safeSize = normalizePageSize(pageSize);
        int safeNo = Math.max(pageNo, 1);
        int offset = (safeNo - 1) * safeSize;
        long total = statShowMapper.countCustStat(statisDt, branchNo, custId, custType, keyword);
        List<LinkedHashMap<String, Object>> rows = total == 0
                ? List.of()
                : statShowMapper.pageCustStat(statisDt, branchNo, custId, custType, keyword, offset, safeSize);
        log.debug("[StatShowService.pageCustStat] statisDt={}, branchNo={}, custId={}, custType={}, "
                + "keyword={}, total={}", statisDt, branchNo, custId, custType, keyword, total);
        return PageResult.of(safeNo, safeSize, total, rows);
    }

    /**
     * 按客户号查客户名称（XAN_M98_CUST_STAT_SHOW3，取一条）.
     *
     * <p>供外部渠道 callpu CASH_GETCUST_INFO 客户号查名；custId 为空、查无匹配或名称为空白
     * 均返回 {@link Optional#empty()}（永不返回空白名）。</p>
     *
     * @param custId 客户号（CUST_ID）
     * @return 客户名称（去空白后非空）；无匹配时 empty
     */
    @Transactional(readOnly = true)
    public Optional<String> getCustNameByCustId(String custId) {
        if (custId == null || custId.isBlank()) {
            return Optional.empty();
        }
        String custName = statShowMapper.selectCustNameByCustId(custId.trim());
        log.debug("[StatShowService.getCustNameByCustId] custId={}, hit={}", custId, custName != null);
        if (custName == null || custName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(custName);
    }

    /**
     * 按客户编号从客户主档 {@code CUST_MASTER} 查询客户名称（新建调整申请页客户名称反显）。
     *
     * <p>替代原 {@link #getCustNameByCustId(String)} 从外部统计表 XAN_M98_CUST_STAT_SHOW3 取名的方式，
     * 改走客户营销中心 {@link CustomerQueryApi#getCustomerByCustNo(String)}（cust_master.cust_no 列）。
     * custNo 为空、客户主档无该编号或名称为空白均返回 {@link Optional#empty()}（永不返回空白名）。</p>
     *
     * @param custNo 客户编号（cust_master.cust_no）
     * @return 客户名称（去空白后非空）；客户主档无匹配时 empty
     */
    @Transactional(readOnly = true)
    public Optional<String> getCustNameFromMaster(String custNo) {
        if (custNo == null || custNo.isBlank()) {
            return Optional.empty();
        }
        Optional<String> name = customerQueryApi.getCustomerByCustNo(custNo.trim())
                .map(CustomerDTO::getCustName)
                .filter(n -> n != null && !n.isBlank());
        log.debug("[StatShowService.getCustNameFromMaster] custNo={}, hit={}", custNo, name.isPresent());
        return name;
    }

    /** pageSize 归一化：缺省 20，上限 200，下限 1. */
    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
