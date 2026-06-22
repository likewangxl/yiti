-- 2026-06-18 通讯录数据范围收口：除系统管理员(ROLE_ID=1, SYS_ADMIN)外，
-- 所有角色的 ADDRBOOK 数据范围统一改为 SELF（只能编辑/查看本人），
-- 仅系统管理员保留 ALL（可编辑所有人）。
-- 背景：历史 seed 把几乎所有角色的 ADDRBOOK 都配成 ALL，导致普通用户也能编辑他人。
-- 权限判据走数据范围（resolveScope==ALL 即管理员），故此处只调数据、不改代码。

UPDATE PT_ROLE_BIZ_SCOPE
   SET DATA_SCOPE = 'SELF'
 WHERE BIZ_TYPE = 'ADDRBOOK'
   AND ROLE_ID <> 1;
