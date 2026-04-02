package com.bank.branch.platform.governance.mapper;

import com.bank.branch.platform.governance.entity.FileObject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件对象 Mapper 接口，操作 file_object 表。
 * <p>
 * 提供文件元数据的增删查操作，支持通过 MD5 哈希值查询实现文件去重。
 * </p>
 */
@Mapper
public interface FileObjectMapper {

    /**
     * 新增文件对象记录。
     *
     * @param fileObject 文件对象实体
     * @return 受影响行数
     */
    int insert(FileObject fileObject);

    /**
     * 根据主键查询文件对象。
     *
     * @param id 文件对象ID
     * @return 文件对象实体，不存在时返回 null
     */
    FileObject selectById(String id);

    /**
     * 根据 MD5 哈希值查询文件对象（用于去重校验）。
     *
     * @param md5Hash MD5 哈希值
     * @return 文件对象实体，不存在时返回 null
     */
    FileObject selectByMd5Hash(String md5Hash);

    /**
     * 根据主键删除文件对象记录（物理删除）。
     *
     * @param id 文件对象ID
     * @return 受影响行数
     */
    int deleteById(String id);
}
