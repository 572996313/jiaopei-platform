<template>
  <div class="file-upload">
    <div v-if="modelValue" class="file-upload-current">
      <span class="file-upload-thumb">
        <FileImage v-if="isImage" :file-id="modelValue" />
        <el-icon v-else class="file-upload-type-icon"><Document /></el-icon>
      </span>
      <span class="file-upload-meta">
        <span class="file-upload-name" :title="displayName">{{ displayName }}</span>
        <span class="file-upload-size">{{ formatFileSize(meta?.sizeBytes) }}</span>
      </span>
      <el-button v-if="!disabled" link type="danger" @click="clear">移除</el-button>
    </div>

    <template v-else>
      <el-upload
        drag
        :accept="accept"
        :disabled="disabled"
        :show-file-list="false"
        :http-request="handleUpload"
        :before-upload="beforeUpload"
      >
        <div class="file-upload-drop">
          <el-icon class="file-upload-drop-icon"><UploadFilled /></el-icon>
          <div class="file-upload-drop-text">
            {{ uploading ? '上传中…' : '将文件拖到此处，或点击选择' }}
          </div>
          <div class="file-upload-hint">{{ hintText }}</div>
        </div>
      </el-upload>
      <el-progress v-if="uploading" :percentage="percent" :stroke-width="6" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, UploadFilled } from '@element-plus/icons-vue'
import FileImage from '@cornerstone/core/components/FileImage.vue'
import { fetchFile, uploadFile } from '@cornerstone/core/api/file'
import type { FileView } from '@cornerstone/core/api/file'
import { formatFileSize, isImageFile } from '@cornerstone/core/utils/file'

/**
 * 通用文件上传（v-model 绑 fileId）。
 *
 * 契约：只把 fileId 交给业务表单，业务表也只存 fileId。上传成功后本组件自己保留一份元数据
 * 用于回显文件名/大小；父组件重新进入编辑页时只拿得到 fileId，故按 fileId 回查一次。
 */
const props = withDefaults(
  defineProps<{
    modelValue?: string | null
    /** 与原生 input 同口径，如 ".png,.jpg,image/*"；不传则不限制（后端仍按白名单校验） */
    accept?: string
    /** 客户端提示用，真正的上限以后端 system config 为准 */
    maxSizeMb?: number
    bizType?: string
    bizId?: string
    disabled?: boolean
    hint?: string
  }>(),
  {
    modelValue: null,
    accept: undefined,
    maxSizeMb: 20,
    bizType: undefined,
    bizId: undefined,
    disabled: false,
    hint: '',
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
  /** 上传成功，父组件需要额外信息（如展示文件名）时用 */
  uploaded: [file: FileView]
}>()

const meta = ref<FileView | null>(null)
const uploading = ref(false)
const percent = ref(0)

const isImage = computed(() => isImageFile(meta.value?.extension, meta.value?.contentType))
const displayName = computed(() => meta.value?.fileName || '已上传文件')
const hintText = computed(
  () =>
    props.hint ||
    `单个文件不超过 ${props.maxSizeMb} MB${props.accept ? `，支持 ${props.accept}` : ''}`
)

watch(
  () => props.modelValue,
  async (value) => {
    if (!value) {
      meta.value = null
      return
    }
    if (meta.value?.fileId === value) {
      return
    }
    meta.value = null
    try {
      meta.value = await fetchFile(value)
    } catch {
      // 元数据拉不到不妨碍文件本身可用，回显退回「已上传文件」
    }
  },
  { immediate: true }
)

function accepts(file: File) {
  const rules = (props.accept ?? '')
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)
  if (rules.length === 0) {
    return true
  }
  const name = file.name.toLowerCase()
  const type = (file.type || '').toLowerCase()
  return rules.some((rule) => {
    if (rule.startsWith('.')) {
      return name.endsWith(rule)
    }
    if (rule.endsWith('/*')) {
      return type.startsWith(rule.slice(0, -1))
    }
    return type === rule
  })
}

function beforeUpload(file: File) {
  if (file.size === 0) {
    ElMessage.warning('不能上传空文件')
    return false
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    ElMessage.warning(`文件不能超过 ${props.maxSizeMb} MB`)
    return false
  }
  if (!accepts(file)) {
    ElMessage.warning('不支持的文件类型')
    return false
  }
  return true
}

async function handleUpload(options: { file: File }) {
  uploading.value = true
  percent.value = 0
  try {
    const view = await uploadFile(options.file, {
      bizType: props.bizType,
      bizId: props.bizId,
      onProgress: (value) => {
        percent.value = value
      },
    })
    meta.value = view
    percent.value = 100
    emit('update:modelValue', view.fileId)
    emit('uploaded', view)
    return view
  } catch (error) {
    ElMessage.error(
      error instanceof Error && error.message ? error.message : '上传失败，请稍后重试'
    )
    throw error
  } finally {
    uploading.value = false
  }
}

function clear() {
  meta.value = null
  percent.value = 0
  emit('update:modelValue', null)
}
</script>

<style scoped>
.file-upload {
  width: 100%;
}

.file-upload:deep(.el-upload) {
  width: 100%;
}

.file-upload:deep(.el-upload-dragger) {
  width: 100%;
  padding: 18px 12px;
}

.file-upload-current {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.file-upload-thumb {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.file-upload-type-icon {
  font-size: 18px;
  color: var(--el-text-color-secondary);
}

.file-upload-meta {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
}

.file-upload-name {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-upload-size {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.file-upload-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.file-upload-drop-icon {
  font-size: 28px;
  color: var(--el-text-color-placeholder);
}

.file-upload-drop-text {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.file-upload-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
