package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfFlowEdge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审批流程连线/分支 Mapper 接口，操作 WF_FLOW_EDGE 表。
 * <p>
 * MyBatis-Plus BaseMapper 提供：insert / selectById / updateById / deleteById。
 * 自定义方法：按流程定义ID批量查询/删除连线（级联操作场景）。
 * </p>
 */
@Mapper
public interface WfFlowEdgeMapper extends BaseMapper<WfFlowEdge> {

    // insert / selectById / updateById / deleteById 由 MyBatis-Plus BaseMapper 提供

    /**
     * 根据流程定义ID查询该流程下所有连线，按 sort_no 升序排列。
     *
     * @param flowDefId 流程定义ID
     * @return 连线列表
     */
    List<WfFlowEdge> selectByFlowDefId(@Param("flowDefId") String flowDefId);

    /**
     * 根据流程定义ID删除该流程下所有连线。
     *
     * @param flowDefId 流程定义ID
     * @return 删除行数
     */
    int deleteByFlowDefId(@Param("flowDefId") String flowDefId);
}
