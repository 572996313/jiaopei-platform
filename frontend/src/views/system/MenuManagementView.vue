<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <article v-if="menuSectionError" class="education-empty">
        {{ menuSectionError }}
      </article>
      <template v-else>
        <div class="education-table-head">
          <div class="panel-title">
            菜单与按钮<span class="panel-count">共 {{ flattenedMenus.length }} 条</span>
          </div>
          <div class="table-actions">
            <span class="education-context-text">
              菜单节点决定页面可见性,按钮节点承载接口权限码;在角色管理的「分配菜单」里勾选按钮即授权。
            </span>
            <el-button
              v-if="permissions.canManageMenus"
              type="primary"
              @click="openCreateMenuDialog"
              >新增菜单</el-button
            >
          </div>
        </div>

        <el-table
          :data="flattenedMenus"
          border
          row-key="id"
          :tree-props="{ children: 'children' }"
          v-loading="menuTreeLoading"
          :height="TABLE_FILL_HEIGHT"
        >
          <el-table-column prop="menuName" label="名称" min-width="180" />
          <el-table-column prop="menuCode" label="编码" width="180" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ menuTypeLabel(row.menuType) }}</template>
          </el-table-column>
          <el-table-column prop="path" label="路径" min-width="160" />
          <el-table-column label="权限码" min-width="170">
            <template #default="{ row }">
              <el-tooltip v-if="row.permissionCode" :content="row.permissionCode" placement="top">
                <span>{{ permissionOptionLabel(row.permissionCode || '') }}</span>
              </el-tooltip>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="可见" width="80">
            <template #default="{ row }">{{ row.visible === false ? '否' : '是' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)">{{
                statusLabel(row.status, statusLabelMap)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" />
          <el-table-column v-if="permissions.canManageMenus" label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <div class="system-operation-group">
                <el-button text type="primary" @click="openEditMenuDialog(row)">编辑</el-button>
                <el-button text type="danger" @click="handleDeleteMenu(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </article>

    <el-dialog
      v-model="menuDialogVisible"
      :title="menuDialogMode === 'create' ? '新增菜单节点' : '编辑菜单节点'"
      width="620px"
    >
      <el-alert
        v-if="menuFormError"
        :title="menuFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <div class="system-form-grid">
          <el-form-item label="父节点">
            <el-tree-select
              v-model="menuForm.parentId"
              :data="menuTree"
              node-key="id"
              check-strictly
              default-expand-all
              :props="{ label: 'menuName', children: 'children' }"
              placeholder="不选则为顶级节点"
              clearable
            />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="menuForm.menuType">
              <el-option
                v-for="item in menuTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="编码">
            <el-input
              v-model="menuForm.menuCode"
              :disabled="menuDialogMode === 'edit'"
              placeholder="如 system_users_manage"
            />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="menuForm.menuName" placeholder="按钮节点请填写中文权限名" />
          </el-form-item>
          <el-form-item label="权限码">
            <el-select
              v-model="menuForm.permissionCode"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="按钮节点必填,可从已知码中选择"
            >
              <el-option
                v-for="item in permissionOptions"
                :key="item.value"
                :label="`${item.label}(${item.value})`"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="menuForm.sortNo" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="路径">
            <el-input
              v-model="menuForm.path"
              :disabled="menuForm.menuType === 'button'"
              placeholder="如 /system/users"
            />
          </el-form-item>
          <el-form-item label="前端组件">
            <el-input
              v-model="menuForm.component"
              :disabled="menuForm.menuType === 'button'"
              placeholder="如 system/users"
            />
          </el-form-item>
          <el-form-item label="图标">
            <el-select
              v-model="menuForm.icon"
              filterable
              clearable
              placeholder="选择图标,用于侧边栏展示"
              style="width: 100%"
            >
              <el-option
                v-for="item in menuIconOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
                <span class="menu-icon-option">
                  <el-icon v-if="resolveMenuIcon(item.value)"
                    ><component :is="resolveMenuIcon(item.value)"
                  /></el-icon>
                  <span>{{ item.label }}</span>
                </span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="menuForm.status">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="侧边栏可见">
            <el-switch v-model="menuForm.visible" />
          </el-form-item>
        </div>

        <el-form-item label="备注">
          <el-input
            v-model="menuForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="补充节点用途说明"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="menuSubmitting" @click="submitMenu">
          {{ menuDialogMode === 'create' ? '创建节点' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { TABLE_FILL_HEIGHT } from '@/utils/table-view'
import { computed, onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  createSystemMenu,
  deleteSystemMenu,
  fetchSystemMenusTree,
  updateSystemMenu,
} from '@/api/system'
import type { SystemMenuTreeNode } from '@/api/system'
import { MENU_ICON_OPTIONS, normalizeMenuIconKey, resolveMenuIcon } from '@/constants/menuIcons'
import {
  ensurePermissionOptionsLoaded,
  permissionOptionLabel,
  usePermissionOptionList,
} from '@/stores/permissionOptions'
import {
  ensureStatusDictLoaded,
  menuTypeLabel,
  statusLabel,
  statusTagType,
  toMessage,
  trimToUndefined,
  useStatusLabelMap,
  useStatusOptions,
  useSystemPermissions,
} from './shared'

const auth = useAuthStore()
const permissions = useSystemPermissions()
const statusOptions = useStatusOptions()
const statusLabelMap = useStatusLabelMap()

const permissionOptions = usePermissionOptionList()

const menuTreeLoading = ref(false)
const menuSectionError = ref('')
const menuTree = ref<SystemMenuTreeNode[]>([])

const menuDialogVisible = ref(false)
const menuDialogMode = ref<'create' | 'edit'>('create')
const menuSubmitting = ref(false)
const menuFormError = ref('')
const selectedMenuNode = ref<SystemMenuTreeNode | null>(null)

const menuTypeOptions = [
  { value: 'catalog', label: '目录' },
  { value: 'menu', label: '菜单' },
  { value: 'button', label: '按钮' },
]

const menuIconOptions = MENU_ICON_OPTIONS

const menuForm = reactive({
  parentId: '' as string | null,
  menuCode: '',
  menuName: '',
  menuType: 'menu',
  path: '',
  component: '',
  permissionCode: '',
  icon: '',
  sortNo: 0,
  visible: true,
  status: 'active',
  remark: '',
})

const flattenedMenus = computed(() => flattenMenuTree(menuTree.value))

function flattenMenuTree(items: SystemMenuTreeNode[]): SystemMenuTreeNode[] {
  return items.map((item) => ({
    ...item,
    children: item.children ? flattenMenuTree(item.children) : [],
  }))
}

function openCreateMenuDialog() {
  menuDialogMode.value = 'create'
  selectedMenuNode.value = null
  menuForm.parentId = ''
  menuForm.menuCode = ''
  menuForm.menuName = ''
  menuForm.menuType = 'menu'
  menuForm.path = ''
  menuForm.component = ''
  menuForm.permissionCode = ''
  menuForm.icon = ''
  menuForm.sortNo = 0
  menuForm.visible = true
  menuForm.status = statusOptions.value[0]?.value || 'active'
  menuForm.remark = ''
  menuFormError.value = ''
  menuDialogVisible.value = true
}

function openEditMenuDialog(node: SystemMenuTreeNode) {
  menuDialogMode.value = 'edit'
  selectedMenuNode.value = node
  menuForm.parentId = node.parentId || ''
  menuForm.menuCode = node.menuCode
  menuForm.menuName = node.menuName
  menuForm.menuType = node.menuType
  menuForm.path = node.path || ''
  menuForm.component = node.component || ''
  menuForm.permissionCode = node.permissionCode || ''
  menuForm.icon = normalizeMenuIconKey(node.icon) || ''
  menuForm.sortNo = node.sortNo ?? 0
  menuForm.visible = node.visible !== false
  menuForm.status = node.status || 'active'
  menuForm.remark = node.remark || ''
  menuFormError.value = ''
  menuDialogVisible.value = true
}

async function loadMenuTree() {
  menuTreeLoading.value = true
  menuSectionError.value = ''
  try {
    menuTree.value = await fetchSystemMenusTree()
  } catch (error) {
    menuTree.value = []
    menuSectionError.value = toMessage(error, '菜单树加载失败')
  } finally {
    menuTreeLoading.value = false
  }
}

async function reloadMenuTree() {
  menuTree.value = await fetchSystemMenusTree()
}

async function submitMenu() {
  if (menuDialogMode.value === 'create' && !trimToUndefined(menuForm.menuCode)) {
    menuFormError.value = '请填写菜单编码'
    return
  }
  if (!trimToUndefined(menuForm.menuName)) {
    menuFormError.value = '请填写菜单名称'
    return
  }
  if (menuForm.menuType === 'button' && !trimToUndefined(menuForm.permissionCode)) {
    menuFormError.value = '按钮节点必须绑定权限码'
    return
  }

  menuSubmitting.value = true
  menuFormError.value = ''
  try {
    if (menuDialogMode.value === 'create') {
      await createSystemMenu({
        parentId: menuForm.parentId || null,
        menuCode: menuForm.menuCode.trim(),
        menuName: menuForm.menuName.trim(),
        menuType: menuForm.menuType,
        path: trimToUndefined(menuForm.path),
        component: trimToUndefined(menuForm.component),
        permissionCode: trimToUndefined(menuForm.permissionCode),
        icon: trimToUndefined(menuForm.icon),
        sortNo: menuForm.sortNo ?? 0,
        visible: menuForm.visible,
        status: menuForm.status,
        remark: trimToUndefined(menuForm.remark),
      })
      ElMessage.success('菜单已创建')
    } else if (selectedMenuNode.value) {
      await updateSystemMenu(selectedMenuNode.value.id, {
        parentId: menuForm.parentId || null,
        menuName: menuForm.menuName.trim(),
        menuType: menuForm.menuType,
        path: trimToUndefined(menuForm.path),
        component: trimToUndefined(menuForm.component),
        permissionCode: trimToUndefined(menuForm.permissionCode),
        icon: trimToUndefined(menuForm.icon),
        sortNo: menuForm.sortNo ?? 0,
        visible: menuForm.visible,
        status: menuForm.status,
        remark: trimToUndefined(menuForm.remark),
      })
      ElMessage.success('菜单已更新')
    }
    menuDialogVisible.value = false
    await reloadMenuTree()
    await auth.loadCurrentMenus()
  } catch (error) {
    menuFormError.value = toMessage(
      error,
      menuDialogMode.value === 'create' ? '创建菜单失败' : '编辑菜单失败'
    )
  } finally {
    menuSubmitting.value = false
  }
}

async function handleDeleteMenu(node: SystemMenuTreeNode) {
  try {
    await ElMessageBox.confirm(`确定删除菜单「${node.menuName}」吗?`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await deleteSystemMenu(node.id)
    ElMessage.success('菜单已删除')
    await reloadMenuTree()
    await auth.loadCurrentMenus()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除菜单失败'))
  }
}

onMounted(async () => {
  await Promise.allSettled([
    ensureStatusDictLoaded(),
    ensurePermissionOptionsLoaded(),
    loadMenuTree(),
  ])
})
</script>
