<template>
  <el-button v-if="visible" text type="primary" size="small" :loading="loading" @click="openWxacode">
    小程序码
  </el-button>

  <el-dialog v-model="dialogVisible" title="邀请码小程序码" width="380px" @closed="revokeUrl">
    <div class="wxacode-box">
      <img v-if="imageUrl" :src="imageUrl" alt="小程序码" class="wxacode-img" />
      <p class="wxacode-tip">学员/家长在微信里扫此码，进入小程序注册页自动使用该邀请码。</p>
      <p class="wxacode-code">凭证码：{{ row.code }}</p>
    </div>
    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
      <el-button type="primary" @click="download">下载图片</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '@cornerstone/core/api/http'
import type { InvitationSummary } from '@cornerstone/core/api/invitation'
import { useAuthStore } from '@cornerstone/core/stores/auth'

/** 行操作扩展点组件：底座只传 row，取数/权限/弹窗全部自管 */
const props = defineProps<{ row: InvitationSummary }>()

const auth = useAuthStore()
// 生成小程序码消耗微信接口配额，按教务维护权限放开
const visible = computed(() => auth.hasPermission('education:write'))

const loading = ref(false)
const dialogVisible = ref(false)
const imageUrl = ref('')

async function openWxacode() {
  loading.value = true
  try {
    const response = await http.get<Blob>(
      `/education/wechat/invitations/${encodeURIComponent(props.row.code)}/wxacode`,
      { responseType: 'blob' }
    )
    revokeUrl()
    imageUrl.value = URL.createObjectURL(response.data)
    dialogVisible.value = true
  } catch (error) {
    ElMessage.error(await extractMessage(error))
  } finally {
    loading.value = false
  }
}

/** blob 请求里后端的失败响应也是 Blob，需要先读出 ApiResponse 的 message */
async function extractMessage(error: unknown): Promise<string> {
  const data = (error as { response?: { data?: unknown } })?.response?.data
  if (data instanceof Blob) {
    try {
      const json = JSON.parse(await data.text())
      if (typeof json?.message === 'string') {
        return json.message
      }
    } catch {
      // 不是 JSON 就落通用文案
    }
  }
  return '生成小程序码失败，请稍后重试'
}

function download() {
  if (!imageUrl.value) {
    return
  }
  const link = document.createElement('a')
  link.href = imageUrl.value
  link.download = `wxacode_${props.row.code}.png`
  link.click()
}

function revokeUrl() {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }
}

onUnmounted(revokeUrl)
</script>

<style scoped>
.wxacode-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.wxacode-img {
  width: 260px;
  height: 260px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
}

.wxacode-tip,
.wxacode-code {
  margin: 0;
  font-size: 12px;
  color: var(--text-secondary, #6b7280);
  text-align: center;
}
</style>
