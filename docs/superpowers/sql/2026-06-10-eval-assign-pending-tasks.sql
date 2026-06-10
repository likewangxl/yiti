-- ============================================================
-- 待处理任务（导入式评价任务）：新表 + 字典 + 资源登记
-- 日期: 2026-06-10  模块: performance-engine-center / eval 子域
-- 目标库: yiti（开发）/ onepl_test_bootstrap（测试）；onepl 生产上线时按需执行
-- 幂等: 表用 IF NOT EXISTS；字典/资源用 INSERT IGNORE
-- ============================================================

-- ---------- 1. 新表 ----------
CREATE TABLE IF NOT EXISTS EVAL_ASSIGN_BATCH (
    BATCH_ID    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    BATCH_NAME  VARCHAR(200) DEFAULT NULL COMMENT '批次名称（可空）',
    TASK_TYPE   VARCHAR(50)  NOT NULL COMMENT '待处理任务类型 dict EVAL_IMPORT_TYPE: EVAL/REWARD',
    SOURCE      VARCHAR(50)  NOT NULL DEFAULT 'IMPORT' COMMENT '来源 dict EVAL_PENDING_SOURCE: IMPORT/AUTO',
    DEADLINE    DATETIME     NOT NULL COMMENT '打分截止时间',
    STATUS      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=进行中, 1=已结束',
    CREATE_BY   VARCHAR(50)  DEFAULT NULL COMMENT '创建人工号',
    CREATE_TIME DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (BATCH_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待处理任务批次（一次导入=一批）';

CREATE TABLE IF NOT EXISTS EVAL_ASSIGN_ITEM (
    ITEM_ID           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    BATCH_ID          BIGINT      NOT NULL COMMENT '所属批次',
    EVAL_USER_ID      VARCHAR(50) NOT NULL COMMENT '打分人工号',
    EVAL_USER_NAME    VARCHAR(100) DEFAULT NULL COMMENT '打分人姓名（导入快照）',
    EVAL_USER_TAG     VARCHAR(100) DEFAULT NULL COMMENT '打分人标签（导入快照）',
    EVAL_USER_DEPT    VARCHAR(200) DEFAULT NULL COMMENT '打分人部门（导入快照）',
    BE_EVAL_USER_ID   VARCHAR(50) NOT NULL COMMENT '被打分人工号',
    BE_EVAL_USER_NAME VARCHAR(100) DEFAULT NULL COMMENT '被打分人姓名（导入快照）',
    BE_EVAL_DEPT      VARCHAR(200) NOT NULL DEFAULT '' COMMENT '被打分人部门（导入快照，分组键，空存空串）',
    BE_EVAL_TAG       VARCHAR(100) DEFAULT NULL COMMENT '被打分人标签（导入快照）',
    WEIGHT_TAG        VARCHAR(100) DEFAULT NULL COMMENT '权重标签 dict EVAL_WEIGHT_TAG',
    SCORE_TYPE        VARCHAR(50) NOT NULL COMMENT '评价类型 dict EVAL_SCORE_TYPE: NUM/GRADE',
    SCORE             INT         DEFAULT NULL COMMENT '打分（提交后填入）',
    SUBMITTED         TINYINT     NOT NULL DEFAULT 0 COMMENT '0=未提交, 1=已提交',
    SUBMIT_TIME       DATETIME    DEFAULT NULL COMMENT '提交时间',
    PRIMARY KEY (ITEM_ID),
    UNIQUE KEY UK_BATCH_PAIR (BATCH_ID, EVAL_USER_ID, BE_EVAL_USER_ID),
    INDEX IDX_SCORER (EVAL_USER_ID, SUBMITTED),
    INDEX IDX_BATCH (BATCH_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待处理任务明细（打分人×被打分人 显式配对）';

-- ---------- 2. 字典 ----------
INSERT IGNORE INTO SYS_DICT (id, dict_type, dict_code, dict_label, dict_value, sort_order, status) VALUES
('EVAL_SRC_AUTO',    'EVAL_PENDING_SOURCE', 'AUTO',   '自动生成', 'AUTO',   1, 'ACTIVE'),
('EVAL_SRC_IMPORT',  'EVAL_PENDING_SOURCE', 'IMPORT', '手工导入', 'IMPORT', 2, 'ACTIVE'),
('EVAL_IMP_EVAL',    'EVAL_IMPORT_TYPE',    'EVAL',   '评价任务', 'EVAL',   1, 'ACTIVE'),
('EVAL_IMP_REWARD',  'EVAL_IMPORT_TYPE',    'REWARD', '奖励分配', 'REWARD', 2, 'ACTIVE'),
('EVAL_ST_NUM',      'EVAL_SCORE_TYPE',     'NUM',    '数值打分', 'NUM',    1, 'ACTIVE'),
('EVAL_ST_GRADE',    'EVAL_SCORE_TYPE',     'GRADE',  '等级打分', 'GRADE',  2, 'ACTIVE'),
-- 权重标签示例（用户可在「系统设置-字典」自行增删改）
('EVAL_WT_MAIN',     'EVAL_WEIGHT_TAG',     'MAIN',   '主要',     'MAIN',   1, 'ACTIVE'),
('EVAL_WT_MINOR',    'EVAL_WEIGHT_TAG',     'MINOR',  '次要',     'MINOR',  2, 'ACTIVE');

-- ---------- 3. PT_RESOURCE 资源登记（PERF_EVAL_23~27）----------
INSERT IGNORE INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, ISMENU, STATUS) VALUES
('PERF_EVAL_23', '/api/admin/eval/assign/import-template', 'GET',  '下载待处理任务导入模板', 0, 0),
('PERF_EVAL_24', '/api/admin/eval/assign/import',          'POST', '导入待处理任务',         0, 0),
('PERF_EVAL_25', '/api/eval/pending-tasks',                'GET',  '我的待处理任务汇总',     0, 0),
('PERF_EVAL_26', '/api/eval/pending-tasks/items',          'GET',  '待处理任务明细',         0, 0),
('PERF_EVAL_27', '/api/eval/pending-tasks/submit',         'POST', '提交待处理任务打分',     0, 0);

INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_ADMIN', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_23','PERF_EVAL_24','PERF_EVAL_25','PERF_EVAL_26','PERF_EVAL_27');

INSERT IGNORE INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID)
SELECT REPLACE(UUID(), '-', ''), 'R_BACK_TECH', RESOURCE_ID
FROM PT_RESOURCE WHERE RESOURCE_ID IN ('PERF_EVAL_23','PERF_EVAL_24','PERF_EVAL_25','PERF_EVAL_26','PERF_EVAL_27');
