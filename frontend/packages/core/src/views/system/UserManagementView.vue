<template>
  <ConfigurableCrudPage
    ref="userCrudPageRef"
    resource-code="system-users"
    :list-context="listRenderContext"
    :form-context="userFormContext"
    :crud="userCrud"
  />

  <!-- 分配角色弹窗(业务专属,宿主实现) -->
  <el-dialog v-model="roleAssignDialogVisible" title="分配角色" width="520px">
    <div class="education-dialog-context">
      <div><strong>账号:</strong>{{ assignRoleUser?.username || '-' }}</div>
      <div><strong>显示名称:</strong>{{ assignRoleUser?.displayName || '-' }}</div>
    </div>

    <el-alert
      v-if="roleAssignError"
      :title="roleAssignError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <el-checkbox-group v-model="selectedRoleIds" class="system-role-checks">
      <el-checkbox v-for="item in roles" :key="item.id" :label="item.id">
        {{ item.roleName }}({{ item.roleCode }})
      </el-checkbox>
    </el-checkbox-group>

    <template #footer>
      <el-button @click="roleAssignDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="roleAssignSubmitting" @click="submitRoleAssignment"
        >保存角色</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, ref } from 'vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import {
  createSystemUser,
  fetchSystemRoles,
  resetSystemUserPassword,
  updateSystemUser,
  updateSystemUserRoles,
} from '@cornerstone/core/api/system'
import type { SystemRoleSummary, SystemUserSummary } from '@cornerstone/core/api/system'
import { fetchInstitutions, type InstitutionSummary } from '@cornerstone/core/api/tenant'
import ConfigurableCrudPage from '@cornerstone/core/components/ConfigurableCrudPage/index.vue'
import type { CrudPageOptions } from '@cornerstone/core/components/ConfigurableCrudPage/types'
import type { RenderContext } from '@cornerstone/core/components/ConfigurableList/renderers'
import {
  ensureStatusDictLoaded,
  toMessage,
  trimToUndefined,
  useInstitutionDisplay,
  useStatusOptions,
} from './shared'

const auth = useAuthStore()
const { institutionMap, formatInstitution } = useInstitutionDisplay()

const statusOptions = useStatusOptions()

const userCrudPageRef = ref<InstanceType<typeof ConfigurableCrudPage> | null>(null)
const institutions = ref<InstitutionSummary[]>([])
const roles = ref<SystemRoleSummary[]>([])

/** 分配角色弹窗状态(与新增/编辑弹窗分离,宿主独立管理) */
const roleAssignDialogVisible = ref(false)
const roleAssignSubmitting = ref(false)
const roleAssignError = ref('')
const assignRoleUser = ref<SystemUserSummary | null>(null)
const selectedRoleIds = ref<string[]>([])

const listRenderContext = computed<RenderContext>(() => ({
  formatInstitution,
  institutionOptions: institutions.value.map((item) => ({
    label: item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`,
    value: item.id,
  })),
}))

function syncInstitutionMaps(list: InstitutionSummary[]) {
  institutions.value = list
  for (const item of list) {
    institutionMap.set(
      item.id,
      item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`
    )
  }
}

/** 表单 optionsLoader 数据源:机构 + 状态字典 */
const userFormContext = computed<RenderContext>(() => ({
  statusOptions: statusOptions.value,
  institutionOptions: institutions.value.map((item) => ({
    label: item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`,
    value: item.id,
  })),
}))

const userCrud: CrudPageOptions = {
  resourceName: '用户',
  onOpenReset: async () => {
    await nextTick()
    // 重置表单(触发 initial 重建)
    void userCrudPageRef.value?.formRef?.reset()
  },
  buildInitial: (mode, row) => {
    if (mode === 'edit' && row) {
      const user = row as SystemUserSummary
      return {
        institutionId: user.institutionId,
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
      username: '',
      displayName: '',
      phone: '',
      initialPassword: '',
      status: statusOptions.value[0]?.value || 'active',
      remark: '',
    }
  },
  buildCreatePayload: (form) => ({
    institutionId: String(form.institutionId ?? '').trim(),
    username: String(form.username ?? '').trim(),
    displayName: String(form.displayName ?? '').trim(),
    phone: trimToUndefined(form.phone),
    initialPassword: trimToUndefined(form.initialPassword),
    status: form.status || 'active',
    remark: trimToUndefined(form.remark),
  }),
  buildUpdatePayload: (form) => ({
    institutionId: String(form.institutionId ?? '').trim(),
    displayName: String(form.displayName ?? '').trim(),
    phone: trimToUndefined(form.phone),
    status: form.status || 'active',
    remark: trimToUndefined(form.remark),
  }),
  createApi: (payload) => createSystemUser(payload),
  updateApi: (id, payload) => updateSystemUser(id, payload),
  toggleStatusApi: (row) =>
    updateSystemUser(row.id, {
      institutionId: row.institutionId,
      displayName: row.displayName,
      phone: row.phone || undefined,
      status: row.status === 'active' ? 'disabled' : 'active',
      remark: row.remark || undefined,
    }),
  extraActions: {
    'user-reset-password': {
      handler: (row) => void handleResetPassword(row as SystemUserSummary),
    },
    'user-assign-roles': {
      handler: (row) => openRoleAssignDialog(row as SystemUserSummary),
    },
  },
}

async function handleResetPassword(user: SystemUserSummary) {
  try {
    await ElMessageBox.confirm(
      `确定重置用户「${user.displayName || user.username}」的密码吗?重置后将生成新的初始密码,请及时告知该用户。`,
      '重置密码',
      { type: 'warning', confirmButtonText: '重置', cancelButtonText: '取消' }
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
      }
    )
    await userCrudPageRef.value?.reload()
  } catch (error) {
    ElMessage.error(toMessage(error, '重置密码失败'))
  }
}

function openRoleAssignDialog(user: SystemUserSummary) {
  assignRoleUser.value = user
  selectedRoleIds.value = [...user.roleIds]
  roleAssignError.value = ''
  roleAssignDialogVisible.value = true
}

async function submitRoleAssignment() {
  if (!assignRoleUser.value) {
    return
  }

  roleAssignSubmitting.value = true
  roleAssignError.value = ''
  try {
    await updateSystemUserRoles(assignRoleUser.value.id, {
      roleIds: selectedRoleIds.value,
    })
    ElMessage.success('用户角色已更新')
    roleAssignDialogVisible.value = false
    await userCrudPageRef.value?.reload()
  } catch (error) {
    roleAssignError.value = toMessage(error, '分配角色失败')
  } finally {
    roleAssignSubmitting.value = false
  }
}

onMounted(async () => {
  const [institutionList, roleList] = await Promise.all([fetchInstitutions(), fetchSystemRoles()])
  syncInstitutionMaps(institutionList)
  roles.value = roleList
  await ensureStatusDictLoaded()
})
</script>
