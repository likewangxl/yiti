package com.bank.branch.platform.report.mapper;

import com.bank.branch.platform.report.entity.RptScreen;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 大屏画布双态自定义 SQL(乐观锁自增/发布态整体更新).
 *
 * <p>单条 CRUD 复用 RptScreenMapper 的 BaseMapper;这里只放 BaseMapper 覆盖不到的
 * 「WHERE canvas_version=? 并自增」乐观锁 UPDATE。
 */
@Mapper
public interface RptScreenCanvasMapper extends BaseMapper<RptScreen> {

    /**
     * 乐观锁保存草稿:仅当 canvas_version=expected 时更新 style/draft 并自增版本。
     * @return 受影响行数(0=版本冲突)
     */
    int bumpVersion(@Param("id") Long id,
                    @Param("expected") int expectedVersion,
                    @Param("styleJson") String styleJson,
                    @Param("draftJson") String draftJson,
                    @Param("empId") String empId);

    /**
     * 发布态整体更新:写 published_json + published_at/by,并按目标状态置 publish_status。
     */
    int applyPublished(@Param("id") Long id,
                       @Param("publishedJson") String publishedJson,
                       @Param("publishStatus") int publishStatus,
                       @Param("empId") String empId);
}
