package com.bank.branch.platform.common.web;

import lombok.Data;
import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> {
    private int pageNo;
    private int pageSize;
    private long total;
    private List<T> records;

    /**
     * 构建分页结果
     *
     * @param pageNo   当前页码
     * @param pageSize 每页大小
     * @param total    总记录数
     * @param records  当前页数据
     * @return 分页结果
     */
    public static <T> PageResult<T> of(int pageNo, int pageSize, long total, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.setPageNo(pageNo);
        r.setPageSize(pageSize);
        r.setTotal(total);
        r.setRecords(records);
        return r;
    }

    /**
     * 获取总页数
     *
     * @return 总页数
     */
    public int getTotalPages() {
        return (int) Math.ceil((double) total / pageSize);
    }
}
