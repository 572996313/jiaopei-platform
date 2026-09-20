package com.jiaopei.platform.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 通用分页响应封装,配合 MyBatis-Plus 分页插件使用。
 *
 * @param records  当前页数据
 * @param total    总记录数
 * @param page     当前页码,从 1 开始
 * @param pageSize 每页条数
 */
@Schema(description = "通用分页响应")
public record PageResult<T>(
    @Schema(description = "当前页数据列表") List<T> records,
    @Schema(description = "总记录数") long total,
    @Schema(description = "当前页码,从 1 开始") long page,
    @Schema(description = "每页条数") long pageSize) {

    /**
     * 从 MyBatis-Plus 分页查询结果构造分页响应,records 为转换后的视图列表。
     */
    public static <T> PageResult<T> of(IPage<?> pageResult, List<T> records) {
        return new PageResult<>(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }
}
