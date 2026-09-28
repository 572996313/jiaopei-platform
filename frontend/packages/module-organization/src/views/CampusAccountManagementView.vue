<template>
  <section class="education-page">
    <article v-if="pageError" class="panel-card">
      <el-alert :title="pageError" type="error" show-icon :closable="false" />
    </article>

    <!-- 这一页只管"哪些账号默认落在哪个校区"：校区本身与学期在「校区与学期」 -->
    <div class="table-toolbar">
      <el-button :icon="Refresh" circle :loading="lookupLoading" @click="refreshAll" />
      <el-button @click="goCampus">校区与学期</el-button>
      <span class="education-context-text">
        默认校区决定该账号打开工作台时预选哪个校区；改完对方下次进工作台生效。
      </span>
    </div>

    <!-- 统一筛选栏:换校区不再自动触发,点「查询」才按新校区重拉偏好与候选 -->
    <FilterBar :loading="lookupLoading" @query="reloadBoth" @reset="resetToDefaultCampus">
      <!-- 偏好按校区成组维护,没有"全部校区"这一档;范围与教室、节次页共用同一个 store -->
      <el-select v-model="campusId" placeholder="选择校区" filterable>
        <el-option
          v-for="item in campuses"
          :key="`${item.id}-${item.campusName || item.campusCode}`"
          :label="item.campusName || item.campusCode || `校区 #${item.id}`"
          :value="item.id"
        />
      </el-select>
    </FilterBar>

    <article class="panel-card education-panel">
      <div class="table-toolbar">
        <template v-if="canPickAccount">
          <el-select
            v-model="pendingUserId"
            placeholder="选择账号"
            filterable
            clearable
            :loading="candidateLoading"
            class="account-pick"
          >
            <el-option
              v-for="item in candidateUsers"
              :key="`${item.id}-${item.displayName || item.username}`"
              :label="`${item.displayName || item.username}（${item.username}）`"
              :value="item.id"
            />
          </el-select>
          <el-button
            type="primary"
            :disabled="!pendingUserId"
            :loading="saving"
            @click="assignDefaultCampus"
          >
            设为本校区默认
          </el-button>
        </template>
        <span class="education-context-text">
          当前校区：{{ formatCampus(campusId) }}；候选账号只列本机构前 100 个,人多用关键字缩小范围。
        </span>
      </div>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="preferences"
          border
          row-key="userId"
          highlight-current-row
          :height="height"
        >
          <el-table-column prop="username" label="账号" width="180" fixed="left" sortable />
          <el-table-column
            prop="displayName"
            label="显示名称"
            min-width="200"
            show-overflow-tooltip
            sortable
          />
          <el-table-column v-if="canWrite" label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" @click="clearDefaultCampus(row)">取消默认</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div v-if="!tableLoading && preferences.length === 0" class="education-empty">
        {{
          campusId
            ? '当前校区下暂无设为默认的账号。'
            : '当前机构下暂无校区，请先在「校区与学期」新增校区。'
        }}
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import { fetchSystemUserPage, type SystemUserSummary } from '@cornerstone/core/api/system'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  fetchCampusAccountPreferences,
  fetchCampuses,
  updateCampusAccountPreference,
  type CampusAccountPreference,
  type CampusSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()
/** 校区范围与教室、节次模板页共用同一份，切页时停在同一个校区 */
const { campusId } = storeToRefs(useEducationScopeStore())

const institutionId = computed(() => institutionStore.currentInstitutionId)
const canWrite = computed(() => auth.hasPermission('organization:write'))
/**
 * 候选账号来自底座的用户分页接口，要 `system:user:manage`。
 * 没有这个码的人只看得清"本校区默认是哪些账号"、能取消，不能新设——列表不藏，藏的是入口。
 */
const canPickAccount = computed(() => auth.hasPermission('system:user:manage') && canWrite.value)

const campuses = ref<CampusSummary[]>([])
const preferences = ref<CampusAccountPreference[]>([])
const candidateUsers = ref<SystemUserSummary[]>([])
const pendingUserId = ref('')

const pageError = ref('')
const lookupLoading = ref(false)
const tableLoading = ref(false)
const candidateLoading = ref(false)
const saving = ref(false)

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function formatCampus(id?: string | null) {
  if (!id) {
    return '未选择'
  }
  const matched = campuses.value.find((item) => item.id === id)
  return matched?.campusName || matched?.campusCode || `校区 #${id}`
}

async function loadPreferences() {
  if (!campusId.value) {
    preferences.value = []
    return
  }
  tableLoading.value = true
  pageError.value = ''
  try {
    preferences.value = await fetchCampusAccountPreferences(campusId.value)
  } catch (error) {
    preferences.value = []
    pageError.value = toMessage(error, '默认账号加载失败')
  } finally {
    tableLoading.value = false
  }
}

async function loadCandidates() {
  if (!canPickAccount.value) {
    candidateUsers.value = []
    return
  }
  candidateLoading.value = true
  try {
    const result = await fetchSystemUserPage({
      institutionId: institutionId.value || undefined,
      page: 1,
      // 全站分页上限 100（core PageQuery 的 @Max），传 200 会被直接拒成"每页条数不能超过 100"，
      // 候选人列表永远加载不出来。超过 100 个账号时靠搜索缩小范围，不要靠调大 pageSize。
      pageSize: 100,
    })
    const assigned = new Set(preferences.value.map((item) => item.userId))
    candidateUsers.value = result.records.filter((item) => !assigned.has(item.id))
  } catch (error) {
    pageError.value = toMessage(error, '账号列表加载失败')
  } finally {
    candidateLoading.value = false
  }
}

async function reloadBoth() {
  await loadPreferences()
  await loadCandidates()
}

/** 没有"全部校区"档,重置 = 回到第一个校区并重查 */
function resetToDefaultCampus() {
  campusId.value = campuses.value[0]?.id || null
  void reloadBoth()
}

async function loadCampuses() {
  if (!institutionId.value) {
    campuses.value = []
    campusId.value = null
    return
  }
  lookupLoading.value = true
  pageError.value = ''
  try {
    campuses.value = await fetchCampuses(institutionId.value)
    // 机构里没有这个校区（换机构、或校区被删）时兜成第一个，避免带着死 id 进来显示"未选择"
    if (!campusId.value || !campuses.value.some((item) => item.id === campusId.value)) {
      campusId.value = campuses.value[0]?.id || null
    }
  } catch (error) {
    pageError.value = toMessage(error, '校区列表加载失败')
  } finally {
    lookupLoading.value = false
  }
  await reloadBoth()
}

async function savePreference(userId: string, targetCampusId: string | null, message: string) {
  saving.value = true
  pageError.value = ''
  try {
    await updateCampusAccountPreference(userId, targetCampusId)
    ElMessage.success(message)
    await reloadBoth()
    pendingUserId.value = ''
  } catch (error) {
    pageError.value = toMessage(error, '默认校区保存失败')
  } finally {
    saving.value = false
  }
}

function assignDefaultCampus() {
  if (!pendingUserId.value || !campusId.value) {
    return
  }
  void savePreference(pendingUserId.value, campusId.value, '默认校区已设置')
}

function clearDefaultCampus(row: CampusAccountPreference) {
  void savePreference(row.userId, null, '默认校区已取消')
}

function goCampus() {
  // 共用的校区范围不用带参数：新页落地读的就是同一个 store
  router.push('/campus')
}

async function refreshAll() {
  await loadCampuses()
}

async function initializePage() {
  try {
    await institutionStore.loadInstitutions()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  }
  await refreshAll()
}

watch(institutionId, async () => {
  campusId.value = null
  await loadCampuses()
})

onMounted(() => {
  initializePage()
})
</script>

<style scoped>
/* 账号选择器要和按钮排在同一行,不能被 .archive-select 那类 100% 宽度规则撑成两行 */
.account-pick {
  width: 240px;
}
</style>
