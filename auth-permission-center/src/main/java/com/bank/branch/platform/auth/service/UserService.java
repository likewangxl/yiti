package com.bank.branch.platform.auth.service;

import com.bank.branch.platform.auth.api.dto.UserCreateReqDTO;
import com.bank.branch.platform.auth.api.dto.UserDetailRespDTO;
import com.bank.branch.platform.auth.config.AuthUserProperties;
import com.bank.branch.platform.auth.entity.PtUser;
import com.bank.branch.platform.auth.enums.AuthErrorCode;
import com.bank.branch.platform.auth.mapper.UserMapper;
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
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthUserProperties props;

    /**
     * 新增用户。
     * 双重唯一性校验（userId + username），密码 BCrypt 加密。
     *
     * @param req      请求 DTO
     * @param operator 操作人工号
     * @throws BizException AUTH-40904 / AUTH-40905
     */
    public void create(UserCreateReqDTO req, String operator) {
        if (userMapper.selectByUserId(req.getUserId()) != null) {
            throw new BizException(AuthErrorCode.USER_ID_DUPLICATE.getCode(),
                    AuthErrorCode.USER_ID_DUPLICATE.getMessage());
        }
        if (userMapper.countByUsername(req.getUsername()) > 0) {
            throw new BizException(AuthErrorCode.USERNAME_DUPLICATE.getCode(),
                    AuthErrorCode.USERNAME_DUPLICATE.getMessage());
        }
        PtUser entity = new PtUser();
        entity.setUserId(req.getUserId());
        entity.setUsername(req.getUsername());
        entity.setUserchnname(req.getUserchnname());
        entity.setEmail(req.getEmail());
        entity.setRemark(req.getRemark());
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
        log.info("[UserService.create] 新增用户 userId={} username={} operator={}",
                entity.getUserId(), entity.getUsername(), operator);
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
        return toDetailDto(u);
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
        java.util.List<com.bank.branch.platform.auth.api.dto.UserListItemRespDTO> items =
                new java.util.ArrayList<>(records.size());
        for (PtUser u : records) items.add(toListItemDto(u));
        return com.bank.branch.platform.common.web.PageResult.of(pageNo, pageSize, total, items);
    }

    private com.bank.branch.platform.auth.api.dto.UserListItemRespDTO toListItemDto(PtUser u) {
        com.bank.branch.platform.auth.api.dto.UserListItemRespDTO dto =
                new com.bank.branch.platform.auth.api.dto.UserListItemRespDTO();
        dto.setUserId(u.getUserId());
        dto.setUsername(u.getUsername());
        dto.setUserchnname(u.getUserchnname());
        dto.setEmail(u.getEmail());
        dto.setRemark(u.getRemark());
        dto.setIsExpired(u.getIsExpired());
        dto.setIsLocked(u.getIsLocked());
        dto.setIsEnabled(u.getIsEnabled());
        dto.setCreateTime(u.getCreateTime());
        dto.setUpdateTime(u.getUpdateTime());
        return dto;
    }

    /** 修改用户基本信息（不改密码） */
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
        u.setUpdateTime(LocalDateTime.now());
        u.setUpdateAuthor(operator);
        userMapper.updateById(u);
        log.info("[UserService.update] userId={} operator={}", userId, operator);
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

    /** PtUser → UserDetailRespDTO（密码字段一律不映射） */
    private UserDetailRespDTO toDetailDto(PtUser u) {
        UserDetailRespDTO dto = new UserDetailRespDTO();
        dto.setUserId(u.getUserId());
        dto.setUsername(u.getUsername());
        dto.setUserchnname(u.getUserchnname());
        dto.setEmail(u.getEmail());
        dto.setRemark(u.getRemark());
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
