package com.bank.branch.platform.workflow.mapper;

import com.bank.branch.platform.workflow.entity.WfNodeFormConf;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 流程节点表单配置 Mapper 接口，操作 wf_node_form_conf 表。
 * <p>
 * 提供按流程定义KEY和节点KEY查询表单配置的能力。
 * 前端根据此配置动态渲染审批表单，后端根据此配置校验提交数据。
 * </p>
 */
@Mapper
public interface NodeFormConfMapper {

    /**
     * 根据流程定义KEY和节点KEY查询表单配置。
     *
     * @param processDefinitionKey 流程定义KEY
     * @param nodeKey              节点KEY
     * @return 表单配置实体，不存在时返回 null
     */
    WfNodeFormConf selectByProcessDefKeyAndNodeKey(
            @Param("processDefinitionKey") String processDefinitionKey,
            @Param("nodeKey") String nodeKey);

    /**
     * 新增表单配置记录。
     *
     * @param conf 表单配置实体
     * @return 受影响行数
     */
    int insert(WfNodeFormConf conf);

    /**
     * 按主键更新表单配置，使用动态 SET 仅更新非 null 字段。
     *
     * @param conf 包含 id 及待更新字段的表单配置实体
     * @return 受影响行数
     */
    int updateById(WfNodeFormConf conf);
}
