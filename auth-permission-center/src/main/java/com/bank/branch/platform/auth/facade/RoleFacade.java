package com.bank.branch.platform.auth.facade;

import com.bank.branch.platform.auth.api.RoleApi;
import com.bank.branch.platform.auth.api.dto.RoleRespDTO;
import com.bank.branch.platform.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 角色查询 Facade，实现 {@link RoleApi}，委托 {@link RoleService}.
 *
 * <p>{@link #listEnabledRoles()} 取 RECORD_STATUS=0（可用）角色，按角色名称中文排序后返回。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleFacade implements RoleApi {

    /** 可用角色的 RECORD_STATUS 值（0=可用）. */
    private static final int RECORD_STATUS_ENABLED = 0;

    private final RoleService roleService;

    @Override
    public List<RoleRespDTO> listEnabledRoles() {
        List<RoleRespDTO> roles = roleService.listAll(RECORD_STATUS_ENABLED);
        if (roles == null) {
            return List.of();
        }
        // 按角色名称（中文）升序；名称为空排在最后
        Collator zh = Collator.getInstance(Locale.CHINA);
        return roles.stream()
                .sorted(Comparator.comparing(
                        r -> r.getRoleChName() == null ? "" : r.getRoleChName(), zh))
                .collect(Collectors.toList());
    }
}
