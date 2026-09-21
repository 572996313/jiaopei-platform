<template>
  <div class="login-shell">
    <section class="login-panel login-panel-brand">
      <div class="login-kicker">{{ brand.loginKicker }}</div>
      <h1 class="login-title">{{ brand.loginTitle }}</h1>
      <p class="login-copy">{{ brand.loginCopy }}</p>

      <div class="login-feature-list">
        <div v-for="feature in brand.loginFeatures" :key="feature.title" class="login-feature-card">
          <div class="login-feature-title">{{ feature.title }}</div>
          <div class="login-feature-text">{{ feature.text }}</div>
        </div>
      </div>
    </section>

    <section class="login-panel login-panel-form">
      <div class="form-card">
        <div class="page-kicker">系统登录</div>
        <h2 class="form-title">{{ brand.loginFormTitle }}</h2>
        <p class="login-form-tip">
          当前已切真实登录接口。默认首个管理员账号：`admin`，初始密码：`Admin@123456`。
        </p>

        <el-alert
          v-if="formError"
          :title="formError"
          type="error"
          show-icon
          :closable="false"
          class="education-alert"
        />

        <el-form label-position="top" @submit.prevent="submitLogin">
          <el-form-item label="账号">
            <el-input v-model="form.username" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入密码"
            />
          </el-form-item>
          <el-button class="login-submit" type="primary" :loading="submitting" @click="submitLogin">
            登录系统
          </el-button>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { currentBrand } from '@cornerstone/core/extension/module-extensions'
import { useAuthStore } from '@cornerstone/core/stores/auth'

const router = useRouter()
const auth = useAuthStore()
/** 品牌文案由壳工程 registerBranding 登记；壳在 import 阶段就跑完了，这里取到的是最终值 */
const brand = currentBrand()

const submitting = ref(false)
const formError = ref('')
const form = reactive({
  username: 'admin',
  password: 'Admin@123456',
})

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

async function submitLogin() {
  const username = trimToUndefined(form.username)
  const password = trimToUndefined(form.password)

  if (!username || !password) {
    formError.value = '请输入账号和密码'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    const currentUser = await auth.login({
      username,
      password,
    })
    if (currentUser?.forceChangePassword) {
      ElMessage.warning('当前账号需要先修改密码后再进入业务首页。')
      router.push('/change-password')
      return
    }
    if (currentUser?.passwordResetRequired) {
      ElMessage.warning('当前账号仍处于初始化密码状态，请登录后尽快修改。')
    } else {
      ElMessage.success('登录成功')
    }
    const redirect =
      typeof router.currentRoute.value.query.redirect === 'string'
        ? router.currentRoute.value.query.redirect
        : '/dashboard'
    router.push(redirect)
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>
