import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import type { ScheduleLessonRecord } from '../api/education'
import {
  canCompleteLesson,
  updateLessonCompletion,
  type WeekLessonAction,
} from '@cornerstone/module-education/utils/schedule'
import { toMessage } from '@cornerstone/core/utils/error'

/** 执行变更弹窗模式：调课 / 停课 / 补课，与共享弹窗组件的契约一致 */
type ExecutionMode = 'reschedule' | 'cancel' | 'makeup'

/**
 * 排课两页（课表与点名、课表总览）共用的执行动作：点名弹窗与调课/停课/补课弹窗的状态、
 * 结课确认、网格课程卡的动作分发。弹窗组件本身由各页自渲染，保存后的刷新走
 * refreshAfterChange 回调（两页刷新各自的数据）。
 */
export function useLessonExecution(options: {
  formatClass: (id?: string | null) => string
  refreshAfterChange: () => void | Promise<void>
}) {
  const router = useRouter()
  const auth = useAuthStore()

  /** 点名按课次点名码收口（按钮节点 113），结课/调课/停课/补课走排课更新接口、按排课维护码收口（按钮节点 112），后端仍按权限串硬拦 */
  const canMark = computed(() => auth.hasPermission('education:attendance:write'))
  const canExecute = computed(() => auth.hasPermission('education:schedule:write'))

  const formDialogVisible = ref(false)
  const formDialogMode = ref<ExecutionMode>('reschedule')
  const formDialogLesson = ref<ScheduleLessonRecord | null>(null)

  const attendanceDialogVisible = ref(false)
  const attendanceLessonId = ref<string | null>(null)

  function openFormDialog(mode: ExecutionMode, lesson: ScheduleLessonRecord) {
    if (!canExecute.value) {
      return
    }
    formDialogMode.value = mode
    formDialogLesson.value = lesson
    formDialogVisible.value = true
  }

  function openAttendanceDialog(lesson: ScheduleLessonRecord) {
    if (!canMark.value) {
      return
    }
    attendanceLessonId.value = lesson.id
    attendanceDialogVisible.value = true
  }

  async function completeLesson(lesson: ScheduleLessonRecord) {
    if (!canCompleteLesson(lesson)) {
      ElMessage.warning('已停课或已结课的课次不支持结课')
      return
    }

    try {
      await ElMessageBox.confirm(
        `确认将 ${lesson.lessonDate || ''} ${options.formatClass(lesson.classId)} 标记为已结课吗？`,
        '课次结课',
        { type: 'warning' }
      )
    } catch {
      return
    }

    try {
      await updateLessonCompletion(lesson.id, 'completed')
      ElMessage.success('课次已结课')
      await options.refreshAfterChange()
    } catch (error) {
      ElMessage.error(toMessage(error, '课次结课失败'))
    }
  }

  function handleDialogSaved(message: string) {
    ElMessage.success(message)
    void options.refreshAfterChange()
  }

  /** 网格课程卡动作分发：执行类就地开弹窗，基本信息编辑归排课管理页（带上下文跳转） */
  function handleLessonAction(payload: WeekLessonAction) {
    switch (payload.type) {
      case 'attendance':
        openAttendanceDialog(payload.lesson)
        break
      case 'complete':
        void completeLesson(payload.lesson)
        break
      case 'reschedule':
        openFormDialog('reschedule', payload.lesson)
        break
      case 'cancel':
        openFormDialog('cancel', payload.lesson)
        break
      case 'makeup':
        openFormDialog('makeup', payload.lesson)
        break
      case 'edit':
        router.push({
          path: '/scheduling',
          query: { editId: payload.lesson.id, date: payload.lesson.lessonDate || undefined },
        })
        break
    }
  }

  return {
    canMark,
    canExecute,
    formDialogVisible,
    formDialogMode,
    formDialogLesson,
    attendanceDialogVisible,
    attendanceLessonId,
    openFormDialog,
    openAttendanceDialog,
    completeLesson,
    handleDialogSaved,
    handleLessonAction,
  }
}
