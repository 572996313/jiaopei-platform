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
        当前校区：{{ campusId ? scopeStore.campusName(campusId) : scopeStore.allCampusesLabel }}；偏好按校区成组维护，改完对方下次进工作台生效。
      </span>
    </div>

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
            :disabled="!pendingUserId || !campusId"
            :loading="saving"
            @click="assignDefaultCampus"
          >
            设为本校区默认
          </el-button>
        </template>
        <span class="education-context-text">候选账号只列本机构前 100 个，人多用关键字缩小范围。</span>
      </div>

      <TableFillBox v-slot="{ height }">
        <el-table
          :data="preferences"
          border
          row-key="userId"
          highlight-current-row
          :height="height"
          :empty-text="
            tableLoading
              ? ''
              : campusId
                ? '当前校区下暂无设为默认的账号。'
                : '默认校区按校区成组维护，请先在顶部把范围收到具体校区。'
          "
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
import { fetchSystemUserPage, type SystemUserSummary } from '@cornerstone/core/api/system'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { toMessage } from '@cornerstone/core/utils/error'
import {
  fetchCampusAccountPreferences,
  updateCampusAccountPreference,
  type CampusAccountPreference,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

const router = useRouter()
const auth = useAuthStore()
const institutionStore = useInstitutionStore()
/** 校区范围与教室、节次模板页共用同一份，切页时停在同一个校区 */
const scopeStore = useEducationScopeStore()
const { campusId } = storeToRefs(scopeStore)

const institutionId = computed(() => institutionStore.currentInstitutionId)
const canWrite = computed(() => auth.hasPermission('organization:write'))
/**
 * 候选账号来自底座的用户分页接口，要 `system:user:manage`。
 * 没有这个码的人只看得清"本校区默认是哪些账号"、能取消，不能新设——列表不藏，藏的是入口。
 */
const canPickAccount = computed(() => auth.hasPermission('system:user:manage') && canWrite.value)

const preferences = ref<CampusAccountPreference[]>([])
const candidateUsers = ref<SystemUserSummary[]>([])
const pendingUserId = ref('')

const pageError = ref('')
const lookupLoading = ref(false)
const tableLoading = ref(false)
const candidateLoading = ref(false)
const saving = ref(false)

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

/** 校区范围在横幅那颗芯片上选，本页只保证清单拉过，然后按当前范围取数 */
async function refreshAll() {
  if (!institutionId.value) {
    preferences.value = []
    candidateUsers.value = []
    return
  }
  lookupLoading.value = true
  pageError.value = ''
  try {
    await scopeStore.loadCampuses(institutionId.value)
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
    // 改的可能是自己:全站筛选与表单预填读的就是这一份偏好,改完要重取
    await scopeStore.loadPreference(institutionId.value, true)
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

async function initializePage() {
  try {
    await institutionStore.loadInstitutions()
  } catch (error) {
    pageError.value = toMessage(error, '页面初始化失败')
  }
  await refreshAll()
}

watch(institutionId, async () => {
  // 换机构时 store 的 reset() 已经把范围清空，横幅芯片会在新机构上重新 initScope
  await refreshAll()
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
