<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <article v-if="sectionError" class="education-empty">{{ sectionError }}</article>
      <template v-else>
        <div class="table-toolbar">
          <el-button @click="guideVisible = true">帮助</el-button>
          <el-button v-if="permissions.canManageAuthProviders" type="primary" @click="openCreate">
            新增渠道
          </el-button>
          <el-button :icon="Refresh" circle :loading="loading" @click="load" />
          <div class="education-context-text">
            登录页的第三方按钮来自这里；停用的渠道不会出现在登录页。
          </div>
        </div>

        <TableFillBox v-slot="{ height }">
          <el-table
            :data="records"
            border
            row-key="id"
            highlight-current-row
            v-loading="loading"
            :height="height"
          >
            <el-table-column label="渠道" min-width="180" fixed="left">
              <template #default="{ row }">
                <div class="provider-name">{{ row.providerName }}</div>
                <code class="provider-code">{{ row.providerCode }}</code>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="120">
              <template #default="{ row }">{{ typeLabel(row.providerType) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                  {{ row.status === 'active' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Client ID" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">{{ row.clientId || '-' }}</template>
            </el-table-column>
            <el-table-column label="密钥" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.hasClientSecret" size="small">已配置</el-tag>
                <span v-else class="provider-muted">未配置</span>
              </template>
            </el-table-column>
            <el-table-column label="回调地址" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <code v-if="row.redirectUri" class="provider-url">{{ row.redirectUri }}</code>
                <span v-else class="provider-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="sortNo" label="排序" width="70" align="right" />
            <el-table-column label="备注" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
            <el-table-column
              v-if="permissions.canManageAuthProviders"
              label="操作"
              width="130"
              fixed="right"
            >
              <template #default="{ row }">
                <div class="system-operation-group">
                  <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
                  <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </TableFillBox>
      </template>
    </article>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增第三方登录渠道' : '编辑第三方登录渠道'"
      width="820px"
    >
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="education-form"
        scroll-to-error
        @submit.prevent
      >
        <div class="provider-form-grid">
          <el-form-item label="渠道编码" prop="providerCode">
            <el-input
              v-model="form.providerCode"
              :disabled="dialogMode === 'edit'"
              placeholder="如 linuxdo，建后不可改"
            />
          </el-form-item>
          <el-form-item label="渠道名称" prop="providerName">
            <el-input v-model="form.providerName" maxlength="64" placeholder="登录页展示的名称" />
          </el-form-item>
        </div>
        <div class="provider-form-grid">
          <el-form-item label="渠道类型" prop="providerType">
            <el-select v-model="form.providerType" :disabled="dialogMode === 'edit'">
              <el-option
                v-for="type in typeOptions"
                :key="type"
                :label="typeLabel(type)"
                :value="type"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="排序号">
            <el-input-number v-model="form.sortNo" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
        </div>
        <div class="provider-form-grid">
          <el-form-item label="图标标识(选填)">
            <el-input v-model="form.icon" maxlength="64" placeholder="如 linuxdo / wechat" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="启用" value="active" />
              <el-option label="停用" value="disabled" />
            </el-select>
          </el-form-item>
        </div>

        <template v-if="form.providerType === 'OAUTH2'">
          <el-divider content-position="left">OAuth2 端点与凭据</el-divider>
          <div class="provider-form-grid">
            <el-form-item label="Client ID" prop="clientId">
              <el-input v-model="form.clientId" maxlength="128" />
            </el-form-item>
            <el-form-item label="Client Secret" prop="clientSecret">
              <el-input
                v-model="form.clientSecret"
                type="password"
                show-password
                maxlength="512"
                :placeholder="secretPlaceholder"
              />
            </el-form-item>
          </div>
          <el-form-item label="授权地址" prop="authorizeUrl">
            <el-input v-model="form.authorizeUrl" maxlength="512" placeholder="https://…/oauth2/authorize" />
          </el-form-item>
          <div class="provider-form-grid">
            <el-form-item label="令牌地址" prop="tokenUrl">
              <el-input v-model="form.tokenUrl" maxlength="512" placeholder="https://…/oauth2/token" />
            </el-form-item>
            <el-form-item label="用户信息地址" prop="userinfoUrl">
              <el-input v-model="form.userinfoUrl" maxlength="512" placeholder="https://…/api/user" />
            </el-form-item>
          </div>
          <div class="provider-form-grid">
            <el-form-item label="授权范围(scope)">
              <el-input v-model="form.scope" maxlength="255" placeholder="如 user，多个用空格分隔" />
            </el-form-item>
            <el-form-item label="令牌认证方式">
              <el-select v-model="form.tokenAuthMethod">
                <el-option label="表单提交(post)" value="post" />
                <el-option label="HTTP Basic(basic)" value="basic" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="回调地址" prop="redirectUri">
            <el-input
              v-model="form.redirectUri"
              maxlength="512"
              placeholder="必须与渠道后台登记的一字不差"
            />
          </el-form-item>
          <el-form-item label="扩展配置(JSON，选填)" prop="extraConfig">
            <el-input
              v-model="form.extraConfig"
              type="textarea"
              :rows="3"
              placeholder='如 {"userIdField":"id","nicknameField":"name","avatarField":"avatar_template"}'
            />
          </el-form-item>
        </template>

        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ dialogMode === 'create' ? '创建渠道' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="guideVisible" title="第三方登录配置说明" width="720px">
      <div class="guide-section">
        <h4 class="guide-title">这一类配置做什么?</h4>
        <p class="guide-text">
          一个渠道一行：编码、名称、端点、Client ID / Secret 与回调地址。标准 OAuth2
          渠道填好就能在登录页出现「一键登录」，不用改代码、不用发版；停用的渠道不会出现在登录页。
        </p>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">接入 LinuxDo 的步骤</h4>
        <ul class="guide-list">
          <li>到 connect.linux.do 创建应用，登记回调地址（见下表）。</li>
          <li>把 Client ID / Secret、授权地址、令牌地址、用户信息地址填进来。</li>
          <li>状态改为「启用」并保存，登录页立即出现入口。</li>
          <li>用户首次登录若提示未绑定，先用账号密码登录，在「个人信息 → 第三方账号」完成绑定。</li>
        </ul>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">回调地址怎么填?</h4>
        <p class="guide-text">
          生产同源部署：<code>https://&lt;域名&gt;/api/auth/oauth2/&lt;渠道编码&gt;/callback</code>；
          本地前端与后端不同源时填前端源（如
          <code>http://127.0.0.1:5173/api/auth/oauth2/linuxdo/callback</code>），由 Vite 代理转发。
          渠道后台登记的地址必须与这里一字不差。
        </p>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">密钥为什么是空的?</h4>
        <p class="guide-text">
          服务端加密保存密钥，编辑时只回显「已配置」，不把密文发到浏览器。留空保存表示保持原值；
          要更换就重新填一个。
        </p>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  createAuthProvider,
  deleteAuthProvider,
  fetchAuthProviderTypes,
  fetchAuthProviders,
  updateAuthProvider,
} from '@cornerstone/core/api/system'
import type { AuthProviderSummary } from '@cornerstone/core/api/system'
import { toMessage, trimToUndefined, useSystemPermissions } from './shared'

const permissions = useSystemPermissions()

const loading = ref(false)
const sectionError = ref('')
const records = ref<AuthProviderSummary[]>([])
const providerTypes = ref<string[]>(['OAUTH2'])

const guideVisible = ref(false)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formError = ref('')
const selected = ref<AuthProviderSummary | null>(null)
const formRef = ref<FormInstance | null>(null)

const form = reactive({
  providerCode: '',
  providerName: '',
  providerType: 'OAUTH2',
  icon: '',
  sortNo: 0,
  status: 'disabled',
  clientId: '',
  clientSecret: '',
  authorizeUrl: '',
  tokenUrl: '',
  userinfoUrl: '',
  scope: '',
  tokenAuthMethod: 'post',
  redirectUri: '',
  extraConfig: '',
  remark: '',
})

/** 新增时只列已实现的类型；编辑既有行时把该行自己的类型补进来（如微信小程序无协议实现） */
const typeOptions = computed(() => {
  const list = [...providerTypes.value]
  if (form.providerType && !list.includes(form.providerType)) {
    list.push(form.providerType)
  }
  return list
})

const secretPlaceholder = computed(() =>
  dialogMode.value === 'edit' && selected.value?.hasClientSecret
    ? '已配置；留空表示保持原值'
    : '渠道后台提供的 Client Secret'
)

const rules: FormRules = {
  providerCode: [
    { required: true, whitespace: true, message: '请填写渠道编码', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_-]{1,63}$/,
      message: '只能用小写字母、数字、下划线和短横线，且以字母开头',
      trigger: 'blur',
    },
  ],
  providerName: [{ required: true, whitespace: true, message: '请填写渠道名称', trigger: 'blur' }],
  providerType: [{ required: true, message: '请选择渠道类型', trigger: 'change' }],
  clientId: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.status === 'active' && form.providerType === 'OAUTH2' && !value.trim()) {
          callback(new Error('启用前必须填写 Client ID'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  clientSecret: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.status !== 'active' || form.providerType !== 'OAUTH2') {
          callback()
          return
        }
        const existing = dialogMode.value === 'edit' && selected.value?.hasClientSecret
        if (!value.trim() && !existing) {
          callback(new Error('启用前必须填写 Client Secret'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  authorizeUrl: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.status === 'active' && form.providerType === 'OAUTH2' && !value.trim()) {
          callback(new Error('启用前必须填写授权地址'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  tokenUrl: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.status === 'active' && form.providerType === 'OAUTH2' && !value.trim()) {
          callback(new Error('启用前必须填写令牌地址'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  userinfoUrl: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.status === 'active' && form.providerType === 'OAUTH2' && !value.trim()) {
          callback(new Error('启用前必须填写用户信息地址'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  redirectUri: [
    {
      validator: (_rule, value: string, callback) => {
        if (form.status === 'active' && form.providerType === 'OAUTH2' && !value.trim()) {
          callback(new Error('启用前必须填写回调地址'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  extraConfig: [
    {
      validator: (_rule, value: string, callback) => {
        const trimmed = value.trim()
        if (!trimmed) {
          callback()
          return
        }
        try {
          const parsed = JSON.parse(trimmed)
          if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
            callback(new Error('扩展配置必须是 JSON 对象'))
            return
          }
        } catch {
          callback(new Error('扩展配置不是合法的 JSON'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function typeLabel(type: string) {
  if (type === 'OAUTH2') {
    return '标准 OAuth2'
  }
  if (type === 'WECHAT_MINIAPP') {
    return '微信小程序'
  }
  return type
}

async function load() {
  loading.value = true
  sectionError.value = ''
  try {
    records.value = await fetchAuthProviders()
  } catch (error) {
    records.value = []
    sectionError.value = toMessage(error, '第三方登录渠道加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTypes() {
  try {
    const types = await fetchAuthProviderTypes()
    if (types.length) {
      providerTypes.value = types
    }
  } catch {
    // 类型清单加载失败不影响列表；新增时仍可用默认的 OAUTH2
  }
}

function resetForm() {
  form.providerCode = ''
  form.providerName = ''
  form.providerType = providerTypes.value[0] || 'OAUTH2'
  form.icon = ''
  form.sortNo = 0
  form.status = 'disabled'
  form.clientId = ''
  form.clientSecret = ''
  form.authorizeUrl = ''
  form.tokenUrl = ''
  form.userinfoUrl = ''
  form.scope = ''
  form.tokenAuthMethod = 'post'
  form.redirectUri = ''
  form.extraConfig = ''
  form.remark = ''
  formError.value = ''
}

function openCreate() {
  dialogMode.value = 'create'
  selected.value = null
  resetForm()
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row: AuthProviderSummary) {
  dialogMode.value = 'edit'
  selected.value = row
  form.providerCode = row.providerCode
  form.providerName = row.providerName
  form.providerType = row.providerType
  form.icon = row.icon || ''
  form.sortNo = row.sortNo ?? 0
  form.status = row.status || 'disabled'
  form.clientId = row.clientId || ''
  form.clientSecret = ''
  form.authorizeUrl = row.authorizeUrl || ''
  form.tokenUrl = row.tokenUrl || ''
  form.userinfoUrl = row.userinfoUrl || ''
  form.scope = row.scope || ''
  form.tokenAuthMethod = row.tokenAuthMethod || 'post'
  form.redirectUri = row.redirectUri || ''
  form.extraConfig = row.extraConfig || ''
  form.remark = row.remark || ''
  formError.value = ''
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  const payload = {
    providerCode: form.providerCode.trim(),
    providerName: form.providerName.trim(),
    providerType: form.providerType,
    icon: trimToUndefined(form.icon),
    sortNo: form.sortNo,
    status: form.status,
    clientId: trimToUndefined(form.clientId),
    clientSecret: trimToUndefined(form.clientSecret),
    authorizeUrl: trimToUndefined(form.authorizeUrl),
    tokenUrl: trimToUndefined(form.tokenUrl),
    userinfoUrl: trimToUndefined(form.userinfoUrl),
    scope: trimToUndefined(form.scope),
    tokenAuthMethod: form.tokenAuthMethod || undefined,
    redirectUri: trimToUndefined(form.redirectUri),
    extraConfig: trimToUndefined(form.extraConfig),
    remark: trimToUndefined(form.remark),
  }

  submitting.value = true
  formError.value = ''
  try {
    if (dialogMode.value === 'create') {
      await createAuthProvider(payload)
      ElMessage.success('渠道已创建')
    } else if (selected.value) {
      await updateAuthProvider(selected.value.id, payload)
      ElMessage.success('渠道已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    formError.value = toMessage(
      error,
      dialogMode.value === 'create' ? '创建渠道失败' : '保存渠道失败'
    )
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: AuthProviderSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除渠道「${row.providerName}」吗?删除后该登录入口消失；仍有账号绑定时后端会拒绝。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteAuthProvider(row.id)
    ElMessage.success('渠道已删除')
    await load()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除渠道失败'))
  }
}

onMounted(async () => {
  await Promise.allSettled([loadTypes(), load()])
})
</script>

<style scoped>
.provider-name {
  font-size: 13px;
  color: var(--text-primary);
}

.provider-code,
.provider-url {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  color: var(--tint-ink);
  background: var(--tint-bg);
  border-radius: 4px;
  padding: 1px 6px;
}

.provider-muted {
  color: var(--text-secondary);
}

.provider-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.guide-section {
  margin-bottom: 18px;
}

.guide-title {
  margin: 0 0 6px;
  font-size: 14px;
  color: var(--tint-ink);
}

.guide-text {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-primary);
}

.guide-text code {
  background: var(--tint-bg);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 12px;
}

.guide-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-primary);
}
</style>
