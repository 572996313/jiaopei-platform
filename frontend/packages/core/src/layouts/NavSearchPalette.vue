<template>
  <Teleport to="body">
    <div v-if="modelValue" class="nav-search-mask" @click="close"></div>

    <div v-if="modelValue" class="nav-search-panel" role="dialog" aria-label="搜索页面">
      <div class="nav-search-input">
        <el-icon><Search /></el-icon>
        <el-input
          ref="inputRef"
          v-model="keyword"
          placeholder="输入页面名称，如「排课」「菜单」"
          aria-label="搜索页面"
          @keydown.down.prevent="move(1)"
          @keydown.up.prevent="move(-1)"
          @keydown.enter.prevent="pick()"
          @keydown.esc.prevent="close()"
        />
        <kbd>Esc</kbd>
      </div>

      <div class="nav-search-list">
        <button
          v-for="(item, index) in matched"
          :key="item.to"
          type="button"
          class="nav-search-item"
          :class="{ 'is-active': index === cursor }"
          @mouseenter="cursor = index"
          @click="pick(item)"
        >
          <el-icon v-if="item.icon" class="nav-search-item-icon"
            ><component :is="item.icon"
          /></el-icon>
          <span class="nav-search-item-name">{{ item.label }}</span>
          <span class="nav-search-item-path">{{ trailOf(item) }}</span>
        </button>
        <div v-if="!matched.length" class="nav-search-empty">没有匹配的页面</div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import type { ElInput } from 'element-plus'

/** 一个可跳转的页面入口:label 是页名,trail 是它在导航里的来路(分组 / 顶层目录) */
export interface SearchablePage {
  label: string
  groupLabel: string
  parentLabel: string
  to: string
  icon?: Component
}

const props = defineProps<{ modelValue: boolean; items: SearchablePage[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const router = useRouter()
const keyword = ref('')
const cursor = ref(0)
const inputRef = ref<InstanceType<typeof ElInput> | null>(null)

/** 空格分隔的多关键词全部命中才算命中,顺序无关("排课 批量" 能找到 "批量排课") */
const matched = computed(() => {
  const terms = keyword.value.trim().toLowerCase().split(/\s+/).filter(Boolean)
  if (!terms.length) {
    return props.items
  }
  return props.items.filter((item) => {
    const haystack = `${item.label} ${item.groupLabel} ${item.parentLabel} ${item.to}`.toLowerCase()
    return terms.every((term) => haystack.includes(term))
  })
})

function trailOf(item: SearchablePage) {
  // 顶层项自己就是组名时(系统设置)只留一处,免得读成"系统设置 / 系统设置"
  return [...new Set([item.groupLabel, item.parentLabel].filter(Boolean))].join(' / ')
}

function move(step: number) {
  const total = matched.value.length
  if (total) {
    cursor.value = (cursor.value + step + total) % total
  }
}

function pick(item?: SearchablePage) {
  const target = item ?? matched.value[cursor.value]
  if (!target) {
    return
  }
  close()
  router.push(target.to)
}

function close() {
  emit('update:modelValue', false)
}

// 重新打开时回到干净状态,并等弹层挂上再抢焦点
watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      return
    }
    keyword.value = ''
    cursor.value = 0
    await nextTick()
    inputRef.value?.focus()
  }
)

watch(matched, () => {
  cursor.value = 0
})
</script>
