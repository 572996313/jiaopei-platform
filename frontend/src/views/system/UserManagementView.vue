<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">用户列表<span class="panel-count">共 {{ userTotal }} 条</span></div>
        <div class="table-actions">
          <span class="education-context-text">支持创建、编辑、启停、重置密码、分配角色。</span>
          <el-button v-if="permissions.canManageUsers" type="primary" @click="openCreateUserDialog">新增用户</el-button>
        </div>
      </div>

      <ConfigurableList
        ref="userListRef"
        resource-code="system-users"
        :context="listRenderContext"
        :actions-width="320"
        @loaded="onUsersLoaded"
      >
        <template #actions="{ row }">
          <el-button v-if="permissions.canManageUsers" text type="primary" @click="openEditUserDialog(row)">编辑</el-button>
          <el-button v-if="permissions.canManageUsers" text @click="toggleUserStatus(row)">
            {{ row.status === 'active' ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="permissions.canManageUsers" text @click="handleResetPassword(row)">重置密码</el-button>
          <el-button v-if="permissions.canManageUsers" text @click="openRoleAssignDialog(row)">分配角色</el-button>
        </template>
      </ConfigurableList>
    </article>

    <el-dialog v-model="userDialogVisible" :title="userDialogMode === 'create' ? '新增用户' : '编辑用户'" width="620px">
      <el-alert v-if="userFormError" :title="userFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="机构">
            <el-select v-model="userForm.institutionId" filterable @change="handleUserInstitutionChange">
              <el-option
                v-for="item in institutions"
                :key="item.id"
                :label="item.orgName || item.shortName || item.orgCode || `机构 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="默认校区">
            <el-select v-model="userForm.defaultCampusId" clearable filterable>
              <el-option
                v-for="item in userDialogCampuses"
                :key="item.id"
                :label="item.campusName || item.campusCode || `校区 #${item.id}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="账号">
            <el-input v-model="userForm.username" :disabled="userDialogMode === 'edit'" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="userForm.displayName" placeholder="请输入显示名称" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="userForm.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="userForm.status">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item v-if="userDialogMode === 'create'" label="初始化密码">
          <el-input v-model="userForm.initialPassword" type="password" show-password placeholder="留空则使用 Init@123456" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="userForm.remark" type="textarea" :rows="3" placeholder="补充用户说明" />
        </el-form-item>
      </el-form>

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
import { computed, onMounted, reactive, ref } from 'vue'
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
import type { RenderContext } from '@/components/ConfigurableList/renderers'
import {
  ensureStatusDictLoaded,
  toMessage,
  trimToUndefined,
  useInstitutionDisplay,
  useStatusOptions,
  useSystemPermissions,
} from './shared'

const auth = useAuthStore()
const permissions = useSystemPermissions()
const { institutionMap, campusMap, formatInstitution, formatCampus } = useInstitutionDisplay()

const statusOptions = useStatusOptions()

const userListRef = ref<InstanceType<typeof ConfigurableList> | null>(null)
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

const userForm = reactive({
  institutionId: '',
  defaultCampusId: '' as string | null,
  username: '',
  displayName: '',
  phone: '',
  initialPassword: '',
  status: 'active',
  remark: '',
})

const roleAssignDialogVisible = ref(false)
const roleAssignSubmitting = ref(false)
const roleAssignError = ref('')
const selectedRoleIds = ref<string[]>([])

function resetUserForm() {
  userForm.institutionId = auth.currentUser?.institutionId || institutions.value[0]?.id || ''
  userForm.defaultCampusId = auth.currentUser?.defaultCampusId || null
  userForm.username = ''
  userForm.displayName = ''
  userForm.phone = ''
  userForm.initialPassword = ''
  userForm.status = statusOptions.value[0]?.value || 'active'
  userForm.remark = ''
  userFormError.value = ''
}

async function syncUserDialogCampuses() {
  if (!userForm.institutionId) {
    userDialogCampuses.value = []
    userForm.defaultCampusId = null
    return
  }
  try {
    userDialogCampuses.value = await fetchCampuses(userForm.institutionId)
    if (userForm.defaultCampusId && !userDialogCampuses.value.some((item) => item.id === userForm.defaultCampusId)) {
      userForm.defaultCampusId = null
    }
  } catch (error) {
    userDialogCampuses.value = []
    userFormError.value = toMessage(error, '校区选项加载失败')
  }
}

async function handleUserInstitutionChange() {
  await syncUserDialogCampuses()
}

async function openCreateUserDialog() {
  userDialogMode.value = 'create'
  selectedUser.value = null
  resetUserForm()
  await syncUserDialogCampuses()
  userDialogVisible.value = true
}

async function openEditUserDialog(user: SystemUserSummary) {
  userDialogMode.value = 'edit'
  selectedUser.value = user
  userForm.institutionId = user.institutionId
  userForm.defaultCampusId = user.defaultCampusId || null
  userForm.username = user.username
  userForm.displayName = user.displayName
  userForm.phone = user.phone || ''
  userForm.initialPassword = ''
  userForm.status = user.status || 'active'
  userForm.remark = user.remark || ''
  userFormError.value = ''
  await syncUserDialogCampuses()
  userDialogVisible.value = true
}

async function submitUser() {
  if (!userForm.institutionId) {
    userFormError.value = '请选择机构'
    return
  }
  if (!trimToUndefined(userForm.username) && userDialogMode.value === 'create') {
    userFormError.value = '请填写账号'
    return
  }
  if (!trimToUndefined(userForm.displayName)) {
    userFormError.value = '请填写显示名称'
    return
  }

  userSubmitting.value = true
  userFormError.value = ''
  try {
    if (userDialogMode.value === 'create') {
      await createSystemUser({
        institutionId: userForm.institutionId,
        defaultCampusId: userForm.defaultCampusId || null,
        username: userForm.username.trim(),
        displayName: userForm.displayName.trim(),
        phone: trimToUndefined(userForm.phone),
        initialPassword: trimToUndefined(userForm.initialPassword),
        status: userForm.status,
        remark: trimToUndefined(userForm.remark),
      })
      ElMessage.success('用户已创建')
    } else if (selectedUser.value) {
      await updateSystemUser(selectedUser.value.id, {
        institutionId: userForm.institutionId,
        defaultCampusId: userForm.defaultCampusId || null,
        displayName: userForm.displayName.trim(),
        phone: trimToUndefined(userForm.phone),
        status: userForm.status,
        remark: trimToUndefined(userForm.remark),
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

onMounted(async () => {
  const [institutionList, roleList] = await Promise.all([fetchInstitutions(), fetchSystemRoles()])
  syncInstitutionMaps(institutionList)
  roles.value = roleList
  await Promise.allSettled([ensureStatusDictLoaded(), loadCampusDirectory()])
})
</script>
