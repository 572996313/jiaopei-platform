<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <article v-if="dataSourceSectionError" class="education-empty">
        {{ dataSourceSectionError }}
      </article>
      <template v-else>
        <div class="table-toolbar">
          <el-button @click="guideDialogVisible = true">帮助</el-button>
          <el-button
            v-if="permissions.canManageDataSources"
            type="primary"
            @click="openCreateDialog"
            >新增数据源</el-button
          >
          <el-button :icon="Refresh" circle :loading="dataSourceLoading" @click="loadDataSources" />
          <div class="education-context-text">页面下拉选项的接口与字段映射在此维护。</div>
        </div>

        <TableFillBox v-slot="{ height }">
          <el-table
            :data="dataSourceRecords"
            border
            row-key="id"
            highlight-current-row
            v-loading="dataSourceLoading"
            :height="height"
          >
            <el-table-column
              prop="sourceCode"
              label="数据源编码"
              min-width="140"
              show-overflow-tooltip
              fixed="left"
              sortable
            />
            <el-table-column
              prop="sourceName"
              label="名称"
              min-width="120"
              show-overflow-tooltip
              sortable
            />
            <el-table-column label="类型" width="90" prop="sourceType" sortable>
              <template #default="{ row }">
                <el-tag :type="row.sourceType === 'dict' ? 'warning' : 'primary'" size="small">{{
                  sourceTypeLabel(row.sourceType)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="接口路径 / 字典类型" min-width="210">
              <template #default="{ row }">
                <code class="source-endpoint">{{
                  row.sourceType === 'dict' ? row.dictType : row.endpoint
                }}</code>
              </template>
            </el-table-column>
            <el-table-column label="值/标签字段" min-width="130">
              <template #default="{ row }">
                <span v-if="row.sourceType === 'api'" class="source-keys"
                  >{{ row.valueKey || 'id' }} → {{ row.labelKey }}</span
                >
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" prop="status" sortable>
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{
                  statusLabel(row.status, statusLabelMap)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="description"
              label="说明"
              min-width="160"
              show-overflow-tooltip
              sortable
            />
            <el-table-column
              v-if="permissions.canManageDataSources"
              label="操作"
              width="140"
              fixed="right"
            >
              <template #default="{ row }">
                <div class="system-operation-group">
                  <el-button text type="primary" @click="openEditDialog(row)">编辑</el-button>
                  <el-button
                    text
                    type="danger"
                    :disabled="seedDataSourceIds.has(row.id)"
                    @click="handleDelete(row)"
                    >删除</el-button
                  >
                </div>
              </template>
            </el-table-column>
          </el-table>
        </TableFillBox>
      </template>
    </article>

    <el-dialog
      v-model="dataSourceDialogVisible"
      :title="dataSourceDialogMode === 'create' ? '新增数据源' : '编辑数据源'"
      width="640px"
    >
      <el-alert
        v-if="dataSourceFormError"
        :title="dataSourceFormError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form
        ref="dataSourceFormRef"
        :model="dataSourceForm"
        :rules="dataSourceRules"
        label-position="top"
        class="education-form"
        scroll-to-error
        @submit.prevent
      >
        <div class="source-form-grid">
          <el-form-item label="数据源编码" prop="sourceCode">
            <el-input
              v-model="dataSourceForm.sourceCode"
              :disabled="dataSourceDialogMode === 'edit'"
              placeholder="如 institutions,建后不可改"
            />
          </el-form-item>
          <el-form-item label="名称" prop="sourceName">
            <el-input
              v-model="dataSourceForm.sourceName"
              maxlength="64"
              placeholder="如 机构列表"
            />
          </el-form-item>
        </div>
        <el-form-item label="数据源类型">
          <el-radio-group v-model="dataSourceForm.sourceType">
            <el-radio value="api">接口数据(api)</el-radio>
            <el-radio value="dict">字典(dict)</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="dataSourceForm.sourceType === 'api'">
          <el-form-item label="接口路径" prop="endpoint">
            <el-input
              v-model="dataSourceForm.endpoint"
              maxlength="128"
              placeholder="如 /organizations/institutions"
            />
          </el-form-item>
          <div class="source-form-grid">
            <el-form-item label="选项值字段(缺省 id)">
              <el-input v-model="dataSourceForm.valueKey" maxlength="64" placeholder="如 id" />
            </el-form-item>
            <el-form-item label="选项标签字段">
              <el-input v-model="dataSourceForm.labelKey" maxlength="64" placeholder="如 orgName" />
            </el-form-item>
          </div>
          <el-form-item label="请求参数定义(JSON 数组,选填)" prop="paramDefs">
            <el-input
              v-model="dataSourceForm.paramDefs"
              type="textarea"
              :rows="3"
              placeholder='如 [{"name":"institutionId","required":false}]'
            />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="字典类型编码" prop="dictType">
            <el-input
              v-model="dataSourceForm.dictType"
              maxlength="64"
              placeholder="如 common_status"
            />
          </el-form-item>
        </template>

        <div class="source-form-grid">
          <el-form-item label="状态">
            <el-select v-model="dataSourceForm.status">
              <el-option
                v-for="item in statusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="说明">
            <el-input
              v-model="dataSourceForm.description"
              maxlength="500"
              placeholder="选填,说明该数据源的用途"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dataSourceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dataSourceSubmitting" @click="submit">
          {{ dataSourceDialogMode === 'create' ? '创建数据源' : '保存编辑' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 帮助弹窗 -->
    <el-dialog v-model="guideDialogVisible" title="数据源配置说明" width="640px">
      <div class="guide-section">
        <h4 class="guide-title">这是什么?</h4>
        <p class="guide-text">
          数据源把「页面下拉框、筛选框的选项数据」收敛成一条配置: 编码(如 institutions)→ 接口路径 /
          字典类型 → 选项的值字段、标签字段。 「列表配置」里下拉控件的
          <code>optionsLoader</code>
          填的就是数据源编码。
        </p>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">改接口要怎么做?</h4>
        <ul class="guide-list">
          <li>编辑这条数据源,把「接口路径」换成新地址,保存即可。</li>
          <li>返回字段变了,同步改「值字段 / 标签字段」。</li>
          <li>所有引用该编码的页面下次加载立即生效,不用改代码、不用发版。</li>
        </ul>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">请求参数怎么配?</h4>
        <p class="guide-text">
          「请求参数定义」是 JSON 数组,每项含
          <code>name</code>
          (与接口参数同名)。前端组件按名字传参,例如按机构过滤的下拉依赖机构:
          <code>[{"name":"institutionId","required":false}]</code>。
        </p>
      </div>
      <div class="guide-section">
        <h4 class="guide-title">内置数据源能删吗?</h4>
        <p class="guide-text">
          随迁移种下的内置数据源支撑现有页面,不确定有没有页面在用之前先停用,不要直接删除。
        </p>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import {
  createDataSource,
  deleteDataSource,
  fetchDataSources,
  updateDataSource,
} from '@cornerstone/core/api/system'
import type { DataSourceSummary } from '@cornerstone/core/api/system'
import { useDataSourceStore } from '@cornerstone/core/stores/dataSource'
import {
  ensureStatusDictLoaded,
  statusLabel,
  statusTagType,
  toMessage,
  trimToUndefined,
  useStatusLabelMap,
  useStatusOptions,
} from './shared'
import { useSystemPermissions } from './shared'

const dataSourceStore = useDataSourceStore()
const permissions = useSystemPermissions()
const statusOptions = useStatusOptions()
const statusLabelMap = useStatusLabelMap()

const dataSourceSectionError = ref('')
const dataSourceLoading = ref(false)
const dataSourceRecords = ref<DataSourceSummary[]>([])
const dataSourceDialogVisible = ref(false)
const dataSourceDialogMode = ref<'create' | 'edit'>('create')
const dataSourceSubmitting = ref(false)
const dataSourceFormError = ref('')
const selectedDataSource = ref<DataSourceSummary | null>(null)
// V33 种子的六条内置数据源(id 1-6)支撑现有页面,后端禁删,前端同步禁用删除按钮
const seedDataSourceIds = new Set(['1', '2', '3', '4', '5', '6'])

const guideDialogVisible = ref(false)

const dataSourceFormRef = ref<FormInstance | null>(null)

const dataSourceForm = reactive({
  sourceCode: '',
  sourceName: '',
  endpoint: '',
  valueKey: '',
  labelKey: '',
  paramDefs: '',
  sourceType: 'api',
  dictType: '',
  description: '',
  status: 'active',
})

const dataSourceRules: FormRules = {
  // 编码只在新建时填写，编辑态输入框禁用且必带回显值，统一规则即可通过
  sourceCode: [{ required: true, whitespace: true, message: '请填写数据源编码', trigger: 'blur' }],
  sourceName: [{ required: true, whitespace: true, message: '请填写名称', trigger: 'blur' }],
  // 接口路径与字典类型编码二选一必填，依赖 sourceType 在下方 watch 里重校验
  endpoint: [
    {
      validator: (_rule, value: string, callback) => {
        if (dataSourceForm.sourceType === 'api' && !value.trim()) {
          callback(new Error('请填写接口路径'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  dictType: [
    {
      validator: (_rule, value: string, callback) => {
        if (dataSourceForm.sourceType === 'dict' && !value.trim()) {
          callback(new Error('请填写字典类型编码'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  paramDefs: [
    {
      // JSON 合法性校验挂在字段上，空串表示不配置直接通过
      validator: (_rule, value: string, callback) => {
        const trimmed = value.trim()
        if (!trimmed) {
          callback()
          return
        }
        let parsed: unknown
        try {
          parsed = JSON.parse(trimmed)
        } catch {
          callback(new Error('请求参数定义不是合法的 JSON'))
          return
        }
        if (!Array.isArray(parsed)) {
          callback(
            new Error('请求参数定义必须是 JSON 数组,如 [{"name":"institutionId","required":false}]')
          )
          return
        }
        for (let i = 0; i < parsed.length; i++) {
          const item = parsed[i]
          if (
            typeof item !== 'object' ||
            item === null ||
            typeof (item as { name?: unknown }).name !== 'string' ||
            !(item as { name: string }).name.trim()
          ) {
            callback(new Error(`请求参数定义[${i}] 缺少必填字段 name`))
            return
          }
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

function sourceTypeLabel(type: string) {
  return type === 'dict' ? '字典' : '接口'
}

/* ---------------- 数据加载 ---------------- */

async function loadDataSources() {
  dataSourceLoading.value = true
  dataSourceSectionError.value = ''
  try {
    dataSourceRecords.value = await fetchDataSources()
  } catch (error) {
    dataSourceRecords.value = []
    dataSourceSectionError.value = toMessage(error, '数据源加载失败')
  } finally {
    dataSourceLoading.value = false
  }
}

/* ---------------- 打开 / 关闭 ---------------- */

function openCreateDialog() {
  dataSourceDialogMode.value = 'create'
  selectedDataSource.value = null
  dataSourceForm.sourceCode = ''
  dataSourceForm.sourceName = ''
  dataSourceForm.endpoint = ''
  dataSourceForm.valueKey = ''
  dataSourceForm.labelKey = ''
  dataSourceForm.paramDefs = ''
  dataSourceForm.sourceType = 'api'
  dataSourceForm.dictType = ''
  dataSourceForm.description = ''
  dataSourceForm.status = 'active'
  dataSourceFormError.value = ''
  dataSourceDialogVisible.value = true
  // 上一次弹窗留下的红框不能带给新一次填写
  void nextTick(() => dataSourceFormRef.value?.clearValidate())
}

function openEditDialog(row: DataSourceSummary) {
  dataSourceDialogMode.value = 'edit'
  selectedDataSource.value = row
  dataSourceForm.sourceCode = row.sourceCode
  dataSourceForm.sourceName = row.sourceName
  dataSourceForm.endpoint = row.endpoint || ''
  dataSourceForm.valueKey = row.valueKey || ''
  dataSourceForm.labelKey = row.labelKey || ''
  dataSourceForm.paramDefs = row.paramDefs || ''
  dataSourceForm.sourceType = row.sourceType || 'api'
  dataSourceForm.dictType = row.dictType || ''
  dataSourceForm.description = row.description || ''
  dataSourceForm.status = row.status || 'active'
  dataSourceFormError.value = ''
  dataSourceDialogVisible.value = true
  void nextTick(() => dataSourceFormRef.value?.clearValidate())
}

/** paramDefs 的 JSON 合法性已交给 el-form 规则，提交时只需取出修剪后的值（空串表示不配置） */
function normalizedParamDefs(raw: string) {
  const trimmed = raw.trim()
  return trimmed ? trimmed : undefined
}

/* ---------------- 提交 ---------------- */

async function submit() {
  // 校验失败时 el-form 已经在各字段上标红并给出提示，这里不再补一条顶部总文案
  const valid = await dataSourceFormRef.value?.validate().catch(() => false)
  if (valid === false) {
    return
  }

  const common = {
    sourceName: dataSourceForm.sourceName.trim(),
    endpoint: trimToUndefined(dataSourceForm.endpoint),
    valueKey: trimToUndefined(dataSourceForm.valueKey),
    labelKey: trimToUndefined(dataSourceForm.labelKey),
    paramDefs: normalizedParamDefs(dataSourceForm.paramDefs),
    sourceType: dataSourceForm.sourceType,
    dictType: trimToUndefined(dataSourceForm.dictType),
    description: trimToUndefined(dataSourceForm.description),
    status: dataSourceForm.status,
  }

  dataSourceSubmitting.value = true
  dataSourceFormError.value = ''
  try {
    if (dataSourceDialogMode.value === 'create') {
      await createDataSource({
        ...common,
        sourceCode: dataSourceForm.sourceCode.trim(),
        method: 'GET',
      })
      ElMessage.success('数据源已创建')
    } else if (selectedDataSource.value) {
      await updateDataSource(selectedDataSource.value.id, common)
      ElMessage.success('数据源已更新')
    }
    dataSourceDialogVisible.value = false
    dataSourceStore.invalidate()
    await loadDataSources()
  } catch (error) {
    dataSourceFormError.value = toMessage(
      error,
      dataSourceDialogMode.value === 'create' ? '创建数据源失败' : '保存数据源失败'
    )
  } finally {
    dataSourceSubmitting.value = false
  }
}

async function handleDelete(row: DataSourceSummary) {
  try {
    await ElMessageBox.confirm(
      `确定删除数据源「${row.sourceName || row.sourceCode}」吗?引用该数据源的页面下拉将无选项。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await deleteDataSource(row.id)
    ElMessage.success('数据源已删除')
    dataSourceStore.invalidate()
    await loadDataSources()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除数据源失败'))
  }
}

onMounted(async () => {
  await Promise.allSettled([ensureStatusDictLoaded(), loadDataSources()])
})

watch(
  () => dataSourceForm.sourceType,
  () => {
    // 弹窗未打开时（打开弹窗前给 sourceType 赋默认值）不要触发校验
    if (!dataSourceDialogVisible.value) {
      return
    }
    // 接口/字典两种形态切换后，原来那格的红框状态对新的必填口径可能是错的，两格都重校验；
    // v-if 刚换过 DOM，要等下一帧新挂载的字段才接得到校验
    void nextTick(() => {
      void dataSourceFormRef.value?.validateField(['endpoint', 'dictType']).catch(() => undefined)
    })
  }
)
</script>

<style scoped>
.source-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.source-endpoint {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  color: var(--tint-ink);
  background: var(--tint-bg);
  border-radius: 4px;
  padding: 1px 6px;
}

.source-keys {
  font-size: 13px;
  color: var(--text-primary);
}

.guide-section {
  margin-bottom: 18px;
}

.guide-title {
  margin: 0 0 6px;
  font-size: 14px;
  color: var(--tint-ink);
}

.guide-text {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-primary);
}

.guide-text code {
  background: var(--tint-bg);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 12px;
}

.guide-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--text-primary);
}
</style>
