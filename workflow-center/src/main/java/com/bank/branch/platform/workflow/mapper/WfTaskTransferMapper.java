package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfTaskTransfer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务转交待认领生命周期 Mapper 接口，操作 WF_TASK_TRANSFER 表。
 */
@Mapper
public interface WfTaskTransferMapper extends BaseMapper<WfTaskTransfer> {

    /** 查询某任务当前生效（PENDING_ACCEPT）的转交记录，无则返回 null。 */
    WfTaskTransfer selectActiveByTaskId(String taskId);

    /** 乐观流转：仅当当前状态仍为 PENDING_ACCEPT 时才更新，返回影响行数，用于拦截并发决策。 */
    int updateStatusIfPending(@Param("id") String id,
                              @Param("status") String status,
                              @Param("rejectReason") String rejectReason,
                              @Param("decidedTime") LocalDateTime decidedTime);

    /** 收件箱：某员工待认领的转交任务列表。 */
    List<WfTaskTransfer> selectInbox(String toEmpId);

    /** 发件箱：某员工发起的转交任务列表（待认领+已认领）。 */
    List<WfTaskTransfer> selectOutbox(String fromEmpId);
}
