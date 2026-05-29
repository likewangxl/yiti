-- =============================================================
-- 人员评价设置表 EVAL_USER_SETTING（是否启用评价）
-- 模块: performance-engine-center / eval 子域
-- 日期: 2026-05-29
-- 语义: 表中无该工号记录 = 否（未启用）；eval_enabled=1 才视为启用
-- 执行: 在 yiti / onepl / onepl_test_bootstrap 三库手工执行（项目已废弃 Flyway）
-- 幂等: CREATE TABLE IF NOT EXISTS
-- =============================================================
CREATE TABLE IF NOT EXISTS EVAL_USER_SETTING (
    USER_ID       VARCHAR(50) NOT NULL COMMENT '工号，关联 PT_USER.USER_ID',
    EVAL_ENABLED  TINYINT     NOT NULL DEFAULT 0 COMMENT '是否启用评价：1=是 0=否',
    CREATED_TIME  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UPDATED_TIME  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    PRIMARY KEY (USER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员评价设置（是否启用评价）';
