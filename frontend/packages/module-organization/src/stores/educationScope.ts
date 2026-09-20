import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 跨页共享的教学范围(校区/学期)。
 * 仅作为列表筛选条件使用,新增弹窗的必填上下文由弹窗自身表单承担;
 * 机构切换/退出登录时由 institution store 调用 reset()。
 */
export const useEducationScopeStore = defineStore('educationScope', () => {
  const campusId = ref<string | null>(null)
  const termId = ref<string | null>(null)

  function setCampus(id: string | null) {
    campusId.value = id
  }

  function setTerm(id: string | null) {
    termId.value = id
  }

  function reset() {
    campusId.value = null
    termId.value = null
  }

  return { campusId, termId, setCampus, setTerm, reset }
})
