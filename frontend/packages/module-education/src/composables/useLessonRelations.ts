import { computed, ref, watch } from 'vue'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import type { ScheduleLessonRecord } from '../api/education'
import { ensureClassLabels } from './classLabels'
import {
  sortedLessons,
} from '@cornerstone/module-education/utils/schedule'

/**
 * 排课两页（课表与点名、课表总览）共用的停补关联解析。
 *
 * 关联关系（补课↔停课原课）的另一半常在已加载课次之外：已加载来源由调用方通过
 * lessonSources 提供（日页是日课表+待补课聚合，总览页是周月季课表），区间外的课次
 * 走 addRelatedLesson 按需单拉进缓存，再由 lessonMap 统一反查。
 */
export function useLessonRelations(options: {
  /** 各页已加载的课次数组集合（getter 内解包 .value 以便 watch 追踪） */
  lessonSources: () => ScheduleLessonRecord[][]
  formatClass: (id?: string | null) => string
}) {
  const institutionStore = useInstitutionStore()
  const relatedLessonCache = ref<ScheduleLessonRecord[]>([])

  const lessonMap = computed(() => {
    return new Map(
      sortedLessons([...options.lessonSources().flat(), ...relatedLessonCache.value]).map(
        (item) => [item.id, item]
      )
    )
  })

  const makeupLessonsByRelatedId = computed(() => {
    const map = new Map<string, ScheduleLessonRecord[]>()
    for (const lesson of lessonMap.value.values()) {
      if (
        lesson.lessonStatus !== 'makeup' ||
        lesson.changeType !== 'cancelled_makeup' ||
        !lesson.relatedLessonId
      ) {
        continue
      }
      const key = lesson.relatedLessonId
      const list = map.get(key) || []
      list.push(lesson)
      map.set(key, list)
    }
    return map
  })

  // 四块课表数据都只有 classId，班级名按 id 回查（下拉不再持有全量班级当字典）
  watch(
    lessonMap,
    (map) => {
      void ensureClassLabels(
        institutionStore.currentInstitutionId,
        Array.from(map.values()).map((item) => item.classId)
      )
    },
    { immediate: true }
  )

  /** 区间外关联课次按 id 单拉后入缓存，lessonMap 随之更新 */
  function addRelatedLesson(lesson: ScheduleLessonRecord) {
    relatedLessonCache.value = [...relatedLessonCache.value, lesson]
  }

  function relatedCancelledLesson(lesson?: ScheduleLessonRecord | null) {
    if (!lesson?.relatedLessonId) {
      return null
    }
    return lessonMap.value.get(lesson.relatedLessonId) || null
  }

  function relatedMakeupLessons(lesson?: ScheduleLessonRecord | null) {
    if (!lesson) {
      return []
    }
    return sortedLessons(makeupLessonsByRelatedId.value.get(lesson.id) || [])
  }

  function lessonRelationSummary(lesson?: ScheduleLessonRecord | null) {
    if (!lesson) {
      return '无关联'
    }
    if (lesson.lessonStatus === 'cancelled') {
      const related = relatedMakeupLessons(lesson)
      return related.length === 0 ? '未关联补课' : `已关联 ${related.length} 节补课`
    }
    if (lesson.lessonStatus === 'makeup' && lesson.changeType === 'cancelled_makeup') {
      const related = relatedCancelledLesson(lesson)
      if (!related) {
        return '补课原课未加载'
      }
      return `补 ${related.lessonDate || '未设置日期'} ${options.formatClass(related.classId)}`
    }
    return '无关联'
  }

  function relationJumpLabel(lesson?: ScheduleLessonRecord | null) {
    if (!lesson) {
      return ''
    }
    if (lesson.lessonStatus === 'cancelled' && relatedMakeupLessons(lesson).length > 0) {
      return '查看补课'
    }
    if (
      lesson.lessonStatus === 'makeup' &&
      lesson.changeType === 'cancelled_makeup' &&
      lesson.relatedLessonId
    ) {
      return '查看原停课'
    }
    return ''
  }

  return {
    relatedLessonCache,
    lessonMap,
    makeupLessonsByRelatedId,
    addRelatedLesson,
    relatedCancelledLesson,
    relatedMakeupLessons,
    lessonRelationSummary,
    relationJumpLabel,
  }
}
