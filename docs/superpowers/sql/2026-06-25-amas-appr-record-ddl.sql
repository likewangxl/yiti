-- =====================================================================
-- AMAS_APPR_RECORD 审批流程流转记录表（业绩调整查询 / 业绩分配查询 详情的审批列表来源）
-- REGION_DT_ID 关联 AMAS_PERF_ADJUST_APPROVAL.PERF_ADJUST_NO，按 APPR_SEQ 倒序展示。
-- 字段与 report-analytics-center 实体 AmasApprRecord 一致（map-underscore-to-camel-case）。
-- 该表此前仅有 Java 实体/Mapper，DB 缺表导致审批流程恒为空——本脚本补建。
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。需在 yiti（dev）与 onepl（prod）双库执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `AMAS_APPR_RECORD` (
  `RECORD_ID`      varchar(50)   NOT NULL                COMMENT '记录编号(主键)',
  `MODULE_ID`      varchar(50)   DEFAULT NULL            COMMENT '模块编号',
  `REGION_DT_ID`   varchar(50)   DEFAULT NULL            COMMENT '源数据编号(关联 PERF_ADJUST_NO)',
  `APPR_NAME`      varchar(100)  DEFAULT NULL            COMMENT '审批名称(节点名)',
  `APPR_SEQ`       bigint        DEFAULT NULL            COMMENT '序号',
  `CONF_TYPE`      varchar(2)    DEFAULT NULL            COMMENT '类型:1,判断;2,审批',
  `IS_MULTI_APPR`  varchar(2)    DEFAULT NULL            COMMENT '是否多人审批:1,是;2,否',
  `CREATE_TIME`    varchar(50)   DEFAULT NULL            COMMENT '创建时间',
  `APPR_ROLE`      bigint        DEFAULT NULL            COMMENT '审批角色',
  `APPR_USERNAME`  varchar(20)   DEFAULT NULL            COMMENT '审批人工号',
  `APPR_FULLNAME`  varchar(50)   DEFAULT NULL            COMMENT '审批人姓名',
  `APPR_TIME`      varchar(50)   DEFAULT NULL            COMMENT '审批时间',
  `APPR_STATUS`    varchar(2)    DEFAULT NULL            COMMENT '审批状态:0,待审批;1,通过;2,未通过',
  `APPR_OPINION`   varchar(1024) DEFAULT NULL            COMMENT '审批意见',
  `IS_SYS_APPR`    varchar(2)    DEFAULT NULL            COMMENT '是否系统执行:1,是;2,否',
  `SYS_MESSAGE`    varchar(255)  DEFAULT NULL            COMMENT '系统备注',
  `SKIP_STEP`      varchar(50)   DEFAULT NULL            COMMENT '跳过步骤',
  PRIMARY KEY (`RECORD_ID`),
  KEY `idx_REGION_DT_ID` (`REGION_DT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批流程流转记录表';
