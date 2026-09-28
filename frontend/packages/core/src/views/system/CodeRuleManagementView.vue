<template>
  <section class="education-page">
    <article class="panel-card education-panel">
      <article v-if="sectionError" class="education-empty">
        {{ sectionError }}
      </article>
      <template v-else>
        <div class="table-toolbar">
          <el-button :icon="Refresh" circle :loading="loading" @click="loadRules" />
          <NoteHint label="编号规则口径" :width="520">
            <p>
              资源清单由各业务模块在代码里登记（有编号列的主数据一条一个），这一页只能改格式与开关，
              <b>不能新增或删除资源</b>。
            </p>
            <p>
              <b>启用</b>之后，这一资源的新增页点「生成」即可填号、留空保存也会自动补号；不启用就照旧必须自己填。
            </p>
            <p>
              <b>日期段</b>决定序号多久重排一次：yyyyMMdd 每天从 001 开始，yyyy 全年累加，留空则不带日期段、一直累加。
            </p>
            <p><b>分隔符</b>留空表示各段直接相连（如 STU20260928001）。</p>
            <p>
              <b>允许断号</b>：点了生成但没保存，那个号就空着，不回收也不补。这里的编号只是快捷入口，客户自己维护。
            </p>
            <p>序号位数不足补零；实际号超出位数时自动加宽，不会截断成重复号。</p>
          </NoteHint>
        </div>

        <TableFillBox v-slot="{ height }">
          <el-table
            :data="records"
            border
            row-key="resourceCode"
            highlight-current-row
            v-loading="loading"
            :height="height"
          >
            <el-table-column prop="label" label="编号资源" min-width="150" fixed="left" sortable />
            <el-table-column label="资源码" min-width="140" prop="resourceCode" sortable>
              <template #default="{ row }">
                <code class="rule-code-text">{{ row.resourceCode }}</code>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" prop="enabled" sortable>
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '已启用' : row.configured ? '未启用' : '未配置' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="prefix" label="前缀" width="110" sortable />
            <el-table-column label="分隔符" width="100" prop="separator">
              <template #default="{ row }">{{ separatorLabel(row.separator) }}</template>
            </el-table-column>
            <el-table-column label="日期段" width="120" prop="datePattern" sortable>
              <template #default="{ row }">{{ datePatternLabel(row.datePattern) }}</template>
            </el-table-column>
            <el-table-column prop="serialLength" label="序号位数" width="90" align="right" sortable />
            <el-table-column label="下一个号" min-width="170">
              <template #default="{ row }">
                <code v-if="row.previewCode" class="rule-preview-text">{{ row.previewCode }}</code>
                <span v-else class="rule-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="remark"
              label="备注"
              min-width="150"
              show-overflow-tooltip
              sortable
            />
            <el-table-column v-if="permissions.canManageCodeRules" label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="openEditDialog(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </TableFillBox>
      </template>
    </article>

    <el-dialog v-model="dialogVisible" :title="`编辑编号规则 - ${form.label}`" width="560px">
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />
      <el-form label-position="top" class="education-form">
        <el-form-item label="启用自动编号">
          <el-switch v-model="form.enabled" />
          <span class="rule-switch-hint">
            {{
              form.enabled
                ? '该资源的新增页可点「生成」填号，留空保存也会补号'
                : '该资源的新增页仍必须自己填编号'
            }}
          </span>
        </el-form-item>

        <div class="rule-form-grid">
          <el-form-item label="编号前缀">
            <el-input v-model="form.prefix" maxlength="16" placeholder="如 STU" />
          </el-form-item>
          <el-form-item label="段间分隔符">
            <el-select v-model="form.separator">
              <el-option
                v-for="item in SEPARATOR_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <div class="rule-form-grid">
          <el-form-item label="日期段格式">
            <el-select v-model="form.datePattern">
              <el-option
                v-for="item in DATE_PATTERN_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="序号位数">
            <el-input-number v-model="form.serialLength" :min="1" :max="10" controls-position="right" />
          </el-form-item>
        </div>

        <el-form-item label="编号形态">
          <div class="rule-preview-line">
            <code class="rule-preview-text">{{ formatPreview }}</code>
            <span class="rule-muted">（日期段显示的是格式本身，保存后按当天填充；序号用 0 占位表示位数）</span>
          </div>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="选填，如「按入学年份累加」"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存规则</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import NoteHint from '@cornerstone/core/components/NoteHint.vue'
import TableFillBox from '@cornerstone/core/components/TableFillBox.vue'
import { fetchCodeRules, updateCodeRule } from '@cornerstone/core/api/system'
import type { CodeRuleSummary } from '@cornerstone/core/api/system'
import { toMessage, useSystemPermissions } from './shared'

const permissions = useSystemPermissions()

/** 分隔符与日期段的候选值与后端 CodePattern 同一套，改一边要同步另一边 */
const SEPARATOR_OPTIONS = [
  { value: '-', label: '中划线 -' },
  { value: '_', label: '下划线 _' },
  { value: '.', label: '圆点 .' },
  { value: '', label: '直接相连(不留分隔符)' },
]

const DATE_PATTERN_OPTIONS = [
  { value: '', label: '不带日期段(序号全局累加)' },
  { value: 'yyyyMMdd', label: 'yyyyMMdd(每天重排)' },
  { value: 'yyMMdd', label: 'yyMMdd(每天重排)' },
  { value: 'yyyyMM', label: 'yyyyMM(每月重排)' },
  { value: 'yyMM', label: 'yyMM(每月重排)' },
  { value: 'yyyy', label: 'yyyy(每年重排)' },
  { value: 'yy', label: 'yy(每年重排)' },
]

const loading = ref(false)
const sectionError = ref('')
const records = ref<CodeRuleSummary[]>([])

const dialogVisible = ref(false)
const submitting = ref(false)
const formError = ref('')
const editingResource = ref('')

const form = reactive({
  label: '',
  enabled: false,
  prefix: '',
  separator: '-',
  datePattern: '',
  serialLength: 3,
  remark: '',
})

/**
 * 只把各段按分隔符连起来给人看一眼形态，日期段显示的是格式本身而不是当天的值。
 * 换算真正的号在后端（CodePattern.segment/format），这里不重算，避免两处漂移。
 */
const formatPreview = computed(() => {
  const parts = [form.prefix || '?']
  if (form.datePattern) {
    parts.push(form.datePattern)
  }
  parts.push('0'.repeat(form.serialLength || 1))
  return parts.join(form.separator)
})

function separatorLabel(separator: string) {
  return SEPARATOR_OPTIONS.find((item) => item.value === separator)?.label ?? separator ?? '-'
}

function datePatternLabel(datePattern: string) {
  if (!datePattern) {
    return '全局累加'
  }
  return datePattern
}

async function loadRules() {
  loading.value = true
  sectionError.value = ''
  try {
    records.value = await fetchCodeRules()
  } catch (error) {
    records.value = []
    sectionError.value = toMessage(error, '编号规则加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 表单预填：配置过的用生效值，没配置过的用代码登记的默认值。
 * 生效值与默认值是两份数据，不能用"空串"当"没配"的哨兵——分隔符与日期段的空串本身是合法取值。
 */
function openEditDialog(row: CodeRuleSummary) {
  editingResource.value = row.resourceCode
  form.label = row.label
  form.enabled = row.enabled
  form.prefix = row.configured ? row.prefix : row.defaultPrefix
  form.separator = row.configured ? row.separator : row.defaultSeparator
  form.datePattern = row.configured ? row.datePattern : row.defaultDatePattern
  form.serialLength = row.configured ? row.serialLength : row.defaultSerialLength
  form.remark = row.remark || ''
  formError.value = ''
  dialogVisible.value = true
}

async function submit() {
  if (!form.prefix.trim()) {
    formError.value = '请填写编号前缀'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    await updateCodeRule(editingResource.value, {
      enabled: form.enabled,
      prefix: form.prefix.trim(),
      separator: form.separator,
      datePattern: form.datePattern,
      serialLength: form.serialLength,
      remark: form.remark.trim() || null,
    })
    ElMessage.success('编号规则已保存')
    dialogVisible.value = false
    await loadRules()
  } catch (error) {
    formError.value = toMessage(error, '保存编号规则失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadRules)
</script>

<style scoped>
.rule-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.rule-code-text,
.rule-preview-text {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  color: var(--tint-ink);
  background: var(--tint-bg);
  border-radius: 4px;
  padding: 1px 6px;
}

.rule-preview-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.rule-muted {
  color: var(--text-muted);
  font-size: 12px;
}

.rule-switch-hint {
  margin-left: 12px;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
