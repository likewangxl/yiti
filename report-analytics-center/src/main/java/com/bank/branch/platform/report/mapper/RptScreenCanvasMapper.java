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
     * 发布态整体更新：以读取时的 canvas_version 为条件并自增，避免发布/回滚覆盖新草稿。
     */
    int applyPublishedCas(@Param("id") Long id,
                          @Param("expected") int expectedVersion,
                          @Param("publishedJson") String publishedJson,
                          @Param("publishStatus") int publishStatus,
                          @Param("empId") String empId);

    /**
     * 高危权限元数据变更的轻量 CAS。它不改草稿/发布包，但统一推进 canvas_version，
     * 使角色白名单与画布编辑共享同一并发边界。
     */
    int bumpConfigVersion(@Param("id") Long id,
                          @Param("expected") int expectedVersion,
                          @Param("empId") String empId);

    /**
     * 元数据/范围专用 CAS：XML 仅更新显式白名单列，绝不复用 BaseMapper.updateById 覆盖
     * canvas_draft_json、canvas_published_json 或发布状态。
     */
    int updateMetadataCas(@Param("screen") RptScreen screen,
                          @Param("expected") int expectedVersion,
                          @Param("empId") String empId);

    /**
     * 放弃草稿专用 CAS：先安全地写回发布组件树，再在同一事务中恢复缺失的发布 block 行。
     */
    int discardDraftCas(@Param("id") Long id,
                        @Param("expected") int expectedVersion,
                        @Param("draftJson") String draftJson,
                        @Param("empId") String empId);
}
