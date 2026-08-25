# 真实后端验证（无 mock）

- 前端：`http://127.0.0.1:8092`
- 后端：`http://127.0.0.1:18082`
- profile：`screen-scope-e2e`，目标库为隔离测试库 `yiti_test`
- 屏：`9105 / SCR_RETAIL_OVERVIEW`
- 实际选择：`9002 / 机构核心指标(宽表)`
- 保存时间：`2026-08-24 12:46:19`
- traceId：`pc-1787546779266-436892`
- 结果：`ScreenCanvasAdminController.save` 返回 `code=0, message=success`，`canvasVersion=2`，生成 `blockId=29`。
- 后端日志：`bootstrap/target/runtime-logs/screen-save-fix-20260824.log`

保存后设计器状态显示“已保存”，未再出现 `RPT-43009 数据源配置非法`。

权限提示是另一条独立门禁：当前账号角色为 `BACK_FINANCE / SYS_ADMIN / R_2FAB45A1 / R_RE_REPORT`，屏白名单仅为 `R_SCREEN_RETAIL_VIEWER`，服务端以 `RPT-43017` 拒绝运行时取数。未修改任何角色配置。
