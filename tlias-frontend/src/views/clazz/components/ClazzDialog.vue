<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑班级' : '新增班级'"
    width="720px"
    top="7vh"
    :close-on-click-modal="false"
  >
    <el-form
      ref="formRef"
      v-loading="loadingDetail"
      :model="form"
      :rules="rules"
      label-position="top"
    >
      <div class="form-grid">
        <el-form-item label="班级名称" prop="name">
          <el-input v-model="form.name" placeholder="如 JavaEE 就业 3 期" maxlength="30" />
        </el-form-item>
        <el-form-item label="教室" prop="room">
          <el-input v-model="form.room" placeholder="如 A103" maxlength="20" />
        </el-form-item>

        <el-form-item label="开班日期" prop="beginDate">
          <el-date-picker
            v-model="form.beginDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择开班日期"
            style="width: 100%"
            @change="revalidateEndDate"
          />
        </el-form-item>
        <el-form-item label="结班日期" prop="endDate">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择结班日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="班主任" prop="masterId">
          <el-select
            v-model="form.masterId"
            placeholder="请选择班主任（可不选）"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="emp in empOptions"
              :key="emp.id"
              :label="`${emp.name} · ${labelOf(jobMap, emp.job)}`"
              :value="emp.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学科" prop="subject">
          <el-select v-model="form.subject" placeholder="请选择学科" style="width: 100%">
            <el-option
              v-for="item in subjectOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option
              v-for="item in clazzStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { createClazz, getClazzDetail, updateClazz } from '@/api/clazz'
import { getEmps } from '@/api/emp'
import { clazzStatusOptions, jobMap, labelOf, subjectOptions } from '@/utils/dict'

const props = defineProps({
  clazzId: {
    type: Number,
    default: null
  }
})
const emit = defineEmits(['saved'])
const visible = defineModel({ type: Boolean, default: false })

const loadingDetail = ref(false)
const saving = ref(false)
const formRef = ref()
const empOptions = ref([])
const form = reactive({
  id: null,
  name: '',
  room: '',
  beginDate: null,
  endDate: null,
  masterId: null,
  subject: null,
  status: 1
})

const isEdit = computed(() => props.clazzId !== null && props.clazzId !== undefined)

function validateEndDate(rule, value, callback) {
  if (!value) {
    callback(new Error('请选择结班日期'))
    return
  }
  if (form.beginDate && value < form.beginDate) {
    callback(new Error('结班日期不能早于开班日期'))
    return
  }
  callback()
}

const rules = {
  name: [{ required: true, message: '请输入班级名称', trigger: 'blur' }],
  beginDate: [{ required: true, message: '请选择开班日期', trigger: 'change' }],
  endDate: [{ required: true, validator: validateEndDate, trigger: 'change' }],
  subject: [{ required: true, message: '请选择学科', trigger: 'change' }]
}

function resetForm() {
  form.id = null
  form.name = ''
  form.room = ''
  form.beginDate = null
  form.endDate = null
  form.masterId = null
  form.subject = null
  form.status = 1
  formRef.value?.clearValidate()
}

async function loadEmps() {
  if (empOptions.value.length) return
  try {
    const data = await getEmps({ page: 1, pageSize: 200 })
    empOptions.value = data.rows ?? []
  } catch {
    // 拦截器已提示
  }
}

async function loadDetail() {
  loadingDetail.value = true
  try {
    const detail = await getClazzDetail(props.clazzId)
    form.id = detail.id
    form.name = detail.name
    form.room = detail.room || ''
    form.beginDate = detail.beginDate
    form.endDate = detail.endDate
    form.masterId = detail.masterId ?? null
    form.subject = detail.subject
    form.status = detail.status
    formRef.value?.clearValidate()
  } catch {
    visible.value = false
  } finally {
    loadingDetail.value = false
  }
}

watch(visible, async (open) => {
  if (!open) return
  resetForm()
  await loadEmps()
  if (isEdit.value) {
    await loadDetail()
  }
})

function revalidateEndDate() {
  if (form.endDate) {
    formRef.value?.validateField('endDate').catch(() => {})
  }
}

async function submit() {
  if (saving.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  const payload = {
    name: form.name.trim(),
    room: form.room.trim() || null,
    beginDate: form.beginDate,
    endDate: form.endDate,
    masterId: form.masterId ?? null,
    subject: form.subject,
    status: form.status
  }
  try {
    if (isEdit.value) {
      await updateClazz({ id: form.id, ...payload })
      ElMessage.success('班级已更新')
    } else {
      await createClazz(payload)
      ElMessage.success('班级已创建')
    }
    visible.value = false
    emit('saved', { mode: isEdit.value ? 'edit' : 'create', name: payload.name })
  } catch {
    // 拦截器已提示，例如「班级名称已存在」
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
</style>
