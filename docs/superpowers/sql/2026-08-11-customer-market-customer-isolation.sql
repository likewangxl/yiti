-- 客户营销主档与 M98 客户主档隔离
--
-- 1. CUST_MASTER 保持原用途：CUST_INFO_SYNC 每日把 M98 T-1 客户号/名称同步到该表。
-- 2. CUSTOMER_MARKET_CUSTOMER 专用于客户营销：线索审批落档、标签、认领、触达、跨机构营销和转交。
-- 3. 迁移保留原 id，确保 CUST_TAG_REL/CUST_CLAIM/TOUCH_TASK 等存量关联不失效。

CREATE TABLE IF NOT EXISTS `CUSTOMER_MARKET_CUSTOMER` (
  `id` varchar(32) NOT NULL COMMENT '客户营销客户ID',
  `cust_no` varchar(100) DEFAULT NULL COMMENT 'CCRM客户号；未开户潜客为空',
  `cust_name` varchar(200) NOT NULL COMMENT '客户名称',
  `unified_credit_code` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码(客户营销幂等键；迁移历史数据可为空)',
  `contact_person` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_mobile` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `industry` varchar(100) DEFAULT NULL COMMENT '所属行业',
  `group_type` varchar(50) DEFAULT NULL COMMENT '所属集团类型(字典)',
  `customer_type` varchar(50) DEFAULT NULL COMMENT '客户类型(字典)',
  `is_keystone` tinyint(1) DEFAULT NULL COMMENT '是否基石客户(1-是,0-否)',
  `enterprise_type` varchar(50) DEFAULT NULL COMMENT '企业类型(字典)',
  `group_name` varchar(200) DEFAULT NULL COMMENT '所属集团名称',
  `is_account_opened` tinyint(1) DEFAULT NULL COMMENT '是否开户(1-是,0-否)',
  `customer_desc` text COMMENT '客户说明',
  `credit_amount` decimal(20,4) DEFAULT NULL COMMENT '授信金额(元)',
  `credit_exposure_amount` decimal(20,4) DEFAULT NULL COMMENT '授信敞口金额(元)',
  `owner_org_id` varchar(50) DEFAULT NULL COMMENT '来源机构代码（不承载可见性）',
  `lead_id` varchar(32) DEFAULT NULL COMMENT '首次来源线索ID',
  `current_lead_id` varchar(32) DEFAULT NULL COMMENT '当前生效的审批通过线索版本ID',
  `main_manager_id` varchar(32) DEFAULT NULL COMMENT '当前主办客户经理工号',
  `main_org_id` varchar(50) DEFAULT NULL COMMENT '当前主办客户经理机构代码',
  `ownership_status` varchar(30) NOT NULL DEFAULT 'UNASSIGNED' COMMENT '主办状态',
  `last_touch_time` datetime DEFAULT NULL COMMENT '最近一次有效触达时间',
  `source_system` varchar(32) NOT NULL DEFAULT 'LOCAL' COMMENT '客户营销主数据来源',
  `source_updated_time` datetime DEFAULT NULL COMMENT '源系统最后更新时间',
  `status` varchar(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记',
  `created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `lock_version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_market_cust_no` (`cust_no`),
  UNIQUE KEY `uk_customer_market_credit_code` (`unified_credit_code`),
  KEY `idx_customer_market_name` (`cust_name`),
  KEY `idx_customer_market_lead` (`lead_id`),
  KEY `idx_customer_market_current_lead` (`current_lead_id`),
  KEY `idx_customer_market_manager` (`main_manager_id`),
  KEY `idx_customer_market_org` (`main_org_id`),
  KEY `idx_customer_market_opened_manager` (`deleted`, `is_account_opened`, `main_manager_id`, `updated_time`),
  KEY `idx_customer_market_opened_org` (`deleted`, `is_account_opened`, `main_org_id`, `updated_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='客户营销主档表（不承接M98同步）';

-- CUST_MASTER.source_system 历史上有默认 LOCAL，不能用它识别营销客户。
-- 仅迁移已有统一社会信用代码，或已被客户营销业务表引用的客户。
INSERT INTO `CUSTOMER_MARKET_CUSTOMER` (
  id, cust_no, cust_name, unified_credit_code, contact_person, contact_mobile,
  industry, group_type, customer_type, is_keystone, enterprise_type, group_name,
  is_account_opened, customer_desc, credit_amount, credit_exposure_amount,
  owner_org_id, lead_id, current_lead_id, main_manager_id, main_org_id,
  ownership_status, last_touch_time, source_system, source_updated_time,
  status, deleted, created_time, updated_time, lock_version
)
SELECT
  m.id, m.cust_no, m.cust_name, m.unified_credit_code, m.contact_person, m.contact_mobile,
  m.industry, m.group_type, m.customer_type, m.is_keystone, m.enterprise_type, m.group_name,
  m.is_account_opened, m.customer_desc, m.credit_amount, m.credit_exposure_amount,
  m.owner_org_id, m.lead_id, m.current_lead_id, m.main_manager_id, m.main_org_id,
  COALESCE(m.ownership_status, 'UNASSIGNED'), m.last_touch_time, COALESCE(m.source_system, 'LOCAL'),
  m.source_updated_time, m.status, m.deleted, m.created_time, m.updated_time, COALESCE(m.lock_version, 0)
FROM `CUST_MASTER` m
WHERE (m.unified_credit_code IS NOT NULL AND m.unified_credit_code <> '')
   OR m.lead_id IS NOT NULL
   OR m.current_lead_id IS NOT NULL
   OR EXISTS (
        SELECT 1 FROM CUST_LEAD l
        WHERE CONVERT(l.source_cust_id USING utf8mb4) COLLATE utf8mb4_general_ci
                  = CONVERT(m.id USING utf8mb4) COLLATE utf8mb4_general_ci
           OR CONVERT(l.id USING utf8mb4) COLLATE utf8mb4_general_ci
                  = CONVERT(m.lead_id USING utf8mb4) COLLATE utf8mb4_general_ci
   )
   OR EXISTS (SELECT 1 FROM CUST_TAG_REL r
              WHERE CONVERT(r.cust_id USING utf8mb4) COLLATE utf8mb4_general_ci
                    = CONVERT(m.id USING utf8mb4) COLLATE utf8mb4_general_ci)
   OR EXISTS (SELECT 1 FROM CUST_CLAIM c
              WHERE CONVERT(c.cust_id USING utf8mb4) COLLATE utf8mb4_general_ci
                    = CONVERT(m.id USING utf8mb4) COLLATE utf8mb4_general_ci)
   OR EXISTS (SELECT 1 FROM TOUCH_TASK t
              WHERE CONVERT(t.cust_id USING utf8mb4) COLLATE utf8mb4_general_ci
                    = CONVERT(m.id USING utf8mb4) COLLATE utf8mb4_general_ci)
   OR EXISTS (SELECT 1 FROM CROSS_ORG_MARKETING_APPLY a
              WHERE CONVERT(a.cust_id USING utf8mb4) COLLATE utf8mb4_general_ci
                    = CONVERT(m.id USING utf8mb4) COLLATE utf8mb4_general_ci)
   OR EXISTS (SELECT 1 FROM CUST_TRANSFER_LOG x
              WHERE CONVERT(x.cust_id USING utf8mb4) COLLATE utf8mb4_general_ci
                    = CONVERT(m.id USING utf8mb4) COLLATE utf8mb4_general_ci)
ON DUPLICATE KEY UPDATE
  cust_name = VALUES(cust_name),
  unified_credit_code = VALUES(unified_credit_code),
  contact_person = VALUES(contact_person),
  contact_mobile = VALUES(contact_mobile),
  updated_time = VALUES(updated_time);

-- 校验：M98 表仍应保留全量；营销表只包含营销客户。
SELECT COUNT(*) AS m98_customer_count FROM CUST_MASTER;
SELECT COUNT(*) AS customer_market_count FROM CUSTOMER_MARKET_CUSTOMER;
