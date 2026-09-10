<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">用户列表<span class="panel-count">共 {{ userTotal }} 条</span></div>
        <div class="table-actions">
          <span class="education-context-text">支持创建、编辑、启停、重置密码、分配角色。</span>
        </div>
      </div>

      <ConfigurableList
        ref="userListRef"
        resource-code="system-users"
        :context="listRenderContext"
        :actions="userActionHandlers"
        @loaded="onUsersLoaded"
      />
    </article>

    <el-dialog v-model="userDialogVisible" :title="userDialogMode === 'create' ? '新增用户' : '编辑用户'" width="640px">
      <el-alert v-if="userFormError" :title="userFormError" type="error" show-icon :closable="false" class="education-alert" />

      <ConfigurableForm
        ref="userFormRef"
        resource-code="system-users"
        :mode="userDialogMode"
        :initial="userInitial"
        :context="userFormContext"
        :on-field-change="userFormFieldHooks"
      />

      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSubmitting" @click="submitUser">
          {{ userDialogMode === 'create' ? '创建用户' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleAssignDialogVisible" title="分配角色" width="520px">
      <div class="education-dialog-context">
        <div><strong>账号:</strong>{{ selectedUser?.username || '-' }}</div>
        <div><strong>显示名称:</strong>{{ selectedUser?.displayName || '-' }}</div>
      </div>

      <el-alert v-if="roleAssignError" :title="roleAssignError" type="error" show-icon :closable="false" class="education-alert" />

      <el-checkbox-group v-model="selectedRoleIds" class="system-role-checks">
        <el-checkbox v-for="item in roles" :key="item.id" :label="item.id">
          {{ item.roleName }}({{ item.roleCode }})
        </el-checkbox>
      </el-checkbox-group>

      <template #footer>
        <el-button @click="roleAssignDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleAssignSubmitting" @click="submitRoleAssignment">保存角色</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  createSystemUser,
  fetchSystemRoles,
  resetSystemUserPassword,
  updateSystemUser,
  updateSystemUserRoles,
} from '@/api/system'
import type { SystemRoleSummary, SystemUserSummary } from '@/api/system'
import { fetchCampuses, fetchInstitutions } from '@/api/organization'
import type { CampusSummary, InstitutionSummary } from '@/api/organization'
import ConfigurableList from '@/components/ConfigurableList/index.vue'
import ConfigurableForm from '@/components/ConfigurableForm/index.vue'
import type { ActionHandler } from '@/components/ConfigurableList/types'
import type { FormFieldConfig } from '@/components/ConfigurableList/types'
import type { RenderContext } from '@/components/ConfigurableList/renderers'
import {
  ensureStatusDictLoaded,
  toMessage,
  trimToUndefined,
  useInstitutionDisplay,
  useStatusOptions,
} from './shared'

const auth = useAuthStore()
const { institutionMap, campusMap, formatInstitution, formatCampus } = useInstitutionDisplay()

const statusOptions = useStatusOptions()

const userListRef = ref<InstanceType<typeof ConfigurableList> | null>(null)
const userFormRef = ref<InstanceType<typeof ConfigurableForm> | null>(null)
const userTotal = ref(0)
const institutions = ref<InstitutionSummary[]>([])
const campusDirectory = ref<CampusSummary[]>([])
const roles = ref<SystemRoleSummary[]>([])

function onUsersLoaded(payload: { total: number }) {
  userTotal.value = payload.total
}

const listRenderContext = computed<RenderContext>(() => ({
  formatInstitution,
  formatCampus,
  institutionOptions: institutions.value.map((item) => ({
    label: item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`,
    value: item.id,
  })),
}))

function syncInstitutionMaps(list: InstitutionSummary[]) {
  institutions.value = list
  for (const item of list) {
    institutionMap.set(item.id, item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`)
  }
}

async function loadCampusDirectory() {
  const allCampuses = await Promise.all(institutions.value.map((item) => fetchCampuses(item.id)))
  campusDirectory.value = allCampuses.flat()
  for (const item of campusDirectory.value) {
    campusMap.set(item.id, item.campusName || item.campusCode || `校区 #${item.id}`)
  }
}

const userDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const userSubmitting = ref(false)
const userFormError = ref('')
const userDialogCampuses = ref<CampusSummary[]>([])
const selectedUser = ref<SystemUserSummary | null>(null)

const roleAssignDialogVisible = ref(false)
const roleAssignSubmitting = ref(false)
const roleAssignError = ref('')
const selectedRoleIds = ref<string[]>([])

/** 弹窗回填初值(新增模式给默认机构/状态,编辑模式回填该用户) */
const userInitial = computed<Record<string, any>>(() => {
  if (userDialogMode.value === 'edit' && selectedUser.value) {
    const user = selectedUser.value
    return {
      institutionId: user.institutionId,
      defaultCampusId: user.defaultCampusId || '',
      username: user.username,
      displayName: user.displayName,
      phone: user.phone || '',
      initialPassword: '',
      status: user.status || 'active',
      remark: user.remark || '',
    }
  }
  return {
    institutionId: auth.currentUser?.institutionId || institutions.value[0]?.id || '',
    defaultCampusId: auth.currentUser?.defaultCampusId || '',
    username: '',
    displayName: '',
    phone: '',
    initialPassword: '',
    status: statusOptions.value[0]?.value || 'active',
    remark: '',
  }
})

/** 表单 optionsLoader 数据源:机构 + 当前机构下的校区 + 状态字典 */
const userFormContext = computed<RenderContext>(() => ({
  statusOptions: statusOptions.value,
  institutionOptions: institutions.value.map((item) => ({
    label: item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`,
    value: item.id,
  })),
  campusOptions: userDialogCampuses.value.map((item) => ({
    label: item.campusName || item.campusCode || `校区 #${item.id}`,
    value: item.id,
  })),
}))

async function syncUserDialogCampuses(institutionId: string, currentCampusId: string | null = null) {
  if (!institutionId) {
    userDialogCampuses.value = []
    return
  }
  try {
    userDialogCampuses.value = await fetchCampuses(institutionId)
    // 所选校区不在新机构下时清空(通过 ConfigurableForm 回写,见 handleUserInstitutionChange)
    if (
      currentCampusId &&
      userDialogCampuses.value.length > 0 &&
      !userDialogCampuses.value.some((item) => item.id === currentCampusId)
    ) {
      userFormRef.value?.setFieldValue('defaultCampusId', '')
    }
  } catch (error) {
    userDialogCampuses.value = []
    userFormError.value = toMessage(error, '校区选项加载失败')
  }
}

/** 机构变更联动:重拉当前机构校区(读 ConfigurableForm 的 model) */
const userFormFieldHooks: Record<string, (form: Record<string, any>, field: FormFieldConfig) => void> = {
  institutionId: (form) => {
    const institutionId = String(form.institutionId ?? '')
    const currentCampus = form.defaultCampusId ? String(form.defaultCampusId) : null
    void syncUserDialogCampuses(institutionId, currentCampus)
  },
}

async function openCreateUserDialog() {
  userDialogMode.value = 'create'
  selectedUser.value = null
  userFormError.value = ''
  userDialogVisible.value = true
  await nextTick()
  // 重置表单(触发 initial 重建与机构联动钩子),再按默认机构预载校区
  void userFormRef.value?.reset()
  const defaultInstitutionId = auth.currentUser?.institutionId || institutions.value[0]?.id || ''
  void syncUserDialogCampuses(defaultInstitutionId, null)
}

async function openEditUserDialog(user: SystemUserSummary) {
  userDialogMode.value = 'edit'
  selectedUser.value = user
  userFormError.value = ''
  userDialogVisible.value = true
  await nextTick()
  void userFormRef.value?.reset()
  await syncUserDialogCampuses(user.institutionId, user.defaultCampusId || null)
}

async function submitUser() {
  if (!userFormRef.value) {
    return
  }
  const valid = await userFormRef.value.validate()
  if (!valid) {
    userFormError.value = '请检查表单填写是否完整'
    return
  }
  const form = userFormRef.value.values()
  const institutionId = String(form.institutionId ?? '').trim()
  const defaultCampusId = form.defaultCampusId ? String(form.defaultCampusId) : null

  userSubmitting.value = true
  userFormError.value = ''
  try {
    if (userDialogMode.value === 'create') {
      await createSystemUser({
        institutionId,
        defaultCampusId,
        username: String(form.username ?? '').trim(),
        displayName: String(form.displayName ?? '').trim(),
        phone: trimToUndefined(form.phone),
        initialPassword: trimToUndefined(form.initialPassword),
        status: form.status || 'active',
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('用户已创建')
    } else if (selectedUser.value) {
      await updateSystemUser(selectedUser.value.id, {
        institutionId,
        defaultCampusId,
        displayName: String(form.displayName ?? '').trim(),
        phone: trimToUndefined(form.phone),
        status: form.status || 'active',
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('用户已更新')
    }
    userDialogVisible.value = false
    await userListRef.value?.reload()
  } catch (error) {
    userFormError.value = toMessage(error, userDialogMode.value === 'create' ? '创建用户失败' : '编辑用户失败')
  } finally {
    userSubmitting.value = false
  }
}

async function toggleUserStatus(user: SystemUserSummary) {
  const disabling = user.status === 'active'
  try {
    await ElMessageBox.confirm(
      `确定${disabling ? '停用' : '启用'}用户「${user.displayName || user.username}」吗?`,
      '操作确认',
      { type: 'warning', confirmButtonText: disabling ? '停用' : '启用', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await updateSystemUser(user.id, {
      institutionId: user.institutionId,
      defaultCampusId: user.defaultCampusId || null,
      displayName: user.displayName,
      phone: user.phone || undefined,
      status: user.status === 'active' ? 'disabled' : 'active',
      remark: user.remark || undefined,
    })
    ElMessage.success(user.status === 'active' ? '用户已停用' : '用户已启用')
    await userListRef.value?.reload()
  } catch (error) {
    ElMessage.error(toMessage(error, '用户状态更新失败'))
  }
}

async function handleResetPassword(user: SystemUserSummary) {
  try {
    await ElMessageBox.confirm(
      `确定重置用户「${user.displayName || user.username}」的密码吗?重置后将生成新的初始密码,请及时告知该用户。`,
      '重置密码',
      { type: 'warning', confirmButtonText: '重置', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    const result = await resetSystemUserPassword(user.id)
    await ElMessageBox.alert(
      `初始密码:${result.initialPassword}\n登录后请尽快修改密码。`,
      '密码已重置',
      {
        confirmButtonText: '知道了',
      },
    )
    await userListRef.value?.reload()
  } catch (error) {
    ElMessage.error(toMessage(error, '重置密码失败'))
  }
}

function openRoleAssignDialog(user: SystemUserSummary) {
  selectedUser.value = user
  selectedRoleIds.value = [...user.roleIds]
  roleAssignError.value = ''
  roleAssignDialogVisible.value = true
}

async function submitRoleAssignment() {
  if (!selectedUser.value) {
    return
  }

  roleAssignSubmitting.value = true
  roleAssignError.value = ''
  try {
    await updateSystemUserRoles(selectedUser.value.id, {
      roleIds: selectedRoleIds.value,
    })
    ElMessage.success('用户角色已更新')
    roleAssignDialogVisible.value = false
    await userListRef.value?.reload()
  } catch (error) {
    roleAssignError.value = toMessage(error, '分配角色失败')
  } finally {
    roleAssignSubmitting.value = false
  }
}

/**
 * 按钮动作注册表:与 sys_list_config.actions_json 的 action 名一一对应,
 * label/disabled 提供配置无法表达的动态逻辑(停用/启用文案切换)。
 */
const userActionHandlers: Record<string, ActionHandler> = {
  'user-create': { handler: () => void openCreateUserDialog() },
  'user-edit': { handler: (row) => void openEditUserDialog(row as SystemUserSummary) },
  'user-toggle-status': {
    handler: (row) => void toggleUserStatus(row as SystemUserSummary),
    label: (row) => (row?.status === 'active' ? '停用' : '启用'),
  },
  'user-reset-password': { handler: (row) => void handleResetPassword(row as SystemUserSummary) },
  'user-assign-roles': { handler: (row) => openRoleAssignDialog(row as SystemUserSummary) },
}

onMounted(async () => {
  const [institutionList, roleList] = await Promise.all([fetchInstitutions(), fetchSystemRoles()])
  syncInstitutionMaps(institutionList)
  roles.value = roleList
  await Promise.allSettled([ensureStatusDictLoaded(), loadCampusDirectory()])
})
</script>