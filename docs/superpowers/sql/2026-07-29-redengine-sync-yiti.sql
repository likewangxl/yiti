-- ============================================================================
-- 红色引擎正式同步到 yiti
-- 日期：2026-07-29
--
-- 授权背景：
--   2026-07-29 用户明确要求将红色引擎相关数据库调整同步到 yiti。本脚本是一次正式
--   上线编排，针对本次执行解除 2026-07-18 基础表、正式种子脚本原有的“仅测试库”
--   执行范围限制；历史脚本本身保持不变，便于保留原始审计记录。
--
-- 安全约束：
--   1. 必须先完整备份 yiti；
--   2. 必须从项目根目录通过 mysql 客户端执行，以便 SOURCE 相对路径正确解析；
--   3. 显式 USE yiti，禁止通过默认库名隐式决定写入目标；
--   4. 只同步基础表、正式权限/字典/组织种子和平台菜单；
--   5. 明确不加载任何 yiti_test 专用演示业务数据；
--   6. 下游脚本均为 IF NOT EXISTS / INSERT IGNORE / 精确 UPDATE，可重复执行。
--
-- 推荐命令：
--   mysql -h127.0.0.1 -P3306 -uroot -p < docs/superpowers/sql/2026-07-29-redengine-sync-yiti.sql
-- ============================================================================

USE yiti;

SOURCE docs/superpowers/sql/2026-07-18-redengine-tables.sql;
SOURCE docs/superpowers/sql/2026-07-18-redengine-seed.sql;
SOURCE docs/superpowers/sql/2026-07-29-redengine-platform-menu-align.sql;
