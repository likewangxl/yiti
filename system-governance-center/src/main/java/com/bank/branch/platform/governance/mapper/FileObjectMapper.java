package com.bank.branch.platform.governance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.branch.platform.governance.entity.FileObject;
import org.apache.ibatis.annotations.Mapper;

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
}
