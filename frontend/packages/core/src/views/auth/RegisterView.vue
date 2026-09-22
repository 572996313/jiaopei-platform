<template>
  <div class="login-shell">
    <section class="login-panel login-panel-brand">
      <div class="login-kicker">邀请码注册</div>
      <h1 class="login-title">通过邀请码创建账号</h1>
      <p class="login-copy">
        管理员签发的邀请码决定了新账号所属机构与角色，注册成功后会直接进入系统。
      </p>

      <div class="login-feature-list">
        <div class="login-feature-card">
          <div class="login-feature-title">凭证预检</div>
          <div class="login-feature-text">
            填写邀请码后可先预检，确认机构、角色与有效期再提交。
          </div>
        </div>
        <div class="login-feature-card">
          <div class="login-feature-title">一次提交即登录</div>
          <div class="login-feature-text">
            注册成功后自动签发登录态，无需再回到登录页输入密码。
          </div>
        </div>
      </div>
    </section>

    <section class="login-panel login-panel-form">
      <div class="form-card">
        <div class="page-kicker">注册账号</div>
        <h2 class="form-title">填写邀请码与账号信息</h2>

        <el-alert
          v-if="precheck"
          :title="`邀请码有效：${precheck.institutionName || '-'} / ${precheck.roleName || precheck.roleCode || '-'}`"
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

        <el-form label-position="top" @submit.prevent="submitRegister">
          <el-form-item label="邀请码">
            <div class="invitation-code-row">
              <el-input v-model="form.code" placeholder="请输入邀请码" @blur="runPrecheck" />
              <el-button :loading="prechecking" @click="runPrecheck">预检</el-button>
            </div>
          </el-form-item>
          <el-form-item label="登录账号">
            <el-input v-model="form.username" placeholder="请输入登录账号" />
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="form.displayName" placeholder="请输入姓名或显示名" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="form.phone" placeholder="选填" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="请输入初始密码"
            />
          </el-form-item>

          <div class="change-password-actions">
            <el-button @click="goLogin">返回登录</el-button>
            <el-button
              class="login-submit"
              type="primary"
              :loading="submitting"
              @click="submitRegister"
            >
              注册并进入系统
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
  precheckInvitation,
  registerByInvite,
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
  username: '',
  displayName: '',
  phone: '',
  password: '',
})

onMounted(() => {
  // 链接里带码（管理员分享的注册链接）时自动预检一次
  if (form.code) runPrecheck()
})

async function runPrecheck() {
  precheck.value = null
  precheckError.value = ''
  const code = form.code.trim()
  if (!code) return
  prechecking.value = true
  try {
    const result = await precheckInvitation(code)
    if (!result.valid) {
      precheckError.value = result.message || '邀请码无效或已不可用'
      return
    }
    if (result.kind !== 'INVITE') {
      precheckError.value = '该凭证是账号激活码，请前往激活页面完成设置密码。'
      return
    }
    precheck.value = result
  } catch (error) {
    precheckError.value = error instanceof Error ? error.message : '邀请码校验失败'
  } finally {
    prechecking.value = false
  }
}

function goLogin() {
  router.push('/login')
}

async function submitRegister() {
  const code = form.code.trim()
  const username = form.username.trim()
  const displayName = form.displayName.trim()
  const password = form.password

  if (!code || !username || !displayName || !password) {
    formError.value = '请完整填写邀请码、登录账号、显示名称和密码'
    return
  }

  submitting.value = true
  formError.value = ''
  try {
    const session = await registerByInvite({
      code,
      username,
      displayName,
      phone: form.phone.trim() || undefined,
      password,
    })
    auth.setSession(session.accessToken, session.tokenType || 'Bearer')
    await auth.bootstrap()
    ElMessage.success('注册成功，已进入系统')
    if (session.forceChangePassword) {
      router.replace('/change-password')
    } else {
      router.replace('/dashboard')
    }
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '注册失败'
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
