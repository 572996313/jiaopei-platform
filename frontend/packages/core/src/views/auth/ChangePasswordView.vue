<template>
  <div class="login-shell">
    <section class="login-panel login-panel-brand">
      <div class="login-kicker">账号安全</div>
      <h1 class="login-title">账号安全校验</h1>
      <p class="login-copy">
        {{
          auth.mustChangePassword
            ? '当前账号需要先修改密码后才能进入业务首页。'
            : '可在此主动修改当前登录账号密码。'
        }}
      </p>

      <div class="login-feature-list">
        <div class="login-feature-card">
          <div class="login-feature-title">强制改密</div>
          <div class="login-feature-text">
            登录返回 forceChangePassword=true 时，前端会直接锁定到本页。
          </div>
        </div>
        <div class="login-feature-card">
          <div class="login-feature-title">用户态延续</div>
          <div class="login-feature-text">
            提交成功后会刷新 /api/auth/me，再决定回到首页还是继续停留。
          </div>
        </div>
      </div>
    </section>

    <section class="login-panel login-panel-form">
      <div class="form-card">
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
        <el-alert
          v-if="formError"
          :title="formError"
          type="error"
          show-icon
          :closable="false"
          class="education-alert"
        />

        <el-form label-position="top" @submit.prevent="submitChangePassword">
          <el-form-item label="当前密码">
            <el-input
              v-model="form.oldPassword"
              type="password"
              show-password
              placeholder="请输入当前密码"
            />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input
              v-model="form.newPassword"
              type="password"
              show-password
              placeholder="请输入新密码"
            />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
            />
          </el-form-item>

          <div class="change-password-actions">
            <el-button v-if="!auth.mustChangePassword" @click="goBack">返回系统</el-button>
            <el-button
              class="login-submit"
              type="primary"
              :loading="submitting"
              @click="submitChangePassword"
            >
              提交新密码
            </el-button>
          </div>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@cornerstone/core/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const submitting = ref(false)
const formError = ref('')
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function resolveRedirect() {
  if (typeof route.query.redirect === 'string' && route.query.redirect !== '/change-password') {
    return route.query.redirect
  }
  return '/dashboard'
}

function goBack() {
  router.push(resolveRedirect())
}

async function submitChangePassword() {
  const oldPassword = trimToUndefined(form.oldPassword)
  const newPassword = trimToUndefined(form.newPassword)
  const confirmPassword = trimToUndefined(form.confirmPassword)

  if (!oldPassword || !newPassword || !confirmPassword) {
    formError.value = '请完整填写当前密码、新密码和确认密码'
    return
  }
  if (newPassword !== confirmPassword) {
    formError.value = '两次输入的新密码不一致'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    await auth.changePassword({
      oldPassword,
      newPassword,
      confirmPassword,
    })
    ElMessage.success('密码修改成功')
    router.replace(resolveRedirect())
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '密码修改失败'
  } finally {
    submitting.value = false
  }
}
</script>
