<template>
  <el-drawer
    :model-value="modelValue"
    size="520px"
    :title="`家长联系人：${contactLabel}`"
    @update:model-value="emit('update:modelValue', Boolean($event))"
  >
    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <div class="table-toolbar">
      <el-button link type="primary" :disabled="!studentId" @click="openDialog('create')">
        新增联系人
      </el-button>
    </div>

    <el-table
      :data="parents"
      border
      row-key="id"
      v-loading="loading"
      :max-height="TABLE_STACK_MAX_HEIGHT"
      :empty-text="loading ? '' : '该学员下暂无家长联系人。'"
    >
      <el-table-column prop="parentName" label="联系人" min-width="140" show-overflow-tooltip />
      <el-table-column label="关系" width="110" prop="relationType">
        <template #default="{ row }">{{ relationLabel(row.relationType) }}</template>
      </el-table-column>
      <el-table-column prop="parentPhone" label="联系电话" width="150" />
      <el-table-column label="主联系人" width="100" prop="primaryContact">
        <template #default="{ row }">{{ row.primaryContact ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="紧急联系" width="100" prop="emergencyContact">
        <template #default="{ row }">{{ row.emergencyContact ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" prop="status">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button
              link
              type="warning"
              :disabled="row.primaryContact"
              @click="promotePrimary(row)"
            >
              设为主联系人
            </el-button>
            <el-button link type="primary" @click="openDialog('edit', row)">编辑</el-button>
            <el-button link type="danger" @click="removeParent(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>


    <!-- 学员由行上带进来，表单里不再选：省一格高度，也防把联系人记到别人孩子名下 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增家长联系人' : '编辑家长联系人'"
      width="560px"
      append-to-body
    >
      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        class="education-alert"
      />

      <el-form label-position="top" class="education-form">
        <el-form-item label="联系人姓名">
          <el-input v-model="form.parentName" placeholder="例如 学员妈妈" />
        </el-form-item>
        <el-form-item label="关系">
          <DictSelect v-model="form.relationType" type-code="parent_relation" placeholder="选择关系" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.parentPhone" placeholder="填写家长手机号" />
        </el-form-item>
        <el-form-item label="微信号">
          <el-input v-model="form.wechatNo" placeholder="可选" />
        </el-form-item>
        <el-form-item label="联系人状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
        <el-form-item label="主联系人">
          <el-switch v-model="form.isPrimary" />
        </el-form-item>
        <el-form-item label="紧急联系人">
          <el-switch v-model="form.isEmergencyContact" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充联系人说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">
          {{ dialogMode === 'create' ? '提交联系人' : '保存联系人' }}
        </el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'
import DictSelect from '@cornerstone/core/components/DictSelect.vue'
import { useDictStore } from '@cornerstone/core/stores/dict'
import { TABLE_STACK_MAX_HEIGHT } from '@cornerstone/core/utils/table-view'
import { studentLabelOf } from '@cornerstone/module-education/composables/studentLabels'
import {
  createStudentParent,
  deleteStudentParent,
  fetchStudentParents,
  updateStudentParent,
  type StudentParentCreatePayload,
  type StudentParentRecord,
} from '@cornerstone/module-education/api/education'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 机构 id：小号机构运行期可能是数字 */
    institutionId?: string | number | null
    /** 抽屉维护的是哪一个学员；由宿主在点行时定下来 */
    studentId?: string | null
  }>(),
  { institutionId: null, studentId: null }
)

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const dictStore = useDictStore()

const parents = ref<StudentParentRecord[]>([])
const loading = ref(false)
const loadError = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<string | null>(null)
const submitting = ref(false)
const formError = ref('')

const form = reactive({
  parentName: '',
  relationType: '',
  parentPhone: '',
  wechatNo: '',
  isPrimary: true,
  isEmergencyContact: false,
  status: 'active',
  remark: '',
})

const contactLabel = computed(() =>
  props.studentId ? studentLabelOf(props.studentId) : '未选择学员'
)

function trimToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function toMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function relationLabel(value?: string | null) {
  return dictStore.displayLabel('parent_relation', value)
}

function statusLabel(status?: string | null) {
  return status === 'inactive' ? '停用' : '启用'
}

function statusTagType(status?: string | null) {
  return status === 'inactive' ? 'info' : 'success'
}

async function loadParents() {
  if (!props.institutionId || !props.studentId) {
    parents.value = []
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    parents.value = await fetchStudentParents({
      institutionId: String(props.institutionId),
      studentId: props.studentId,
    })
  } catch (error) {
    parents.value = []
    loadError.value = toMessage(error, '家长联系人加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm(row?: StudentParentRecord) {
  form.parentName = row?.parentName ?? ''
  form.relationType = row?.relationType ?? ''
  form.parentPhone = row?.parentPhone ?? ''
  form.wechatNo = row?.wechatNo ?? ''
  form.isPrimary = Boolean(row?.primaryContact ?? true)
  form.isEmergencyContact = Boolean(row?.emergencyContact ?? false)
  form.status = row?.status ?? 'active'
  form.remark = row?.remark ?? ''
  formError.value = ''
  editingId.value = row?.id ?? null
}

function openDialog(mode: 'create' | 'edit', row?: StudentParentRecord) {
  dialogMode.value = mode
  resetForm(mode === 'edit' ? row : undefined)
  dialogVisible.value = true
}

function buildPayload(): StudentParentCreatePayload {
  return {
    institutionId: String(props.institutionId),
    studentId: props.studentId as string,
    parentName: form.parentName.trim(),
    relationType: trimToUndefined(form.relationType),
    parentPhone: trimToUndefined(form.parentPhone),
    wechatNo: trimToUndefined(form.wechatNo),
    isPrimary: form.isPrimary,
    isEmergencyContact: form.isEmergencyContact,
    status: trimToUndefined(form.status),
    remark: trimToUndefined(form.remark),
  }
}

async function submit() {
  if (!trimToUndefined(form.parentName)) {
    formError.value = '请填写联系人姓名'
    return
  }
  submitting.value = true
  formError.value = ''
  try {
    const payload = buildPayload()
    if (dialogMode.value === 'create') {
      await createStudentParent(payload)
      ElMessage.success('家长联系人已新增')
    } else if (editingId.value) {
      await updateStudentParent(editingId.value, payload)
      ElMessage.success('家长联系人已更新')
    }
    dialogVisible.value = false
    await loadParents()
  } catch (error) {
    formError.value = toMessage(
      error,
      dialogMode.value === 'create' ? '新增家长联系人失败' : '更新家长联系人失败'
    )
  } finally {
    submitting.value = false
  }
}

async function promotePrimary(row: StudentParentRecord) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.parentName || '该联系人'}」设为主联系人吗？`,
      '切换主联系人',
      { type: 'warning', confirmButtonText: '设为主联系人', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  try {
    await updateStudentParent(row.id, {
      institutionId: String(props.institutionId),
      studentId: (row.studentId || props.studentId) as string,
      parentName: row.parentName || '',
      relationType: trimToUndefined(row.relationType || ''),
      parentPhone: trimToUndefined(row.parentPhone || ''),
      wechatNo: trimToUndefined(row.wechatNo || ''),
      isPrimary: true,
      isEmergencyContact: Boolean(row.emergencyContact),
      status: row.status || 'active',
      remark: trimToUndefined(row.remark || ''),
    })
    ElMessage.success('已切换主联系人')
    await loadParents()
  } catch (error) {
    ElMessage.error(toMessage(error, '切换主联系人失败'))
  }
}

async function removeParent(row: StudentParentRecord) {
  try {
    await ElMessageBox.confirm(`确认删除 ${row.parentName || '该联系人'} 吗？`, '删除家长联系人', {
      type: 'warning',
    })
  } catch {
    return
  }

  try {
    await deleteStudentParent(row.id)
    ElMessage.success('家长联系人已删除')
    await loadParents()
  } catch (error) {
    ElMessage.error(toMessage(error, '删除家长联系人失败'))
  }
}

// 抽屉是常驻挂载的（el-drawer 只隐藏内容），所以打开与换学员都要重拉，
// 否则会看到上一个孩子的联系人
watch(
  () => [props.modelValue, props.studentId],
  ([visible]) => {
    if (!visible) {
      return
    }
    // 关系列的中文名走字典，抽屉自己拉一次，宿主不必替它管
    void dictStore.ensureLoaded(['parent_relation']).catch(() => undefined)
    void loadParents()
  }
)

watch(dialogVisible, (visible) => {
  if (!visible) {
    resetForm()
  }
})
</script>
