package com.bank.branch.platform.performance.api;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;

/**
 * 绩效（分配关系调整）审批列表对外查询 Api。
 * <p>面向手机端等外部渠道（callpu 网关），按员工号拉取该员工的审批列表。
 * 实现 {@code PerfApprovalQueryFacade} 委托项目内已有的
 * {@code AllocAdjustTodoService}/{@code AllocAdjustDoneService}，
 * 权限逻辑与 Web 端 {@code /api/perf/alloc-adjust/my-todos} / {@code /my-done}
 * 完全一致（底层均走 workflow {@code TodoQueryApi} 的 Flowable 待办/已办）。</p>
 *
 * <p>跨模块调用契约：本接口是 performance-engine-center 暴露给其他模块的唯一入口，
 * 调用方（soap-gateway-center）禁止直接依赖 perf 的 service/mapper/entity。</p>
 */
public interface PerfApprovalQueryApi {

    /**
     * 查询某员工的「分配关系调整」审批列表（待审批 + 本人已审批，合并为一个列表）。
     *
     * @param empId    员工号（外部渠道传入；上游 callpu 已完成身份认证）
     * @param pageNo   页码（从 1 开始，&lt;1 归一为 1）
     * @param pageSize 每页大小（&lt;1 归一为默认值）
     * @return 合并去重后按申请时间倒序的分页结果；员工无任何待办/已办时返回空页
     */
    PageResult<AllocAdjustApprovalItemDTO> listAllocAdjustApprovals(String empId, int pageNo, int pageSize);
}
