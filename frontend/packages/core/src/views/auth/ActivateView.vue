<template>
  <div class="login-shell">
    <section class="login-panel login-panel-brand">
      <div class="login-kicker">账号激活</div>
      <h1 class="login-title">预建账号激活</h1>
      <p class="login-copy">
        管理员已为你创建好账号，凭激活码设置初始密码后即可进入系统。
      </p>

      <div class="login-feature-list">
        <div class="login-feature-card">
          <div class="login-feature-title">账号信息不变</div>
          <div class="login-feature-text">
            激活只设置登录密码，账号、机构与角色沿用管理员预建的信息。
          </div>
        </div>
        <div class="login-feature-card">
          <div class="login-feature-title">一次提交即登录</div>
          <div class="login-feature-text">
            激活成功后自动签发登录态，无需再回到登录页输入密码。
          </div>
        </div>
      </div>
    </section>

    <section class="login-panel login-panel-form">
      <div class="form-card">
        <div class="page-kicker">激活账号</div>
        <h2 class="form-title">使用激活码设置密码</h2>

        <el-alert
          v-if="precheck"
          :title="`激活码有效：${precheck.institutionName || '-'}${precheck.expiresAt ? `，有效期至 ${formatExpiry(precheck.expiresAt)}` : ''}`"
          type="success"
          show-icon
          :closable="false"
          class="education-alert"
        />
        <el-alert
          v-if="precheckError"
          :title="precheckError"
          type="warning"
          show-icon
          :closable="false"
          class="education-alert"
        />
        <el-alert
          v-if="formError"
          :title="formError"
          type="error"
          show-icon
          :closable="false"
          class="education-alert"
        />

        <el-form label-position="top" @submit.prevent="submitActivate">
          <el-form-item label="激活码">
            <div class="invitation-code-row">
              <el-input v-model="form.code" placeholder="请输入激活码" @blur="runPrecheck" />
              <el-button :loading="prechecking" @click="runPrecheck">预检</el-button>
            </div>
          </el-form-item>
          <el-form-item label="初始密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入新密码"
            />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
            />
          </el-form-item>

          <div class="change-password-actions">
            <el-button @click="goLogin">返回登录</el-button>
            <el-button
              class="login-submit"
              type="primary"
              :loading="submitting"
              @click="submitActivate"
            >
              激活并进入系统
            </el-button>
          </div>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  activateAccount,
  precheckInvitation,
  type InvitationPrecheck,
} from '@cornerstone/core/api/invitation'
import { useAuthStore } from '@cornerstone/core/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const submitting = ref(false)
const prechecking = ref(false)
const formError = ref('')
const precheck = ref<InvitationPrecheck | null>(null)
const precheckError = ref('')
const form = reactive({
  code: typeof route.query.code === 'string' ? route.query.code : '',
  password: '',
  confirmPassword: '',
})

onMounted(() => {
  // 链接里带码（管理员分享的激活链接）时自动预检一次
  if (form.code) runPrecheck()
})

function formatExpiry(value: string) {
  return value.replace('T', ' ').slice(0, 16)
}

async function runPrecheck() {
  precheck.value = null
  precheckError.value = ''
  const code = form.code.trim()
  if (!code) return
  prechecking.value = true
  try {
    const result = await precheckInvitation(code)
    if (!result.valid) {
      precheckError.value = result.message || '激活码无效或已不可用'
      return
    }
    if (result.kind !== 'ACTIVATE') {
      precheckError.value = '该凭证是邀请码，请前往注册页面创建账号。'
      return
    }
    precheck.value = result
  } catch (error) {
    precheckError.value = error instanceof Error ? error.message : '激活码校验失败'
  } finally {
    prechecking.value = false
  }
}

function goLogin() {
  router.push('/login')
}

async function submitActivate() {
  const code = form.code.trim()
  if (!code || !form.password || !form.confirmPassword) {
    formError.value = '请填写激活码并两次输入新密码'
    return
  }
  if (form.password !== form.confirmPassword) {
    formError.value = '两次输入的新密码不一致'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    const session = await activateAccount({ code, password: form.password })
    auth.setSession(session.accessToken, session.tokenType || 'Bearer')
    await auth.bootstrap()
    ElMessage.success('账号激活成功，已进入系统')
    if (session.forceChangePassword) {
      router.replace('/change-password')
    } else {
      router.replace('/dashboard')
    }
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '激活失败'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.invitation-code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.invitation-code-row .el-input {
  flex: 1;
}
</style>
