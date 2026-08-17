# yiti 大屏范围管理菜单登记验收

## 变更范围

- 目标库：`yiti`
- `PT_RESOURCE`：新增 `M_RPT_SCR_PROF`、`M_RPT_SCR_GRP`
- `PT_ROLE_RESOURCE`：新增上述两条菜单对 `SYS_ADMIN` 的授权
- 未修改机构组、机构画像、屏配置或其他业务数据

执行 SQL：`docs/superpowers/sql/2026-08-13-register-screen-scope-admin-menus.sql`

## 数据库结果

- 最终 `PT_RESOURCE` 目标行：2
- 最终 `PT_ROLE_RESOURCE` 目标授权：2
- 最终脚本重复执行后：两表总行数及 checksum 均保持不变
- 详细输出：`db-final-validation.txt`

执行前对两张受影响表做定向备份，并在隔离临时库恢复；恢复后的行数和 checksum 与执行前 `yiti` 一致。备份文件保存在受限临时目录，不提交仓库；清单见 `backup-manifest.txt`。

## 官方 Playwright CLI 验收

- 工具：项目内 `@playwright/cli 0.1.18`
- 视口：`1920x1080`
- 地址：`http://127.0.0.1:8091`
- 用户：`admin`
- mock：未注册；最终 `route-list` 为 `No active routes`
- `POST /api/auth/login`：200
- `GET /api/auth/my-menus`：200，响应含两个新增菜单
- `GET /api/admin/org-profiles`：200
- `GET /api/admin/org-groups`：200
- `GET /api/admin/roles/all?recordStatus=0`：200
- console error：0
- console warning：45，均为既有未注册路由提示，原始日志见 `console-raw.log`

证据文件：

- `requests-raw.txt`
- `route-list-final.txt`
- `my-menus-response.json`
- `org-groups-response.json`
- `console-raw.log`
- `01-admin-menu.png`
- `02-org-profiles.png`
- `03-org-groups.png`
