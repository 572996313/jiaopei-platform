<template>
  <LoginShell>
    <div class="page-kicker">系统登录</div>
    <h2 class="form-title">{{ loginFormTitle }}</h2>
    <p v-if="loginPageTip" class="login-form-tip">{{ loginPageTip }}</p>

    <el-alert
      v-if="formError"
      :title="formError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <el-form
      ref="loginFormRef"
      :model="form"
      :rules="loginRules"
      label-position="top"
      scroll-to-error
      @submit.prevent="submitLogin"
    >
      <el-form-item label="账号" prop="username">
        <el-input v-model="form.username" placeholder="请输入账号" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
      </el-form-item>
      <el-button class="login-submit" type="primary" :loading="submitting" @click="submitLogin">
        登录系统
      </el-button>
    </el-form>

    <div class="login-alt-entries">
      <router-link to="/register">有邀请码？注册账号</router-link>
      <span class="login-alt-divider">|</span>
      <router-link to="/activate">账号待激活？设置密码</router-link>
    </div>
  </LoginShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { fetchLoginPage } from '@cornerstone/core/api/auth'
import LoginShell from '@cornerstone/core/components/LoginShell.vue'
import { useBranding } from '@cornerstone/core/composables/use-branding'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { trimToUndefined } from '@cornerstone/core/utils/text'

const router = useRouter()
const auth = useAuthStore()
/** 登录表单标题来自系统参数 brand.login_form_title，与 LoginShell 读同一份请求结果 */
const { loginFormTitle } = useBranding()

const submitting = ref(false)
const formError = ref('')
/** 提示语来自系统参数 auth.login.page_tip：底座不写死项目专属文案，取不到或为空就不展示 */
const loginPageTip = ref('')
const form = reactive({
  // 本地开发预填：口令是各项目自己的事，底座不写死默认值。只有开发模式、且壳工程在自己的
  // `.env.development` 里显式给了这两个变量才预填，生产构建更不会把口令打进登录页。
  username: import.meta.env.DEV ? (import.meta.env.VITE_DEV_LOGIN_USERNAME ?? '') : '',
  password: import.meta.env.DEV ? (import.meta.env.VITE_DEV_LOGIN_PASSWORD ?? '') : '',
})

const loginFormRef = ref<FormInstance | null>(null)

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案 */
const loginRules: FormRules = {
  username: [{ required: true, whitespace: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, whitespace: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(async () => {
  try {
    loginPageTip.value = (await fetchLoginPage()).tip.trim()
  } catch {
    // 提示语是装饰性信息，接口不可用时保持不展示
  }
})

async function submitLogin() {
  // 校验失败时 el-form 已经在字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  const username = trimToUndefined(form.username)
  const password = trimToUndefined(form.password)
  // required 规则已保证非空，这里只为类型收窄
  if (!username || !password) {
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
    // 只按生效值提示:总开关关闭时后端会把 forceChangePassword 置 false,
    // 但 passwordResetRequired 仍为库里的原始标记,不能拿来判断是否提示
    ElMessage.success('登录成功')
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

<style scoped>
.login-alt-entries {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--login-line);
  font-size: 13px;
  color: var(--login-ink-dim);
}

/* 交替入口跟在表单卡里,颜色一律取外壳令牌,明亮/夜晚两档不用各写一套 */
.login-alt-entries a {
  color: var(--login-ink-label);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition:
    color 0.2s ease,
    border-color 0.2s ease;
}

.login-alt-entries a:hover {
  color: var(--login-accent);
  border-bottom-color: var(--login-accent-line);
}

.login-alt-divider {
  color: var(--login-line);
}
</style>
