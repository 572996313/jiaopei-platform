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
}
