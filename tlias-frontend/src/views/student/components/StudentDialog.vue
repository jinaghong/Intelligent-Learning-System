<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑学员' : '新增学员'"
    width="760px"
    top="6vh"
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
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" maxlength="20" />
        </el-form-item>
        <el-form-item label="学号" prop="no">
          <el-input v-model="form.no" placeholder="如 202601009" maxlength="20" />
        </el-form-item>

        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" placeholder="请选择性别" style="width: 100%">
            <el-option
              v-for="item in genderOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学历" prop="degree">
          <el-select v-model="form.degree" placeholder="请选择学历" style="width: 100%">
            <el-option
              v-for="item in degreeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="班级" prop="clazzId">
          <el-select v-model="form.clazzId" placeholder="请选择班级（可不选）" filterable style="width: 100%">
            <el-option
              v-for="clazz in clazzOptions"
              :key="clazz.id"
              :label="clazz.name"
              :value="clazz.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="form.idCard" placeholder="18 位身份证号" maxlength="18" />
        </el-form-item>
        <el-form-item label="毕业时间" prop="graduationDate">
          <el-date-picker
            v-model="form.graduationDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择毕业时间"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="院校合作生源" prop="isCollege">
          <el-radio-group v-model="form.isCollege">
            <el-radio v-for="item in isCollegeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </div>

      <el-form-item label="家庭住址" prop="address">
        <el-input
          v-model="form.address"
          type="textarea"
          :rows="2"
          maxlength="100"
          show-word-limit
          placeholder="请输入家庭住址"
        />
      </el-form-item>
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

import { createStudent, getStudentDetail, updateStudent } from '@/api/student'
import { degreeOptions, genderOptions, isCollegeOptions } from '@/utils/dict'

const props = defineProps({
  studentId: {
    type: Number,
    default: null
  },
  clazzOptions: {
    type: Array,
    default: () => []
  }
})
const emit = defineEmits(['saved'])
const visible = defineModel({ type: Boolean, default: false })

const loadingDetail = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  name: '',
  no: '',
  gender: null,
  phone: '',
  degree: null,
  clazzId: null,
  idCard: '',
  isCollege: 0,
  address: '',
  graduationDate: null
})

const isEdit = computed(() => props.studentId !== null && props.studentId !== undefined)

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  no: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  degree: [{ required: true, message: '请选择学历', trigger: 'change' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  idCard: [{ pattern: /^\d{17}[\dXx]$/, message: '身份证号格式不正确', trigger: 'blur' }]
}

function resetForm() {
  form.id = null
  form.name = ''
  form.no = ''
  form.gender = null
  form.phone = ''
  form.degree = null
  form.clazzId = null
  form.idCard = ''
  form.isCollege = 0
  form.address = ''
  form.graduationDate = null
  formRef.value?.clearValidate()
}

async function loadDetail() {
  loadingDetail.value = true
  try {
    const detail = await getStudentDetail(props.studentId)
    form.id = detail.id
    form.name = detail.name
    form.no = detail.no
    form.gender = detail.gender
    form.phone = detail.phone || ''
    form.degree = detail.degree
    form.clazzId = detail.clazzId ?? null
    form.idCard = detail.idCard || ''
    form.isCollege = detail.isCollege ?? 0
    form.address = detail.address || ''
    form.graduationDate = detail.graduationDate || null
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
  if (isEdit.value) {
    await loadDetail()
  }
})

async function submit() {
  if (saving.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  const payload = {
    name: form.name.trim(),
    no: form.no.trim(),
    gender: form.gender,
    phone: form.phone.trim() || null,
    degree: form.degree,
    clazzId: form.clazzId ?? null,
    idCard: form.idCard.trim() || null,
    isCollege: form.isCollege,
    address: form.address.trim() || null,
    graduationDate: form.graduationDate || null
  }
  try {
    if (isEdit.value) {
      await updateStudent({ id: form.id, ...payload })
      ElMessage.success('学员已更新')
    } else {
      await createStudent(payload)
      ElMessage.success('学员已创建')
    }
    visible.value = false
    emit('saved', { mode: isEdit.value ? 'edit' : 'create', name: payload.name })
  } catch {
    // 拦截器已提示，例如「学号已存在」
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
