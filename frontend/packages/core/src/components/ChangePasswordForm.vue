<template>
  <el-form
    ref="passwordFormRef"
    :model="form"
    :rules="rules"
    label-position="top"
    scroll-to-error
    @submit.prevent="submit"
  >
    <el-alert
      v-if="formError"
      :title="formError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />
    <el-form-item label="当前密码" prop="oldPassword">
      <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入当前密码" />
    </el-form-item>
    <el-form-item label="新密码" prop="newPassword">
      <el-input v-model="form.newPassword" type="password" show-password placeholder="请输入新密码" />
    </el-form-item>
    <el-form-item label="确认新密码" prop="confirmPassword">
      <el-input
        v-model="form.confirmPassword"
        type="password"
        show-password
        placeholder="请再次输入新密码"
      />
    </el-form-item>

    <div class="change-password-actions">
      <el-button v-if="cancelable" @click="emit('cancel')">取消</el-button>
      <el-button class="login-submit" type="primary" :loading="submitting" @click="submit">
        {{ submitLabel }}
      </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@cornerstone/core/stores/auth'

/**
 * 修改密码表单。
 *
 * <p>强制改密页（整页）与"个人信息"面板（弹窗内快捷修改）共用这一份校验和提交逻辑，
 * 避免两处规则漂移；成功后只抛 `success`，跳转/收起由调用方决定。</p>
 */
withDefaults(
  defineProps<{
    submitLabel?: string
    /** 弹窗场景显示"取消"，强制改密页没有可返回的去处就不显示 */
    cancelable?: boolean
  }>(),
  { submitLabel: '提交新密码', cancelable: false }
)

const emit = defineEmits<{
  success: []
  cancel: []
}>()

const auth = useAuthStore()

const submitting = ref(false)
const formError = ref('')
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordFormRef = ref<FormInstance | null>(null)

const rules: FormRules = {
  oldPassword: [{ required: true, whitespace: true, message: '请填写当前密码', trigger: 'blur' }],
  newPassword: [{ required: true, whitespace: true, message: '请填写新密码', trigger: 'blur' }],
  // 跨字段规则：两次输入一致挂在后一个字段上
  confirmPassword: [
    { required: true, whitespace: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value && form.newPassword !== value) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }
  const oldPassword = form.oldPassword.trim()
  const newPassword = form.newPassword.trim()
  const confirmPassword = form.confirmPassword.trim()

  submitting.value = true
  formError.value = ''
  try {
    await auth.changePassword({ oldPassword, newPassword, confirmPassword })
    ElMessage.success('密码修改成功')
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    emit('success')
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '密码修改失败'
  } finally {
    submitting.value = false
  }
}

watch(
  () => form.newPassword,
  () => {
    // 新密码改动后，确认密码那一格的一致性红框要立刻跟上，不用等再次提交
    if (form.confirmPassword) {
      void passwordFormRef.value?.validateField('confirmPassword').catch(() => undefined)
    }
  }
)
</script>
