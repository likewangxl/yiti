-- 大屏画布设计器 V2 —— RPT_SCREEN 双态字段 + 发布归档新表(2026-07-12)
-- 目标库:yiti + onepl_test_bootstrap 手工执行(root/djdev)。
-- 【严禁重跑既有基线 2026-07-12-screen-dashboard-ddl.sql】本脚本为增量 ALTER + CREATE。
-- 幂等策略:新表 CREATE TABLE IF NOT EXISTS(标准 MySQL 语法);ALTER ADD COLUMN 不支持列级 IF NOT
-- EXISTS(该写法为 MariaDB 扩展,MySQL 8.0.33 实测报 ERROR 1064 语法错误),故本脚本 ALTER 部分**不幂等**,
-- 严禁对同一库重复执行(重跑会因列已存在报 Duplicate column name 失败)。
--
-- ===== 正向变更 =====
ALTER TABLE `RPT_SCREEN`
  ADD COLUMN `canvas_style_json`     LONGTEXT     DEFAULT NULL COMMENT '画布全局样式JSON(设计基准/背景/适配策略/主题覆盖,schemaVersion)',
  ADD COLUMN `canvas_draft_json`     LONGTEXT     DEFAULT NULL COMMENT '编辑态组件树JSON(草稿,编辑器唯一读写对象)',
  ADD COLUMN `canvas_published_json` LONGTEXT     DEFAULT NULL COMMENT '发布态渲染包JSON=组件树+图表绑定快照,线上/预览只读它',
  ADD COLUMN `canvas_version`        INT          NOT NULL DEFAULT 0 COMMENT '真乐观锁:保存 WHERE canvas_version=? 并自增,冲突RPT-43012',
  ADD COLUMN `publish_status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0未发布/1已发布/2已发布但有未发布修改',
  ADD COLUMN `published_at`          DATETIME     DEFAULT NULL COMMENT '最近一次发布时间',
  ADD COLUMN `published_by`          VARCHAR(32)  DEFAULT NULL COMMENT '最近一次发布人工号';

CREATE TABLE IF NOT EXISTS `RPT_SCREEN_PUBLISH_LOG` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_id`     BIGINT      NOT NULL COMMENT '所属大屏 RPT_SCREEN.id',
  `snapshot_json` LONGTEXT    NOT NULL COMMENT '发布时的渲染包(CANVAS_PUBLISHED_JSON 全量)',
  `published_by`  VARCHAR(32) DEFAULT NULL COMMENT '发布人工号',
  `published_at`  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (`id`),
  KEY `idx_scr_pub_log_screen` (`screen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏发布归档(按屏滚动保留最近10次)';

-- ===== 回滚脚本(如需撤销本次变更,手工执行以下语句;生产慎用,会丢发布归档与草稿) =====
-- ALTER TABLE `RPT_SCREEN`
--   DROP COLUMN `canvas_style_json`, DROP COLUMN `canvas_draft_json`,
--   DROP COLUMN `canvas_published_json`, DROP COLUMN `canvas_version`,
--   DROP COLUMN `publish_status`, DROP COLUMN `published_at`, DROP COLUMN `published_by`;
-- DROP TABLE IF EXISTS `RPT_SCREEN_PUBLISH_LOG`;
