package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.controller.dto.nav.NavCreateReqDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavSortItemReqDTO;
import com.bank.branch.platform.portal.controller.dto.nav.NavUpdateReqDTO;
import com.bank.branch.platform.portal.entity.PortalNav;
import com.bank.branch.platform.portal.service.NavService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 网址导航管理端 REST Controller (B.2-B.5)
 *
 * <p>提供导航的新增、编辑、逻辑删除和批量排序接口。
 * 所有接口需要 NAV + WRITE 业务权限。</p>
 */
@RestController
@RequestMapping("/api/admin/nav")
@RequiredArgsConstructor
public class AdminNavController {

    private final NavService navService;

    /**
     * B.2 新增导航
     *
     * @param req 新增请求
     * @return 新创建的导航 ID
     */
    @PostMapping
    @BizAuth(bizType = BizType.NAV, action = BizAction.WRITE)
    public ResponseWrapper<String> createNav(@Valid @RequestBody NavCreateReqDTO req) {
        PortalNav nav = navService.createNav(req);
        return ResponseWrapper.success(nav.getId());
    }

    /**
     * B.3 编辑导航
     *
     * @param id  导航ID
     * @param req 更新请求
     * @return 空成功响应
     */
    @PutMapping("/{id}")
    @BizAuth(bizType = BizType.NAV, action = BizAction.WRITE)
    public ResponseWrapper<Void> updateNav(
            @PathVariable String id,
            @Valid @RequestBody NavUpdateReqDTO req) {
        navService.updateNav(id, req);
        return ResponseWrapper.success(null);
    }

    /**
     * B.4 删除导航（逻辑删除）
     *
     * @param id 导航ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    @BizAuth(bizType = BizType.NAV, action = BizAction.WRITE)
    public ResponseWrapper<Void> deleteNav(@PathVariable String id) {
        navService.deleteNav(id);
        return ResponseWrapper.success(null);
    }

    /**
     * B.5 批量调整排序
     *
     * @param items 排序项列表
     * @return 空成功响应
     */
    @PutMapping("/sort")
    @BizAuth(bizType = BizType.NAV, action = BizAction.WRITE)
    public ResponseWrapper<Void> batchSort(@Valid @RequestBody List<NavSortItemReqDTO> items) {
        navService.batchSort(items);
        return ResponseWrapper.success(null);
    }
}
