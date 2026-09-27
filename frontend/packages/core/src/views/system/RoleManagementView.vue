<template>
  <ConfigurableCrudPage
    resource-code="system-roles"
    :form-context="roleFormContext"
    :crud="roleCrud"
  >
    <!-- permission-groups 复合控件:按权限分组渲染复选框网格 -->
    <template #field-permissionCodes="{ model, disabled }">
      <el-checkbox-group v-model="model.permissionCodes" :disabled="disabled">
        <div
          v-for="group in permissionGroupData"
          :key="group.group"
          class="system-permission-group"
        >
          <div class="system-permission-group-title">{{ group.group }}</div>
          <div class="system-permission-grid">
            <el-checkbox v-for="item in group.items" :key="item.code" :value="item.code">
              {{ item.label }}
            </el-checkbox>
          </div>
        </div>
      </el-checkbox-group>
    </template>
  </ConfigurableCrudPage>

  <!-- 分配菜单弹窗(业务专属,宿主实现) -->
  <el-dialog v-model="roleMenuDialogVisible" title="分配菜单" width="620px">
    <div class="education-dialog-context">
      <div><strong>角色:</strong>{{ selectedMenuRole?.roleName || '-' }}</div>
      <div><strong>角色编码:</strong>{{ selectedMenuRole?.roleCode || '-' }}</div>
    </div>

    <el-alert
      v-if="roleMenuError"
      :title="roleMenuError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <div class="education-context-text">
      勾选页面节点即授予该页读取权限,勾选按钮节点即授予对应写操作权限,保存后需重新登录生效。
    </div>

    <el-tree
      ref="roleMenuTreeRef"
      node-key="id"
      show-checkbox
      check-on-click-node
      default-expand-all
      :data="menuTree"
      :props="{ label: 'menuName', children: 'children' }"
      class="system-menu-tree"
    />

    <template #footer>
      <el-button @click="roleMenuDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="roleMenuSubmitting" @click="submitRoleMenus"
        >保存菜单</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  createSystemRole,
  fetchSystemMenusTree,
  fetchSystemRoleMenuIds,
  updateSystemRole,
  updateSystemRoleMenus,
} from '@cornerstone/core/api/system'
import type { SystemMenuTreeNode, SystemRoleSummary } from '@cornerstone/core/api/system'
import ConfigurableCrudPage from '@cornerstone/core/components/ConfigurableCrudPage/index.vue'
import type { CrudPageOptions } from '@cornerstone/core/components/ConfigurableCrudPage/types'
import type { RenderContext } from '@cornerstone/core/components/ConfigurableList/renderers'
import {
  ensurePermissionOptionsLoaded,
  usePermissionOptionGroups,
  usePermissionOptionList,
} from '@cornerstone/core/stores/permissionOptions'
import {
  ensureStatusDictLoaded,
  parseCustomPermissionCodes,
  toMessage,
  trimToUndefined,
  useStatusOptions,
} from './shared'

const statusOptions = useStatusOptions()

const permissionGroupData = usePermissionOptionGroups()
const permissionOptions = usePermissionOptionList()

/** 菜单分配弹窗状态 */
const roleMenuDialogVisible = ref(false)
const roleMenuSubmitting = ref(false)
const roleMenuError = ref('')
const selectedMenuRole = ref<SystemRoleSummary | null>(null)
const roleMenuTreeRef = ref<any>(null)
const menuTree = ref<SystemMenuTreeNode[]>([])

const roleFormContext = computed<RenderContext>(() => ({
  statusOptions: statusOptions.value,
}))

function collectRolePermissionCodes(form: Record<string, any>): string[] {
  const preset = Array.isArray(form.permissionCodes) ? form.permissionCodes : []
  const custom = typeof form.customPermissionCodes === 'string' ? form.customPermissionCodes : ''
  return [...new Set([...preset, ...parseCustomPermissionCodes(custom)])]
}

function normalizeRoleMenuKeys(nodes: SystemMenuTreeNode[], selectedIds: string[]): string[] {
  const selectedSet = new Set(selectedIds)

  const visit = (node: SystemMenuTreeNode): string[] => {
    const childResults: string[] = (node.children || []).flatMap((child) => visit(child))
    const nodeId = String(node.id)

    if (!selectedSet.has(nodeId)) {
      return childResults
    }

    if (!node.children?.length) {
      return [nodeId]
    }

    const childIds = node.children.map((child) => String(child.id))
    const allChildrenSelected = childIds.every((childId) => selectedSet.has(childId))

    if (allChildrenSelected) {
      return [nodeId]
    }

    return childResults
  }

  const normalized: string[] = []
  for (const node of nodes) {
    normalized.push(...visit(node))
  }
  return [...new Set(normalized)]
}

async function openRoleMenuDialog(role: SystemRoleSummary) {
  selectedMenuRole.value = role
  roleMenuError.value = ''
  roleMenuSubmitting.value = false

  try {
    if (!menuTree.value.length) {
      menuTree.value = await fetchSystemMenusTree()
    }
    const menuIds = await fetchSystemRoleMenuIds(role.id)
    roleMenuDialogVisible.value = true
    await nextTick()
    roleMenuTreeRef.value?.setCheckedKeys(normalizeRoleMenuKeys(menuTree.value, menuIds))
  } catch (error) {
    roleMenuError.value = toMessage(error, '角色菜单加载失败')
    roleMenuDialogVisible.value = true
  }
}

function collectCheckedMenuIds(): string[] {
  const checked: Array<string | number> = roleMenuTreeRef.value?.getCheckedKeys?.() || []
  return [...new Set(checked.map((item: string | number) => String(item)))]
}

async function submitRoleMenus() {
  if (!selectedMenuRole.value) {
    return
  }

  roleMenuSubmitting.value = true
  roleMenuError.value = ''
  try {
    await updateSystemRoleMenus(selectedMenuRole.value.id, {
      menuIds: collectCheckedMenuIds(),
    })
    ElMessage.success('角色菜单已更新')
    roleMenuDialogVisible.value = false
  } catch (error) {
    roleMenuError.value = toMessage(error, '保存角色菜单失败')
  } finally {
    roleMenuSubmitting.value = false
  }
}

/** 注册到列表 actions 的内置动作;assign-menus 是业务专属动作,走 extraActions */
const roleCrud: CrudPageOptions = {
  resourceName: '角色',
  buildInitial: (mode, row) => {
    if (mode === 'edit' && row) {
      const role = row as SystemRoleSummary
      const knownPermissions = new Set(permissionOptions.value.map((item) => item.value))
      return {
        roleCode: role.roleCode,
        roleName: role.roleName,
        permissionCodes: role.permissionCodes.filter((item) => knownPermissions.has(item)),
        customPermissionCodes: role.permissionCodes
          .filter((item) => !knownPermissions.has(item))
          .join(', '),
        status: role.status || 'active',
        remark: role.remark || '',
      }
    }
    return {
      roleCode: '',
      roleName: '',
      permissionCodes: [],
      customPermissionCodes: '',
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
    permissionCodes: collectRolePermissionCodes(form),
    status: form.status || 'active',
    remark: trimToUndefined(form.remark),
  }),
  buildUpdatePayload: (form) => ({
    roleName: String(form.roleName ?? '').trim(),
    permissionCodes: collectRolePermissionCodes(form),
    status: form.status || 'active',
    remark: trimToUndefined(form.remark),
  }),
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
    'role-assign-menus': { handler: (row) => void openRoleMenuDialog(row as SystemRoleSummary) },
  },
}

onMounted(() => {
  void ensureStatusDictLoaded()
  void ensurePermissionOptionsLoaded()
})
</script>
