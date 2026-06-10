package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.EmpIndexResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工指标结果宽表 Mapper（emp_index_result）.
 *
 * <p>V1.1 Task P1.1 交付。宽表共 400 个值槽（{@code val_1 .. val_400}），
 * 本 Mapper 以"单 slot 读写"为核心抽象，屏蔽 200 列带来的代码爆炸：
 * <ul>
 *   <li>{@link #insertSlotValue} 采用 "INSERT ... ON DUPLICATE KEY UPDATE" 语义，
 *       保证首次写入插入新行、后续不同 slot 写入累加到同一行；</li>
 *   <li>{@link #selectSlotValue} 单值回读；</li>
 *   <li>{@link #selectSlotValuesByEmps} 按 empIds 批量查同一 slot；</li>
 *   <li>{@link #insertRow} 保留"行粒度"插入入口（供单测校验 UK 冲突，生产代码优先走 insertSlotValue）。</li>
 * </ul>
 *
 * <p><strong>安全（SQL 注入）声明</strong>：
 * <ul>
 *   <li>为实现 "按 slot 动态切换列名"，{@code insertSlotValue} / {@code selectSlotValue}
 *       / {@code selectSlotValuesByEmps} 在 XML 中使用 {@code val_${slot}} 拼接列名
 *       （列名不能用 #{} 参数化，属 common-dev-guide §5 允许的合法例外）；</li>
 *   <li>调用方 <strong>必须</strong> 在 Service 层强制校验 {@code slot ∈ [1, 400]}（例如
 *       {@code @Range(min=1, max=200)} 或 {@code Assert.isTrue}），否则构成 SQL 注入漏洞；</li>
 *   <li>其余全部参数一律使用 {@code #{}} 预编译占位。</li>
 * </ul>
 * <p>BaseMapper 标准方法由 MyBatis-Plus 提供; insertRow 保留行粒度插入入口（单测 UK 冲突场景）.
 */
@Mapper
public interface EmpIndexResultMapper extends BaseMapper<EmpIndexResult> {

    /**
     * 插入/更新员工在指定 slot 上的指标值（UPSERT 语义）.
     *
     * <p>实现细节：SQL 采用 {@code INSERT INTO ... (emp_id, data_date, version, val_{slot}) VALUES (...)
     * ON DUPLICATE KEY UPDATE val_{slot}=VALUES(val_{slot})}，依赖 uk_subject_date_ver 保证幂等。
     *
     * @param empId    员工工号
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
     * @param value    指标值（可为 null，表示清空该槽）
     */
    void insertSlotValue(@Param("empId") String empId,
                         @Param("dataDate") LocalDate dataDate,
                         @Param("version") String version,
                         @Param("slot") Integer slot,
                         @Param("value") BigDecimal value);

    /**
     * 清空指定数据日期+版本下某 slot 的全部员工指标值（落库前清理，避免上一轮残留主体的脏数据）.
     *
     * <p>SQL：{@code UPDATE EMP_INDEX_RESULT SET val_{slot}=NULL WHERE data_date=#{dataDate} AND version=#{version}}。
     * 仅置空该 slot 列，不影响同行其它指标列；之后由 {@link #insertSlotValue} 重新写入本轮结果。
     *
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200）
     */
    void clearSlot(@Param("dataDate") LocalDate dataDate,
                   @Param("version") String version,
                   @Param("slot") Integer slot);

    /**
     * 查询员工在指定 slot 上的指标值.
     *
     * @param empId    员工工号
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
     * @return slot 值，行不存在或该槽未赋值返回 null
     */
    BigDecimal selectSlotValue(@Param("empId") String empId,
                               @Param("dataDate") LocalDate dataDate,
                               @Param("version") String version,
                               @Param("slot") Integer slot);

    /**
     * 查询员工在指定 slot 上的指标值（不限版本，只按 empId + dataDate，多版本取 updated_time 最新）。
     * <p>供动态指标查询使用：动态查询不区分版本，只看数据日期；KPI 计算等仍走带 version 的
     * {@link #selectSlotValue}。</p>
     *
     * @param empId    员工工号
     * @param dataDate 数据日期
     * @param slot     值槽（1..400，<strong>调用方必须校验</strong>）
     * @return slot 值，行不存在或该槽未赋值返回 null
     */
    BigDecimal selectSlotValueNoVersion(@Param("empId") String empId,
                                        @Param("dataDate") LocalDate dataDate,
                                        @Param("slot") Integer slot);

    /**
     * 按 empIds 批量查询同一 slot 值.
     *
     * @param empIds   员工工号列表（非空；调用方应控制 &le; 500 批量上限）
     * @param dataDate 数据日期
     * @param version  数据版本
     * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
     * @return 每个命中员工对应的 (empId, metricValue) 行列表；未命中员工不返回
     */
    List<EmpMetricValueRow> selectSlotValuesByEmps(@Param("empIds") List<String> empIds,
                                                   @Param("dataDate") LocalDate dataDate,
                                                   @Param("version") String version,
                                                   @Param("slot") Integer slot);

    /**
     * 纯行粒度插入（不写 val_*，仅建立唯一维度行）.
     *
     * <p>用途：单元测试构造 UK 冲突场景；生产代码一般直接使用 {@link #insertSlotValue}。
     * 同 (emp_id, data_date, version) 第二次调用将抛 {@link org.springframework.dao.DuplicateKeyException}。
     *
     * @param row Entity（empId/dataDate/version 必填，其余字段可空）
     * @return 受影响行数
     */
    int insertRow(EmpIndexResult row);

    /**
     * 查询指定 data_date + version 下的 distinct empId 集合.
     *
     * <p>V1.1 Task P4.4 新增：KPI 批量计算需要定位"该日/版本下有数据的员工全集"，
     * 避免对没有任何指标数据的员工跑空计算。
     *
     * @param dataDate 数据日期
     * @param version  数据版本
     * @return 员工工号列表（可能为空）
     */
    List<String> selectDistinctEmpIds(@Param("dataDate") LocalDate dataDate,
                                      @Param("version") String version);

    /**
     * KPI 分值计算：取某数据日期下某 slot 的全部员工实际值（同员工多版本取最新 version）.
     *
     * <p>列名 {@code val_${slot}} 属 common-dev-guide §5 允许的动态列名例外，
     * 调用方必须保证 {@code slot ∈ [1, 400]}。
     *
     * @param dataDate 数据日期
     * @param slot     值槽（1..400，<strong>调用方必须校验</strong>）
     * @return 每个员工（最新版本）的 (subjectId, value)；该槽位为 null 的对象不返回
     */
    List<SubjectSlotValueRow> selectLatestSlotValuesByDate(@Param("dataDate") LocalDate dataDate,
                                                           @Param("slot") Integer slot);

    /**
     * V1.5 P4.1 新增：按 dataDate 列表批量查询同一 slot 的值.
     *
     * <p>用途：{@code MetricApi.getUserMetricCards} 的 mom/yoy 场景，一次 IN 查询
     * 拿回 current / previous / yearAgo 三个日期的 slot 值，取代原 3 次单点查询。
     * 20 metric 场景由 60 次查询降到 20 次（-66%）。
     *
     * <p>默认方法在 Mapper 接口层做 null/empty short-circuit + List→Map 聚合，
     * 把聚合责任留在 Mapper 接口内，Facade 调用方零感知聚合细节。
     * 返回 Map 中未命中的日期不出现（调用方需 null 判断）。
     *
     * @param empId    员工工号
     * @param dates    数据日期列表（可空；为 null 或空时返回空 Map，不下发 SQL）。
     *                 <strong>调用方约束</strong>：dates 长度建议 ≤ 100；当前 buildCard 最多传 3 个
     *                 （current/previous/yearAgo），生产无超限风险。若未来调用方场景扩大，
     *                 需评估 IN 子句长度（MySQL max_allowed_packet / 优化器解析成本）。
     * @param version  数据版本
     * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
     * @return (dataDate → metricValue) 映射；未命中日期不入 Map
     */
    default Map<LocalDate, BigDecimal> selectSlotValuesByDates(String empId,
                                                               List<LocalDate> dates,
                                                               String version,
                                                               Integer slot) {
        if (dates == null || dates.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EmpDateValueRow> rows = selectSlotValuesByDatesRaw(empId, dates, version, slot);
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (EmpDateValueRow row : rows) {
            if (row.getMetricValue() != null) {
                result.put(row.getDataDate(), row.getMetricValue());
            }
        }
        return result;
    }

    /**
     * V1.5 P4.1 新增：{@link #selectSlotValuesByDates} 的底层原始投影查询.
     *
     * <p>一次 IN 查询返回 {@code (dataDate, val_{slot})} 的行列表。
     * 未命中日期不返回（与 {@code selectSlotValue} 返回 null 语义一致）。
     * 调用方一般走 {@link #selectSlotValuesByDates} 拿聚合 Map；本方法作为
     * 直接行查询入口暴露，方便排查时看原始投影。
     *
     * @param empId    员工工号
     * @param dates    数据日期列表（调用方已确保非空；为空/ null 的 short-circuit 已在 default 方法完成）
     * @param version  数据版本
     * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
     * @return 每个命中日期对应的 (dataDate, metricValue) 行列表；未命中日期不返回
     */
    List<EmpDateValueRow> selectSlotValuesByDatesRaw(@Param("empId") String empId,
                                                     @Param("dates") List<LocalDate> dates,
                                                     @Param("version") String version,
                                                     @Param("slot") Integer slot);

    /**
     * V1.7：取多个 metricCode 对应的 val_slot 映射（一次查询，查 perf_metric_def）.
     *
     * @param metricCodes 指标编码列表
     * @return metricCode -&gt; val_slot 映射
     */
    List<Map<String, Object>> selectValSlotRows(@Param("metricCodes") List<String> metricCodes);

    /**
     * V1.7（V2 修复）：metricCode -&gt; val_slot 映射。
     *
     * <p>原 XML 用两列 {@code key/value} + 返回 {@code Map} 但无 {@code @MapKey}，MyBatis 会走
     * {@code selectOne}：单行被错映射成 {@code {key,value}} 两条目（{@code get(code)} 返回 null）、
     * 多行直接抛 {@code TooManyResultsException}，导致 Groovy 引用指标恒为 0。
     * 改为 List 行查询 + Java 端聚合，杜绝该缺陷。
     */
    default Map<String, Integer> selectValSlotsByCodes(List<String> metricCodes) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<String, Integer> slotMap = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : selectValSlotRows(metricCodes)) {
            Object code = row.get("metricCode");
            Object slot = row.get("valSlot");
            if (code != null && slot instanceof Number) {
                slotMap.put(code.toString(), ((Number) slot).intValue());
            }
        }
        return slotMap;
    }

    /**
     * V1.7：按 slot 列号查单主体单值（val_${slot} 动态列名）.
     *
     * <p><strong>安全说明</strong>：val_${slot} 属 common-dev-guide §5 合法例外，
     * 调用方必须保证 slot ∈ [1, 400]（MetricCalcService.validateSlot 负责校验）。
     *
     * @param subject  员工工号
     * @param slot     值槽（1..200）
     * @param dataDate 数据日期
     * @param version  数据版本
     * @return 指标值，行不存在返回 null
     */
    java.math.BigDecimal selectValBySlot(@Param("subject") String subject,
                                         @Param("slot") Integer slot,
                                         @Param("dataDate") java.time.LocalDate dataDate,
                                         @Param("version") String version);

    /**
     * 试运行：取某主体某日期"最近导入"的数据版本（按 updated_time 优先）。
     *
     * <p>宽表行按 (subject, data_date, version) 隔离，导入数据散落在多个时间戳版本里，
     * SYS_CONTROL 当前版本未必就是该主体该日有数据的版本。试运行据此按数据反查真实值，
     * 避免恒为 0。无数据返回 null。
     */
    String selectLatestVersionForSubject(@Param("subject") String subject,
                                         @Param("dataDate") java.time.LocalDate dataDate);

    /**
     * V1.7：按 subject + 多 metricCode 在单一 dataDate+version 下取宽表 slot 值.
     *
     * <p>实现：先查 perf_metric_def 拿 metricCode → val_slot 映射，
     * 再为每个 slot 查 val_${slot}，返回 Map&lt;metricCode, value&gt;。
     * 未命中的 metricCode 不入结果 Map。
     *
     * @param subject     员工工号
     * @param metricCodes 指标编码列表（空列表直接返回空 Map）
     * @param dataDate    数据日期
     * @param version     数据版本
     * @return metricCode -&gt; 指标值 映射
     */
    default java.util.Map<String, java.math.BigDecimal> selectSlotValuesByCodes(
            String subject, List<String> metricCodes,
            java.time.LocalDate dataDate, String version) {
        if (metricCodes == null || metricCodes.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<String, Integer> slotMap = selectValSlotsByCodes(metricCodes);
        java.util.Map<String, java.math.BigDecimal> result = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, Integer> e : slotMap.entrySet()) {
            java.math.BigDecimal value = selectValBySlot(subject, e.getValue(), dataDate, version);
            if (value != null) {
                result.put(e.getKey(), value);
            }
        }
        return result;
    }
}
