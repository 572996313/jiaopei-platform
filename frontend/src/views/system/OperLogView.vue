<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <div class="education-table-head">
        <div>
          <div class="panel-title">操作日志</div>
          <div class="education-context-text">
            查询业务系统写操作与登录/登出的留痕记录。请求参数中的手机号、身份证、银行卡等敏感信息已脱敏展示。
          </div>
        </div>
        <div class="table-actions">
          <el-button :loading="loading" @click="handleSearch">刷新</el-button>
        </div>
      </div>

      <el-form :inline="true" class="operlog-filter-form" @submit.prevent>
        <el-form-item label="操作人">
          <el-input v-model="query.username" placeholder="账号" clearable style="width: 160px" @keyup.enter="handleSearch" @clear="handleSearch" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="query.actionType" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
            <el-option v-for="item in actionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="端口">
          <el-select v-model="query.portal" placeholder="全部" clearable style="width: 140px" @change="handleSearch">
            <el-option v-for="item in portalOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.success" placeholder="全部" clearable style="width: 120px" @change="handleSearch">
            <el-option label="成功" :value="true" />
            <el-option label="失败" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作名称">
          <el-input v-model="query.operation" placeholder="模糊匹配操作名" clearable style="width: 180px" @keyup.enter="handleSearch" @clear="handleSearch" />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
            :clearable="true"
            @change="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button :disabled="loading" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="records" stripe style="width: 100%">
        <el-table-column prop="createdAt" label="操作时间" width="170" />
        <el-table-column prop="operation" label="操作名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="username" label="操作人" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.username || '未知' }}
          </template>
        </el-table-column>
        <el-table-column prop="portal" label="端口" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="portalTagType(row.portal)">{{ portalLabel(row.portal) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="actionType" label="类型" width="90">
          <template #default="{ row }">
            {{ actionTypeLabel(row.actionType) }}
          </template>
        </el-table-column>
        <el-table-column prop="method" label="方法" width="80" />
        <el-table-column prop="path" label="请求路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="clientIp" label="IP" width="130" show-overflow-tooltip />
        <el-table-column prop="durationMs" label="耗时(ms)" width="90" align="right" />
        <el-table-column prop="success" label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="operlog-pagination">
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

    <el-drawer v-model="detailVisible" title="操作日志详情" size="520px">
      <el-descriptions :column="1" border v-if="detail">
        <el-descriptions-item label="操作时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作名称">{{ detail.operation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">{{ actionTypeLabel(detail.actionType) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="端口">{{ portalLabel(detail.portal) }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ detail.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求路径">{{ detail.path || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ detail.clientIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.durationMs ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="detail.success ? 'success' : 'danger'">{{ detail.success ? '成功' : '失败' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="链路ID">{{ detail.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent">
          <span style="word-break: break-all">{{ detail.userAgent || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数">
          <pre v-if="detail.params" class="operlog-params">{{ detail.params }}</pre>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="异常信息">
          <pre v-if="detail.errorMsg" class="operlog-params">{{ detail.errorMsg }}</pre>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import type { OperLogSummary } from '@/api/system'
import { fetchOperLogs } from '@/api/system'
import { toMessage } from './shared'

const loading = ref(false)
const records = ref<OperLogSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const detailVisible = ref(false)
const detail = ref<OperLogSummary | null>(null)

const actionTypeOptions = [
  { label: '新增', value: 'CREATE' },
  { label: '修改', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '登录', value: 'LOGIN' },
  { label: '登出', value: 'LOGOUT' },
  { label: '其他', value: 'OTHER' },
]

const portalOptions = [
  { label: '管理后台', value: 'admin' },
  { label: '学生端', value: 'student' },
  { label: '家长端', value: 'parent' },
]

const query = reactive<{
  username: string
  actionType: string
  portal: string
  operation: string
  success: boolean | null
}>({
  username: '',
  actionType: '',
  portal: '',
  operation: '',
  success: null,
})

const timeRange = ref<[string, string] | null>(null)

function actionTypeLabel(value?: string | null) {
  return actionTypeOptions.find((item) => item.value === value)?.label || value || '-'
}

function portalLabel(value?: string | null) {
  return portalOptions.find((item) => item.value === value)?.label || value || '未知'
}

function portalTagType(value?: string | null) {
  if (value === 'admin') {
    return 'primary'
  }
  if (value === 'student') {
    return 'warning'
  }
  if (value === 'parent') {
    return 'success'
  }
  return 'info'
}

function buildPageParams() {
  return {
    page: page.value,
    pageSize: pageSize.value,
    username: query.username.trim() || undefined,
    actionType: query.actionType || undefined,
    portal: query.portal || undefined,
    operation: query.operation.trim() || undefined,
    success: query.success ?? undefined,
    beginTime: timeRange.value?.[0] || undefined,
    endTime: timeRange.value?.[1] || undefined,
  }
}

async function load() {
  loading.value = true
  try {
    const result = await fetchOperLogs(buildPageParams())
    records.value = result.records || []
    total.value = result.total || 0
  } catch (error) {
    records.value = []
    total.value = 0
    ElMessage.error(toMessage(error, '操作日志加载失败,请确认账号权限'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  void load()
}

function handleReset() {
  query.username = ''
  query.actionType = ''
  query.portal = ''
  query.operation = ''
  query.success = null
  timeRange.value = null
  page.value = 1
  void load()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  void load()
}

function openDetail(row: OperLogSummary) {
  detail.value = row
  detailVisible.value = true
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.operlog-filter-form {
  margin-bottom: 12px;
}
.operlog-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.operlog-params {
  margin: 0;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.6;
  background: #f7f8fa;
  padding: 8px;
  border-radius: 4px;
}
</style>