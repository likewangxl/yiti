-- ==========================================================================================
-- 红色引擎演示数据导入脚本（仅 yiti_test 验收基准用，2026-07-18，Task 16）
-- 【文件名 -only 后缀明示】仅允许在 yiti_test 执行；严禁进 onepl_test_bootstrap
--   （该库是 IT 集成测试库，须保持干净，三张目标表恒为 0 行）；严禁随正式上线，
--   严禁对 yiti 生产库执行任何写操作。
--
-- 数据来源：redengine/red_engine.db（SQLite，只读，本次导入未对其做任何修改）
--   biz_submit(16 行) / biz_score(16 行) / biz_overdue_deduction(4 行)
--   （biz_submit_file 核实为 0 行，源库本无附件数据可带）
--
-- 列映射与取值口径（逐列核对目标 DDL：docs/superpowers/sql/2026-07-18-redengine-tables.sql）：
--   RE_SUBMIT.id / RE_SCORE.id / RE_OVERDUE_DEDUCTION.id  沿用 SQLite 源自增 id（显式主键，幂等基础）
--   org_id            沿用源库 org_id(2~5)，对齐 Task 4 RE_PARTY_ORG 自增 id(已核实 yiti_test 现有
--                     10 行 id=1~10；2~5 对应 高新/经开/曲江/碑林支行党支部，均为 org_level=2)
--   submitter_id      统一置 'admin'（源库 created_by 是 SQLite 侧整数用户 id，平台无对应工号，
--                     按任务简报要求替换为占位工号 'admin'）
--   dimension         源 biz_submit 表本身无此列；按 (org_id, item_code, year) 与 biz_score 表
--                     1:1 关联后带出 biz_score.dimension 真实值('dim_clean')；该值与 Task 4 落库
--                     的 RE_DIMENSION 字典(dim1~dim4)不同源体系，原样带入，不做字典对齐/翻译
--   item_code         原样带入源值(CLEAN_PROJECT/SYSTEM_PROJECT/TEAM_PROJECT/CULTURE_PROJECT)，
--                     与 Task 4 RE_ITEM_CODE 字典(1.1/1.2/.../sup)不同源体系，同上不做转换
--   max_score         原样带入源值
--   submit_date       取源 created_at 的日期部分（源库无独立 submit_date 字段）
--   status            源 submit_status='submitted' 全量 → RE_SUBMIT.status=1（对照目标 DDL 注释：
--                     0草稿/1已提交/2已通过/3已驳回）
--   form_data         原样带入源 JSON 字符串
--   file_urls         源库 biz_submit_file 核实为 0 行，无附件数据可带，本次全部落 NULL
--   item_name/project_name/submit_type/review_feedback/reviewer_id/review_date
--                     源库均无对应字段，不做臆造，落 NULL
--   create_time/update_time  取源 created_at/updated_at（忠实源值，非落库时刻）
--
--   RE_SCORE.submit_id  源 biz_score 本身无 submit_id 列；按 (org_id, item_code, year) 与
--                       biz_submit 表关联求得（已程序校验：该关联在 16 行数据中唯一且
--                       score.id 与关联到的 submit.id 恒相等）
--   RE_SCORE.score_year       取源 year
--   RE_SCORE.score_period     源库无月度粒度数据，不臆造 YYYY-MM，落 NULL
--   RE_SCORE.base_score       显式落 100.00（DDL 默认基准分，源库无对应覆盖值）
--   RE_SCORE.deduction_score  显式落 0.00（逾期扣分独立记录于 RE_OVERDUE_DEDUCTION，不在此重复计）
--   RE_SCORE.final_score      原样带入源 score 值
--
--   RE_OVERDUE_DEDUCTION.submit_id   源库该表无上报维度关联，落 NULL
--   RE_OVERDUE_DEDUCTION.deduction_points  原样带入源 deduction_score 值
--   RE_OVERDUE_DEDUCTION.deduction_date    取源 created_at 的日期部分（源库无独立扣分日期字段）
--   RE_OVERDUE_DEDUCTION.remark    源库 status='pending' 且 executed_at=NULL，表示该笔扣分
--                       "尚未执行"，语义对应驾驶舱"执行逾期扣分"(P_RE_CKPT_EXEC)的待执行队列；
--                       目标 DDL 无 status/executed_at 列，该信息落 remark 保留，避免静默丢失
--
-- 幂等设计：显式主键 id（沿用源库自增 id）+ INSERT IGNORE。执行前已核实 yiti_test 三张目标表
--   均为 0 行，显式 id 落库天然与既有数据无冲突；重放时 INSERT IGNORE 命中主键冲突自动跳过，
--   计数保持不变，无需 DELETE-then-insert，不存在误删他人数据的风险。
-- ==========================================================================================

-- 1) RE_SUBMIT（16 行，源 biz_submit）
INSERT IGNORE INTO RE_SUBMIT
  (id, org_id, submitter_id, dimension, item_code, item_name, max_score, project_name,
   submit_type, submit_date, status, form_data, file_urls, review_feedback, reviewer_id,
   review_date, deleted, create_time, update_time)
VALUES
  (1, 2, 'admin', 'dim_clean', 'CLEAN_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (2, 2, 'admin', 'dim_clean', 'SYSTEM_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (3, 2, 'admin', 'dim_clean', 'TEAM_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (4, 2, 'admin', 'dim_clean', 'CULTURE_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (5, 3, 'admin', 'dim_clean', 'CLEAN_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (6, 3, 'admin', 'dim_clean', 'SYSTEM_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (7, 3, 'admin', 'dim_clean', 'TEAM_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (8, 3, 'admin', 'dim_clean', 'CULTURE_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (9, 4, 'admin', 'dim_clean', 'CLEAN_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (10, 4, 'admin', 'dim_clean', 'SYSTEM_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (11, 4, 'admin', 'dim_clean', 'TEAM_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (12, 4, 'admin', 'dim_clean', 'CULTURE_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (13, 5, 'admin', 'dim_clean', 'CLEAN_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (14, 5, 'admin', 'dim_clean', 'SYSTEM_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (15, 5, 'admin', 'dim_clean', 'TEAM_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (16, 5, 'admin', 'dim_clean', 'CULTURE_PROJECT', NULL, 25.0, NULL, NULL, '2026-05-16', 1, '{"data": "test"}', NULL, NULL, NULL, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54');

-- 2) RE_SCORE（16 行，源 biz_score）
INSERT IGNORE INTO RE_SCORE
  (id, org_id, submit_id, item_code, score_year, score_period, base_score,
   deduction_score, final_score, remark, deleted, create_time, update_time)
VALUES
  (1, 2, 1, 'CLEAN_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (2, 2, 2, 'SYSTEM_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (3, 2, 3, 'TEAM_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (4, 2, 4, 'CULTURE_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (5, 3, 5, 'CLEAN_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (6, 3, 6, 'SYSTEM_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (7, 3, 7, 'TEAM_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (8, 3, 8, 'CULTURE_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (9, 4, 9, 'CLEAN_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (10, 4, 10, 'SYSTEM_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (11, 4, 11, 'TEAM_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (12, 4, 12, 'CULTURE_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (13, 5, 13, 'CLEAN_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (14, 5, 14, 'SYSTEM_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (15, 5, 15, 'TEAM_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (16, 5, 16, 'CULTURE_PROJECT', 2026, NULL, 100.0, 0.0, 15.0, NULL, 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54');

-- 3) RE_OVERDUE_DEDUCTION（4 行，源 biz_overdue_deduction）
INSERT IGNORE INTO RE_OVERDUE_DEDUCTION
  (id, org_id, submit_id, deduction_reason, deduction_points, deduction_date,
   remark, deleted, create_time, update_time)
VALUES
  (1, 2, NULL, NULL, 2.0, '2026-05-16', '源库 status=pending，executed_at=NULL，表示尚未执行（对应驾驶舱"执行逾期扣分"待执行队列语义），year=2026', 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (2, 3, NULL, NULL, 2.0, '2026-05-16', '源库 status=pending，executed_at=NULL，表示尚未执行（对应驾驶舱"执行逾期扣分"待执行队列语义），year=2026', 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (3, 4, NULL, NULL, 2.0, '2026-05-16', '源库 status=pending，executed_at=NULL，表示尚未执行（对应驾驶舱"执行逾期扣分"待执行队列语义），year=2026', 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54'),
  (4, 5, NULL, NULL, 2.0, '2026-05-16', '源库 status=pending，executed_at=NULL，表示尚未执行（对应驾驶舱"执行逾期扣分"待执行队列语义），year=2026', 0, '2026-05-16 15:49:54', '2026-05-16 15:49:54');

