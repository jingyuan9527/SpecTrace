package com.spectrace.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页响应结构。
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private long current;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.total = page.getTotal();
        result.current = page.getCurrent();
        result.size = page.getSize();
        result.records = page.getRecords();
        return result;
    }

    public static <T> PageResult<T> of(long total, long current, long size, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.total = total;
        result.current = current;
        result.size = size;
        result.records = records;
        return result;
    }
}
