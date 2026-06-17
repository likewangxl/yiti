package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.auth.mapper.UserDirectoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 用户通讯录查询服务 —— 基于 PT_USER + EXT_USER_ORG + EXT_ORG_INFO 三表联查，
 * 替代原 portal {@code ADDRBOOK_EMPLOYEE} 作为员工选择器/审批人选择的数据源。
 *
 * <p>仅承担只读查询；不改动原 portal 通讯录接口与表。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

    /** 搜索返回上限保护（与原通讯录搜索一致，前端默认 20，最大 50） */
    private static final int MAX_SEARCH_LIMIT = 50;

    private final UserDirectoryMapper userDirectoryMapper;

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
                keyword, safeLimit, result.size());
        return result;
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
}
