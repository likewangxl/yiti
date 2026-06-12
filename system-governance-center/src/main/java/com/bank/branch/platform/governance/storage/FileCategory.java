package com.bank.branch.platform.governance.storage;

/**
 * 文件存储 key 类型前缀。
 * <p>OBS 对象名形如 {@code {yyyy/MM/dd}/{prefix}_{uuid}.{ext}}，便于在 OBS 控制台按前缀区分文件类型。</p>
 */
public final class FileCategory {

    private FileCategory() {
    }

    /** 自由报表上传 */
    public static final String FREE_REPORT = "zybb";
    /** 数据导入源文件 */
    public static final String PERF_IMPORT = "sjdr";
    /** 绩效 KPI 导出 */
    public static final String EXPORT_KPI = "jxkpi";
    /** 绩效指标导出 */
    public static final String EXPORT_METRIC = "jxzb";
    /** 绩效分配导出 */
    public static final String EXPORT_ALLOC = "jxfp";
    /** 绩效明细导出 */
    public static final String EXPORT_DETAIL = "jxmx";
    /** 报表动态查询导出 */
    public static final String EXPORT_DYNAMIC = "bbdc";
    /** 客户池汇总导出 */
    public static final String EXPORT_CUSTPOOL = "khchz";
    /** 公告附件 */
    public static final String ANNOUNCEMENT = "gg";
    /** 通用/默认 */
    public static final String GENERAL = "wj";
}
