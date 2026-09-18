<template>
  <div class="table-editor-wrap">
    <el-table :data="modelValue" border size="small" class="table-editor">
      <!-- 展开行:高级属性放在这里,默认收起,主表保持紧凑 -->
      <el-table-column type="expand" width="36" class-name="te-expand-col">
        <template #default="{ row }">
          <div class="te-expand-body">
            <slot name="expand" :row="row" />
          </div>
        </template>
      </el-table-column>

      <!-- 主列(由使用者传入) -->
      <slot />

      <!-- 行操作:上移 / 下移 / 复制 / 删除 -->
      <el-table-column label="操作" width="130" fixed="right" class-name="te-ops-col">
        <template #default="{ row, $index }">
          <div class="te-ops">
            <el-button text size="small" title="上移" :disabled="$index === 0" @click="move(row, -1)">
              <el-icon><ArrowUp /></el-icon>
            </el-button>
            <el-button text size="small" title="下移" :disabled="$index === modelValue.length - 1" @click="move(row, 1)">
              <el-icon><ArrowDown /></el-icon>
            </el-button>
            <el-button text size="small" title="复制这一行" @click="duplicate(row)">复制</el-button>
            <el-button text type="danger" size="small" title="删除" @click="remove(row)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="te-footer">
      <el-button size="small" type="primary" plain @click="add">
        <el-icon class="te-add-icon"><Plus /></el-icon>添加一行
      </el-button>
      <slot name="tip" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowUp, Delete, Plus } from '@element-plus/icons-vue'

/**
 * 通用表格编辑器壳:一行一条配置,主属性直接以表格行内控件编辑(对齐、紧凑),
 * 次要属性放进「展开行」,行操作(上移/下移/复制/删除/添加)统一在此处理。
 * 使用者通过默认插槽传入若干 el-table-column(绑 row 字段),通过 #expand 传入展开行内容。
 */

/** 新增一行时的初始值工厂(由使用者提供,保证每类配置的默认字段齐全) */
const props = defineProps<{
  createRow: () => Record<string, any>
}>()

const modelValue = defineModel<Record<string, any>[]>({ required: true })

function add() {
  modelValue.value.push(props.createRow())
}

function duplicate(row: Record<string, any>) {
  const index = modelValue.value.indexOf(row)
  if (index < 0) {
    return
  }
  // 行结构是可 JSON 化的纯数据,深拷贝一份作为副本
  const copy = JSON.parse(JSON.stringify(row)) as Record<string, any>
  modelValue.value.splice(index + 1, 0, copy)
}

function remove(row: Record<string, any>) {
  const index = modelValue.value.indexOf(row)
  if (index >= 0) {
    modelValue.value.splice(index, 1)
  }
}

function move(row: Record<string, any>, delta: number) {
  const index = modelValue.value.indexOf(row)
  const target = index + delta
  if (index < 0 || target < 0 || target >= modelValue.value.length) {
    return
  }
  const list = modelValue.value
  const [item] = list.splice(index, 1)
  list.splice(target, 0, item)
}
</script>

<style scoped src="./editor.css"></style>