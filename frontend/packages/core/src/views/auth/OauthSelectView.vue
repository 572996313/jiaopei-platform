<template>
  <LoginShell>
    <div class="page-kicker">第三方登录</div>
    <h2 class="form-title">选择登录账号</h2>
    <p class="oauth-desc">当前第三方账号绑定了多个系统账号，请选择本次要使用的身份。</p>

    <div v-if="status === 'loading'" class="oauth-state">
      <el-icon class="oauth-spinner"><Loading /></el-icon>
      <p class="oauth-tip">正在加载可选账号…</p>
    </div>

    <template v-else-if="status === 'error'">
      <el-alert :title="errorText" type="error" show-icon :closable="false" />
      <el-button class="oauth-back" type="primary" @click="backToLogin">返回登录</el-button>
    </template>

    <div v-else class="account-list">
      <button
        v-for="account in accounts"
        :key="account.key"
        type="button"
        class="account-item"
        :disabled="submittingKey !== ''"
        @click="choose(account)"
      >
        <span class="account-mark">{{ (account.displayName || '?').slice(0, 1) }}</span>
        <span class="account-meta">
          <span class="account-name">{{ account.displayName }}</span>
          <span class="account-sub">{{ subtitle(account) }}</span>
        </span>
        <span class="account-arrow">{{ submittingKey === account.key ? '…' : '›' }}</span>
      </button>
    </div>
  </LoginShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import {
  fetchOauthSelectOptions,
  selectOauthAccount,
  type AccountOptionView,
} from '@cornerstone/core/api/auth'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import LoginShell from '@cornerstone/core/components/LoginShell.vue'

const PORTAL_LABELS: Record<string, string> = {
  admin: '管理后台',
  student: '学生端',
  parent: '家长端',
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const status = ref<'loading' | 'ready' | 'error'>('loading')
const errorText = ref('')
const accounts = ref<AccountOptionView[]>([])
const ticket = ref('')
const submittingKey = ref('')

onMounted(async () => {
  ticket.value = ticketFromHash()
  if (!ticket.value) {
    fail('登录票据缺失，请重新发起第三方登录')
    return
  }
  try {
    const selection = await fetchOauthSelectOptions(ticket.value)
    accounts.value = selection.accounts
    if (!accounts.value.length) {
      fail('没有可登录的账号，请改用账号密码登录')
      return
    }
    status.value = 'ready'
  } catch (error) {
    fail(error instanceof Error ? error.message : '加载可选账号失败')
  }
})

function subtitle(account: AccountOptionView) {
  return [account.institutionName, PORTAL_LABELS[account.portal] || account.portal]
    .filter(Boolean)
    .join(' · ')
}

async function choose(account: AccountOptionView) {
  if (submittingKey.value) {
    return
  }
  submittingKey.value = account.key
  try {
    const result = await selectOauthAccount(ticket.value, account.key)
    auth.setSession(result.accessToken, result.tokenType || 'Bearer')
    await auth.bootstrap()
    if (auth.mustChangePassword) {
      router.replace('/change-password')
      return
    }
    ElMessage.success('登录成功')
    router.replace('/dashboard')
  } catch (error) {
    fail(error instanceof Error ? error.message : '登录失败，请重试')
  } finally {
    submittingKey.value = ''
  }
}

function ticketFromHash() {
  const raw = (route.hash || window.location.hash || '').replace(/^#/, '')
  return new URLSearchParams(raw).get('ticket') || ''
}

function fail(message: string) {
  status.value = 'error'
  errorText.value = message
}

function backToLogin() {
  router.replace('/login')
}
</script>

<style scoped>
.oauth-desc {
  margin: 0 0 18px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--login-ink-dim);
}

.oauth-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 28px 0 12px;
}

.oauth-spinner {
  font-size: 30px;
  color: var(--login-accent);
  animation: oauth-rotate 1s linear infinite;
}

@keyframes oauth-rotate {
  to {
    transform: rotate(360deg);
  }
}

.oauth-tip {
  margin: 0;
  font-size: 14px;
  color: var(--login-ink-dim);
}

.oauth-back {
  width: 100%;
  margin-top: 18px;
}

.account-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.account-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--login-line);
  border-radius: 8px;
  background: transparent;
  color: var(--login-ink-label);
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease;
}

.account-item:hover:not(:disabled) {
  border-color: var(--login-accent-line);
  background: color-mix(in srgb, var(--login-accent) 6%, transparent);
}

.account-item:disabled {
  cursor: default;
  opacity: 0.7;
}

.account-mark {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--login-accent);
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
}

.account-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.account-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--login-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-sub {
  font-size: 12px;
  color: var(--login-ink-dim);
}

.account-arrow {
  font-size: 20px;
  color: var(--login-ink-dim);
}
</style>
