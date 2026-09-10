<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">角色列表<span class="panel-count">共 {{ roleTotal }} 条</span></div>
        <div class="table-actions">
          <span class="education-context-text">支持角色维护,并可进一步分配菜单树。</span>
        </div>
      </div>

      <ConfigurableList
        ref="roleListRef"
        resource-code="system-roles"
        :actions="roleActionHandlers"
        @loaded="onRolesLoaded"
      />
    </article>

    <el-dialog v-model="roleDialogVisible" :title="roleDialogMode === 'create' ? '新增角色' : '编辑角色'" width="680px">
      <el-alert v-if="roleFormError" :title="roleFormError" type="error" show-icon :closable="false" class="education-alert" />

      <ConfigurableForm
        ref="roleFormRef"
        resource-code="system-roles"
        :mode="roleDialogMode"
        :initial="roleInitial"
        :context="roleFormContext"
      >
        <!-- permission-groups 复合控件:按权限分组渲染复选框网格 -->
        <template #field-permissionCodes="{ model, disabled }">
          <el-checkbox-group v-model="model.permissionCodes" :disabled="disabled">
            <div v-for="group in permissionGroupData" :key="group.group" class="system-permission-group">
              <div class="system-permission-group-title">{{ group.group }}</div>
              <div class="system-permission-grid">
                <el-checkbox v-for="item in group.items" :key="item.code" :value="item.code">
                  {{ item.label }}
                </el-checkbox>
              </div>
            </div>
          </el-checkbox-group>
        </template>
      </ConfigurableForm>

      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitRole">
          {{ roleDialogMode === 'create' ? '创建角色' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleMenuDialogVisible" title="分配菜单" width="620px">
      <div class="education-dialog-context">
        <div><strong>角色:</strong>{{ selectedMenuRole?.roleName || '-' }}</div>
        <div><strong>角色编码:</strong>{{ selectedMenuRole?.roleCode || '-' }}</div>
      </div>

      <el-alert v-if="roleMenuError" :title="roleMenuError" type="error" show-icon :closable="false" class="education-alert" />

      <div class="education-context-text">勾选按钮节点即授予该角色对应接口权限,保存后需重新登录生效。</div>

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
        <el-button type="primary" :loading="roleMenuSubmitting" @click="submitRoleMenus">保存菜单</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, ref } from 'vue'
import {
  createSystemRole,
  fetchSystemMenusTree,
  fetchSystemRoleMenuIds,
  updateSystemRole,
  updateSystemRoleMenus,
} from '@/api/system'
import type { SystemMenuTreeNode, SystemRoleSummary } from '@/api/system'
import ConfigurableList from '@/components/ConfigurableList/index.vue'
import ConfigurableForm from '@/components/ConfigurableForm/index.vue'
import type { ActionHandler } from '@/components/ConfigurableList/types'
import type { RenderContext } from '@/components/ConfigurableList/renderers'
import { permissionGroups } from '@/constants/permissionLabels'
import {
  ensureStatusDictLoaded,
  parseCustomPermissionCodes,
  toMessage,
  trimToUndefined,
  useStatusOptions,
} from './shared'

const statusOptions = useStatusOptions()

const permissionGroupData = permissionGroups()
const permissionOptions = permissionGroupData.flatMap((group) =>
  group.items.map((item) => ({ value: item.code, label: item.label })),
)

const roleListRef = ref<InstanceType<typeof ConfigurableList> | null>(null)
const roleFormRef = ref<InstanceType<typeof ConfigurableForm> | null>(null)
const roleTotal = ref(0)

function onRolesLoaded(payload: { total: number }) {
  roleTotal.value = payload.total
}

const roleDialogVisible = ref(false)
const roleDialogMode = ref<'create' | 'edit'>('create')
const roleSubmitting = ref(false)
const roleFormError = ref('')
const selectedRole = ref<SystemRoleSummary | null>(null)

const roleMenuDialogVisible = ref(false)
const roleMenuSubmitting = ref(false)
const roleMenuError = ref('')
const selectedMenuRole = ref<SystemRoleSummary | null>(null)
const roleMenuTreeRef = ref<any>(null)

const menuTree = ref<SystemMenuTreeNode[]>([])

/** 弹窗回填初值(编辑:权限码拆为"已知权限组勾选"与"自定义补充文本") */
const roleInitial = computed<Record<string, any>>(() => {
  if (roleDialogMode.value === 'edit' && selectedRole.value) {
    const role = selectedRole.value
    const knownPermissions = new Set(permissionOptions.map((item) => item.value))
    return {
      roleCode: role.roleCode,
      roleName: role.roleName,
      permissionCodes: role.permissionCodes.filter((item) => knownPermissions.has(item)),
      customPermissionCodes: role.permissionCodes.filter((item) => !knownPermissions.has(item)).join(', '),
      status: role.status || 'active',
      remark: role.remark || '',
    }
  }
  return {}
})

const roleFormContext = computed<RenderContext>(() => ({
  statusOptions: statusOptions.value,
}))

function openCreateRoleDialog() {
  roleDialogMode.value = 'create'
  selectedRole.value = null
  roleFormError.value = ''
  roleDialogVisible.value = true
  void roleFormRef.value?.reset()
}

function openEditRoleDialog(role: SystemRoleSummary) {
  roleDialogMode.value = 'edit'
  selectedRole.value = role
  roleFormError.value = ''
  roleDialogVisible.value = true
}

function collectRolePermissionCodes(form: Record<string, any>): string[] {
  const preset = Array.isArray(form.permissionCodes) ? form.permissionCodes : []
  const custom = typeof form.customPermissionCodes === 'string' ? form.customPermissionCodes : ''
  return [...new Set([...preset, ...parseCustomPermissionCodes(custom)])]
}

async function submitRole() {
  if (!roleFormRef.value) {
    return
  }
  const valid = await roleFormRef.value.validate()
  if (!valid) {
    roleFormError.value = '请检查表单填写是否完整'
    return
  }
  const form = roleFormRef.value.values()

  const permissionCodes = collectRolePermissionCodes(form)
  if (permissionCodes.length === 0) {
    roleFormError.value = '请至少选择或输入一个权限码'
    return
  }

  roleSubmitting.value = true
  roleFormError.value = ''
  try {
    if (roleDialogMode.value === 'create') {
      await createSystemRole({
        roleCode: String(form.roleCode ?? '').trim(),
        roleName: String(form.roleName ?? '').trim(),
        permissionCodes,
        status: form.status || 'active',
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('角色已创建')
    } else if (selectedRole.value) {
      await updateSystemRole(selectedRole.value.id, {
        roleName: String(form.roleName ?? '').trim(),
        permissionCodes,
        status: form.status || 'active',
        remark: trimToUndefined(form.remark),
      })
      ElMessage.success('角色已更新')
    }
    roleDialogVisible.value = false
    await roleListRef.value?.reload()
  } catch (error) {
    roleFormError.value = toMessage(error, roleDialogMode.value === 'create' ? '创建角色失败' : '编辑角色失败')
  } finally {
    roleSubmitting.value = false
  }
}

async function toggleRoleStatus(role: SystemRoleSummary) {
  const disabling = role.status === 'active'
  try {
    await ElMessageBox.confirm(
      `确定${disabling ? '停用' : '启用'}角色「${role.roleName}」吗?`,
      '操作确认',
      { type: 'warning', confirmButtonText: disabling ? '停用' : '启用', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    await updateSystemRole(role.id, {
      roleName: role.roleName,
      permissionCodes: role.permissionCodes,
      status: role.status === 'active' ? 'disabled' : 'active',
      remark: role.remark || undefined,
    })
    ElMessage.success(role.status === 'active' ? '角色已停用' : '角色已启用')
    await roleListRef.value?.reload()
  } catch (error) {
    ElMessage.error(toMessage(error, '角色状态更新失败'))
  }
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

/**
 * 按钮动作注册表:与 sys_list_config.actions_json 的 action 名一一对应,
 * label 提供配置无法表达的动态逻辑(停用/启用文案切换)。
 */
const roleActionHandlers: Record<string, ActionHandler> = {
  'role-create': { handler: () => openCreateRoleDialog() },
  'role-edit': { handler: (row) => openEditRoleDialog(row as SystemRoleSummary) },
  'role-assign-menus': { handler: (row) => void openRoleMenuDialog(row as SystemRoleSummary) },
  'role-toggle-status': {
    handler: (row) => void toggleRoleStatus(row as SystemRoleSummary),
    label: (row) => (row?.status === 'active' ? '停用' : '启用'),
  },
}

onMounted(() => {
  void ensureStatusDictLoaded()
})
</script>
