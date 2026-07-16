package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.config.AuthUserProperties;
import com.bank.branch.platform.auth.entity.ExtUserOrg;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.UserMapper;
import com.bank.branch.platform.auth.mapper.UserOrgMapper;
import com.bank.branch.platform.common.web.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户管理服务。
 * <p>
 * 提供 CRUD、批量状态变更、密码重置/修改 12 个能力，模块内被 UserController 调用，
 * 不暴露到跨模块 *Api 层。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserOrgMapper userOrgMapper;
    private final com.bank.branch.platform.auth.mapper.UserRoleMapper userRoleMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthUserProperties props;

    /**
     * 导出全部用户（用户管理「导出」按钮）。忽略筛选条件，返回系统内所有用户，
     * 每个用户带上其在 PT_USER_ROLE 绑定的全部角色中文名（以「、」拼接）。
     *
     * <p>状态做正向语义转换：ISENABLED 0=启用/其它=停用，ISLOCKED 1=锁定/其它=正常。
     * 角色用 {@code selectRolesByUserIds} 批量查询（分块 IN，避免 N+1：内网 4000+ 用户逐个查会超时）。</p>
     */
    public java.util.List<com.bank.branch.platform.auth.controller.dto.UserExportRow> exportAllUsers() {
        com.bank.branch.platform.auth.api.dto.UserQueryReqDTO q =
                new com.bank.branch.platform.auth.api.dto.UserQueryReqDTO();
        long total = userMapper.countByQuery(q);
        int size = (int) Math.min(Math.max(total, 1L), 100000L);
        java.util.List<PtUser> users = userMapper.selectByQuery(q, 0, size);

        // 一次性批量取所有用户的角色名（分块 500/批，IN 列表不至于过大），按 userId 归并
        java.util.List<String> allIds = users.stream().map(PtUser::getUserId).toList();
        java.util.Map<String, java.util.List<String>> roleNameMap = new java.util.HashMap<>();
        final int CHUNK = 500;
        for (int i = 0; i < allIds.size(); i += CHUNK) {
            java.util.List<String> chunk = allIds.subList(i, Math.min(i + CHUNK, allIds.size()));
            for (com.bank.branch.platform.auth.api.dto.UserRoleItemDTO it : userRoleMapper.selectRolesByUserIds(chunk)) {
                if (it.getRoleChName() == null) continue;
                roleNameMap.computeIfAbsent(it.getUserId(), k -> new java.util.ArrayList<>()).add(it.getRoleChName());
            }
        }

        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        java.util.List<com.bank.branch.platform.auth.controller.dto.UserExportRow> rows =
                new java.util.ArrayList<>(users.size());
        for (PtUser u : users) {
            com.bank.branch.platform.auth.controller.dto.UserExportRow r =
                    new com.bank.branch.platform.auth.controller.dto.UserExportRow();
            r.setUsername(u.getUsername());
            r.setUserchnname(u.getUserchnname());
            r.setUserType(mapUserType(u.getUserType()));
            r.setStatus(u.getIsEnabled() != null && u.getIsEnabled() == 0 ? "启用" : "停用");
            r.setLocked(u.getIsLocked() != null && u.getIsLocked() == 1 ? "锁定" : "正常");
            java.util.List<String> rs = roleNameMap.get(u.getUserId());
            r.setRoles(rs == null ? "" : String.join("、", rs));
            r.setEmail(u.getEmail());
            r.setRemark(u.getRemark());
            r.setCreateTime(u.getCreateTime() != null ? fmt.format(u.getCreateTime()) : "");
            rows.add(r);
        }
        log.info("[UserService.exportAllUsers] 导出用户数={}", rows.size());
        return rows;
    }

    /** 用户类型字典 USER_TYPE：1-员工 / 2-虚拟员工，未知原样返回. */
    private String mapUserType(String userType) {
        if (userType == null) return "";
        return switch (userType) {
            case "1" -> "员工";
            case "2" -> "虚拟员工";
            default -> userType;
        };
    }

    /**
     * 新增用户。
     * 双重唯一性校验（userId + username），密码 BCrypt 加密。
     *
     * @param req      请求 DTO
     * @param operator 操作人工号
     * @throws BizException AUTH-40904 / AUTH-40905
     */
    @org.springframework.transaction.annotation.Transactional
    public void create(UserCreateReqDTO req, String operator) {
        // userId 可选：前端不传时默认与 username 一致
        String userId = req.getUserId();
        if (userId == null || userId.isBlank()) {
            userId = req.getUsername();
            req.setUserId(userId);
        }
        if (userMapper.selectByUserId(userId) != null) {
            throw new BizException(AuthErrorCode.USER_ID_DUPLICATE.getCode(),
                    AuthErrorCode.USER_ID_DUPLICATE.getMessage());
        }
        if (userMapper.countByUsername(req.getUsername()) > 0) {
            throw new BizException(AuthErrorCode.USERNAME_DUPLICATE.getCode(),
                    AuthErrorCode.USERNAME_DUPLICATE.getMessage());
        }
        PtUser entity = new PtUser();
        entity.setUserId(userId);
        entity.setUsername(req.getUsername());
        entity.setUserchnname(req.getUserchnname());
        entity.setEmail(req.getEmail());
        entity.setRemark(req.getRemark());
        entity.setUserType(req.getUserType());
        entity.setPwd(passwordEncoder.encode(req.getInitialPassword()));
        entity.setIsExpired(0);
        entity.setIsLocked(0);
        entity.setIsEnabled(0);
        entity.setPassWrongCount(0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setCreateAuthor(operator);
        entity.setPwdUpdateTime(now);
        userMapper.insert(entity);
        // 如果带了 orgCode，同步写 EXT_USER_ORG 单主机构关联
        if (req.getOrgCode() != null && !req.getOrgCode().isBlank()) {
            ExtUserOrg uo = new ExtUserOrg();
            uo.setUserId(req.getUserId());
            uo.setOrgCode(req.getOrgCode());
            userOrgMapper.insert(uo);
        }
        log.info("[UserService.create] 新增用户 userId={} username={} orgCode={} operator={}",
                entity.getUserId(), entity.getUsername(), req.getOrgCode(), operator);
    }

    /** 检查 username 是否已存在 */
    public boolean existsByUsername(String username) {
        return userMapper.countByUsername(username) > 0;
    }

    /** 按 userId 加载，找不到抛 USER_NOT_FOUND */
    public UserDetailRespDTO getById(String userId) {
        PtUser u = userMapper.selectByUserId(userId);
        if (u == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        UserDetailRespDTO dto = toDetailDto(u);
        // 反显主机构（V1 单主机构），编辑用户弹窗依赖此字段
        ExtUserOrg uo = userOrgMapper.selectByUserId(userId);
        if (uo != null) dto.setOrgCode(uo.getOrgCode());
        return dto;
    }

    /**
     * 分页查询用户列表。pageSize 上限 100、下限 1；pageNo 下限 1。
     */
    public com.bank.branch.platform.common.web.PageResult<
            com.bank.branch.platform.auth.api.dto.UserListItemRespDTO> pageUsers(
                    com.bank.branch.platform.auth.api.dto.UserQueryReqDTO q) {
        int pageNo = (q.getPageNo() == null || q.getPageNo() < 1) ? 1 : q.getPageNo();
        int pageSize = (q.getPageSize() == null || q.getPageSize() < 1) ? 20 : q.getPageSize();
        if (pageSize > 100) pageSize = 100;
        q.setPageNo(pageNo);
        q.setPageSize(pageSize);
        int offset = (pageNo - 1) * pageSize;
        java.util.List<PtUser> records = userMapper.selectByQuery(q, offset, pageSize);
        long total = userMapper.countByQuery(q);
        // 批量补「部门」列（EXT_USER_ORG ⋈ EXT_ORG_INFO 取 ORG_NAME），一次 IN 查询避免逐行 N+1
        java.util.Map<String, String> deptMap = loadDeptNameMap(records);
        java.util.List<com.bank.branch.platform.auth.api.dto.UserListItemRespDTO> items =
                new java.util.ArrayList<>(records.size());
        for (PtUser u : records) {
            com.bank.branch.platform.auth.api.dto.UserListItemRespDTO dto = toListItemDto(u);
            dto.setDeptName(deptMap.get(u.getUserId()));
            items.add(dto);
        }
        return com.bank.branch.platform.common.web.PageResult.of(pageNo, pageSize, total, items);
    }

    /** 批量查询用户部门名映射（userId → 聚合 ORG_NAME）；空列表不发 SQL */
    private java.util.Map<String, String> loadDeptNameMap(java.util.List<PtUser> users) {
        if (users == null || users.isEmpty()) return java.util.Map.of();
        java.util.List<String> ids = new java.util.ArrayList<>(users.size());
        for (PtUser u : users) ids.add(u.getUserId());
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (com.bank.branch.platform.auth.api.dto.UserDeptNameDTO r : userOrgMapper.selectDeptNamesByUserIds(ids)) {
            if (r.getUserId() != null) m.put(r.getUserId(), r.getDeptName());
        }
        return m;
    }

    private com.bank.branch.platform.auth.api.dto.UserListItemRespDTO toListItemDto(PtUser u) {
        com.bank.branch.platform.auth.api.dto.UserListItemRespDTO dto =
                new com.bank.branch.platform.auth.api.dto.UserListItemRespDTO();
        dto.setUserId(u.getUserId());
        dto.setUsername(u.getUsername());
        dto.setUserchnname(u.getUserchnname());
        dto.setEmail(u.getEmail());
        dto.setRemark(u.getRemark());
        dto.setUserType(u.getUserType());
        dto.setIsExpired(u.getIsExpired());
        dto.setIsLocked(u.getIsLocked());
        dto.setIsEnabled(u.getIsEnabled());
        dto.setCreateTime(u.getCreateTime());
        dto.setUpdateTime(u.getUpdateTime());
        return dto;
    }

    /** 修改用户基本信息（不改密码） */
    @org.springframework.transaction.annotation.Transactional
    public void update(String userId,
                       com.bank.branch.platform.auth.api.dto.UserUpdateReqDTO req,
                       String operator) {
        PtUser u = userMapper.selectByUserId(userId);
        if (u == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (req.getUsername() != null && !req.getUsername().isBlank()) u.setUsername(req.getUsername());
        if (req.getUserchnname() != null && !req.getUserchnname().isBlank()) u.setUserchnname(req.getUserchnname());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getRemark() != null) u.setRemark(req.getRemark());
        if (req.getUserType() != null) u.setUserType(req.getUserType());
        u.setUpdateTime(LocalDateTime.now());
        u.setUpdateAuthor(operator);
        userMapper.updateById(u);
        // 如果带了 orgCode，upsert EXT_USER_ORG：行在 → UPDATE，行不在 → INSERT
        if (req.getOrgCode() != null && !req.getOrgCode().isBlank()) {
            int affected = userOrgMapper.updateOrgCodeByUserId(userId, req.getOrgCode());
            if (affected == 0) {
                ExtUserOrg uo = new ExtUserOrg();
                uo.setUserId(userId);
                uo.setOrgCode(req.getOrgCode());
                userOrgMapper.insert(uo);
            }
        }
        log.info("[UserService.update] userId={} orgCode={} operator={}", userId, req.getOrgCode(), operator);
    }

    /** 批量物理删除 */
    public int deleteByIds(java.util.List<String> userIds) {
        validateIds(userIds);
        int n = userMapper.deleteByUserIds(userIds);
        log.info("[UserService.deleteByIds] affected={} ids={}", n, userIds);
        return n;
    }

    /** ids 列表合法性校验（空/超限/含空串） */
    private void validateIds(java.util.List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BizException(AuthErrorCode.INVALID_USER_IDS.getCode(),
                    AuthErrorCode.INVALID_USER_IDS.getMessage());
        }
        if (userIds.size() > props.getMaxBatchIds()) {
            throw new BizException(AuthErrorCode.INVALID_USER_IDS.getCode(),
                    AuthErrorCode.INVALID_USER_IDS.getMessage());
        }
        for (String id : userIds) {
            if (id == null || id.isBlank()) {
                throw new BizException(AuthErrorCode.INVALID_USER_IDS.getCode(),
                        AuthErrorCode.INVALID_USER_IDS.getMessage());
            }
        }
    }

    /** 管理员为指定用户设置新密码：不需校验旧密码，直接 BCrypt 加密后覆盖 */
    public void setPasswordByAdmin(String userId, String newPassword, String operator) {
        validateIds(java.util.Collections.singletonList(userId));
        com.bank.branch.platform.auth.entity.PtUser u = userMapper.selectByUserId(userId);
        if (u == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        String bcrypt = passwordEncoder.encode(newPassword);
        int affected = userMapper.updatePassword(userId, bcrypt, operator);
        log.info("[UserService.setPasswordByAdmin] affected={} userId={} operator={}", affected, userId, operator);
    }

    /** 批量重置密码为系统默认值（BCrypt 加密） */
    public int resetPassword(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        String bcrypt = passwordEncoder.encode(props.getDefaultPassword());
        int affected = 0;
        for (String id : userIds) {
            affected += userMapper.updatePassword(id, bcrypt, operator);
        }
        log.info("[UserService.resetPassword] affected={} ids={} operator={}", affected, userIds, operator);
        return affected;
    }

    /** 当前用户修改密码：校验旧密码 → BCrypt 加密新密码 → 更新 */
    public void changeMyPassword(String currentUserId,
                                 com.bank.branch.platform.auth.api.dto.ChangeMyPasswordReqDTO req) {
        PtUser u = userMapper.selectByUserId(currentUserId);
        if (u == null) {
            throw new BizException(AuthErrorCode.USER_NOT_FOUND.getCode(),
                    AuthErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (!passwordEncoder.matches(req.getOldPassword(), u.getPwd())) {
            throw new BizException(AuthErrorCode.OLD_PASSWORD_MISMATCH.getCode(),
                    AuthErrorCode.OLD_PASSWORD_MISMATCH.getMessage());
        }
        String bcrypt = passwordEncoder.encode(req.getNewPassword());
        userMapper.updatePassword(currentUserId, bcrypt, currentUserId);
        log.info("[UserService.changeMyPassword] userId={} 修改自己的密码", currentUserId);
    }

    /** 批量启用：ISENABLED=0 */
    public int batchActivate(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        int affected = 0;
        for (String id : userIds) affected += userMapper.updateActiveStatus(id, 0, operator);
        log.info("[UserService.batchActivate] affected={} ids={}", affected, userIds);
        return affected;
    }

    /** 批量禁用：ISENABLED=1 */
    public int batchInactivate(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        int affected = 0;
        for (String id : userIds) affected += userMapper.updateActiveStatus(id, 1, operator);
        log.info("[UserService.batchInactivate] affected={} ids={}", affected, userIds);
        return affected;
    }

    /** 批量锁定：ISLOCKED=1（复用已有 updateLockedStatus） */
    public int batchLock(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        int affected = 0;
        for (String id : userIds) affected += userMapper.updateLockedStatus(id, 1);
        log.info("[UserService.batchLock] affected={} ids={} operator={}", affected, userIds, operator);
        return affected;
    }

    /** 批量解锁：ISLOCKED=0 */
    public int batchUnlock(java.util.List<String> userIds, String operator) {
        validateIds(userIds);
        int affected = 0;
        for (String id : userIds) affected += userMapper.updateLockedStatus(id, 0);
        log.info("[UserService.batchUnlock] affected={} ids={} operator={}", affected, userIds, operator);
        return affected;
    }

    /** PtUser → UserDetailRespDTO（密码字段一律不映射） */
    private UserDetailRespDTO toDetailDto(PtUser u) {
        UserDetailRespDTO dto = new UserDetailRespDTO();
        dto.setUserId(u.getUserId());
        dto.setUsername(u.getUsername());
        dto.setUserchnname(u.getUserchnname());
        dto.setEmail(u.getEmail());
        dto.setRemark(u.getRemark());
        dto.setUserType(u.getUserType());
        dto.setIsExpired(u.getIsExpired());
        dto.setIsLocked(u.getIsLocked());
        dto.setIsEnabled(u.getIsEnabled());
        dto.setPassWrongCount(u.getPassWrongCount());
        dto.setCreateTime(u.getCreateTime());
        dto.setCreateAuthor(u.getCreateAuthor());
        dto.setUpdateTime(u.getUpdateTime());
        dto.setUpdateAuthor(u.getUpdateAuthor());
        dto.setPwdUpdateTime(u.getPwdUpdateTime());
        return dto;
    }
}
