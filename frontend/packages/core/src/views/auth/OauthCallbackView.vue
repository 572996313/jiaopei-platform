<template>
  <LoginShell>
    <div class="page-kicker">第三方登录</div>
    <h2 class="form-title">{{ status === 'error' ? '登录未完成' : '正在完成登录' }}</h2>

    <div v-if="status === 'loading'" class="oauth-state">
      <el-icon class="oauth-spinner"><Loading /></el-icon>
      <p class="oauth-tip">正在换取访问令牌，请稍候…</p>
    </div>

    <template v-else>
      <el-alert :title="errorText" type="error" show-icon :closable="false" />
      <el-button class="oauth-back" type="primary" @click="backToLogin">返回登录</el-button>
    </template>
  </LoginShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { exchangeOauthTicket } from '@cornerstone/core/api/auth'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import LoginShell from '@cornerstone/core/components/LoginShell.vue'

/**
 * 第三方登录回调落点：票据放在 URL fragment（#ticket=…），
 * 只在这里用 JS 读出并 POST 换令牌，令牌与票据都不进查询串。
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const status = ref<'loading' | 'error'>('loading')
const errorText = ref('')

onMounted(async () => {
  const ticket = ticketFromHash()
  if (!ticket) {
    fail('登录票据缺失，请重新发起第三方登录')
    return
  }
  try {
    const result = await exchangeOauthTicket(ticket)
    auth.setSession(result.accessToken, result.tokenType || 'Bearer')
    await auth.bootstrap()
    if (auth.mustChangePassword) {
      router.replace('/change-password')
      return
    }
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (error) {
    fail(error instanceof Error ? error.message : '第三方登录失败，请重试')
  }
})

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
</style>
