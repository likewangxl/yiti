package com.bank.branch.platform.report.config;

import com.bank.branch.platform.report.support.SqlSafeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SQL 探查校验器配置（Task M4.2.1）.
 *
 * <p>把 {@link SqlSafeValidator} 注册为 Spring Bean，从配置文件读取白名单 / 禁用关键字 / 上限.
 * 默认值对齐 08 §1.1：
 * <ul>
 *   <li>12 张白名单表（CUST_MASTER / CUST_LEAD / CUST_TAG / touch_record / EMP_INDEX_RESULT /
 *       ORG_INDEX_RESULT / CUST_INDEX_RESULT / KPI_RESULT / metric_def / SYS_DICT / sys_dict_item /
 *       EXT_ORG_INFO / EXT_USER_ORG）</li>
 *   <li>30 个禁用关键字（DROP / DELETE / UPDATE / INSERT / TRUNCATE / ALTER 等）</li>
 *   <li>maxRows = 1000、maxSqlLength = 5000、maxSubqueryDepth = 3</li>
 * </ul>
 */
@Configuration
public class RptSqlSafeConfig {

    @Bean
    public SqlSafeValidator sqlSafeValidator(
            @Value("#{'${rpt.sql.probe.whitelist-tables:CUST_MASTER,CUST_LEAD,CUST_TAG,touch_record,EMP_INDEX_RESULT,ORG_INDEX_RESULT,CUST_INDEX_RESULT,KPI_RESULT,metric_def,SYS_DICT,sys_dict_item,EXT_ORG_INFO,EXT_USER_ORG}'.split(',')}") List<String> whitelistTables,
            @Value("#{'${rpt.sql.probe.forbidden-keywords:DROP,DELETE,UPDATE,INSERT,TRUNCATE,ALTER,CREATE,RENAME,REPLACE,GRANT,REVOKE,LOCK,UNLOCK,SET,CALL,EXEC,EXECUTE,LOAD,SHUTDOWN,USE,DESCRIBE,EXPLAIN,SHOW,COMMIT,ROLLBACK,SAVEPOINT,DECLARE,HANDLER,SIGNAL,RESIGNAL}'.split(',')}") List<String> forbiddenKeywords,
            @Value("${rpt.sql.probe.max-rows:1000}") int maxRows,
            @Value("${rpt.sql.probe.max-sql-length:5000}") int maxSqlLength,
            @Value("${rpt.sql.probe.max-subquery-depth:3}") int maxSubqueryDepth) {
        return new SqlSafeValidator(
                whitelistTables.stream().map(String::trim).toList(),
                forbiddenKeywords.stream().map(String::trim).toList(),
                maxRows,
                maxSqlLength,
                maxSubqueryDepth);
    }
}
