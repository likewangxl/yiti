package com.bank.branch.platform.performance.support;

/**
 * 测试数据构造器标记接口.
 * <p>各子代理在 test/support/ 下创建自己的 XxxTestDataBuilder 实现类, 避免 6 个并行子代理修改同一文件引发 git 冲突.
 * <p>子代理私有 Builder 约定:
 * <ul>
 *   <li>P1-A SysControl 子代理: SysControlTestDataBuilder</li>
 *   <li>P1-B Metric 子代理: MetricTestDataBuilder</li>
 *   <li>P1-C Kpi 子代理: KpiTestDataBuilder</li>
 *   <li>P1-D Target 子代理: TargetTestDataBuilder</li>
 *   <li>P1-E RunTask 子代理: RunTaskTestDataBuilder</li>
 *   <li>P1-F Alloc 子代理: AllocTestDataBuilder</li>
 * </ul>
 */
public interface TestDataBuilder {
}
