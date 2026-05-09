package com.bank.branch.platform.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.governance.entity.FileObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件对象 Mapper 接口，操作 file_object 表。
 * <p>
 * 提供文件元数据的增删查操作，支持通过 MD5 哈希值查询实现文件去重。
 * </p>
 * <p>
 * insert / selectById / deleteById 由 MyBatis-Plus BaseMapper 提供。
 * </p>
 */
@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {

    /**
     * 根据 MD5 哈希值查询文件对象（用于去重校验）。
     *
     * @param md5Hash MD5 哈希值
     * @return 文件对象实体，不存在时返回 null
     */
    FileObject selectByMd5Hash(String md5Hash);

    /**
     * 全局文件列表分页查询（管理后台用）。
     * 支持按 fileName 模糊、fileType 精确、uploadedBy 精确、上传时间区间过滤。
     *
     * @param fileName     文件名关键字（LIKE %?%），可空
     * @param fileType     文件类型，可空
     * @param uploadedBy   上传人工号，可空
     * @param startTime    上传时间下界（含），可空，ISO LOCAL_DATE_TIME 字符串
     * @param endTime      上传时间上界（含），可空
     * @param offset       SQL 偏移量
     * @param limit        每页条数
     * @return 命中记录列表
     */
    List<FileObject> selectByCondition(@Param("fileName") String fileName,
                                       @Param("fileType") String fileType,
                                       @Param("uploadedBy") String uploadedBy,
                                       @Param("startTime") String startTime,
                                       @Param("endTime") String endTime,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 与 selectByCondition 配套的总条数统计。
     */
    long countByCondition(@Param("fileName") String fileName,
                          @Param("fileType") String fileType,
                          @Param("uploadedBy") String uploadedBy,
                          @Param("startTime") String startTime,
                          @Param("endTime") String endTime);
}
