-- 奖励分配明细表 EVAL_REWARD_ITEM（2026-07-11）
-- 批次复用 EVAL_ASSIGN_BATCH（task_type=REWARD）；本表仅存奖励分配明细。
-- 目标库：yiti + onepl_test_bootstrap 手工执行。EVAL_IMP_REWARD 字典项已存在，无需新增。
CREATE TABLE IF NOT EXISTS EVAL_REWARD_ITEM (
    item_id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_id              BIGINT       NOT NULL COMMENT '所属批次(EVAL_ASSIGN_BATCH)',
    assign_user_id        VARCHAR(64)  NOT NULL COMMENT '分配人USER_ID(归一化后)',
    be_assigned_user_id   VARCHAR(64)  NOT NULL COMMENT '被分配人工号(原样快照,不校验)',
    be_assigned_user_name VARCHAR(128)          COMMENT '被分配人姓名(快照)',
    dept_name             VARCHAR(128) NOT NULL DEFAULT '' COMMENT '部门名称(分组键)',
    original_value        DECIMAL(18,4)         COMMENT '原始值(展示)',
    cash_value            DECIMAL(18,4)         COMMENT '兑现值(展示)',
    assign_total          DECIMAL(18,4) NOT NULL COMMENT '分配合计(组内一致,分配目标池)',
    assign_value          DECIMAL(18,4)         COMMENT '分配值(提交时填入)',
    submitted             TINYINT      NOT NULL DEFAULT 0 COMMENT '0未提交/1已提交',
    submit_time           DATETIME              COMMENT '提交时间',
    create_time           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (item_id),
    KEY idx_reward_assigner (assign_user_id, batch_id, dept_name, submitted),
    KEY idx_reward_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖励分配明细';
