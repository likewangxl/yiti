package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfFlowNodeApprover;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审批流程节点审批人规则 Mapper 接口，操作 WF_FLOW_NODE_APPROVER 表。
 * <p>
 * MyBatis-Plus BaseMapper 提供：insert / selectById / updateById / deleteById。
 * 自定义方法：按节点ID列表批量查询/删除审批人规则（支持级联删除场景）。
 * </p>
 */
@Mapper
public interface WfFlowNodeApproverMapper extends BaseMapper<WfFlowNodeApprover> {

    // insert / selectById / updateById / deleteById 由 MyBatis-Plus BaseMapper 提供

    /**
     * 根据节点ID列表批量查询审批人规则，按 sort_no 升序排列。
     *
     * @param nodeIds 节点ID列表，不能为空
     * @return 审批人规则列表
     */
    List<WfFlowNodeApprover> selectByNodeIds(@Param("nodeIds") List<String> nodeIds);

    /**
     * 根据节点ID列表批量删除审批人规则（级联删除节点时使用）。
     *
     * @param nodeIds 节点ID列表，不能为空
     * @return 删除行数
     */
    int deleteByNodeIds(@Param("nodeIds") List<String> nodeIds);
}
