package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按 applyIds IN 集合 + 业务字段过滤 + 分页查询业绩调整申请。
 * <p>用于「我的待审批」按 businessKey 取出 applyIds 后做业务字段二次过滤分页。</p>
 */
@Mapper
public interface PerfAllocAdjustTodoMapper {

    /**
     * 按 applyIds IN 集合 + 业务字段过滤计数。
     */
    long countMyTodos(@Param("applyIds") List<String> applyIds,
                      @Param("keyword") String keyword,
                      @Param("allocDim") String allocDim,
                      @Param("bizKind") String bizKind,
                      @Param("dateFrom") LocalDateTime dateFrom,
                      @Param("dateToExclusive") LocalDateTime dateToExclusive);

    /**
     * 按 applyIds IN 集合 + 业务字段过滤 + 分页，按 created_time DESC, id DESC。
     */
    List<PerfAllocAdjustApply> selectMyTodos(@Param("applyIds") List<String> applyIds,
                                             @Param("keyword") String keyword,
                                             @Param("allocDim") String allocDim,
                                             @Param("bizKind") String bizKind,
                                             @Param("dateFrom") LocalDateTime dateFrom,
                                             @Param("dateToExclusive") LocalDateTime dateToExclusive,
                                             @Param("offset") int offset,
                                             @Param("pageSize") int pageSize);
}
