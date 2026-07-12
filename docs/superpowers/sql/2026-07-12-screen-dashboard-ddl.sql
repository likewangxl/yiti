-- 经营管理大屏 4 张配置表（2026-07-12）
-- 目标库：yiti + onepl_test_bootstrap 手工执行（root/djdev）
-- 幂等：DROP 后重建（首发无存量数据；后续结构变更须另写 ALTER 脚本，禁止重跑本脚本）

DROP TABLE IF EXISTS `RPT_SCREEN_DATASOURCE`;
CREATE TABLE `RPT_SCREEN_DATASOURCE` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `ds_code`         VARCHAR(64)  NOT NULL COMMENT '数据源编码（应用层保证 deleted=0 内唯一）',
  `ds_name`         VARCHAR(100) NOT NULL COMMENT '数据源名称',
  `ds_type`         VARCHAR(20)  NOT NULL COMMENT '能力标签：TIMESERIES 时序/SINGLE 单值',
  `source_kind`     VARCHAR(20)  NOT NULL COMMENT '来源：WIDE_TABLE/KPI_RESULT/CUSTOM_SQL',
  `config_json`     TEXT         NOT NULL COMMENT '类型化配置 JSON（三形态见 spec §5）',
  `time_param_json` VARCHAR(500) DEFAULT NULL COMMENT '允许的预设周期 JSON 数组，如 ["LATEST","LAST_10D"]',
  `status`          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `remark`          VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by`      VARCHAR(32)  DEFAULT NULL COMMENT '创建人工号',
  `created_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_scr_ds_code` (`ds_code`),
  KEY `idx_scr_ds_type` (`ds_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏数据源定义';

DROP TABLE IF EXISTS `RPT_SCREEN`;
CREATE TABLE `RPT_SCREEN` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_code`  VARCHAR(64)  NOT NULL COMMENT '大屏编码（应用层保证 deleted=0 内唯一）',
  `screen_name`  VARCHAR(100) NOT NULL COMMENT '大屏名称',
  `view_level`   VARCHAR(20)  NOT NULL COMMENT '视角：PROVINCE/BRANCH/PERSON',
  `theme_json`   VARCHAR(1000) DEFAULT NULL COMMENT '主题变量覆盖 JSON（一期留空）',
  `status`       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_by`   VARCHAR(32)  DEFAULT NULL COMMENT '创建人工号',
  `created_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否1是',
  PRIMARY KEY (`id`),
  KEY `idx_scr_code` (`screen_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏定义';

DROP TABLE IF EXISTS `RPT_SCREEN_BLOCK`;
CREATE TABLE `RPT_SCREEN_BLOCK` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_id`      BIGINT      NOT NULL COMMENT '所属大屏 RPT_SCREEN.id',
  `region`         VARCHAR(10) NOT NULL COMMENT '区域：LEFT/MAIN/RIGHT',
  `row_no`         INT         NOT NULL DEFAULT 1 COMMENT '区域内行号（从 1 起）',
  `col_no`         INT         NOT NULL DEFAULT 1 COMMENT '行内列号（从 1 起）',
  `width_pct`      INT         NOT NULL DEFAULT 100 COMMENT '行内宽度百分比 1~100',
  `height_pct`     INT         NOT NULL DEFAULT 100 COMMENT '区域内行高百分比 1~100（同行取首块值）',
  `component_type` VARCHAR(20) NOT NULL COMMENT 'METRIC_CARD/LINE_TREND/PIE_SHARE/RANK_LIST/FLOW_STATUS',
  `bind_json`      TEXT        NOT NULL COMMENT '数据绑定 JSON',
  `style_json`     TEXT        DEFAULT NULL COMMENT '样式 JSON',
  `drill_json`     TEXT        DEFAULT NULL COMMENT '钻取/跳转 JSON',
  `created_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_scr_block_screen` (`screen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏区块（布局+组件+绑定+钻取）';

DROP TABLE IF EXISTS `RPT_SCREEN_MAP_POINT`;
CREATE TABLE `RPT_SCREEN_MAP_POINT` (
  `id`                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `org_code`           VARCHAR(32)   NOT NULL COMMENT '支行机构号',
  `org_name`           VARCHAR(100)  NOT NULL COMMENT '支行名称',
  `lng`                DECIMAL(10,6) NOT NULL COMMENT '经度',
  `lat`                DECIMAL(10,6) NOT NULL COMMENT '纬度',
  `target_screen_code` VARCHAR(64)   DEFAULT 'SCR_BRANCH' COMMENT '点击跳转目标屏编码',
  `status`             VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `created_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scr_map_org` (`org_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='大屏地图支行点位';
