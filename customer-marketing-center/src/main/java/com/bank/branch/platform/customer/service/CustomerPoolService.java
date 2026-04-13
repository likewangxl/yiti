package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户池业务服务。
 * <p>
 * 负责分页查询客户池（未被任何机构有效认领的客户），供客户经理浏览并认领。
 * 不直接操作 cust_master 表，借助 {@link CustClaimMapper#selectPoolPage} 的
 * LEFT JOIN 查询剔除已认领记录。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerPoolService {

    private final CustClaimMapper claimMapper;

    /**
     * 分页查询客户池（未被任何机构有效认领的客户）。
     * <p>
     * offset = (pageNo - 1) * pageSize
     * </p>
     *
     * @param keyword  搜索关键词（模糊匹配 cust_name），可为 null
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页的未认领客户主档列表
     */
    public PageResult<CustMaster> listPool(String keyword, int pageNo, int pageSize) {
        log.info("[CustomerPoolService.listPool] keyword={}, pageNo={}, pageSize={}", keyword, pageNo, pageSize);

        int offset = (pageNo - 1) * pageSize;
        List<CustMaster> list = claimMapper.selectPoolPage(keyword, offset, pageSize);
        long total = claimMapper.countPoolPage(keyword);

        log.info("[CustomerPoolService.listPool] total={}", total);
        return PageResult.of(pageNo, pageSize, total, list);
    }
}
