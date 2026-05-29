-- 审批流程设计器模型表（workflow-center）。库表大写。
CREATE TABLE IF NOT EXISTS WF_FLOW_DEF (
  id varchar(32) NOT NULL PRIMARY KEY,
  flow_key varchar(64) NOT NULL,
  biz_type varchar(32) NOT NULL,
  name varchar(128) NOT NULL,
  description varchar(512) NULL,
  status varchar(16) NOT NULL DEFAULT 'DRAFT',
  version int NOT NULL DEFAULT 0,
  deployed_proc_def_key varchar(64) NULL,
  deployed_proc_def_id varchar(64) NULL,
  source_proc_def_key varchar(64) NULL,
  is_readonly_import tinyint NOT NULL DEFAULT 0,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by varchar(32) NULL,
  updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by varchar(32) NULL,
  UNIQUE KEY uk_flow_key (flow_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程设计器-流程定义';

CREATE TABLE IF NOT EXISTS WF_FLOW_NODE (
  id varchar(32) NOT NULL PRIMARY KEY,
  flow_def_id varchar(32) NOT NULL,
  node_key varchar(64) NOT NULL,
  node_type varchar(16) NOT NULL,
  name varchar(128) NULL,
  approve_mode varchar(8) NULL,
  sort_no int NOT NULL DEFAULT 0,
  pos_x int NULL, pos_y int NULL,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_node_flow (flow_def_id),
  UNIQUE KEY uk_flow_node (flow_def_id, node_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程设计器-节点';

CREATE TABLE IF NOT EXISTS WF_FLOW_NODE_APPROVER (
  id varchar(32) NOT NULL PRIMARY KEY,
  node_id varchar(32) NOT NULL,
  approver_type varchar(8) NOT NULL,
  approver_value varchar(64) NOT NULL,
  sort_no int NOT NULL DEFAULT 0,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_approver_node (node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程设计器-节点审批人规则';

CREATE TABLE IF NOT EXISTS WF_FLOW_EDGE (
  id varchar(32) NOT NULL PRIMARY KEY,
  flow_def_id varchar(32) NOT NULL,
  from_node_id varchar(32) NOT NULL,
  to_node_id varchar(32) NOT NULL,
  name varchar(128) NULL,
  is_default tinyint NOT NULL DEFAULT 0,
  condition_json text NULL,
  sort_no int NOT NULL DEFAULT 0,
  created_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_edge_flow (flow_def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程设计器-连线/分支';
