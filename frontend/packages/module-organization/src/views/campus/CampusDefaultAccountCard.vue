<template>
  <!--
    校区管理页的"校区默认账号"卡片：维护哪些账号把本校区设为默认校区。
    原先这一属性挂在底座用户表的 default_campus_id 列上（用户管理页里编辑），
    阶段五为了让 cornerstone-core 能脱离教培业务独立建库而外移到本模块。
  -->
  <article class="panel-card resource-card">
    <div class="resource-head">
      <div>
        <div class="panel-title">校区默认账号</div>
        <div class="education-context-text">
          {{ campusId ? `当前校区：${campusLabel}` : '当前机构下暂无校区' }}
        </div>
      </div>
      <div class="resource-head-actions">
        <!-- 校区范围与教室/节次页共用同一个 store，这里切了那两页跟着切 -->
        <el-select v-model="campusId" placeholder="选择校区" filterable class="campus-pref-picker">
          <el-option
            v-for="item in campuses"
            :key="item.id"
            :label="item.campusName || item.campusCode || `校区 #${item.id}`"
            :value="item.id"
          />
        </el-select>
        <template v-if="campusId && canManageUsers">
          <el-select
            v-model="pendingUserId"
            placeholder="选择账号"
            filterable
            clearable
            :loading="candidateLoading"
            class="campus-pref-picker"
          >
            <el-option
              v-for="item in candidateUsers"
              :key="item.id"
              :label="`${item.displayName || item.username}(${item.username})`"
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
      </div>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <el-table :data="preferences" border :max-height="TABLE_STACK_MAX_HEIGHT">
      <el-table-column prop="username" label="账号" width="180" sortable />
      <el-table-column prop="displayName" label="显示名称" min-width="160" sortable />
      <el-table-column label="默认校区" min-width="160">
        <template #default>{{ campusLabel }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            :disabled="!canWrite"
            @click="clearDefaultCampus(row as CampusAccountPreference)"
            >取消默认</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </article>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { storeToRefs } from 'pinia'
import { computed, ref, watch } from 'vue'
import { TABLE_STACK_MAX_HEIGHT } from '@cornerstone/core/utils/table-view'
import { fetchSystemUserPage, type SystemUserSummary } from '@cornerstone/core/api/system'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import {
  fetchCampusAccountPreferences,
  updateCampusAccountPreference,
  type CampusAccountPreference,
  type CampusSummary,
} from '@cornerstone/module-organization/api/organization'
import { useEducationScopeStore } from '@cornerstone/module-organization/stores/educationScope'

const props = defineProps<{
  campuses: CampusSummary[]
}>()

const auth = useAuthStore()
const institutionStore = useInstitutionStore()
/** 校区范围与教室、节次模板页共用同一份，卡片内自带选择器 */
const { campusId } = storeToRefs(useEducationScopeStore())

const canWrite = computed(() => auth.hasPermission('organization:write'))
/** 候选账号来自底座用户分页接口，需要用户管理权限；无权限时只隐藏选择器，列表仍可查看 */
const canManageUsers = computed(() => auth.hasPermission('system:user:manage'))

const preferences = ref<CampusAccountPreference[]>([])
const candidateUsers = ref<SystemUserSummary[]>([])
const pendingUserId = ref('')
const saving = ref(false)
const candidateLoading = ref(false)
const errorMessage = ref('')

const campusLabel = computed(() => {
  if (!campusId.value) {
    return '未选择'
  }
  const matched = props.campuses.find((item) => item.id === campusId.value)
  return matched?.campusName || matched?.campusCode || `校区 #${campusId.value}`
})

async function loadPreferences() {
  if (!campusId.value) {
    preferences.value = []
    return
  }
  errorMessage.value = ''
  try {
    preferences.value = await fetchCampusAccountPreferences(campusId.value)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '默认账号加载失败'
  }
}

async function loadCandidates() {
  if (!canManageUsers.value) {
    return
  }
  candidateLoading.value = true
  try {
    const result = await fetchSystemUserPage({
      institutionId: institutionStore.currentInstitutionId || undefined,
      page: 1,
      // 全站分页上限 100（core PageQuery 的 @Max），传 200 会被直接拒成"每页条数不能超过 100"，
      // 候选人列表永远加载不出来。超过 100 个账号时靠搜索缩小范围，不要靠调大 pageSize。
      pageSize: 100,
    })
    const assigned = new Set(preferences.value.map((item) => item.userId))
    candidateUsers.value = result.records.filter((item) => !assigned.has(item.id))
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '账号列表加载失败'
  } finally {
    candidateLoading.value = false
  }
}

async function savePreference(userId: string, campusId: string | null, message: string) {
  saving.value = true
  errorMessage.value = ''
  try {
    await updateCampusAccountPreference(userId, campusId)
    ElMessage.success(message)
    await loadPreferences()
    await loadCandidates()
    pendingUserId.value = ''
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '默认校区保存失败'
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

watch(
  campusId,
  async () => {
    await loadPreferences()
    await loadCandidates()
  },
  { immediate: true }
)
</script>

<style scoped>
.resource-head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.campus-pref-picker {
  width: 240px;
}
</style>
