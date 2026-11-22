<template>
  <el-dialog
    v-model="visible"
    title="个人信息"
    width="560px"
    append-to-body
    @closed="resetTransient"
  >
    <div class="profile-head">
      <button
        type="button"
        class="profile-avatar-btn"
        :disabled="uploading"
        title="更换头像"
        @click="chooseFile"
      >
        <img v-if="previewUrl" :src="previewUrl" class="profile-avatar-img" alt="头像预览" />
        <UserAvatar
          v-else
          :file-id="auth.currentUser?.avatarFileId"
          :name="auth.displayName"
          :size="76"
        />
        <span class="profile-avatar-mask">{{ uploading ? '上传中…' : '更换' }}</span>
      </button>
      <div class="profile-identity">
        <div class="profile-name">{{ auth.displayName }}</div>
        <div class="profile-account">@{{ auth.currentUser?.username || '-' }}</div>
        <div class="profile-roles">
          <el-tag v-for="role in roleLabels" :key="role" size="small" effect="plain">
            {{ role }}
          </el-tag>
          <span v-if="!roleLabels.length" class="profile-muted">未分配角色</span>
        </div>
      </div>
      <el-button
        v-if="auth.currentUser?.avatarFileId"
        link
        type="danger"
        :disabled="uploading"
        @click="removeAvatar"
      >
        移除头像
      </el-button>
    </div>
    <input
      ref="fileInputRef"
      type="file"
      class="profile-file-input"
      accept="image/jpeg,image/png"
      @change="onFileChange"
    />

    <el-descriptions :column="2" border size="small" class="profile-info">
      <el-descriptions-item label="所属机构">{{ institutionName }}</el-descriptions-item>
      <el-descriptions-item label="手机号">{{ auth.currentUser?.phone || '-' }}</el-descriptions-item>
      <el-descriptions-item label="最近登录">
        {{ formatDateTime(auth.currentUser?.lastLoginAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="密码更新">
        {{ formatDateTime(auth.currentUser?.passwordUpdatedAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="数据范围" :span="2">{{ dataScopeText }}</el-descriptions-item>
    </el-descriptions>

    <section class="profile-security">
      <div class="profile-security-head">
        <div>
          <div class="profile-section-title">账号安全</div>
          <div class="profile-section-tip">修改后当前登录保持有效，下次登录使用新密码。</div>
        </div>
        <el-button v-if="!passwordOpen" size="small" @click="passwordOpen = true">
          修改密码
        </el-button>
        <el-button v-else size="small" @click="passwordOpen = false">收起</el-button>
      </div>
      <ChangePasswordForm
        v-if="passwordOpen"
        cancelable
        submit-label="确认修改"
        @cancel="passwordOpen = false"
        @success="passwordOpen = false"
      />
    </section>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteMyAvatar, uploadMyAvatar } from '@cornerstone/core/api/auth'
import { useAuthStore } from '@cornerstone/core/stores/auth'
import { useInstitutionStore } from '@cornerstone/core/stores/institution'
import { AVATAR_MAX_BYTES, normalizeAvatarFile } from '@cornerstone/core/utils/avatar-image'
import { formatDateTime } from '@cornerstone/core/components/ConfigurableList/renderers'
import ChangePasswordForm from '@cornerstone/core/components/ChangePasswordForm.vue'
import UserAvatar from '@cornerstone/core/components/UserAvatar.vue'

/**
 * 顶栏"个人信息"面板：展示账号常用信息，并承接换头像与快捷改密。
 *
 * <p>头像选中即上传（先在浏览器裁成 512×512），不再有二次确认；改密仍复用
 * {@link ChangePasswordForm}，与强制改密页同一套校验。</p>
 */
const props = defineProps<{ modelValue: boolean }>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const auth = useAuthStore()
const institutionStore = useInstitutionStore()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const fileInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const passwordOpen = ref(false)
const previewUrl = ref('')

const roleLabels = computed(() => {
  const names = auth.currentUser?.roleNames
  return names?.length ? names : auth.currentUser?.roles || []
})

const institutionName = computed(() => {
  const id = auth.currentUser?.institutionId
  if (!id) {
    return '-'
  }
  const matched = institutionStore.institutions.find((item) => item.id === id)
  return matched?.orgName || matched?.shortName || `机构 #${id}`
})

const dataScopeText = computed(() => {
  const key = auth.currentUser?.dataScopeKey
  if (!key) {
    return '不限制'
  }
  if (key === 'CAMPUS') {
    return `按校区（${auth.currentUser?.dataScopeIds?.length ?? 0} 个）`
  }
  return key
})

// 打开时拉一次最新账号资料：手机号、密码更新时间可能被管理员改过，别拿登录时的缓存当真相
watch(visible, (open) => {
  if (open) {
    void refreshQuietly()
  }
})

function chooseFile() {
  fileInputRef.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空 value：连续选择同一个文件也要触发 change
  input.value = ''
  if (!file) {
    return
  }
  if (!/\.(jpe?g|png)$/i.test(file.name) && !['image/jpeg', 'image/png'].includes(file.type)) {
    ElMessage.warning('头像只支持 JPG、PNG 格式')
    return
  }
  let normalized: File
  try {
    normalized = await normalizeAvatarFile(file)
  } catch {
    ElMessage.error('图片处理失败，请换一张试试')
    return
  }
  if (normalized.size > AVATAR_MAX_BYTES) {
    ElMessage.warning('头像不能超过 2MB')
    return
  }

  uploading.value = true
  setPreview(URL.createObjectURL(normalized))
  try {
    await uploadMyAvatar(normalized)
    ElMessage.success('头像已更新')
    await refreshQuietly()
  } catch (error) {
    clearPreview()
    ElMessage.error(error instanceof Error && error.message ? error.message : '头像上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

async function removeAvatar() {
  try {
    await ElMessageBox.confirm('移除后头像将回落为名称首字，确定移除吗？', '移除头像', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  uploading.value = true
  try {
    await deleteMyAvatar()
    clearPreview()
    ElMessage.success('头像已移除')
    await refreshQuietly()
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '移除失败，请重试')
  } finally {
    uploading.value = false
  }
}

/** 头像已保存成功，刷新失败只影响横幅即时性，不把成功操作报成失败 */
async function refreshQuietly() {
  try {
    await auth.refreshCurrentUser()
  } catch {
    // 下次进入系统会重新拉取
  }
}

function setPreview(url: string) {
  clearPreview()
  previewUrl.value = url
}

function clearPreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function resetTransient() {
  clearPreview()
  passwordOpen.value = false
}
</script>

<style scoped>
.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-avatar-btn {
  position: relative;
  flex: 0 0 auto;
  padding: 0;
  overflow: hidden;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.profile-avatar-btn:disabled {
  cursor: progress;
}

.profile-avatar-img {
  display: block;
  width: 76px;
  height: 76px;
  border-radius: 50%;
  object-fit: cover;
}

.profile-avatar-mask {
  position: absolute;
  inset: auto 0 0;
  padding: 3px 0;
  color: #ffffff;
  font-size: 11px;
  text-align: center;
  background: rgba(15, 23, 42, 0.55);
  opacity: 0;
  transition: opacity 0.15s;
}

.profile-avatar-btn:hover .profile-avatar-mask,
.profile-avatar-btn:focus-visible .profile-avatar-mask {
  opacity: 1;
}

.profile-identity {
  flex: 1;
  min-width: 0;
}

.profile-name {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-account {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.profile-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.profile-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.profile-info {
  margin-top: 18px;
}

.profile-security {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.profile-security-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.profile-section-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.profile-section-tip {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.profile-file-input {
  display: none;
}
</style>
