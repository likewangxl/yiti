package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.auth.entity.ExtOrgInfo;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.mapper.OrgMapper;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户信息 Facade 实现
 * <p>
 * 实现 UserApi，提供用户信息查询能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFacade implements UserApi {

    private final UserMapper userMapper;
    private final UserOrgMapper userOrgMapper;
    private final OrgMapper orgMapper;

    @Override
    public UserDTO getUserByEmpId(String empId) {
        if (empId == null || empId.isEmpty()) {
            return null;
        }
        PtUser user = userMapper.selectByUserId(empId);
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO();
        dto.setEmpId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getUserchnname());

        // 获取主机构信息
        var userOrg = userOrgMapper.selectByUserId(empId);
        if (userOrg != null) {
            dto.setMainOrgCode(userOrg.getOrgCode());
            ExtOrgInfo orgInfo = orgMapper.selectByOrgCode(userOrg.getOrgCode());
            if (orgInfo != null) {
                dto.setMainOrgName(orgInfo.getOrgName());
            }
        }

        return dto;
    }

    @Override
    public String getUserName(String empId) {
        PtUser user = userMapper.selectByUserId(empId);
        return user != null ? user.getUserchnname() : null;
    }

    @Override
    public List<UserDTO> getUserByEmpIds(List<String> empIds) {
        if (empIds == null || empIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<UserDTO> results = new ArrayList<>();
        for (String empId : empIds) {
            UserDTO dto = getUserByEmpId(empId);
            if (dto != null) {
                results.add(dto);
            }
        }
        return results;
    }
}
