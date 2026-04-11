-- ============================================================================
-- 模块：业务申请中心 (business-application-center)
-- 描述：资产投放申请 (loan_apply) + 中场支持申请 (support_request)
-- 版本：V1
-- 更新日期：2026-04-10（从 docs/modules/business-application-center/05-表结构DDL.md 对齐）
-- ============================================================================
--
-- 重要说明：
-- 1. V1 版本仅包含 2 张主表：
--    - loan_apply        资产投放申请主表
--    - support_request   中场支持申请主表（支持多产品拆单 + 双视图 SUPPORT/SUPPORT_DEPT）
--
-- 2. 以下历史/辅助数据由其他模块统一管理，本模块不再单独建表：
--    - 附件关联        → system-governance-center.biz_file_rel (biz_type='LOAN'/'SUPPORT')
--    - 派单历史        → audit_log（当前态记录在 support_request 的 dispatch_* 字段）
--    - 审批历史        → Flowable ACT_HI_TASKINST / ACT_HI_VARINST / ACT_HI_ACTINST
--    - 异步导出任务    → system-governance-center.sys_async_task
--
-- 3. 所有表遵循 docs/common-dev-guide.md 的通用字段规范：
--    created_by / created_time / updated_by / updated_time / deleted
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. loan_apply — 资产投放申请表
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `loan_apply`;
CREATE TABLE `loan_apply` (
  `id`                        VARCHAR(32)    NOT NULL COMMENT '申请ID(UUID)',
  `apply_no`                  VARCHAR(100)   DEFAULT NULL COMMENT '申请编号(LA+yyyyMMdd+6位序号)',
  `cust_id`                   VARCHAR(32)    NOT NULL COMMENT '客户ID,逻辑外键→cust_master.id',
  `source_touch_task_id`      VARCHAR(32)    DEFAULT NULL COMMENT '来源触达任务ID,逻辑外键→touch_task.id',
  `project_type`              VARCHAR(32)    DEFAULT NULL COMMENT '项目类型(字典PROJECT_TYPE)',
  `biz_type`                  VARCHAR(32)    DEFAULT NULL COMMENT '业务类型(字典BIZ_TYPE)',
  `guarantee_type`            VARCHAR(32)    DEFAULT NULL COMMENT '担保方式(字典GUARANTEE_TYPE)',
  `credit_amount`             DECIMAL(20,4)  DEFAULT NULL COMMENT '授信金额(元,保留4位小数)',
  `credit_exposure_amount`    DECIMAL(20,4)  DEFAULT NULL COMMENT '敞口金额(元,保留4位小数)',
  `status`                    VARCHAR(20)    NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/IN_APPROVAL/COMPLETED/REJECTED/CANCELLED',
  `business_key`              VARCHAR(100)   DEFAULT NULL COMMENT '流程业务键,固定格式LOAN:{id}',
  `process_instance_id`       VARCHAR(64)    DEFAULT NULL COMMENT '流程实例ID,对应ACT_RU_EXECUTION/ACT_HI_PROCINST',
  `owner_org_id`              VARCHAR(50)    NOT NULL COMMENT '归属机构(ORG_CODE),用于数据范围过滤',
  `created_by`                VARCHAR(32)    NOT NULL COMMENT '创建人工号(PT_USER.emp_id)',
  `created_time`              DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`                VARCHAR(32)    DEFAULT NULL COMMENT '更新人工号',
  `updated_time`              DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除:0=未删,1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_process_inst` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产投放申请表';

-- ----------------------------------------------------------------------------
-- 2. support_request — 中场支持申请表
-- ----------------------------------------------------------------------------
-- 说明：
-- - 支持多产品拆单（同一提交批次按产品拆为多条记录，共享 submit_group_id）
-- - 支持双视图：SUPPORT（发起侧）与 SUPPORT_DEPT（承接侧）
-- - 场景 A（产品直达）：assigned_emp_id 为产品负责人，dispatch_emp_id 为 NULL
-- - 场景 B（部门承接）：先由秘书（dispatch_emp_id）派单给支持人员（assigned_emp_id）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `support_request`;
CREATE TABLE `support_request` (
  `id`                        VARCHAR(32)    NOT NULL COMMENT '申请ID(UUID)',
  `request_no`                VARCHAR(100)   DEFAULT NULL COMMENT '申请编号(SR+yyyyMMdd+6位序号)',
  `submit_group_id`           VARCHAR(64)    DEFAULT NULL COMMENT '同批提交分组ID(多产品拆单时同组共享)',
  `cust_id`                   VARCHAR(32)    NOT NULL COMMENT '客户ID,逻辑外键→cust_master.id',
  `source_touch_task_id`      VARCHAR(32)    DEFAULT NULL COMMENT '来源触达任务ID,逻辑外键→touch_task.id',
  `product_id`                VARCHAR(64)    DEFAULT NULL COMMENT '产品ID,逻辑外键→product_info.id',
  `support_dept_id`           VARCHAR(50)    DEFAULT NULL COMMENT '承接部门ORG_CODE,逻辑外键→EXT_ORG_INFO.org_code',
  `other_demand`              TEXT           DEFAULT NULL COMMENT '其他需求/补充说明',
  `dispatch_emp_id`           VARCHAR(32)    DEFAULT NULL COMMENT '派单人工号(部门秘书,仅场景B)',
  `dispatch_time`             DATETIME       DEFAULT NULL COMMENT '派单时间',
  `assigned_emp_id`           VARCHAR(32)    DEFAULT NULL COMMENT '承接办理人工号(场景A=产品负责人,场景B=秘书派单)',
  `status`                    VARCHAR(20)    NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/IN_APPROVAL/IN_PROGRESS/COMPLETED/REJECTED/CANCELLED',
  `business_key`              VARCHAR(100)   DEFAULT NULL COMMENT '流程业务键,固定格式SUPPORT:{id}',
  `process_instance_id`       VARCHAR(64)    DEFAULT NULL COMMENT '流程实例ID',
  `owner_org_id`              VARCHAR(50)    NOT NULL COMMENT '归属机构(发起侧ORG_CODE)',
  `created_by`                VARCHAR(32)    NOT NULL COMMENT '创建人工号(发起人)',
  `created_time`              DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by`                VARCHAR(32)    DEFAULT NULL COMMENT '更新人工号',
  `updated_time`              DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_cust_id` (`cust_id`),
  KEY `idx_support_dept` (`support_dept_id`),
  KEY `idx_assigned_emp` (`assigned_emp_id`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_status` (`status`),
  KEY `idx_business_key` (`business_key`),
  KEY `idx_submit_group` (`submit_group_id`),
  KEY `idx_owner_org` (`owner_org_id`),
  KEY `idx_created_time` (`created_time`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='中场支持申请表';

-- ============================================================================
-- 建表执行顺序：
-- 1) 先建 loan_apply（无外部依赖）
-- 2) 再建 support_request（无外部依赖）
-- 3) governance.biz_file_rel 由 governance 模块先建表（本模块的附件依赖它）
-- 4) workflow.ACT_* 由 Flowable 引擎启动时自动建表
-- ============================================================================
