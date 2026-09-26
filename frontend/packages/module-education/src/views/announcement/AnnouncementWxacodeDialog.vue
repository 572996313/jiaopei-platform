<template>
  <el-dialog
    :model-value="modelValue"
    title="公告小程序码"
    width="440px"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
    @open="load"
    @closed="revoke"
  >
    <div class="wxacode-box">
      <img v-if="blobUrl" :src="blobUrl" alt="公告小程序码" class="wxacode-img" />
      <div v-else-if="loading" class="wxacode-placeholder">生成中…</div>
      <div v-else class="wxacode-placeholder">{{ errorText }}</div>

      <div v-if="blobUrl" class="wxacode-copy">
        <div class="wxacode-copy-head">
          <span>发到群里的文案</span>
          <el-button link type="primary" size="small" @click="copyShareText">复制文案</el-button>
        </div>
        <p class="wxacode-copy-body">{{ shareText }}</p>
      </div>

      <p class="wxacode-tip">
        扫码后进入小程序公告详情页；<b>只有这条公告的接收人登录后才看得到正文</b>，
        未登录的会先走一键登录再自动回到这条公告。
      </p>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :disabled="!blobUrl" @click="download">下载图片</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useBranding } from '@cornerstone/core/composables/use-branding'
import { fetchWxacode } from '@cornerstone/module-education/api/wechat'
import type { AnnouncementRecord } from '@cornerstone/module-education/api/announcement'

const props = defineProps<{ modelValue: boolean; announcement: AnnouncementRecord | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const { brandName } = useBranding()

const loading = ref(false)
const errorText = ref('')
const blobUrl = ref('')
const scene = ref('')

/**
 * 复制进群的正文。品牌名未配置时不带前缀——
 * useBranding 的"未配置(brand.name)"是给运维看的维护提示，不该被粘到家长群里。
 */
const shareText = computed(() => {
  const title = props.announcement?.title ?? ''
  const brand = brandName.value
  const prefix = brand && !brand.startsWith('未配置') ? `【${brand}】` : ''
  return `${prefix}${title}\n\n长按或扫描上方小程序码即可在微信里打开，登录后自动进入这条公告。`
})

async function load() {
  const id = props.announcement?.id
  if (!id) {
    return
  }
  revoke()
  loading.value = true
  errorText.value = ''
  scene.value = `a=${id}`
  try {
    const blob = await fetchWxacode(`/education/wechat/announcements/${id}/wxacode`)
    blobUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    errorText.value = error instanceof Error && error.message ? error.message : '生成小程序码失败'
  } finally {
    loading.value = false
  }
}

async function copyShareText() {
  try {
    await navigator.clipboard.writeText(shareText.value)
    ElMessage.success('文案已复制，和图片一起发到群里即可')
  } catch {
    ElMessage.warning('浏览器不支持自动复制，请手动选中上方文案')
  }
}

function download() {
  if (!blobUrl.value) {
    return
  }
  const link = document.createElement('a')
  link.href = blobUrl.value
  link.download = `wxacode_${props.announcement?.id ?? 'announcement'}.png`
  link.click()
}

function revoke() {
  if (blobUrl.value) {
    URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = ''
  }
}

onUnmounted(revoke)
</script>

<style scoped>
.wxacode-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.wxacode-img {
  align-self: center;
  width: 260px;
  height: 260px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: #ffffff;
}

.wxacode-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  align-self: center;
  width: 260px;
  height: 260px;
  border-radius: 8px;
  border: 1px dashed var(--border);
  color: var(--text-muted);
  font-size: 13px;
  text-align: center;
  padding: 0 16px;
}

.wxacode-copy {
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--subtle-bg);
  padding: 10px 12px;
}

.wxacode-copy-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.wxacode-copy-body {
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
}

.wxacode-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-muted);
}
</style>
