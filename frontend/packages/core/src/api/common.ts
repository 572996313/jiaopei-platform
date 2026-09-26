/**
 * 通用分页响应结构,对应后端 common/api/PageResult。
 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/**
 * 分页查询基础参数,各资源按需扩展自己的筛选字段。
 */
export interface PageParams {
  page?: number
  pageSize?: number
  /** 排序字段,取值见各接口 Service 的白名单;不传则该接口用自己的默认排序 */
  sortField?: string
  /** 排序方向(asc/desc),仅在 sortField 存在时生效;由 useTableSort 生成,不手写 */
  sortOrder?: string
}
