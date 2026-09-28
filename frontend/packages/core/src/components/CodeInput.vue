<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { nextCodeNumber } from '@cornerstone/core/api/system'
import { useAutoCode } from '@cornerstone/core/composables/use-code-rules'

const props = withDefaults(
  defineProps<{
    /** 编码资源码，与后端 CodeRuleContributor 登记的一致（student / campus / class…） */
    resource: string
    /** create=新增（打开就预填建议号）；edit=编辑（显示已有编号，不给预填） */
    mode?: 'create' | 'edit'
    placeholder?: string
  }>(),
  { mode: 'create', placeholder: undefined }
)

const modelValue = defineModel<string>({ default: '' })

const { autoEnabled } = useAutoCode(() => props.resource)

const generating = ref(false)
/** 框里的值还是不是系统给的那个数；客户一改就不该再说"建议号" */
const suggestion = ref('')

const hint = computed(() => {
  if (!autoEnabled.value) {
    return props.placeholder ?? ''
  }
  return suggestion.value && modelValue.value === suggestion.value
    ? '系统给的建议号，可自己改'
    : (props.placeholder ?? '')
})

/** 点按钮是明确意图，直接覆盖框里的内容 */
async function generate() {
  generating.value = true
  try {
    suggestion.value = (await nextCodeNumber(props.resource)).trim()
    modelValue.value = suggestion.value
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '编号生成失败')
  } finally {
    generating.value = false
  }
}

/**
 * 新增弹窗一打开就把建议号填进输入框。
 *
 * <p>编号必须让客户亲眼看见再保存，不允许"留空、后端悄悄补一个"——那是客户最反对的隐藏生成。
 * 请求回来时若客户已经自己填了号，就不覆盖他的输入。</p>
 */
async function suggest() {
  generating.value = true
  try {
    const code = (await nextCodeNumber(props.resource)).trim()
    if (!modelValue.value.trim()) {
      suggestion.value = code
      modelValue.value = code
    }
  } catch {
    // 拉不到建议号就留空让人工填，「生成」按钮还在，点了会报错提示
  } finally {
    generating.value = false
  }
}

watch(
  () => props.mode === 'create' && autoEnabled.value && !modelValue.value.trim(),
  (needsSuggestion) => {
    if (needsSuggestion) void suggest()
  },
  { immediate: true }
)
</script>

<template>
  <div class="code-input">
    <el-input v-model="modelValue" :placeholder="hint" />
    <el-button v-if="autoEnabled" :loading="generating" @click="generate">
      {{ modelValue ? '重新生成' : '生成' }}
    </el-button>
  </div>
</template>

<style scoped>
/* 输入框占满一行，按钮贴在右边（与登录页邀请码那行同形态） */
.code-input {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-input .el-input {
  flex: 1 1 auto;
  min-width: 0;
}
</style>
