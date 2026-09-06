<template>
  <div class="login-shell">
    <section class="login-panel login-panel-brand">
      <div class="login-kicker">教培管理平台</div>
      <h1 class="login-title">线下班课教务中台</h1>
      <p class="login-copy">
        聚焦校区、课程、班级、排课与课表，先把教务执行主链路跑通。
      </p>

      <div class="login-feature-list">
        <div class="login-feature-card">
          <div class="login-feature-title">组织与校区</div>
          <div class="login-feature-text">机构、校区、教室、学期、时间段统一管理。</div>
        </div>
        <div class="login-feature-card">
          <div class="login-feature-title">排课与执行</div>
          <div class="login-feature-text">围绕线下班课模式搭建稳定的教务中台。</div>
        </div>
      </div>
    </section>

    <section class="login-panel login-panel-form">
      <div class="form-card">
        <div class="page-kicker">系统登录</div>
        <h2 class="form-title">进入教培平台</h2>
        <p class="login-form-tip">
          当前已切真实登录接口。默认首个管理员账号：`admin`，初始密码：`Admin@123456`。
        </p>

        <el-alert v-if="formError" :title="formError" type="error" show-icon :closable="false" class="education-alert" />

        <el-form label-position="top" @submit.prevent="submitLogin">
          <el-form-item label="账号">
            <el-input v-model="form.username" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
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
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

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
    const redirect = typeof router.currentRoute.value.query.redirect === 'string'
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
