package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.UserDirectoryApi;
import com.bank.branch.platform.auth.api.dto.UserDirectoryDTO;
import com.bank.branch.platform.auth.service.UserDirectoryService;
import com.bank.branch.platform.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户通讯录对外 API 实现。
 * <p>Facade 只负责跨模块契约转发，查询和当前用户安全边界由 Service 统一处理。</p>
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryFacade implements UserDirectoryApi {

    private final UserDirectoryService userDirectoryService;

    /** {@inheritDoc} */
    @Override
    public PageResult<UserDirectoryDTO> pageActiveUsers(String keyword, String orgCode,
                                                        int pageNo, int pageSize) {
        return userDirectoryService.pageActiveUsers(keyword, orgCode, pageNo, pageSize);
    }

    /** {@inheritDoc} */
    @Override
    public UserDirectoryDTO getEmployee(String userId) {
        return userDirectoryService.getEmployee(userId);
    }

    /** {@inheritDoc} */
    @Override
    public List<UserDirectoryDTO> getEmployeesByIds(List<String> userIds) {
        return userDirectoryService.getEmployeesByIds(userIds);
    }

    /** {@inheritDoc} */
    @Override
    public List<UserDirectoryDTO> searchEmployees(String keyword, int limit) {
        return userDirectoryService.searchEmployees(keyword, limit);
    }

    /** {@inheritDoc} */
    @Override
    public UserDirectoryDTO updateCurrentUserContact(String mobile, String email) {
        return userDirectoryService.updateCurrentUserContact(mobile, email);
    }
}
