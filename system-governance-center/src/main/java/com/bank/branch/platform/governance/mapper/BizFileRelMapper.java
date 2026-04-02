package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.BizFileRel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 业务-附件关联 Mapper 接口，操作 biz_file_rel 表。
 * <p>
 * 提供业务对象与文件对象之间关联关系的增删查操作。
 * 支持通过业务类型+业务ID查询关联文件，以及判断关联是否已存在（用于幂等控制）。
 * </p>
 */
@Mapper
public interface BizFileRelMapper {

    /**
     * 新增业务-附件关联记录。
     *
     * @param rel 关联实体
     * @return 受影响行数
     */
    int insert(BizFileRel rel);

    /**
     * 根据业务类型和业务ID查询关联的文件列表，按创建时间升序排列。
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @return 关联记录列表
     */
    List<BizFileRel> selectByBizTypeAndBizId(@Param("bizType") String bizType,
                                              @Param("bizId") String bizId);

    /**
     * 判断指定业务对象与文件对象的关联是否已存在。
     * 用于 bindFile 幂等控制。
     *
     * @param bizType      业务类型
     * @param bizId        业务ID
     * @param fileObjectId 文件对象ID
     * @return 存在返回 true，否则返回 false
     */
    boolean existsByBizTypeAndBizIdAndFileObjectId(@Param("bizType") String bizType,
                                                    @Param("bizId") String bizId,
                                                    @Param("fileObjectId") String fileObjectId);

    /**
     * 根据文件对象ID删除所有关联记录。
     * 用于文件删除时解除所有业务关联。
     *
     * @param fileObjectId 文件对象ID
     * @return 受影响行数
     */
    int deleteByFileObjectId(String fileObjectId);
}
