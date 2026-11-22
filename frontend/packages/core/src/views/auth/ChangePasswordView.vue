<template>
  <LoginShell>
    <div class="page-kicker">修改密码</div>
    <h2 class="form-title">修改登录密码</h2>
    <p class="login-form-tip">
      当前账号：{{ auth.currentUser?.username || '-' }} / {{ auth.displayName }}
    </p>

    <el-alert
      v-if="auth.mustChangePassword"
      title="当前账号仍处于强制改密状态，完成修改前不能进入业务首页。"
      type="warning"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <ChangePasswordForm
      :cancelable="!auth.mustChangePassword"
      submit-label="提交新密码"
      @cancel="goBack"
      @success="finish"
    />
  </LoginShell>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import LoginShell from '@cornerstone/core/components/LoginShell.vue'
import ChangePasswordForm from '@cornerstone/core/components/ChangePasswordForm.vue'
import { useAuthStore } from '@cornerstone/core/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function resolveRedirect() {
  if (typeof route.query.redirect === 'string' && route.query.redirect !== '/change-password') {
    return route.query.redirect
  }
  return '/dashboard'
}

function goBack() {
  router.push(resolveRedirect())
}

function finish() {
  router.replace(resolveRedirect())
}
</script>
