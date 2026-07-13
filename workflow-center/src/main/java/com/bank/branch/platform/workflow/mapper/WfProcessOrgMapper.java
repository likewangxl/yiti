package com.bank.branch.platform.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.workflow.entity.WfProcessOrg;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 审批流参与机构快照 Mapper 接口，操作 WF_PROCESS_ORG 表。
 */
@Mapper
public interface WfProcessOrgMapper extends BaseMapper<WfProcessOrg> {

    /** 幂等插入：同实例同机构只留一条（依赖 uk_pi_org）。 */
    int insertIgnore(WfProcessOrg row);

    /** 某实例已记录的参与机构编码。 */
    List<String> selectOrgCodesByPi(String processInstanceId);
}
