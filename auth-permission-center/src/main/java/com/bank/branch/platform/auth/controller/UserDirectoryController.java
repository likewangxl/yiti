package com.bank.branch.platform.auth.controller;

import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.auth.service.UserDirectoryService;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.ResponseWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户通讯录查询 Controller —— PT_USER + EXT_USER_ORG + EXT_ORG_INFO 三表联查。
 *
 * <p>作为原 portal {@code /api/employees}（AddressBookController）员工查询能力的
 * 替代数据源，供审批流程「指定人」选择器等场景使用。原通讯录接口保持不动。</p>
 *
 * <p>权限沿用 {@code BizType.ADDRBOOK}，与原 /api/employees 查询接口口径一致。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/users/directory")
@RequiredArgsConstructor
@Tag(name = "用户通讯录", description = "基于 PT_USER 三表联查的员工查询（替代 ADDRBOOK_EMPLOYEE）")
public class UserDirectoryController {

    private final UserDirectoryService userDirectoryService;

    /**
     * 模糊搜索员工（对应原 GET /api/employees/search）。
     *
     * @param keyword 关键字（姓名/工号）
     * @param limit   最大返回条数，默认 20
     * @return 员工通讯录视图列表
     */
    @GetMapping("/search")
    @Operation(summary = "模糊搜索员工", description = "按姓名或工号模糊匹配在职员工，返回员工+主机构信息")
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.READ)
    public ResponseWrapper<List<UserDirectoryDTO>> searchEmployees(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("[UserDirectoryController.searchEmployees] keyword={}, limit={}", keyword, limit);
        return ResponseWrapper.success(userDirectoryService.searchEmployees(keyword, limit));
    }

    /**
     * 员工详情（对应原 GET /api/employees/{empId}）。
     *
     * @param empId 员工标识（= PT_USER.USER_ID 代理键）
     * @return 员工通讯录视图
     */
    @GetMapping("/{empId}")
    @Operation(summary = "员工详情", description = "按 empId(USER_ID) 查询员工+主机构信息")
    @BizAuth(bizType = BizType.ADDRBOOK, action = BizAction.READ)
    public ResponseWrapper<UserDirectoryDTO> getEmployee(@PathVariable String empId) {
        log.debug("[UserDirectoryController.getEmployee] empId={}", empId);
        return ResponseWrapper.success(userDirectoryService.getEmployee(empId));
    }
}
