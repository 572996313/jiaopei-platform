import { requestDelete, requestGet, requestPost, requestPut } from '@cornerstone/core/api/request'
import type { PageParams, PageResult } from '@cornerstone/core/api/common'

/**
 * 教务 · 课程与班型 的接口与类型（由 api/education.ts 按功能域拆出）。
 */

export interface CourseCategoryRecord {
  id: string
  institutionId?: string | null
  categoryCode?: string | null
  categoryName?: string | null
  status?: string | null
  sortOrder?: number | null
  remark?: string | null
}

export interface CourseRecord {
  id: string
  institutionId?: string | null
  categoryId?: string | null
  courseCode?: string | null
  courseName?: string | null
  subjectName?: string | null
  teachingMethod?: string | null
  status?: string | null
  remark?: string | null
}

export interface ClassTypeRecord {
  id: string
  institutionId?: string | null
  typeCode?: string | null
  typeName?: string | null
  teachingMethod?: string | null
  minStudents?: number | null
  maxStudents?: number | null
  status?: string | null
  remark?: string | null
}

export interface CourseCategoryCreatePayload {
  institutionId: string
  categoryCode: string
  categoryName: string
  status?: string
  sortOrder?: number
  remark?: string
}

export interface CourseCreatePayload {
  institutionId: string
  categoryId?: string | null
  courseCode: string
  courseName: string
  subjectName?: string
  teachingMethod?: string
  status?: string
  remark?: string
}

export interface ClassTypeCreatePayload {
  institutionId: string
  typeCode: string
  typeName: string
  teachingMethod?: string
  minStudents?: number
  maxStudents?: number
  status?: string
  remark?: string
}

export interface CourseCategoryUpdatePayload {
  categoryCode?: string
  categoryName?: string
  status?: string
  sortOrder?: number
  remark?: string
}

export interface CourseUpdatePayload {
  categoryId?: string | null
  courseCode?: string
  courseName?: string
  subjectName?: string
  teachingMethod?: string
  status?: string
  remark?: string
}

export interface ClassTypeUpdatePayload {
  typeCode?: string
  typeName?: string
  teachingMethod?: string
  minStudents?: number
  maxStudents?: number
  status?: string
  remark?: string
}

export function fetchCourseCategories(params?: { institutionId?: string | null }) {
  return requestGet<CourseCategoryRecord[]>('/education/course-categories', params ?? undefined)
}

export function createCourseCategory(payload: CourseCategoryCreatePayload) {
  return requestPost<CourseCategoryRecord>('/education/course-categories', payload)
}

export function updateCourseCategory(id: string, payload: CourseCategoryUpdatePayload) {
  return requestPut<CourseCategoryRecord>(`/education/course-categories/${id}`, payload)
}

export function deleteCourseCategory(id: string) {
  return requestDelete<null>(`/education/course-categories/${id}`)
}

export function fetchCourses(params?: {
  institutionId?: string | null
  categoryId?: string | null
}) {
  return requestGet<CourseRecord[]>('/education/courses', params ?? undefined)
}

export function createCourse(payload: CourseCreatePayload) {
  return requestPost<CourseRecord>('/education/courses', payload)
}

export function updateCourse(id: string, payload: CourseUpdatePayload) {
  return requestPut<CourseRecord>(`/education/courses/${id}`, payload)
}

export function deleteCourse(id: string) {
  return requestDelete<null>(`/education/courses/${id}`)
}

export function fetchClassTypes(params?: { institutionId?: string | null }) {
  return requestGet<ClassTypeRecord[]>('/education/class-types', params ?? undefined)
}

export function createClassType(payload: ClassTypeCreatePayload) {
  return requestPost<ClassTypeRecord>('/education/class-types', payload)
}

export function updateClassType(id: string, payload: ClassTypeUpdatePayload) {
  return requestPut<ClassTypeRecord>(`/education/class-types/${id}`, payload)
}

export function deleteClassType(id: string) {
  return requestDelete<null>(`/education/class-types/${id}`)
}

export interface CoursePageParams extends PageParams {
  institutionId?: string | null
  categoryId?: string | null
  keyword?: string | null
  status?: string | null
}

export function fetchCoursePage(params: CoursePageParams) {
  return requestGet<PageResult<CourseRecord>>('/education/courses/page', { ...params })
}
