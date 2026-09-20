<template>
  <div class="editor-blocks">
    <!-- 页面壳 pageJson -->
    <div class="block-card">
      <div class="block-card-head">
        <span class="block-card-title">页面显示(页面壳)</span>
        <span class="block-card-sub">顶部标题、计数、空态文案</span>
      </div>
      <div class="form-grid">
        <el-form-item label="页面大标题 title" class="full-span">
          <el-input v-model="page.title" placeholder="留空则显示资源名称,如 用户管理" />
        </el-form-item>
        <el-form-item label="标题右侧说明 description" class="full-span">
          <el-input v-model="page.description" placeholder="一句话说明这个页面是干什么的(选填)" />
        </el-form-item>
        <el-form-item label="显示共 X 条">
          <el-switch v-model="page.showCount" />
        </el-form-item>
        <el-form-item label="计数文案 countFormat">
          <el-input v-model="page.countFormat" placeholder="共 {total} 条" />
        </el-form-item>
        <el-form-item label="空数据文案 emptyText" class="full-span">
          <el-input v-model="page.emptyText" placeholder="暂无数据" />
        </el-form-item>
      </div>
    </div>

    <!-- 弹窗壳 dialogJson -->
    <div class="block-card">
      <div class="block-card-head">
        <span class="block-card-title">弹窗显示(弹窗壳)</span>
        <span class="block-card-sub">新增/编辑弹窗的宽度、标题、按钮文案</span>
      </div>
      <div class="form-grid">
        <el-form-item label="弹窗宽度 width">
          <el-input v-model="dialog.width" placeholder="如 680px" />
        </el-form-item>
        <el-form-item label="新增标题 createTitle">
          <el-input v-model="dialog.createTitle" placeholder="如 新增用户" />
        </el-form-item>
        <el-form-item label="编辑标题 editTitle">
          <el-input v-model="dialog.editTitle" placeholder="如 编辑用户" />
        </el-form-item>
        <el-form-item label="提交按钮文案 submitText">
          <el-input v-model="dialog.submitText" placeholder="保存" />
        </el-form-item>
        <el-form-item label="取消按钮文案 cancelText">
          <el-input v-model="dialog.cancelText" placeholder="取消" />
        </el-form-item>
        <div class="empty-item" />
        <el-form-item label="新增成功提示 createMessage">
          <el-input v-model="dialog.createMessage" placeholder="如 用户已创建" />
        </el-form-item>
        <el-form-item label="编辑成功提示 updateMessage">
          <el-input v-model="dialog.updateMessage" placeholder="如 用户已更新" />
        </el-form-item>
        <el-form-item label="提交后刷新列表">
          <el-switch v-model="dialog.reloadAfterSubmit" />
        </el-form-item>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DialogShellState, PageShellState } from './types'

defineModel<PageShellState>('page', { required: true })
defineModel<DialogShellState>('dialog', { required: true })
</script>

<style scoped src="./editor.css"></style>
