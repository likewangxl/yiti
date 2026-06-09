-- =============================================================================
-- 2026-06-02 注册 /api/auth/switch-role 资源并授权全部角色（所有登录用户可切换角色）
-- -----------------------------------------------------------------------------
-- 背景：角色切换端点原仅靠 WebMvcAuthConfig.excludePathPatterns 放行（拦截器跳过 RBAC）。
--       改为正规 RBAC 资源：登记 PT_RESOURCE + 授权全部角色，并从拦截器 exclude 移除，
--       与 /api/auth/my-menus 等"已登录即可访问"的端点保持一致。
-- 执行：yiti(dev) + onepl(prod) 双库（已于 2026-06-02 应用）。
-- =============================================================================

-- 1. 登记资源（幂等）
INSERT INTO PT_RESOURCE
    (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_RANK_NO,
     ISMENU, MENU_ENDFLAG, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
    ('A_SWITCH_ROLE', '/api/auth/switch-role', 'POST', '切换当前角色', 0,
     0, 0, 0, 'PLATFORM', NOW(), 'admin', '角色切换，所有登录用户可访问')
ON DUPLICATE KEY UPDATE
    RESOURCE_URL = VALUES(RESOURCE_URL),
    RESOURCE_METHOD = VALUES(RESOURCE_METHOD),
    STATUS = 0;

-- 2. 授权全部角色（幂等，跳过已存在）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
SELECT REPLACE(UUID(), '-', ''), r.ROLE_ID, 'A_SWITCH_ROLE', 'PLATFORM', NOW()
FROM PT_ROLE r
WHERE NOT EXISTS (
    SELECT 1 FROM PT_ROLE_RESOURCE rr
    WHERE rr.ROLE_ID = r.ROLE_ID AND rr.RESOURCE_ID = 'A_SWITCH_ROLE'
);
