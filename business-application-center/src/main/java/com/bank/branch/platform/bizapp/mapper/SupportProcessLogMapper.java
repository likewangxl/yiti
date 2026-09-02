package com.bank.branch.platform.bizapp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.bizapp.entity.SupportProcessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MARKETING_SUPPORT_PROCESS_LOG Mapper。 */
@Mapper
public interface SupportProcessLogMapper extends BaseMapper<SupportProcessLog> {
    SupportProcessLog selectByRequestAndClientUuid(@Param("supportRequestId") String supportRequestId,
                                                    @Param("clientUuid") String clientUuid);

    List<SupportProcessLog> selectByRequestId(@Param("supportRequestId") String supportRequestId);

    long countByRequestId(@Param("supportRequestId") String supportRequestId);

    /** 统计申请下指定类型的留痕，完成约束只认可 PROCESS 记录。 */
    long countByRequestIdAndLogType(@Param("supportRequestId") String supportRequestId,
                                    @Param("logType") String logType);
}
