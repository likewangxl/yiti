package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;

import java.util.Collection;
import java.util.Optional;

/**
 * 分行经营大屏不可变批次只读查询契约。
 *
 * <p>调用方必须先获得当前登录人的授权机构集合，再把集合传入查询；实现会再次从
 * {@code OrgGroupApi} 读取 live 成员，防止机构移出后继续返回旧快照。</p>
 */
public interface BranchDashboardBatchQueryApi {

    /** 查询指定机构组最新的 SUCCESS 批次。 */
    Optional<BranchDashboardBatchDTO> latest(String groupCode,
                                             Collection<String> currentAuthorizedOrgCodes);

    /** 按不可变批次 ID 查询，并执行当前成员与授权过滤。 */
    Optional<BranchDashboardBatchDTO> byId(String batchId,
                                           Collection<String> currentAuthorizedOrgCodes);
}
