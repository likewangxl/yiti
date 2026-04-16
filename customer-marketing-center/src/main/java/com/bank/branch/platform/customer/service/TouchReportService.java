package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.dto.resp.TouchReportVO;
import com.bank.branch.platform.customer.dto.resp.TouchStatisticVO;
import com.bank.branch.platform.customer.mapper.TouchReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 触达报告业务服务。
 * <p>
 * 提供触达报告的分页查询和状态统计功能。
 * 本服务通过 {@link TouchReportMapper} 执行聚合 JOIN 查询，
 * 汇聚 touch_task 与 cust_master 的数据，无需触碰任何写操作。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouchReportService {

    private final TouchReportMapper reportMapper;

    /**
     * 分页查询触达报告列表。
     * <p>
     * 通过 keyword 模糊搜索 task_no 或 cust_name，
     * status 精确过滤任务状态，orgId 限制机构范围。
     * offset 计算公式：(pageNo - 1) * pageSize。
     * </p>
     *
     * @param keyword  关键词（模糊匹配 task_no 或 cust_name），可为 null
     * @param status   任务状态（PENDING/SUCCESS/CANCELLED），可为 null
     * @param orgId    机构代码过滤，可为 null
     * @param pageNo   页码（从 1 开始）
     * @param pageSize 每页条数（最大 100）
     * @return 分页的触达报告列表
     */
    public PageResult<TouchReportVO> listPage(String keyword, String status, String orgId,
                                              int pageNo, int pageSize) {
        log.debug("[TouchReportService.listPage] keyword={}, status={}, orgId={}, pageNo={}, pageSize={}",
                keyword, status, orgId, pageNo, pageSize);

        // 计算 LIMIT 偏移量：(pageNo - 1) * pageSize
        int offset = (pageNo - 1) * pageSize;

        List<TouchReportVO> records = reportMapper.selectReportPage(keyword, status, orgId, offset, pageSize);
        long total = reportMapper.countReportPage(keyword, status, orgId);

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 按状态分组统计触达任务数量。
     * <p>
     * orgId 为 null 时统计全量数据；不为 null 时限制在指定机构范围内。
     * 返回各状态（PENDING/SUCCESS/CANCELLED）的任务计数，
     * 供前端绘制状态分布饼图或汇总卡片展示。
     * </p>
     *
     * @param orgId 机构代码（可为 null，null 时统计全量）
     * @return 按状态分组的统计列表
     */
    public List<TouchStatisticVO> statistic(String orgId) {
        log.debug("[TouchReportService.statistic] orgId={}", orgId);
        return reportMapper.selectStatistics(orgId);
    }
}
