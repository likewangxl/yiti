package com.bank.branch.platform.performance.enums;

/**
 * performance-engine-center 模块错误码定义.
 *
 * <p>格式: PERF-{HTTP_STATUS}{SEQ}
 * <p>权威来源: docs/modules/performance-engine-center/03-接口设计与报文.md §K「错误码完整汇总」
 * <p>使用: throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
 *
 * <p>§K 共 33 条编码，METRIC_SLOT_CONFLICT 复用 PERF-40901，枚举常量总数 = 34。
 * <p>2026-04-22 对齐 §K 权威清单，废弃旧编号体系（40401/40402/40403/40406/40407/
 *    40903/40904/40905/40911/40912/40913/40914）。
 * <p>2026-04-23（V1.1 P8.1）新增 3 条语义细化编号：40005/40006/40007，
 *    替代 V1.0 整改期临时复用的 METRIC_CODE_DUP/METRIC_NOT_FOUND（KPI/Target/RunTask 场景）。
 * <p>2026-04-24（V1.3 R3.1）新增 1 条：50003 IDEMPOTENCY_WAIT_TIMEOUT，拆分
 *    DataTaskService 幂等等待超时语义（原复用 CALC_JOB_FAILED 语义不清）。
 * <p>2026-04-30（V1.7 P2）新增 4 条：METRIC_CALC_FREQ_INVALID/METRIC_SUBJECT_SQL_REQUIRED/METRIC_SUBJECT_SQL_FAILED/KPI_CYCLE_TYPE_INVALID
 */
public enum PerfErrorCode {

    // =============================================
    // K.1 400xx 参数错误 / 资源不存在
    // =============================================

    /** 指标不存在 */
    METRIC_NOT_FOUND("PERF-40001", "指标不存在"),

    /** bizKind 参数无效 */
    BIZ_KIND_INVALID("PERF-40002", "bizKind 参数无效"),

    /** KPI 方案不存在 */
    KPI_SCHEME_NOT_FOUND("PERF-40003", "KPI 方案不存在"),

    /** 目标方案不存在 */
    TARGET_PLAN_NOT_FOUND("PERF-40004", "目标方案不存在"),

    /** KPI 方案编码已存在（V1.1 P8.1 新增，替代 KpiSchemeService.create 场景 METRIC_CODE_DUP 的错位复用） */
    KPI_SCHEME_CODE_EXISTS("PERF-40005", "KPI 方案编码已存在"),

    /** 目标方案编码已存在（V1.1 P8.1 新增，替代 TargetPlanService.create 场景 METRIC_CODE_DUP 的错位复用） */
    TARGET_PLAN_CODE_EXISTS("PERF-40006", "目标方案编码已存在"),

    /** 执行任务不存在（V1.1 P8.1 新增，替代 PerfRunTaskController.getById 场景 METRIC_NOT_FOUND 的错位复用） */
    RUN_TASK_NOT_FOUND("PERF-40007", "执行任务不存在"),

    /** sys_control 版本不存在 */
    SYS_CONTROL_VERSION_NOT_FOUND("PERF-40012", "sys_control 版本不存在"),

    /** 分配关系记录不存在 */
    ALLOC_RELATION_NOT_FOUND("PERF-40014", "分配关系记录不存在"),

    /** 导入批次不存在（V1.1 占位） */
    IMPORT_BATCH_NOT_FOUND("PERF-40017", "导入批次不存在"),

    /** 周期参数不合法（V1.1 占位） */
    CYCLE_PARAM_INVALID("PERF-40019", "周期参数不合法"),

    /** 目标修正申请不存在（V1.2 占位） */
    TARGET_ADJUST_APPLY_NOT_FOUND("PERF-40020", "目标修正申请不存在"),

    /** V1.7：calc_freq 非法（不在 DAY/WEEK/MONTH/QUARTER/YEAR 内）. */
    METRIC_CALC_FREQ_INVALID("PERF-40021", "calc_freq 非法"),

    /** V1.7：EXPR/GROOVY 类型 subject_sql 必填. */
    METRIC_SUBJECT_SQL_REQUIRED("PERF-40022", "EXPR/GROOVY 类型 subject_sql 必填"),

    /** V1.7：KPI 方案 cycle_type 非法. */
    KPI_CYCLE_TYPE_INVALID("PERF-40023", "KPI 方案 cycle_type 非法"),

    // =============================================
    // K.2 409xx 业务冲突 / 幂等
    // =============================================

    /** 指标编码已存在 */
    METRIC_CODE_DUP("PERF-40901", "指标编码已存在"),

    /**
     * 指标槽位已占用.
     * 复用 PERF-40901 编号（HTTP 409），通过 message 区分语义。
     */
    METRIC_SLOT_CONFLICT("PERF-40901", "指标槽位已占用"),

    /**
     * 指标名称已存在（V1.13：对齐 V1.11 新增的 uk_metric_name_alive 唯一约束）.
     * <p>复用 PERF-40901 编号（HTTP 409），通过 message 区分"编码重复"与"中文名重复".
     */
    METRIC_NAME_DUP("PERF-40901", "指标名称已存在"),

    /** 指标存在下游引用，不可删除（V1.1 占位） */
    METRIC_HAS_DOWNSTREAM_REF("PERF-40902", "指标存在下游引用，不可删除"),

    /** sys_control 同维度同日期版本冲突 */
    SYS_CONTROL_VERSION_CONFLICT("PERF-40903", "sys_control 同维度同日期版本冲突"),

    /** 目标修正申请流程已发起（V1.2 占位） */
    TARGET_ADJUST_APPLY_RUNNING("PERF-40906", "目标修正申请流程已发起"),

    /** 该客户已有审批中的分配调整申请，不可重复提交 */
    ALLOC_ADJUST_APPLY_RUNNING("PERF-40907", "该客户已有审批中的分配调整申请，不可重复提交"),

    // =============================================
    // K.3 422xx 参数校验 / 业务规则
    // =============================================

    /** 参数校验失败（通用） */
    VALIDATION_FAILED("PERF-42200", "参数校验失败"),

    /**
     * 指标计算逻辑非法.
     * 覆盖场景：指标层级与上级不符、Groovy 语法/沙箱校验失败、循环依赖检测等。
     */
    METRIC_CALC_LOGIC_INVALID("PERF-42201", "指标计算逻辑非法"),

    /** KPI 方案权重之和不等于 100 */
    KPI_WEIGHT_SUM_INVALID("PERF-42202", "KPI 方案权重之和不等于 100"),

    /** 目标值导入 Excel 列映射错误（V1.1 占位） */
    IMPORT_COLUMN_MAPPING_INVALID("PERF-42203", "目标值导入 Excel 列映射错误"),

    /** 导入指标不在目标方案关联的 KPI 方案中 */
    IMPORT_METRIC_NOT_IN_KPI("PERF-42204", "导入指标不在目标方案关联的 KPI 方案中"),

    /** 试运行超时（30 秒）（V1.1 占位） */
    TRIAL_RUN_TIMEOUT("PERF-42205", "试运行超时（30 秒）"),

    /** 批量查询超过上限（500 条） */
    BATCH_QUERY_EXCEEDS_LIMIT("PERF-42206", "批量查询超过上限（500 条）"),

    /** 导出行数超过上限（200000）（V1.2 占位） */
    EXPORT_ROWS_EXCEEDS_LIMIT("PERF-42207", "导出行数超过上限（200000）"),

    /** 异步导出任务不存在或已过期（V1.2 占位） */
    EXPORT_TASK_NOT_FOUND("PERF-42208", "异步导出任务不存在或已过期"),

    /** 不能下载他人创建的导出任务（V1.2 占位，HTTP 403） */
    EXPORT_TASK_OWNER_MISMATCH("PERF-42209", "不能下载他人创建的导出任务"),

    /** 导出过滤条件未通过 DATA_SCOPE 校验（V1.2 占位） */
    EXPORT_FILTER_SCOPE_VIOLATION("PERF-42210", "导出过滤条件未通过 DATA_SCOPE 校验"),

    /**
     * V1.9：指标定义批量导入预校验失败（整批 all-or-none）.
     * <p>场景：MetricDefImportStrategy 预扫描发现任一行错误（必填缺失、文件内 metric_code
     * 重复、calc_freq 越界等），整批回滚为 FAILED，错误明细写入 PerfImportBatch.remark。
     */
    IMPORT_BATCH_ALL_OR_NONE_FAILED("PERF-42211", "指标定义批量导入校验失败"),

    // =============================================
    // K.4 500xx 系统错误
    // =============================================

    /** 导出文件生成失败（V1.2 占位） */
    EXPORT_FILE_GENERATE_FAILED("PERF-50002", "导出文件生成失败"),

    /**
     * 幂等等待超时（V1.3 R3.1 新增）.
     * <p>场景：DataTaskService.report 获 Redis 锁失败进入等待路径，循环 3s 后 DB 仍查不到既有记录。
     * <p>与 {@link #CALC_JOB_FAILED} 的区别：本码专用于"幂等协商超时"，后者用于
     * DuplicateKey 回查 null 等"DB 层异常状态"场景。
     */
    IDEMPOTENCY_WAIT_TIMEOUT("PERF-50003", "幂等等待超时（Redis 锁释放后仍无 DB 记录）"),

    /** V1.7：subject_sql 执行失败. */
    METRIC_SUBJECT_SQL_FAILED("PERF-50004", "subject_sql 执行失败"),

    /** 指标/KPI 计算 Job 执行失败（V1.1 占位） */
    CALC_JOB_FAILED("PERF-50007", "指标/KPI 计算 Job 执行失败"),

    // ===== 评价模块 =====
    EVAL_TAG_NAME_DUP("PERF-40050", "标签名称重复"),
    EVAL_RULE_TAG_EXISTS("PERF-40051", "该被评价人标签已存在规则"),
    EVAL_RULE_WEIGHT_INVALID("PERF-40052", "评价人组权重之和必须等于100%"),
    EVAL_SCORE_OUT_OF_RANGE("PERF-40053", "分数必须在10~100范围内"),
    EVAL_SCORE_DUPLICATE("PERF-40054", "已评价不可重复提交"),
    EVAL_TASK_CLOSED("PERF-40055", "任务已结束不可打分"),
    EVAL_NO_PERMISSION("PERF-40056", "当前用户无权评价该人员"),
    EVAL_TASK_END_TIME_INVALID("PERF-40057", "截止时间必须晚于当前时间"),
    EVAL_RULE_NOT_FOUND("PERF-40058", "被评价人无匹配的评价规则"),
    EVAL_IMPORT_FILE_EMPTY("PERF-40060", "导入文件为空"),
    EVAL_IMPORT_FILE_INVALID("PERF-40061", "导入文件解析失败"),
    EVAL_IMPORT_ROWS_EXCEEDED("PERF-40062", "导入行数超过上限"),
    EVAL_ROLE_CONFLICT("PERF-40063", "评价角色不能与被评价角色相同"),
    EVAL_ASSIGN_ITEM_NOT_FOUND("PERF-40064", "待处理任务明细不存在"),
    EVAL_IMPORT_TYPE_INVALID("PERF-40065", "导入类型非法"),
    EVAL_BATCH_NOT_ACTIVE("PERF-40066", "批次未发布或已结束，暂不可打分"),
    EVAL_BATCH_NOT_DRAFT("PERF-40067", "仅草稿状态的批次可发布"),
    EVAL_TASK_DELETE_BEFORE_DEADLINE("PERF-40068", "截止时间未到不可删除"),
    KPI_ITEM_EXPR_REQUIRED("PERF-40069", "指标项必须且只能配置一种表达式（计算表达式 或 SQL表达式）"),

    /** 导入批次越权访问（非本人批次且非管理员数据范围） */
    IMPORT_BATCH_NO_PERMISSION("PERF-40070", "无权访问该导入批次"),

    /** 导入批次无归档源文件（source_object_key 为空，多为旧数据） */
    IMPORT_BATCH_NO_SOURCE_FILE("PERF-40071", "该批次无可下载的源文件");

    private final String code;
    private final String message;

    PerfErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 填充消息模板占位符，返回最终异常消息.
     *
     * @param args 占位符参数
     * @return 格式化后的消息
     */
    public String format(Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        return message + ": " + String.join(", ", java.util.Arrays.stream(args)
                .map(String::valueOf)
                .toArray(String[]::new));
    }
}
