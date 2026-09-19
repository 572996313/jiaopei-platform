<template>
  <div class="configurable-form">
    <!-- 配置错误:直接展示,由管理页修复 -->
    <el-alert
      v-if="configError"
      :title="configError"
      type="error"
      show-icon
      :closable="false"
      class="education-alert"
    />

    <!-- 未配置表单:提示先到列表配置里填写表单配置 -->
    <el-empty
      v-else-if="!formConfig"
      :description="`资源「${resourceCode}」未配置表单,请先在「列表配置」中填写 form_json`"
      :image-size="80"
    />

    <el-form
      v-else
      ref="formRef"
      :model="model"
      :rules="rules"
      :label-width="formConfig?.labelWidth ?? 'auto'"
      class="configurable-form-body"
      @submit.prevent
    >
      <div
        class="configurable-form-grid"
        :style="
          gridColumns > 1 ? { gridTemplateColumns: `repeat(${gridColumns}, 1fr)` } : undefined
        "
      >
        <el-form-item
          v-for="field in visibleFields"
          :key="field.field"
          :prop="field.field"
          :label="field.label"
          :class="{ 'is-full-row': (field.gridSpan ?? 1) >= gridColumns }"
          :style="spanStyle(field)"
        >
          <!-- input / password -->
          <el-input
            v-if="field.type === 'input' || field.type === 'password'"
            v-model="model[field.field]"
            :type="field.type === 'password' ? 'password' : 'text'"
            show-password
            :placeholder="field.placeholder || `请输入${field.label}`"
            :maxlength="field.maxlength"
            :disabled="isDisabled(field)"
            clearable
          />

          <!-- textarea -->
          <el-input
            v-else-if="field.type === 'textarea'"
            v-model="model[field.field]"
            type="textarea"
            :rows="field.rows ?? 3"
            :maxlength="field.maxlength"
            :placeholder="field.placeholder || `请输入${field.label}`"
            :disabled="isDisabled(field)"
          />

          <!-- number -->
          <el-input-number
            v-else-if="field.type === 'number'"
            v-model="model[field.field]"
            :min="field.min ?? 0"
            :max="field.max ?? 9999"
            :disabled="isDisabled(field)"
            controls-position="right"
          />

          <!-- select:静态 options 或 optionsLoader -->
          <el-select
            v-else-if="field.type === 'select'"
            v-model="model[field.field]"
            :placeholder="field.placeholder || '请选择'"
            :clearable="field.clearable !== false"
            :disabled="isDisabled(field)"
            filterable
            class="configurable-form-control"
            @change="handleFieldChange(field, model[field.field])"
          >
            <el-option
              v-for="option in optionsFor(field)"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>

          <!-- dict-select:字典下拉 -->
          <DictSelect
            v-else-if="field.type === 'dict-select'"
            v-model="model[field.field]"
            :type-code="field.dictType || ''"
            :placeholder="field.placeholder || '请选择'"
            :disabled="isDisabled(field)"
            @change="handleFieldChange(field, model[field.field])"
          />

          <!-- switch -->
          <el-switch
            v-else-if="field.type === 'switch'"
            v-model="model[field.field]"
            :disabled="isDisabled(field)"
            @change="handleFieldChange(field, model[field.field])"
          />

          <!-- radio -->
          <el-radio-group
            v-else-if="field.type === 'radio'"
            v-model="model[field.field]"
            :disabled="isDisabled(field)"
            @change="handleFieldChange(field, model[field.field])"
          >
            <el-radio v-for="option in optionsFor(field)" :key="option.value" :value="option.value">
              {{ option.label }}
            </el-radio>
          </el-radio-group>

          <!-- checkbox-group -->
          <el-checkbox-group
            v-else-if="field.type === 'checkbox-group'"
            v-model="model[field.field]"
            :disabled="isDisabled(field)"
            @change="handleFieldChange(field, model[field.field])"
          >
            <el-checkbox
              v-for="option in optionsFor(field)"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-checkbox>
          </el-checkbox-group>

          <!-- 复合渲染器(如 permission-groups):代码侧自定义,走 slot 或内置占位 -->
          <slot
            v-else
            :name="`field-${field.field}`"
            :field="field"
            :model="model"
            :disabled="isDisabled(field)"
            :mode="mode"
          />

          <div v-if="field.tip" class="configurable-form-tip">{{ field.tip }}</div>
        </el-form-item>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormItemRule } from 'element-plus'
import { useListConfigStore } from '@/stores/listConfig'
import { useDictStore } from '@/stores/dict'
import { useDataSourceStore } from '@/stores/dataSource'
import { resolveOptionLoader, type RenderContext } from '../ConfigurableList/renderers'
import type {
  FormFieldConfig,
  FormMode,
  FormPageConfig,
  QueryOption,
} from '../ConfigurableList/types'

const props = withDefaults(
  defineProps<{
    /** 列表配置资源编码,如 system-users */
    resourceCode: string
    /** 当前模式:create=新增 / edit=编辑 */
    mode: FormMode
    /** 编辑回填初始值(键与 field 对应,缺省用字段 defaultValue) */
    initial?: Record<string, any>
    /** 选项加载上下文(optionsLoader 数据源,宿主注入) */
    context?: RenderContext
    /** 字段联动回调:field.field → (form, field) => void(如机构变化刷新校区) */
    onFieldChange?: Record<string, (form: Record<string, any>, field: FormFieldConfig) => void>
    /** 是否禁用整个表单(如提交中) */
    disabled?: boolean
    /** 栅格列数,缺省 2 */
    gridColumns?: number
  }>(),
  {
    context: undefined,
    onFieldChange: undefined,
    disabled: false,
    gridColumns: 2,
  }
)

const emit = defineEmits<{
  /** 任意字段值变化(供宿主监听做联动/脏检查) */
  change: [field: string, value: unknown]
}>()

const listConfigStore = useListConfigStore()
const dictStore = useDictStore()
const dataSourceStore = useDataSourceStore()

const configError = ref('')
const loading = ref(true)
const formConfig = ref<FormPageConfig | null>(null)
const model = reactive<Record<string, any>>({})
const formRef = ref<FormInstance | null>(null)

const visibleFields = computed<FormFieldConfig[]>(() => {
  if (!formConfig.value) {
    return []
  }
  const mode = props.mode
  return formConfig.value.fields.filter((field) => !field.showOn || field.showOn.includes(mode))
})

const gridColumns = computed(() => props.gridColumns || formConfig.value?.gridColumns || 2)

/** 由 schema 生成 el-form rules(必填/轻量规则),复杂校验走宿主 submit 前自检 */
const rules = computed<Record<string, FormItemRule[]>>(() => {
  const result: Record<string, FormItemRule[]> = {}
  for (const field of visibleFields.value) {
    const list: FormItemRule[] = []
    if (field.required) {
      list.push({
        required: true,
        message: `${field.label}不能为空`,
        trigger: 'blur',
      })
    }
    for (const rule of field.rules ?? []) {
      if (rule.required !== undefined) {
        list.push({
          required: rule.required,
          message: rule.message || `${field.label}不能为空`,
          trigger: 'blur',
        })
      }
    }
    if (list.length) {
      result[field.field] = list
    }
  }
  return result
})

function isDisabled(field: FormFieldConfig) {
  return props.disabled || Boolean(field.disabledOn?.includes(props.mode))
}

/** 选项:静态 options 优先,其次 optionsLoader 统一解析(context 优先,配置化兜底) */
function optionsFor(field: FormFieldConfig): QueryOption[] {
  if (field.options?.length) {
    return field.options
  }
  if (field.optionsLoader) {
    return resolveOptionLoader(field.optionsLoader, props.context)
  }
  return []
}

function spanStyle(field: FormFieldConfig) {
  const span = field.gridSpan ?? 1
  const cols = gridColumns.value
  if (span >= cols || cols <= 1) {
    return { gridColumn: `1 / -1` }
  }
  return span > 1 ? { gridColumn: `span ${span}` } : undefined
}

/** 控件默认值:按类型给合理初值(下拉给空串以兼容 trim,勾选给数组) */
function defaultFor(field: FormFieldConfig): unknown {
  if (field.defaultValue !== undefined) {
    return field.defaultValue
  }
  switch (field.type) {
    case 'checkbox-group':
    case 'permission-groups':
      return []
    case 'switch':
      return false
    case 'number':
      return 0
    case 'select':
    case 'dict-select':
      return ''
    default:
      return ''
  }
}

function initModel() {
  for (const key of Object.keys(model)) {
    delete model[key]
  }
  if (!formConfig.value) {
    return
  }
  const initial = props.initial ?? {}
  for (const field of formConfig.value.fields) {
    model[field.field] = field.field in initial ? initial[field.field] : defaultFor(field)
  }
}

/** 值初始化完成后,通知声明了 onChange 联动钩子的字段(如机构变化刷新校区选项) */
function notifyFieldHooks() {
  if (!props.onFieldChange) {
    return
  }
  for (const field of visibleFields.value) {
    const hook = props.onFieldChange[field.field]
    if (field.onChange && hook) {
      hook(model, field)
    }
  }
}

/** 由配置 JSON 字符串解析成 FormPageConfig;返回 null 表示未配置 */
function parseFormJson(
  raw: string | null | undefined,
  resourceCode: string
): FormPageConfig | null {
  if (!raw || !raw.trim()) {
    return null
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    throw new Error(`资源「${resourceCode}」的表单配置 form_json 解析失败,请到「列表配置」中修正`)
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`资源「${resourceCode}」的表单配置 form_json 必须是 JSON 对象`)
  }
  const obj = parsed as { fields?: unknown; labelWidth?: unknown; gridColumns?: unknown }
  if (!Array.isArray(obj.fields) || obj.fields.length === 0) {
    throw new Error(`资源「${resourceCode}」的表单配置 form_json 缺少非空的 fields 数组`)
  }
  return {
    resourceCode,
    labelWidth: obj.labelWidth as string | number | undefined,
    gridColumns: obj.gridColumns as number | undefined,
    fields: obj.fields as FormFieldConfig[],
  }
}

async function loadConfig() {
  configError.value = ''
  try {
    const summary = await listConfigStore.getOf(props.resourceCode)
    formConfig.value = parseFormJson(summary.formJson, props.resourceCode)
    // 预热字典
    if (formConfig.value) {
      const dictTypes = formConfig.value.fields
        .filter((field) => field.type === 'dict-select' && field.dictType)
        .map((field) => field.dictType as string)
      if (dictTypes.length) {
        await dictStore.ensureLoaded(dictTypes)
      }
      // 预热表单 optionsLoader 引用的配置化数据源(宿主未注入的编码)
      const loaderCodes = formConfig.value.fields
        .map((field) => field.optionsLoader)
        .filter((code): code is string => Boolean(code))
      if (loaderCodes.length) {
        await dataSourceStore.preloadCodes(loaderCodes)
      }
    }
    initModel()
    notifyFieldHooks()
  } catch (error) {
    formConfig.value = null
    configError.value = error instanceof Error ? error.message : '表单配置加载失败'
  } finally {
    loading.value = false
  }
}

function handleFieldChange(field: FormFieldConfig, value: unknown) {
  emit('change', field.field, value)
  if (props.onFieldChange?.[field.field]) {
    props.onFieldChange[field.field](model, field)
  }
}

/** 供宿主在打开弹窗时重置为默认值(不触发校验) */
function reset() {
  initModel()
  formRef.value?.clearValidate()
  notifyFieldHooks()
}

/** 同步校验;返回是否通过,宿主在 submit 前调用 */
async function validate(): Promise<boolean> {
  if (!formRef.value) {
    return false
  }
  try {
    await formRef.value.validate()
    return true
  } catch {
    return false
  }
}

/** 当前表单值(宿主组装 payload 用) */
function values(): Record<string, any> {
  return { ...model }
}

/** 供宿主在打开弹窗后按需设置某字段值(如默认机构),可触发字段 change 钩子 */
function setFieldValue(field: string, value: unknown, notify = false) {
  model[field] = value
  const fieldConfig = formConfig.value?.fields.find((item) => item.field === field)
  if (notify && fieldConfig) {
    handleFieldChange(fieldConfig, value)
  }
}

watch(
  () => props.resourceCode,
  () => {
    void loadConfig()
  }
)
watch(
  () => props.mode,
  () => {
    // 切换 create/edit 时重建可见字段默认值(如仅 create 字段消失)
    initModel()
    formRef.value?.clearValidate()
  }
)

watch(
  () => props.initial,
  () => {
    // 宿主在打开弹窗后异步回填 initial(如先 set mode 再 set 选中行)时重建表单值
    initModel()
    formRef.value?.clearValidate()
    notifyFieldHooks()
  }
)

defineExpose({
  reset,
  validate,
  values,
  setFieldValue,
  get fields() {
    return visibleFields.value
  },
  get form() {
    return model
  },
})

onMounted(() => {
  void loadConfig()
})
</script>
