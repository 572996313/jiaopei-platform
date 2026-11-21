<template>
  <ConfigurableCrudPage
    resource-code="system-roles"
    :form-context="roleFormContext"
    :crud="roleCrud"
  />

  <!-- 页面 + 按钮授权弹窗（客户视角，不出现权限码） -->
  <RoleAuthorizationDialog v-model="authorizationVisible" :role="authorizationRole" />

  <!-- 高级视图：历史预设权限码，仅平台管理员（system:menu:manage）可见 -->
  <el-drawer v-model="advancedVisible" title="高级视图：历史预设权限码" size="480px">
    <div class="education-dialog-context">
      <div><strong>角色:</strong>{{ advancedRole?.roleName || '-' }}</div>
      <div><strong>角色编码:</strong>{{ advancedRole?.roleCode || '-' }}</div>
    </div>

    <el-alert
      v-if="advancedError"
      :title="advancedError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <el-alert
      type="warning"
      show-icon
      :closable="false"
      class="education-alert"
      title="这里直接维护角色的历史预设权限码，仅供平台管理员清理遗留授权。"
      description="新增授权请在「授权页面与按钮」中勾选；在此删除的码会立即从该角色所有用户身上撤下，保存后相关用户需重新登录。"
    />

    <el-input
      v-model="advancedCodesText"
      type="textarea"
      :rows="10"
      placeholder="多个权限码用逗号或换行分隔，如 system:user:manage, education:fee:write"
    />

    <template #footer>
      <el-button @click="advancedVisible = false">取消</el-button>
      <el-button type="primary" :loading="advancedSubmitting" @click="submitAdvancedCodes">
        保存权限码
      </el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import { createSystemRole, updateSystemRole } from '@cornerstone/core/api/system'
import type { SystemRoleSummary } from '@cornerstone/core/api/system'
import ConfigurableCrudPage from '@cornerstone/core/components/ConfigurableCrudPage/index.vue'
import type { CrudPageOptions } from '@cornerstone/core/components/ConfigurableCrudPage/types'
import type { RenderContext } from '@cornerstone/core/components/ConfigurableList/renderers'
import RoleAuthorizationDialog from './RoleAuthorizationDialog.vue'
import {
  ensureStatusDictLoaded,
  parseCustomPermissionCodes,
  toMessage,
  trimToUndefined,
  useStatusOptions,
} from './shared'

const statusOptions = useStatusOptions()

/** 页面 + 按钮授权弹窗 */
const authorizationVisible = ref(false)
const authorizationRole = ref<SystemRoleSummary | null>(null)

/** 高级视图（历史预设权限码） */
const advancedVisible = ref(false)
const advancedRole = ref<SystemRoleSummary | null>(null)
const advancedCodesText = ref('')
const advancedSubmitting = ref(false)
const advancedError = ref('')

/**
 * 编辑前的历史预设权限码。角色弹窗已不再呈现该字段，保存时必须原样回传：
 * 一旦从表单收集（字段不存在 → 空数组），就会静默清空角色的 permission_codes，
 * 这是本次改造最大的事故点。
 */
const preservedPermissionCodes = ref<string[]>([])

const roleFormContext = computed<RenderContext>(() => ({
  statusOptions: statusOptions.value,
}))

function openAuthorizationDialog(role: SystemRoleSummary) {
  authorizationRole.value = role
  authorizationVisible.value = true
}

function openAdvancedView(role: SystemRoleSummary) {
  advancedRole.value = role
  advancedCodesText.value = (role.permissionCodes || []).join(', ')
  advancedError.value = ''
  advancedVisible.value = true
}

async function submitAdvancedCodes() {
  const role = advancedRole.value
  if (!role) {
    return
  }
  advancedSubmitting.value = true
  advancedError.value = ''
  try {
    const permissionCodes = [...new Set(parseCustomPermissionCodes(advancedCodesText.value))]
    await updateSystemRole(role.id, {
      roleName: role.roleName,
      permissionCodes,
      status: role.status || 'active',
      remark: role.remark || undefined,
    })
    advancedVisible.value = false
    ElMessage.success('权限码已保存，相关用户需重新登录后生效')
  } catch (error) {
    advancedError.value = toMessage(error, '保存权限码失败')
  } finally {
    advancedSubmitting.value = false
  }
}

/** 注册到列表 actions 的内置动作；授权与高级视图是业务专属动作，走 extraActions */
const roleCrud: CrudPageOptions = {
  resourceName: '角色',
  buildInitial: (mode, row) => {
    if (mode === 'edit' && row) {
      const role = row as SystemRoleSummary
      preservedPermissionCodes.value = [...(role.permissionCodes || [])]
      return {
        roleCode: role.roleCode,
        roleName: role.roleName,
        status: role.status || 'active',
        remark: role.remark || '',
      }
    }
    preservedPermissionCodes.value = []
    return {
      roleCode: '',
      roleName: '',
      status: statusOptions.value[0]?.value || 'active',
      remark: '',
    }
  },
  // 权限码允许为空:实际权限是「预设权限码 ∪ 已勾菜单节点带的码」，V74 种下的机构管理员/教师
  // 两个预设角色只走菜单节点授权，这里再强制填码会让它们连备注都改不了。
  beforeSubmit: () => null,
  buildCreatePayload: (form) => ({
    roleCode: String(form.roleCode ?? '').trim(),
    roleName: String(form.roleName ?? '').trim(),
    // 新角色的权限一律通过「授权页面与按钮」授予，不再写历史 CSV
    permissionCodes: [],
    status: form.status || 'active',
    remark: trimToUndefined(form.remark),
  }),
  buildUpdatePayload: (form, row) => {
    const role = row as SystemRoleSummary | null | undefined
    return {
      roleName: String(form.roleName ?? '').trim(),
      // 原样回传编辑前的历史预设码，绝不从（已隐藏的）表单字段收集
      permissionCodes: [...(role?.permissionCodes ?? preservedPermissionCodes.value)],
      status: form.status || 'active',
      remark: trimToUndefined(form.remark),
    }
  },
  createApi: (payload) => createSystemRole(payload),
  updateApi: (id, payload) => updateSystemRole(id, payload),
  toggleStatusApi: (row) =>
    updateSystemRole(row.id, {
      roleName: row.roleName,
      permissionCodes: row.permissionCodes,
      status: row.status === 'active' ? 'disabled' : 'active',
      remark: row.remark || undefined,
    }),
  extraActions: {
    'role-assign-menus': {
      handler: (row) => openAuthorizationDialog(row as SystemRoleSummary),
    },
    'role-advanced-csv': { handler: (row) => openAdvancedView(row as SystemRoleSummary) },
  },
}

onMounted(() => {
  void ensureStatusDictLoaded()
})
</script>
