package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.resp.CustomerCrossOrgHistoryVO;
import com.bank.branch.platform.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户跨机构历史查询控制器（高危/跨域授权/独立审计）。
 *
 * <p>
 * 本控制器提供唯一高危端点：GET /api/customers/{id}/history，
 * 用于总行部门查询客户在全行范围内的所有认领记录和触达历史。
 * </p>
 *
 * <p>
 * 安全声明：
 * <ul>
 *   <li>权限：@BizAuth(bizType=CUSTOMER, action=READ) — 须持有 CUSTOMER:READ 业务权限</li>
 *   <li>审计：@AuditLog(action=VIEW_CROSS_ORG_CUSTOMER_HISTORY, reasonRequired=true) — 操作必须留痕</li>
 *   <li>TODO: 待 @AuditLog 支持 specialCategory 属性后，补充 specialCategory=CROSS_ORG，
 *       实现 5 年留存与月度专项审计（见 docs/modules/customer-marketing-center/07-审计要求.md）。</li>
 * </ul>
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customers")
@Validated
@Tag(name = "客户跨机构历史查询（高危）")
public class CustomerHistoryController {

    private final CustomerService customerService;

    /**
     * 跨机构全量客户历史查询（高危 / 跨域授权 / 独立审计）。
     *
     * <p>
     * 返回指定客户在全行范围内的所有认领记录（含 CANCELLED 历史）与触达任务历史。
     * 该端点为独立授权高危操作，审计日志需标注 specialCategory=CROSS_ORG。
     * </p>
     *
     * @param id 客户 ID（路径参数）
     * @return 跨机构聚合历史 VO，包含 customer / claims / touchTasks
     */
    @GetMapping("/{id}/history")
    @BizAuth(bizType = BizType.CUSTOMER, action = BizAction.READ)
    @AuditLog(action = "VIEW_CROSS_ORG_CUSTOMER_HISTORY",
              resourceType = "CUSTOMER",
              reasonRequired = true)
    @Operation(summary = "跨机构客户历史查询（高危）",
               description = "查询客户在全行范围内所有认领记录与触达历史，独立授权，审计留存5年")
    public ResponseWrapper<CustomerCrossOrgHistoryVO> getHistory(@PathVariable String id) {
        log.info("[CustomerHistoryController.getHistory] custId={}", id);
        CustomerCrossOrgHistoryVO vo = customerService.getCrossOrgHistory(id);
        return ResponseWrapper.success(vo);
    }
}
