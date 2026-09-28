import { computed, ref } from 'vue'
import { fetchMyStudentProfile, listMyStudents, type StudentBrief } from '@/api/miniapp'
import { useAuthStore } from '@/stores/auth'

/**
 * "看某一个学员"的页面(课表、同学名单)共用这一份解析逻辑。
 * 入口可以带 studentId,也可以不带:学生取自己的档案,家长只有一个孩子就直接选中,
 * 多个孩子时由页面显示切换条。
 */
export function useBoundStudent() {
  const authStore = useAuthStore()
  const students = ref<StudentBrief[]>([])
  const studentId = ref('')
  const ready = ref(false)

  /** 家长绑定多个孩子才有切换条;学生端列表故意留空,不显示多余的一行 */
  const showSwitcher = computed(() => students.value.length > 1)

  async function init(fromRoute: string) {
    const isStudent = authStore.userInfo?.roles?.includes('MINIAPP_STUDENT') ?? false
    try {
      if (isStudent) {
        const profile = await fetchMyStudentProfile()
        studentId.value = fromRoute || String(profile.studentId ?? '')
      } else {
        students.value = await listMyStudents()
        studentId.value = fromRoute || String(students.value[0]?.id ?? '')
      }
    } catch {
      // 拉不到就按入口参数走,页面自己的空态会把"没选中"说清楚
      studentId.value = fromRoute
    } finally {
      ready.value = true
    }
  }

  function switchTo(id: string) {
    studentId.value = id
  }

  return { students, studentId, ready, showSwitcher, init, switchTo }
}
