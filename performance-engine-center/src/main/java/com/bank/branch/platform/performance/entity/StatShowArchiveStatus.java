package com.bank.branch.platform.performance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计展示表归档状态记录。
 *
 * <p>该表只记录成功完成的目标表归档；管理员删除对应记录后，任务会再次处理该目标表。</p>
 */
@Data
@TableName("PERF_STAT_SHOW_ARCHIVE_STATUS")
public class StatShowArchiveStatus {

    /** 状态记录 ID（varchar(32) 主键）。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 归档数据日期。 */
    private LocalDate dataDate;

    /** 归档源 TMP 表。 */
    private String sourceTable;

    /** 归档目标表。 */
    private String targetTable;

    /** 归档前源 TMP 表对应日期的行数。 */
    private Long tmpCount;

    /** 归档状态。 */
    private String archiveStatus;

    /** 状态记录创建时间。 */
    private LocalDateTime createdTime;

    /** 成功状态。 */
    public static final String SUCCESS = "SUCCESS";
}
