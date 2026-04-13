package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.service.CustomerPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户池 REST 控制器。
 * <p>
 * 提供客户池分页查询接口，仅展示未被任何机构有效认领的客户，
 * 供客户经理浏览并发起认领操作。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/customer-pool")
@Validated
@Tag(name = "客户池管理")
public class CustomerPoolController {

    private final CustomerPoolService customerPoolService;

    /**
     * 分页查询客户池（未被认领的客户）。
     *
     * @param keyword  关键词（模糊匹配客户名称），可为空
     * @param pageNo   页码，默认 1
     * @param pageSize 每页大小，默认 20
     * @return 分页的未认领客户列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.CUSTOMER_POOL, action = BizAction.LIST)
    @Operation(summary = "分页查询客户池")
    public ResponseWrapper<CustMaster> listPool(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[CustomerPoolController.listPool] keyword={}, pageNo={}, pageSize={}", keyword, pageNo, pageSize);
        PageResult<CustMaster> result = customerPoolService.listPool(keyword, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }
}
