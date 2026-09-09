<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div class="panel-title">角色列表<span class="panel-count">共 {{ roleTotal }} 条</span></div>
        <div class="table-actions">
          <span class="education-context-text">支持角色维护,并可进一步分配菜单树。</span>
          <el-button v-if="permissions.canManageRoles" type="primary" @click="openCreateRoleDialog">新增角色</el-button>
        </div>
      </div>

      <ConfigurableList
        ref="roleListRef"
        resource-code="system-roles"
        :actions-width="260"
        @loaded="onRolesLoaded"
      >
        <template #actions="{ row }">
          <el-button v-if="permissions.canManageRoles" text type="primary" @click="openEditRoleDialog(row)">编辑</el-button>
          <el-button v-if="permissions.canManageMenus" text @click="openRoleMenuDialog(row)">分配菜单</el-button>
          <el-button v-if="permissions.canManageRoles" text @click="toggleRoleStatus(row)">
            {{ row.status === 'active' ? '停用' : '启用' }}
          </el-button>
        </template>
      </ConfigurableList>
    </article>

    <el-dialog v-model="roleDialogVisible" :title="roleDialogMode === 'create' ? '新增角色' : '编辑角色'" width="640px">
      <el-alert v-if="roleFormError" :title="roleFormError" type="error" show-icon :closable="false" class="education-alert" />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="角色编码">
            <el-input v-model="roleForm.roleCode" :disabled="roleDialogMode === 'edit'" placeholder="请输入角色编码" />
          </el-form-item>
          <el-form-item label="角色名称">
            <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="roleForm.status">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="预设权限">
          <el-checkbox-group v-model="roleForm.permissionCodes">
            <div v-for="group in permissionGroupData" :key="group.group" class="system-permission-group">
              <div class="system-permission-group-title">{{ group.group }}</div>
              <div class="system-permission-grid">
                <el-checkbox v-for="item in group.items" :key="item.code" :label="item.code">
                  {{ item.label }}
                </el-checkbox>
              </div>
            </div>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="补充权限码">
          <el-input
            v-model="roleForm.customPermissionCodes"
            type="textarea"
            :rows="3"
            placeholder="可输入额外权限码,使用逗号或换行分隔"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="roleForm.remark" type="textarea" :rows="3" placeholder="补充角色说明" />
        </el-form-item>
      </el-form>

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
import { nextTick, onMounted, reactive, ref } from 'vue'
import {
  createSystemRole,
  fetchSystemMenusTree,
  fetchSystemRoleMenuIds,
  updateSystemRole,
  updateSystemRoleMenus,
} from '@/api/system'
import type { SystemMenuTreeNode, SystemRoleSummary } from '@/api/system'
import ConfigurableList from '@/components/ConfigurableList/index.vue'
import { permissionGroups } from '@/constants/permissionLabels'
import {
  ensureStatusDictLoaded,
  parseCustomPermissionCodes,
  toMessage,
  trimToUndefined,
  useStatusOptions,
  useSystemPermissions,
} from './shared'

const permissions = useSystemPermissions()
const statusOptions = useStatusOptions()

const permissionGroupData = permissionGroups()
const permissionOptions = permissionGroupData.flatMap((group) =>
  group.items.map((item) => ({ value: item.code, label: item.label })),
)

const roleListRef = ref<InstanceType<typeof ConfigurableList> | null>(null)
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

const roleForm = reactive({
  roleCode: '',
  roleName: '',
  permissionCodes: [] as string[],
  customPermissionCodes: '',
  status: 'active',
  remark: '',
})

function resetRoleForm() {
  roleForm.roleCode = ''
  roleForm.roleName = ''
  roleForm.permissionCodes = []
  roleForm.customPermissionCodes = ''
  roleForm.status = statusOptions.value[0]?.value || 'active'
  roleForm.remark = ''
  roleFormError.value = ''
}

function openCreateRoleDialog() {
  roleDialogMode.value = 'create'
  selectedRole.value = null
  resetRoleForm()
  roleDialogVisible.value = true
}

function openEditRoleDialog(role: SystemRoleSummary) {
  roleDialogMode.value = 'edit'
  selectedRole.value = role
  const knownPermissions = new Set(permissionOptions.map((item) => item.value))
  roleForm.roleCode = role.roleCode
  roleForm.roleName = role.roleName
  roleForm.permissionCodes = role.permissionCodes.filter((item) => knownPermissions.has(item))
  roleForm.customPermissionCodes = role.permissionCodes.filter((item) => !knownPermissions.has(item)).join(', ')
  roleForm.status = role.status || 'active'
  roleForm.remark = role.remark || ''
  roleFormError.value = ''
  roleDialogVisible.value = true
}

function collectRolePermissionCodes() {
  return [...new Set([...roleForm.permissionCodes, ...parseCustomPermissionCodes(roleForm.customPermissionCodes)])]
}

async function submitRole() {
  if (!trimToUndefined(roleForm.roleCode) && roleDialogMode.value === 'create') {
    roleFormError.value = '请填写角色编码'
    return
  }
  if (!trimToUndefined(roleForm.roleName)) {
    roleFormError.value = '请填写角色名称'
    return
  }

  const permissionCodes = collectRolePermissionCodes()
  if (permissionCodes.length === 0) {
    roleFormError.value = '请至少选择或输入一个权限码'
    return
  }

  roleSubmitting.value = true
  roleFormError.value = ''
  try {
    if (roleDialogMode.value === 'create') {
      await createSystemRole({
        roleCode: roleForm.roleCode.trim(),
        roleName: roleForm.roleName.trim(),
        permissionCodes,
        status: roleForm.status,
        remark: trimToUndefined(roleForm.remark),
      })
      ElMessage.success('角色已创建')
    } else if (selectedRole.value) {
      await updateSystemRole(selectedRole.value.id, {
        roleName: roleForm.roleName.trim(),
        permissionCodes,
        status: roleForm.status,
        remark: trimToUndefined(roleForm.remark),
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

onMounted(() => {
  void ensureStatusDictLoaded()
})
</script>
