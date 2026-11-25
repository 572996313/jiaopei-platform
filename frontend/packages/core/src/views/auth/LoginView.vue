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

    <div v-if="providers.length" class="login-providers">
      <div class="login-provider-divider"><span>或使用第三方账号</span></div>
      <button
        v-for="provider in providers"
        :key="provider.code"
        type="button"
        class="login-provider-btn"
        @click="startOauth(provider.code)"
      >
        <span class="login-provider-mark">{{ provider.name.slice(0, 1) }}</span>
        <span>{{ provider.name }} 一键登录</span>
      </button>
    </div>

    <div class="login-alt-entries">
      <router-link to="/register">有邀请码？注册账号</router-link>
      <span class="login-alt-divider">|</span>
      <router-link to="/activate">账号待激活？设置密码</router-link>
    </div>
  </LoginShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  fetchLoginPage,
  fetchLoginProviders,
  oauthAuthorizeUrl,
  type AuthProviderView,
} from '@cornerstone/core/api/auth'
import LoginShell from '@cornerstone/core/components/LoginShell.vue'
import { useBranding } from '@cornerstone/core/composables/use-branding'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { trimToUndefined } from '@cornerstone/core/utils/text'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
/** 登录表单标题来自系统参数 brand.login_form_title，与 LoginShell 读同一份请求结果 */
const { loginFormTitle } = useBranding()

const submitting = ref(false)
const formError = ref('')
/** 启用中的第三方登录渠道；为空时整块不出现，不占版式 */
const providers = ref<AuthProviderView[]>([])

/** 回调失败时后端只回错误码，文案在前端维护，便于统一改口吻 */
const OAUTH_ERROR_MESSAGES: Record<string, string> = {
  access_denied: '已取消第三方授权',
  state_expired: '登录请求已过期，请重新发起',
  invalid_callback: '第三方登录回调参数不完整，请重试',
  provider_unavailable: '该第三方登录渠道暂不可用',
  not_bound: '该第三方账号尚未绑定系统账号，请先用账号密码登录后在个人信息中绑定',
  account_disabled: '绑定的账号已被禁用，请联系管理员',
  portal_not_allowed: '该账号请使用微信小程序登录',
  identity_missing: '第三方账号信息不完整，请重试',
  exchange_failed: '第三方登录失败，请稍后重试',
}
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
  const oauthError = typeof route.query.oauthError === 'string' ? route.query.oauthError : ''
  if (oauthError) {
    formError.value = OAUTH_ERROR_MESSAGES[oauthError] || '第三方登录失败，请重试'
  }
  try {
    loginPageTip.value = (await fetchLoginPage()).tip.trim()
  } catch {
    // 提示语是装饰性信息，接口不可用时保持不展示
  }
  try {
    providers.value = await fetchLoginProviders()
  } catch {
    // 第三方渠道拉取失败不影响账号密码登录
  }
})

/** 浏览器整页跳转去渠道授权页；令牌不经过前端，回调后再用一次性票据换 */
function startOauth(providerCode: string) {
  window.location.href = oauthAuthorizeUrl(providerCode)
}

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

.login-providers {
  margin-top: 18px;
}

.login-provider-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--login-ink-dim);
}

.login-provider-divider::before,
.login-provider-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--login-line);
}

.login-provider-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px 14px;
  border: 1px solid var(--login-line);
  border-radius: 8px;
  background: transparent;
  color: var(--login-ink-label);
  font-size: 14px;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    color 0.2s ease,
    background-color 0.2s ease;
}

.login-provider-btn:hover {
  border-color: var(--login-accent-line);
  color: var(--login-accent);
  background: color-mix(in srgb, var(--login-accent) 6%, transparent);
}

.login-provider-mark {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--login-accent);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
}
</style>
