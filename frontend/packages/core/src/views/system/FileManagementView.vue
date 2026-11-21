<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="table-toolbar">
        <el-button :icon="Refresh" circle :loading="loading" @click="load" />
        <el-button :icon="Delete" :loading="cleaning" @click="cleanup">清理孤儿文件</el-button>
        <span class="education-context-text">
          业务数据只保存文件ID；未绑定业务且超过保留时长的文件会被定时清理。
        </span>
      </div>

      <FilterBar :loading="loading" @query="handleSearch" @reset="handleReset">
        <span class="filter-bar-group">
          <span class="filter-bar-group-label">文件名</span>
          <el-input
            v-model="query.keyword"
            placeholder="模糊匹配"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </span>
        <span class="filter-bar-group">
          <span class="filter-bar-group-label">业务类型</span>
          <el-input
            v-model="query.bizType"
            placeholder="如 student_photo"
            clearable
            style="width: 170px"
            @keyup.enter="handleSearch"
          />
        </span>
        <span class="filter-bar-group">
          <span class="filter-bar-group-label">上传时间</span>
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            :clearable="true"
          />
        </span>
      </FilterBar>

      <TableFillBox v-slot="{ height }">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="records"
          border
          row-key="fileId"
          highlight-current-row
          style="width: 100%"
          :height="height"
          :cell-class-name="sort.cellClass"
          @sort-change="sort.onSortChange"
        >
          <el-table-column label="预览" width="76" align="center">
            <template #default="{ row }">
              <span class="file-thumb">
                <FileImage
                  v-if="isImageFile(row.extension, row.contentType)"
                  :file-id="row.fileId"
                />
                <el-icon v-else class="file-thumb-icon"><Document /></el-icon>
              </span>
            </template>
          </el-table-column>
          <el-table-column
            prop="fileName"
            label="文件名"
            min-width="220"
            show-overflow-tooltip
            sortable="custom"
          />
          <el-table-column prop="extension" label="类型" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.extension" size="small" type="info">{{ row.extension }}</el-tag>
              <span v-else class="file-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column
            prop="sizeBytes"
            label="大小"
            width="110"
            align="right"
            sortable="custom"
          >
            <template #default="{ row }">{{ formatFileSize(row.sizeBytes) }}</template>
          </el-table-column>
          <el-table-column prop="bizType" label="业务类型" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.bizType">{{ row.bizType }}</span>
              <span v-else class="file-muted">未绑定</span>
            </template>
          </el-table-column>
          <el-table-column prop="uploadedBy" label="上传人" width="130">
            <template #default="{ row }">
              {{ row.uploadedBy ? `用户 ${row.uploadedBy}` : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="170" sortable="custom" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="download(row)">下载</el-button>
              <el-tooltip
                v-if="row.bizId"
                content="文件已被业务数据引用，请在业务处解除绑定后再删除"
                placement="top"
              >
                <span class="file-delete-disabled">
                  <el-button link type="danger" disabled>删除</el-button>
                </span>
              </el-tooltip>
              <el-button v-else link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </TableFillBox>

      <div class="file-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="handleSizeChange"
        />
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Refresh } from '@element-plus/icons-vue'
import FilterBar from '@cornerstone/core/components/FilterBar.vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import FileImage from '@cornerstone/core/components/FileImage.vue'
import { cleanupOrphanFiles, deleteFile, pageFiles, saveFile } from '@cornerstone/core/api/file'
import type { FileListView } from '@cornerstone/core/api/file'
import { useTableSort } from '@cornerstone/core/composables/use-table-sort'
import { formatFileSize, isImageFile } from '@cornerstone/core/utils/file'

const loading = ref(false)
const cleaning = ref(false)
const records = ref<FileListView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const tableRef = ref<{ clearSort: () => void } | null>(null)
const sort = useTableSort({
  onSorted: () => {
    page.value = 1
    void load()
  },
  clearTableSort: () => tableRef.value?.clearSort(),
})

const query = reactive({
  keyword: '',
  bizType: '',
})

const timeRange = ref<[string, string] | null>(null)

/** 后端 LocalDateTime 按 ISO 绑定，界面选出来的是「YYYY-MM-DD HH:mm:ss」，这里换掉空格 */
function toIsoDateTime(value?: string | null) {
  return value ? value.replace(' ', 'T') : undefined
}

function buildPageParams() {
  return {
    page: page.value,
    pageSize: pageSize.value,
    ...sort.sortParams(),
    keyword: query.keyword.trim() || undefined,
    bizType: query.bizType.trim() || undefined,
    createdFrom: toIsoDateTime(timeRange.value?.[0]),
    createdTo: toIsoDateTime(timeRange.value?.[1]),
  }
}

async function load() {
  loading.value = true
  try {
    const result = await pageFiles(buildPageParams())
    records.value = result.records || []
    total.value = result.total || 0
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(error instanceof Error && error.message ? error.message : '文件列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  void load()
}

function handleReset() {
  query.keyword = ''
  query.bizType = ''
  timeRange.value = null
  sort.resetSort()
  page.value = 1
  void load()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  void load()
}

async function download(row: FileListView) {
  try {
    await saveFile(row.fileId, row.fileName)
  } catch (error) {
    ElMessage.error(
      error instanceof Error && error.message ? error.message : '下载失败，请稍后重试'
    )
  }
}

async function remove(row: FileListView) {
  try {
    await ElMessageBox.confirm(`删除后不可恢复，确定删除「${row.fileName}」吗？`, '删除文件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteFile(row.fileId)
    ElMessage.success('文件已删除')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '删除文件失败')
  }
}

async function cleanup() {
  try {
    await ElMessageBox.confirm(
      '将删除所有未绑定业务、且超过保留时长的文件，删除后不可恢复。',
      '清理孤儿文件',
      { type: 'warning', confirmButtonText: '开始清理', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  cleaning.value = true
  try {
    const removed = await cleanupOrphanFiles()
    ElMessage.success(`本次清理 ${removed ?? 0} 个文件`)
    await load()
  } catch (error) {
    ElMessage.error(
      error instanceof Error && error.message ? error.message : '清理失败，请稍后重试'
    )
  } finally {
    cleaning.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.file-thumb {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.file-thumb-icon {
  font-size: 20px;
  color: var(--el-text-color-secondary);
}

.file-muted {
  color: var(--el-text-color-placeholder);
}

.file-delete-disabled {
  display: inline-flex;
}

.file-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
