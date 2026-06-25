-- 2026-06-24 删除分组父节点(M_GROUP_*)在 PT_ROLE_RESOURCE 中的错误授权。
-- 原因：分组节点(URL #group/*)只是菜单树的父容器，不是可授权的叶子菜单。
-- 前端"分配菜单"保存用 getCheckedKeys(true) 只回写叶子，父节点本不该入表；
-- 但菜单种子 SQL 误把分组父节点也插了进去。回显时 el-tree(check-strictly=false)
-- 见 default-checked-keys 含父节点 → 自动联动勾选其全部子菜单 →
-- 弹窗里看起来"所有子菜单都被授权"，且误保存会把整组子菜单写回。
-- 左侧导航不受影响：AuthService.pruneMenuTree 只要角色有任一子菜单即保留父分组。
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID LIKE 'M\_GROUP\_%';
