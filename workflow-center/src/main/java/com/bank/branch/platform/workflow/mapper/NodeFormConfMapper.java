package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfNodeFormConf;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程节点表单配置 Mapper 接口，操作 wf_node_form_conf 表。
 * <p>
 * 提供按流程定义KEY和节点KEY查询表单配置的能力。
 * 前端根据此配置动态渲染审批表单，后端根据此配置校验提交数据。
 * </p>
 * <p>
 * MyBatis-Plus 接入：继承 {@link BaseMapper} 后，{@code insert(T)} /
 * {@code selectById(Serializable)} / {@code updateById(T)} 由 BaseMapper 提供。
 * 自定义 SQL 继续保留在本接口和 XML。
 * </p>
 */
@Mapper
public interface NodeFormConfMapper extends BaseMapper<WfNodeFormConf> {

    // insert / selectById / updateById 由 MyBatis-Plus BaseMapper 提供

    /**
     * 根据流程定义KEY查询所有节点表单配置。
     *
     * @param processDefinitionKey 流程定义KEY
     * @return 表单配置实体列表
     */
    List<WfNodeFormConf> selectByProcessDefKey(@Param("processDefinitionKey") String processDefinitionKey);

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
}
