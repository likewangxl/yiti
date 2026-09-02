package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserDirectoryMapper;
import com.bank.branch.platform.auth.security.context.CurrentUserProvider;
import com.bank.branch.platform.common.security.context.CurrentUserContext;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.AuthException;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户通讯录查询服务 —— 基于 PT_USER + EXT_USER_ORG + EXT_ORG_INFO 三表联查，
 * 替代原 portal {@code ADDRBOOK_EMPLOYEE} 作为员工选择器/审批人选择的数据源。
 *
 * <p>用户身份及联系方式均来自 PT_USER；机构信息来自 EXT_USER_ORG + EXT_ORG_INFO。
 * 只有联系方式更新是写操作，并且始终绑定当前登录用户。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    /** 搜索返回上限保护（与原通讯录搜索一致，前端默认 20，最大 50） */
    private static final int MAX_SEARCH_LIMIT = 50;

    /** 分页查询默认页大小。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 分页查询最大页大小。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final UserDirectoryMapper userDirectoryMapper;
    private final UserMapper userMapper;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 关键字模糊搜索在职员工（姓名/工号），返回通讯录视图列表。
     *
     * @param keyword 关键字；为空/空白时返回空列表（避免全表扫描）
     * @param limit   期望返回条数，内部夹紧到 [1, 50]
     * @return 员工通讯录视图列表（empId=USER_ID）
     */
    public List<UserDirectoryDTO> searchEmployees(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        List<UserDirectoryDTO> result = userDirectoryMapper.searchByKeyword(keyword.trim(), safeLimit);
        log.debug("[UserDirectoryService.searchEmployees] keyword={}, limit={}, hit={}",
                keyword, safeLimit, result == null ? 0 : result.size());
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * 按 empId（= USER_ID 代理键）查询单个员工通讯录视图。
     *
     * @param empId 员工标识（PT_USER.USER_ID）
     * @return 员工通讯录视图；不存在时返回 null（与原 getEmployee 失败回退由调用方/前端处理保持一致）
     */
    public UserDirectoryDTO getEmployee(String empId) {
        if (empId == null || empId.isBlank()) {
            return null;
        }
        UserDirectoryDTO dto = userDirectoryMapper.selectByEmpId(empId.trim());
        log.debug("[UserDirectoryService.getEmployee] empId={}, found={}", empId, dto != null);
        return dto;
    }

    /**
     * 分页查询在职用户。人员身份来自 PT_USER，机构条件通过 EXT_USER_ORG 过滤，
     * 机构名称由 EXT_ORG_INFO 联查获得。
     *
     * @param keyword 关键字（USER_ID、USERNAME、USERCHNNAME、MOBILE 或 EMAIL），可为空
     * @param orgCode 机构编码，可为空
     * @param pageNo  页码，从 1 开始
     * @param pageSize 每页条数，默认 20，最大 100
     * @return 在职用户分页结果
     */
    public PageResult<UserDirectoryDTO> pageActiveUsers(String keyword, String orgCode,
                                                        int pageNo, int pageSize) {
        int safePageNo = pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long offsetLong = (long) (safePageNo - 1) * safePageSize;
        int offset = offsetLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offsetLong;
        String normalizedKeyword = normalize(keyword);
        String normalizedOrgCode = normalize(orgCode);

        List<UserDirectoryDTO> records = userDirectoryMapper.selectActiveUsersByPage(
                normalizedKeyword, normalizedOrgCode, offset, safePageSize);
        long total = userDirectoryMapper.countActiveUsers(normalizedKeyword, normalizedOrgCode);
        return PageResult.of(safePageNo, safePageSize, total,
                records == null ? Collections.emptyList() : records);
    }

    /**
     * 批量查询用户通讯录视图，并按调用方传入的 USER_ID 顺序返回。
     *
     * @param empIds USER_ID 列表，可为空
     * @return 命中的用户列表，未命中的 ID 不补空值
     */
    public List<UserDirectoryDTO> getEmployeesByIds(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalizedIds = empIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDirectoryDTO> rows = userDirectoryMapper.selectByEmpIds(normalizedIds);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, UserDirectoryDTO> byId = new HashMap<>();
        for (UserDirectoryDTO row : rows) {
            if (row != null && row.getEmpId() != null) {
                byId.put(row.getEmpId(), row);
            }
        }
        List<UserDirectoryDTO> result = new java.util.ArrayList<>();
        for (String id : normalizedIds) {
            UserDirectoryDTO row = byId.get(id);
            if (row != null) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 更新当前登录用户自己的电话和邮箱。
     * <p>方法不接收目标用户 ID，用户 ID 只从认证过滤器填充的当前上下文取得，
     * 以保证自助维护不能越权修改其他用户。</p>
     *
     * @param mobile 电话号码，可为空以清空
     * @param email 邮箱，可为空以清空
     * @return 更新后的通讯录视图
     * @throws AuthException 未登录或当前上下文缺少用户 ID
     * @throws BizException 当前用户不存在或联系方式超出数据库字段长度
     */
    public UserDirectoryDTO updateCurrentUserContact(String mobile, String email) {
        CurrentUserContext context = currentUserProvider.get();
        if (context == null || context.empId() == null || context.empId().isBlank()) {
            throw new AuthException(AuthErrorCode.NOT_AUTHENTICATED.getCode(),
                    AuthErrorCode.NOT_AUTHENTICATED.getMessage());
        }
        String userId = context.empId().trim();
        String normalizedMobile = normalize(mobile);
        String normalizedEmail = normalize(email);
        validateContactLength(normalizedMobile, normalizedEmail);

        int affected = userMapper.updateContact(userId, normalizedMobile, normalizedEmail);
        if (affected == 0) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        return getEmployee(userId);
    }

    /** 统一去除查询/联系方式两端空格，空字符串按 null 处理。 */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** PT_USER.EMAIL 为 100 字符，MOBILE 测试/目标表按 20 字符预留。 */
    private void validateContactLength(String mobile, String email) {
        if (mobile != null && mobile.length() > 20) {
            throw new BizException("AUTH-40017", "电话号码长度不能超过20个字符");
        }
        if (email != null && email.length() > 100) {
            throw new BizException("AUTH-40018", "邮箱长度不能超过100个字符");
        }
    }
}
