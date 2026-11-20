import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useTabsStore } from '@cornerstone/core/stores/tabs'
import {
  fetchCampuses,
  fetchMyCampusPreference,
  type CampusSummary,
} from '@cornerstone/module-organization/api/organization'

/**
 * 全站教学范围（校区 / 学期）。
 *
 * 校区只有横幅那一个入口：CampusScopeChip 调 initScope() 落初值、setCampus() 改值，
 * 页面一律直接读 campusId 作为查询条件，不再各自放一颗校区下拉。
 * 改范围只走 setCampus()，它会把全部页签重挂一遍；不要直接给 campusId 赋值。
 * 首屏拉数前想要范围已经落定，就 await initScope(institutionId)：同机构并发只跑一趟，已初始化时是空操作。
 * campusId=null 表示「全部校区」（不收窄），它是个明确的选项，不再是"还没选"的中间态。
 * preferredCampusId 是账号在 /campus-accounts 维护的默认校区，只用于初值与表单预填，
 * 表单预填一律走 prefillCampusId，不要在页面里再拼一次 ?? 兜底。
 * 机构切换/退出登录时由 institution store 经 onInstitutionReset 调用 reset()。
 */
export const useEducationScopeStore = defineStore('educationScope', () => {
  const authStore = useAuthStore()
  /** 角色被授了校区范围：后端已按白名单收窄，前端只需调整「全部」的文案与选项 */
  const campusRestricted = computed(
    () =>
      authStore.currentUser?.dataScopeKey === 'CAMPUS' &&
      Array.isArray(authStore.currentUser?.dataScopeIds)
  )
  const allCampusesLabel = computed(() =>
    campusRestricted.value ? '全部可访问校区' : '全部校区'
  )
  const campusId = ref<string | null>(null)
  const campuses = ref<CampusSummary[]>([])
  const preferredCampusId = ref<string | null>(null)
  const termId = ref<string | null>(null)
  let loadedInstitutionId: string | null = null
  let preferenceInstitutionId: string | null = null
  /** initScope 的并发闸门：同机构首屏只跑一趟 */
  let initializing: { institutionId: string; promise: Promise<void> } | null = null

  /** 新增/编辑弹窗的校区初值：已选校区优先，没选（全部校区）就用账号默认校区 */
  const prefillCampusId = computed(() => campusId.value ?? preferredCampusId.value)

  /**
   * 换校区范围。和切机构同理：页签是 keep-alive 的，失活页面不会补跑 watch，
   * 所以换完范围必须让全部页签重挂一次，否则切回去的还是上一个校区的数。
   */
  function setCampus(id: string | null) {
    if (campusId.value === id) {
      return
    }
    campusId.value = id
    useTabsStore().refreshAll()
  }

  function setTerm(id: string | null) {
    termId.value = id
  }

  /** 同一机构只拉一次；没有 organization:read 时会 403，清空列表让芯片自己隐藏，不弹错。 */
  async function loadCampuses(institutionId: string | null, force = false): Promise<void> {
    if (!institutionId) {
      campuses.value = []
      loadedInstitutionId = null
      return
    }
    if (!force && loadedInstitutionId === institutionId) {
      return
    }
    try {
      campuses.value = await fetchCampuses(institutionId)
      loadedInstitutionId = institutionId
    } catch {
      campuses.value = []
    }
  }

  /** 同一机构只拉一次偏好；拉不到就留空，范围落「全部校区」。force=true 用于偏好刚被改动后重取。 */
  async function loadPreference(institutionId: string | null, force = false): Promise<void> {
    if (!institutionId) {
      preferredCampusId.value = null
      preferenceInstitutionId = null
      return
    }
    if (!force && preferenceInstitutionId === institutionId) {
      return
    }
    try {
      const preference = await fetchMyCampusPreference()
      preferredCampusId.value = preference.campusId || null
      preferenceInstitutionId = institutionId
    } catch {
      preferredCampusId.value = null
    }
  }

  /**
   * 进系统/换机构时把范围对齐到账号默认校区；没设过偏好就停「全部校区」，
   * 不替用户猜一个校区（那会把别的校区的行静默藏掉）。
   * 已选且仍在本机构校区列表里时不动，所以切页签不会把用户改过的范围顶回去。
   * force=true 用于偏好刚被改过：重取偏好并重落范围。
   */
  async function initScope(institutionId: string | null, force = false): Promise<void> {
    if (!institutionId) {
      reset()
      return
    }
    // 首屏时横幅芯片与页面各自都会调一次；同机构并发就共用这一趟，
    // 免得两份请求各写一遍 campusId（后写完的那份会盖掉用户刚选的范围）
    if (!force && initializing?.institutionId === institutionId) {
      return initializing.promise
    }
    const entry: { institutionId: string; promise: Promise<void> } = {
      institutionId,
      promise: Promise.resolve(),
    }
    entry.promise = (async () => {
      await Promise.all([loadCampuses(institutionId, force), loadPreference(institutionId, force)])
      if (!force && campusId.value && campuses.value.some((item) => item.id === campusId.value)) {
        return
      }
      // 只有一个可访问校区时没有「全部」这一档，直接落到唯一校区
      if (campusRestricted.value && campuses.value.length === 1) {
        campusId.value = campuses.value[0].id
        return
      }
      campusId.value =
        campuses.value.find((item) => item.id === preferredCampusId.value)?.id || null
    })().finally(() => {
      if (initializing === entry) {
        initializing = null
      }
    })
    initializing = entry
    return entry.promise
  }

  function campusName(id: string | null): string {
    if (!id) {
      return allCampusesLabel.value
    }
    const matched = campuses.value.find((item) => item.id === id)
    return matched?.campusName || matched?.campusCode || `校区 #${id}`
  }

  function reset() {
    campusId.value = null
    campuses.value = []
    preferredCampusId.value = null
    termId.value = null
    loadedInstitutionId = null
    preferenceInstitutionId = null
    initializing = null
  }

  return {
    campusRestricted,
    allCampusesLabel,
    campusId,
    campuses,
    preferredCampusId,
    prefillCampusId,
    termId,
    setCampus,
    setTerm,
    loadCampuses,
    loadPreference,
    initScope,
    campusName,
    reset,
  }
})
