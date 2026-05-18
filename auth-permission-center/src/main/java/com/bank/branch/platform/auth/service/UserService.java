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
