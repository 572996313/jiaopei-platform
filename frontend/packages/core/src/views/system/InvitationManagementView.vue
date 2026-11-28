<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <!-- 页头 h1 已是"注册凭证";邀请码/激活码的差别在"类型"列里,不在表头复述一遍 -->
      <!-- 主操作按钮:放查询条件上方,条件与表格贴着看 -->
      <div class="table-toolbar">
        <el-button v-if="permissions.canManageInvitations" @click="openActivationDialog"
          >生成激活码</el-button
        >
        <el-button v-if="permissions.canManageInvitations" type="primary" @click="openInviteDialog"
          >创建邀请码</el-button
        >
        <el-button :icon="Refresh" circle :loading="loading" @click="reload()" />
      </div>

      <!-- 本表查询条件:统一筛选栏形态,查询/重置固定右侧,点按钮才触发 -->
      <FilterBar :loading="loading" @query="reload(1)" @reset="resetFilters">
        <el-input
          v-model="query.keyword"
          placeholder="凭证码 / 备注"
          clearable
          style="width: 220px"
          @keyup.enter="reload(1)"
        />
        <el-select v-model="query.kind" placeholder="类型" clearable style="width: 140px">
          <el-option label="邀请码" value="INVITE" />
          <el-option label="激活码" value="ACTIVATE" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="启用" value="active" />
          <el-option label="停用" value="disabled" />
        </el-select>
      </FilterBar>

      <TableFillBox v-slot="{ height }">
        <el-table
          ref="tableRef"
          :data="records"
          border
          row-key="id"
          highlight-current-row
          v-loading="loading"
          :height="height"
          :cell-class-name="sort.cellClass"
          @sort-change="sort.onSortChange"
        >
          <el-table-column
            prop="code"
            label="凭证码"
            min-width="170"
            fixed="left"
            sortable="custom"
          >
            <template #default="{ row }">
              <code class="invitation-code">{{ row.code }}</code>
              <el-button text type="primary" size="small" @click="copyCode(row.code)"
                >复制</el-button
              >
            </template>
          </el-table-column>
          <el-table-column prop="kind" label="类型" width="90" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="row.kind === 'INVITE' ? 'primary' : 'warning'" size="small">
                {{ row.kind === 'INVITE' ? '邀请码' : '激活码' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="机构" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.institutionName || row.institutionId }}</template>
          </el-table-column>
          <el-table-column label="角色 / 目标账号" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.kind === 'INVITE'">{{ row.roleName || row.roleCode || '-' }}</span>
              <span v-else>{{ row.username || row.userId || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="usedCount" label="用量" width="90" align="right" sortable="custom">
            <template #default="{ row }">
              {{ row.usedCount }} / {{ row.maxUses ?? '不限' }}
            </template>
          </el-table-column>
          <el-table-column prop="expiresAt" label="有效期至" width="170" sortable="custom">
            <template #default="{ row }">{{
              row.expiresAt ? formatDateTime(row.expiresAt) : '长期'
            }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="110" sortable="custom">
            <template #default="{ row }">
              <el-tag :type="availabilityTag(row)" size="small">{{
                availabilityLabel(row)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="remark"
            label="备注"
            min-width="140"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column
            v-if="permissions.canManageInvitations || hasRowActions()"
            label="操作"
            :width="permissions.canManageInvitations ? 180 : 120"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="permissions.canManageInvitations"
                text
                type="danger"
                :disabled="row.status !== 'active'"
                @click="handleDisable(row)"
                >停用</el-button
              >
              <!-- 业务模块登记的行操作(如小程序码):组件自取数据、自管弹窗,未装配时列为空 -->
              <component
                v-for="action in contributedInvitationActions(row.kind)"
                :is="action.component"
                :key="action.key"
                :row="row"
              />
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <el-pagination
        class="invitation-pagination"
        layout="total, prev, pager, next, sizes"
        :total="total"
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :page-sizes="[20, 50, 100]"
        @current-change="reload()"
        @size-change="reload(1)"
      />
    </article>

    <!-- 创建邀请码 -->
    <el-dialog v-model="inviteDialogVisible" title="创建邀请码" width="520px">
      <el-alert
        v-if="inviteFormError"
        :title="inviteFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form
        ref="inviteFormRef"
        :model="inviteForm"
        :rules="inviteRules"
        label-position="top"
        scroll-to-error
        @submit.prevent
      >
        <el-form-item label="机构" prop="institutionId">
          <el-select v-model="inviteForm.institutionId" filterable placeholder="选择机构">
            <el-option
              v-for="item in institutions"
              :key="item.id"
              :label="item.orgName || item.id"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="注册后分配的角色" prop="roleCode">
          <el-select v-model="inviteForm.roleCode" filterable placeholder="选择角色">
            <el-option
              v-for="role in roles"
              :key="role.id"
              :label="`${role.roleName}(${role.roleCode})`"
              :value="role.roleCode"
            />
          </el-select>
        </el-form-item>
        <div class="invite-form-grid">
          <el-form-item label="可用次数(留空不限)">
            <el-input-number
              v-model="inviteForm.maxUses"
              :min="1"
              placeholder="不限"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="有效天数(留空长期)">
            <el-input-number
              v-model="inviteForm.validDays"
              :min="1"
              placeholder="长期"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="inviteForm.remark" maxlength="500" placeholder="如 秋季班家长注册" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="inviteSubmitting" @click="submitInvite">创建</el-button>
      </template>
    </el-dialog>

    <!-- 生成激活码 -->
    <el-dialog v-model="activationDialogVisible" title="为待激活账号生成激活码" width="520px">
      <el-alert
        v-if="activationFormError"
        :title="activationFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form
        ref="activationFormRef"
        :model="activationForm"
        :rules="activationRules"
        label-position="top"
        scroll-to-error
        @submit.prevent
      >
        <el-form-item label="待激活账号" prop="userId">
          <el-select
            v-model="activationForm.userId"
            filterable
            placeholder="选择状态为待激活的账号"
            :loading="pendingLoading"
          >
            <el-option
              v-for="user in pendingUsers"
              :key="user.id"
              :label="`${user.displayName}(${user.username})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <p class="activation-tip">
          待激活账号在「用户管理」里以状态 pending_activation 创建(如批量导入的家长账号)。
          同一账号重复申请会返回仍在有效期内的原激活码。
        </p>
      </el-form>
      <template #footer>
        <el-button @click="activationDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="activationSubmitting" @click="submitActivation">
          生成激活码
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { nextTick, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import { formatDateTime } from '@cornerstone/core/components/ConfigurableList/renderers'
import {
  createActivationCode,
  createInvite,
  disableInvitation,
  fetchInvitationPage,
  type InvitationSummary,
} from '@cornerstone/core/api/invitation'
import { fetchSystemRoles, fetchSystemUserPage } from '@cornerstone/core/api/system'
import type { SystemRoleSummary, SystemUserSummary } from '@cornerstone/core/api/system'
import { fetchInstitutions, type InstitutionSummary } from '@cornerstone/core/api/tenant'
import { contributedInvitationActions } from '@cornerstone/core/extension/module-extensions'
import { statusTagType, toMessage, trimToUndefined, useSystemPermissions } from './shared'

const permissions = useSystemPermissions()

/** 业务模块是否登记了行操作(登记发生在模块入口 import 时);没装业务模块且无管理权限时整列不出现 */
function hasRowActions() {
  return (
    contributedInvitationActions('INVITE').length +
      contributedInvitationActions('ACTIVATE').length >
    0
  )
}

const loading = ref(false)
const records = ref<InvitationSummary[]>([])
const total = ref(0)
const query = reactive({
  keyword: '',
  kind: '',
  status: '',
  page: 1,
  pageSize: 20,
})

/** 重置只清查询条件并回到第一页,分页大小保留 */
function resetFilters() {
  query.keyword = ''
  query.kind = ''
  query.status = ''
  void reload(1)
}

const tableRef = ref<{ clearSort: () => void } | null>(null)
const sort = useTableSort({
  onSorted: () => {
    void reload(1)
  },
  clearTableSort: () => tableRef.value?.clearSort(),
})

const institutions = ref<InstitutionSummary[]>([])
const roles = ref<SystemRoleSummary[]>([])
const pendingUsers = ref<SystemUserSummary[]>([])
const pendingLoading = ref(false)

const inviteDialogVisible = ref(false)
const inviteSubmitting = ref(false)
const inviteFormError = ref('')
const inviteFormRef = ref<FormInstance | null>(null)
const inviteForm = reactive({
  institutionId: '',
  roleCode: '',
  maxUses: undefined as number | undefined,
  validDays: undefined as number | undefined,
  remark: '',
})

const inviteRules: FormRules = {
  institutionId: [{ required: true, message: '请选择机构', trigger: 'change' }],
  roleCode: [{ required: true, message: '请选择注册后分配的角色', trigger: 'change' }],
}

const activationDialogVisible = ref(false)
const activationSubmitting = ref(false)
const activationFormError = ref('')
const activationFormRef = ref<FormInstance | null>(null)
const activationForm = reactive({ userId: '' })

const activationRules: FormRules = {
  userId: [{ required: true, message: '请选择待激活账号', trigger: 'change' }],
}

/** 派生"可用性"展示:凭证是否还能用,比原始 status 更贴近使用者视角 */
function availabilityLabel(row: InvitationSummary) {
  if (row.status !== 'active') {
    return '已停用'
  }
  if (row.expiresAt && new Date(row.expiresAt.replace(/-/g, '/')) < new Date()) {
    return '已过期'
  }
  if (row.maxUses != null && row.usedCount >= row.maxUses) {
    return '已用尽'
  }
  return '可用'
}

function availabilityTag(row: InvitationSummary) {
  return availabilityLabel(row) === '可用'
    ? 'success'
    : statusTagType(row.status) === 'success'
      ? 'info'
      : 'info'
}

async function reload(page?: number) {
  if (page) {
    query.page = page
  }
  loading.value = true
  try {
    const result = await fetchInvitationPage({
      keyword: trimToUndefined(query.keyword),
      kind: trimToUndefined(query.kind),
      status: trimToUndefined(query.status),
      page: query.page,
      pageSize: query.pageSize,
      ...sort.sortParams(),
    })
    records.value = result.records
    total.value = result.total
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(toMessage(error, '注册凭证加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [institutionList, roleList] = await Promise.all([fetchInstitutions(), fetchSystemRoles()])
    institutions.value = institutionList
    roles.value = roleList
  } catch {
    // 选项加载失败不阻断列表,弹窗里再提示
  }
}

async function loadPendingUsers() {
  pendingLoading.value = true
  try {
    const result = await fetchSystemUserPage({
      status: 'pending_activation',
      page: 1,
      pageSize: 100,
    })
    pendingUsers.value = result.records
  } catch (error) {
    pendingUsers.value = []
    activationFormError.value = toMessage(error, '待激活账号加载失败')
  } finally {
    pendingLoading.value = false
  }
}

function openInviteDialog() {
  inviteDialogModeGuard()
  inviteForm.institutionId = institutions.value.length === 1 ? institutions.value[0].id : ''
  inviteForm.roleCode = ''
  // 默认给"1 次/7 天"并真正进表单:之前只写 placeholder,提交的是空值,
  // 后端按"不限/长期"落库,与界面展示的 1/7 相反。要不限/长期就清空对应输入框。
  inviteForm.maxUses = 1
  inviteForm.validDays = 7
  inviteForm.remark = ''
  inviteFormError.value = ''
  inviteDialogVisible.value = true
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => inviteFormRef.value?.clearValidate())
}

function inviteDialogModeGuard() {
  // 选项还没加载成功时先补一次,避免下拉空白
  if (institutions.value.length === 0) {
    void loadOptions()
  }
}

async function submitInvite() {
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await inviteFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  inviteSubmitting.value = true
  inviteFormError.value = ''
  try {
    const created = await createInvite({
      institutionId: inviteForm.institutionId,
      roleCode: inviteForm.roleCode,
      maxUses: inviteForm.maxUses,
      validDays: inviteForm.validDays,
      remark: trimToUndefined(inviteForm.remark),
    })
    inviteDialogVisible.value = false
    ElMessage.success(`邀请码已创建:${created.code}`)
    await reload(1)
  } catch (error) {
    inviteFormError.value = toMessage(error, '创建邀请码失败')
  } finally {
    inviteSubmitting.value = false
  }
}

function openActivationDialog() {
  activationForm.userId = ''
  activationFormError.value = ''
  activationDialogVisible.value = true
  void loadPendingUsers()
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => activationFormRef.value?.clearValidate())
}

async function submitActivation() {
  // 校验失败时 el-form 已经在字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await activationFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  activationSubmitting.value = true
  activationFormError.value = ''
  try {
    const created = await createActivationCode({ userId: activationForm.userId })
    activationDialogVisible.value = false
    ElMessage.success(`激活码已生成:${created.code}`)
    await reload(1)
  } catch (error) {
    activationFormError.value = toMessage(error, '生成激活码失败')
  } finally {
    activationSubmitting.value = false
  }
}

async function handleDisable(row: InvitationSummary) {
  try {
    await ElMessageBox.confirm(
      `确定停用凭证「${row.code}」吗?停用后持有者将无法再注册或激活。`,
      '停用确认',
      { confirmButtonText: '停用', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await disableInvitation(row.id)
    ElMessage.success('凭证已停用')
    await reload()
  } catch (error) {
    ElMessage.error(toMessage(error, '停用凭证失败'))
  }
}

async function copyCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('凭证码已复制')
  } catch {
    ElMessage.warning('浏览器不支持自动复制,请手动选中复制')
  }
}

onMounted(async () => {
  await Promise.allSettled([reload(1), loadOptions()])
})
</script>

<style scoped>
.invitation-code {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 13px;
  color: var(--tint-ink);
  background: var(--tint-bg);
  border-radius: 4px;
  padding: 1px 6px;
}

.invite-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.activation-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.invitation-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
