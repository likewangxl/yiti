package com.bank.branch.platform.customer.mapper;

import com.bank.branch.platform.customer.dto.resp.TouchReportVO;
import com.bank.branch.platform.customer.dto.resp.TouchStatisticVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 触达报告聚合查询 Mapper 接口。
 * <p>
 * 区别于 {@link TouchTaskMapper}（单表 CRUD），本接口专为报告域设计：
 * 通过 TOUCH_TASK LEFT JOIN CUSTOMER_MARKET_CUSTOMER 聚合客户名称，并统计每个任务的触达日志条数。
 * 统计接口按 task_status 分组，供前端绘制状态分布图。
 * </p>
 */
@Mapper
public interface TouchReportMapper {

    /**
     * 分页查询触达报告（JOIN TOUCH_TASK + CUSTOMER_MARKET_CUSTOMER）。
     * <p>
     * keyword 模糊匹配 task_no 或 CUSTOMER_MARKET_CUSTOMER.cust_name；
     * status 精确匹配 task_status；orgId 精确匹配 org_id。
     * </p>
     *
     * @param keyword 关键词（搜索 task_no 或 cust_name），可为 null
     * @param status  任务状态过滤（PENDING/SUCCESS/CANCELLED），可为 null
     * @param orgId   机构代码过滤，可为 null
     * @param offset  偏移量
     * @param limit   每页条数
     * @return 触达报告列表
     */
    List<TouchReportVO> selectReportPage(@Param("keyword") String keyword,
                                         @Param("status") String status,
                                         @Param("orgId") String orgId,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /**
     * 统计分页查询总记录数（与 selectReportPage 共享 WHERE 条件）。
     *
     * @param keyword 关键词，可为 null
     * @param status  任务状态过滤，可为 null
     * @param orgId   机构代码过滤，可为 null
     * @return 总记录数
     */
    long countReportPage(@Param("keyword") String keyword,
                         @Param("status") String status,
                         @Param("orgId") String orgId);

    /**
     * 按状态分组统计触达任务数量。
     * <p>
     * 若 orgId 不为 null，则限制在该机构范围内统计；否则统计全局数据。
     * </p>
     *
     * @param orgId 机构代码，可为 null（null 时统计全量）
     * @return 按状态分组的统计列表
     */
    List<TouchStatisticVO> selectStatistics(@Param("orgId") String orgId);
}
