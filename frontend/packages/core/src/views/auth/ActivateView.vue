<template>
  <LoginShell>
    <div class="page-kicker">激活账号</div>
    <h2 class="form-title">使用激活码设置密码</h2>
    <p class="login-form-tip">
      管理员已建好账号，设置初始密码即可启用；账号、机构与角色沿用预建信息。
    </p>

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

    <el-form
      ref="activateFormRef"
      :model="form"
      :rules="activateRules"
      label-position="top"
      scroll-to-error
      @submit.prevent="submitActivate"
    >
      <el-form-item label="激活码" prop="code">
        <div class="invitation-code-row">
          <el-input v-model="form.code" placeholder="请输入激活码" @blur="runPrecheck" />
          <el-button :loading="prechecking" @click="runPrecheck">预检</el-button>
        </div>
      </el-form-item>
      <el-form-item label="初始密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="请输入新密码"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
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
  </LoginShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import LoginShell from '@cornerstone/core/components/LoginShell.vue'
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

const activateFormRef = ref<FormInstance | null>(null)

/** 必填校验统一交给 el-form：标签红星 + 输入框红边 + 框下行内提示，不靠顶部一条总文案 */
const activateRules: FormRules = {
  code: [{ required: true, whitespace: true, message: '请输入激活码', trigger: 'blur' }],
  password: [{ required: true, whitespace: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, whitespace: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value && form.password !== value) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

// 改新密码时要重新比对确认密码那一格，别等到再点一次提交才提示不一致
watch(
  () => form.password,
  () => {
    if (form.confirmPassword) {
      void activateFormRef.value?.validateField('confirmPassword').catch(() => undefined)
    }
  }
)

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
  // 校验失败时 el-form 已经在字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await activateFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  const code = form.code.trim()
  // required 规则已保证非空，这里只为类型收窄
  if (!code) {
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
