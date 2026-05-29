package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfFlowNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审批流程节点 Mapper 接口，操作 WF_FLOW_NODE 表。
 * <p>
 * MyBatis-Plus BaseMapper 提供：insert / selectById / updateById / deleteById。
 * 自定义方法：按流程定义ID批量查询/删除节点。
 * </p>
 */
@Mapper
public interface WfFlowNodeMapper extends BaseMapper<WfFlowNode> {

    // insert / selectById / updateById / deleteById 由 MyBatis-Plus BaseMapper 提供

    /**
     * 根据流程定义ID查询该流程下所有节点，按 sort_no 升序排列。
     *
     * @param flowDefId 流程定义ID
     * @return 节点列表
     */
    List<WfFlowNode> selectByFlowDefId(@Param("flowDefId") String flowDefId);

    /**
     * 根据流程定义ID删除该流程下所有节点。
     *
     * @param flowDefId 流程定义ID
     * @return 删除行数
     */
    int deleteByFlowDefId(@Param("flowDefId") String flowDefId);
}
