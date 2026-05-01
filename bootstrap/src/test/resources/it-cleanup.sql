-- ============================================================
-- IT 三 profile 共享库 cleanup（FU-25 抽出，2026-04-29）
-- 用途：onepl_test_bootstrap 库被 default / flowable-e2e / lead-e2e 三 profile 共用，
--      flowable-e2e 引入 PT_RESOURCE 100-105（/api/workflow/tasks/...）
--      lead-e2e 引入 PT_RESOURCE LR101-105（同一组 URL+METHOD），
--      uk_pt_resource_url_method_sys 唯一键互冲突。
--      之前是 flowable-e2e-data.sql 与 lead-e2e-data.sql 各自顶部加双向 DELETE 矩阵，
--      维护性差。本 SQL 抽出统一前置 cleanup。
-- 引入方式：application-flowable-e2e.yml / application-lead-e2e.yml 在
--          spring.sql.init.data-locations 中放在 data.sql 之前（第一个）。
-- DELETE 顺序：先删依赖（PT_ROLE_RESOURCE 引用 100-105/WRR/LRR）→ 再删主体 PT_RESOURCE。
-- ============================================================
DELETE FROM PT_ROLE_RESOURCE WHERE ID LIKE 'WRR%' OR ID LIKE 'LRR%'
    OR RESOURCE_ID IN ('100','101','102','103','104','105')
    OR RESOURCE_ID IN ('LR001','LR002','LR003','LR101','LR102','LR103','LR104','LR105');
DELETE FROM PT_RESOURCE WHERE RESOURCE_ID IN ('100','101','102','103','104','105')
    OR RESOURCE_ID IN ('LR001','LR002','LR003','LR101','LR102','LR103','LR104','LR105');

-- ====== FU-32（2026-04-29 加）：清掉 sys_job_conf 历史 J001/J002 测试数据 ======
-- 真因：onepl_test_bootstrap 历史保留的 J001(DAILY_REPORT) / J002(MONTHLY_PERF) 行经 V1.6
-- ALTER TABLE 后 quartz_job_class 字段填默认空字符串（NOT NULL DEFAULT ''），
-- V1.6 JobService.syncJobsOnStartup 期望非空类全限定名做 Class.forName，会报 ERROR：
--   ERROR JobService - [JobService.syncJobsOnStartup] jobKey=DAILY_REPORT 同步失败，跳过继续
-- 测试环境下这些是历史 stub 数据，不被任何 IT 引用，统一清掉避免日志噪音。
DELETE FROM SYS_JOB_CONF WHERE id IN ('J001', 'J002');
