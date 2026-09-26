package com.cornerstone.core.common.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页查询参数基类,各域查询 DTO 继承后补充各自的筛选字段。
 */
@Schema(description = "分页查询参数")
public class PageQuery {

    @Schema(description = "页码,从 1 开始", defaultValue = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @Schema(description = "每页条数,1-100", defaultValue = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer pageSize = 10;

    @Schema(description = "排序字段,取值见各接口说明;不传则用该接口的默认排序")
    private String sortField;

    @Schema(description = "排序方向(asc=升序,desc=降序),仅在 sortField 存在时生效,缺省 asc",
            allowableValues = {"asc", "desc"})
    private String sortOrder;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
