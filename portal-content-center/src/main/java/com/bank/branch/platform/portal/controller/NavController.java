package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.controller.dto.nav.NavGroupRespDTO;
import com.bank.branch.platform.portal.service.NavService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网址导航公开只读 REST Controller (B.1)
 *
 * <p>提供导航列表的分组查询接口，供前端门户页面调用。</p>
 */
@RestController
@RequestMapping("/api/nav")
@RequiredArgsConstructor
public class NavController {

    private final NavService navService;

    /**
     * B.1 导航列表（按 category 分组）
     *
     * @param category 分类过滤（可选）
     * @param status   状态过滤（可选，默认 ACTIVE）
     * @return 分组后的导航列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.NAV, action = BizAction.READ)
    public ResponseWrapper<NavGroupRespDTO> listGrouped(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        return ResponseWrapper.success(navService.listGrouped(category, status));
    }
}
