-- 2026-05-30 清理「待办」类通知（工作台通知模块不再显示待办信息）
-- 背景：TaskAssignmentListener 历史上对未被 isNotifySuppressed 命中的 bizType 仍发送
--      "待办：xxx" / "您有新的待办任务" 通知（notify_type=WORKFLOW），混入工作台「通知」模块。
--      现已在源头统一抑制（isNotifySuppressed 对所有 bizType 返回 true），此脚本清理库内存量待办通知。
-- 安全性：仅删待办类（title 前缀「待办」或「您有新的待办」）；审批结果类通知
--        （如「业绩调整审批 · 审批通过/驳回」「目标修正审批通过」等）title 不匹配，不受影响。
-- 备份：docs/superpowers/sql/backup/2026-05-30-user-notification-daiban-backup.sql（43 行，yiti）
-- 双库执行：dev(yiti) + 生产(onepl)。

DELETE FROM USER_NOTIFICATION
 WHERE notify_type = 'WORKFLOW'
   AND (title LIKE '待办%' OR title LIKE '您有新的待办%');
