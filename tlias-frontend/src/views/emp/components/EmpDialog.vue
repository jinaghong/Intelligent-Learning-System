<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="780px"
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
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" maxlength="30" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则不修改密码' : '请输入初始密码'"
            maxlength="30"
          />
        </el-form-item>

        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" maxlength="20" />
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

        <el-form-item label="职位" prop="job">
          <el-select v-model="form.job" placeholder="请选择职位" style="width: 100%">
            <el-option
              v-for="item in jobOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="入职日期" prop="entryDate">
          <el-date-picker
            v-model="form.entryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择入职日期"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="部门">
          <el-select v-model="form.deptId" placeholder="请选择部门（可不选）" style="width: 100%">
            <el-option
              v-for="dept in deptOptions"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
            <el-option
              v-for="item in empStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="头像" class="span-2">
          <div class="avatar-row">
            <el-avatar :size="64" :src="form.image || undefined" class="avatar-preview">
              {{ form.name?.slice(0, 1) || '员' }}
            </el-avatar>
            <div class="avatar-actions">
              <el-upload
                action="#"
                :show-file-list="false"
                :http-request="handleUpload"
                accept=".jpg,.jpeg,.png,.gif,.webp"
              >
                <el-button :loading="uploading" :icon="Upload">上传头像</el-button>
              </el-upload>
              <span class="field-hint">jpg / png / gif / webp，最大 2MB</span>
            </div>
          </div>
        </el-form-item>
      </div>

      <div class="experience-section">
        <div class="section-head">
          <div>
            <h4>工作经历</h4>
            <p>可添加多条；编辑时全部删除会清空原经历</p>
          </div>
          <el-button link type="primary" :icon="Plus" @click="addExperience">添加经历</el-button>
        </div>

        <div v-if="!form.exprList.length" class="experience-empty">暂无工作经历</div>
        <div v-for="(item, index) in form.exprList" :key="index" class="experience-row">
          <el-date-picker
            v-model="item.beginDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="开始日期"
            style="width: 140px"
          />
          <el-date-picker
            v-model="item.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="结束日期"
            style="width: 140px"
          />
          <el-input v-model="item.company" placeholder="公司名称" />
          <el-input v-model="item.job" placeholder="职位" style="width: 130px" />
          <el-button
            link
            type="danger"
            :icon="Delete"
            title="删除这条经历"
            @click="removeExperience(index)"
          />
        </div>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from '@/utils/feedback'
import { Delete, Plus, Upload } from '@element-plus/icons-vue'

import { getDepts } from '@/api/dept'
import { createEmp, getEmpDetail, updateEmp } from '@/api/emp'
import { uploadFile } from '@/api/upload'
import { empStatusOptions, genderOptions, jobOptions } from '@/utils/dict'

const props = defineProps({
  empId: { type: [Number, String], default: null }
})
const emit = defineEmits(['saved'])
const visible = defineModel({ type: Boolean, default: false })

const formRef = ref()
const saving = ref(false)
const uploading = ref(false)
const loadingDetail = ref(false)
const deptOptions = ref([])
const form = reactive({
  id: null,
  username: '',
  password: '',
  name: '',
  gender: 1,
  image: '',
  job: 1,
  entryDate: '',
  deptId: null,
  status: 1,
  exprList: []
})

const isEdit = computed(() => props.empId !== null && props.empId !== undefined)
const dialogTitle = computed(() => (isEdit.value ? '编辑员工' : '新增员工'))
const rules = computed(() => ({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: isEdit.value ? [] : [{ required: true, message: '请输入密码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  job: [{ required: true, message: '请选择职位', trigger: 'change' }],
  entryDate: [{ required: true, message: '请选择入职日期', trigger: 'change' }]
}))

async function resetForm() {
  Object.assign(form, {
    id: null,
    username: '',
    password: '',
    name: '',
    gender: 1,
    image: '',
    job: 1,
    entryDate: '',
    deptId: null,
    status: 1,
    exprList: []
  })
  await nextTick()
  formRef.value?.clearValidate()
}

async function loadDepts() {
  try {
    deptOptions.value = await getDepts()
  } catch {
    deptOptions.value = []
  }
}

async function loadDetail() {
  loadingDetail.value = true
  try {
    const detail = await getEmpDetail(props.empId)
    Object.assign(form, {
      id: detail.id,
      username: detail.username || '',
      password: '',
      name: detail.name || '',
      gender: detail.gender ?? 1,
      image: detail.image || '',
      job: detail.job ?? 1,
      entryDate: detail.entryDate || '',
      deptId: detail.deptId ?? null,
      status: detail.status ?? 1,
      exprList: (detail.exprList || []).map((item) => ({
        beginDate: item.beginDate,
        endDate: item.endDate,
        company: item.company,
        job: item.job
      }))
    })
  } catch {
    visible.value = false
  } finally {
    loadingDetail.value = false
  }
}

watch(visible, async (open) => {
  if (!open) return
  await resetForm()
  await loadDepts()
  if (isEdit.value) {
    await loadDetail()
  }
})

function addExperience() {
  form.exprList.push({
    beginDate: '',
    endDate: '',
    company: '',
    job: ''
  })
}

function removeExperience(index) {
  form.exprList.splice(index, 1)
}

function validateExperiences() {
  for (let index = 0; index < form.exprList.length; index += 1) {
    const item = form.exprList[index]
    if (!item.beginDate || !item.endDate || !item.company?.trim() || !item.job?.trim()) {
      ElMessage.warning(`第 ${index + 1} 条工作经历不完整`)
      return false
    }
    if (item.endDate < item.beginDate) {
      ElMessage.warning(`第 ${index + 1} 条工作经历的结束日期不能早于开始日期`)
      return false
    }
  }
  return true
}

async function handleUpload(options) {
  const file = options.file
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 2MB')
    return
  }

  uploading.value = true
  try {
    form.image = await uploadFile(file)
    ElMessage.success('头像上传成功')
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

async function submit() {
  if (saving.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!validateExperiences()) return

  saving.value = true
  try {
    const payload = {
      username: form.username.trim(),
      name: form.name.trim(),
      gender: form.gender,
      image: form.image || null,
      job: form.job,
      entryDate: form.entryDate,
      deptId: form.deptId ?? null,
      status: form.status,
      exprList: form.exprList.map((item) => ({
        beginDate: item.beginDate,
        endDate: item.endDate,
        company: item.company.trim(),
        job: item.job.trim()
      }))
    }
    if (form.password) {
      payload.password = form.password
    }
    if (isEdit.value) {
      payload.id = props.empId
      await updateEmp(payload)
    } else {
      await createEmp(payload)
    }
    ElMessage.success(isEdit.value ? '员工已更新' : '员工已创建')
    visible.value = false
    emit('saved', { mode: isEdit.value ? 'update' : 'create', name: payload.name })
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 18px;
}

.span-2 {
  grid-column: span 2;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-preview {
  background: #eef2ff;
  color: var(--tl-primary);
  font-size: 20px;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.field-hint {
  font-size: 11px;
  color: #94a3b8;
}

.experience-section {
  margin-top: 6px;
  padding-top: 16px;
  border-top: 1px dashed var(--tl-border);
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-head h4 {
  font-size: 14px;
  color: var(--tl-ink);
}

.section-head p {
  margin-top: 4px;
  font-size: 11px;
  color: var(--tl-muted);
}

.experience-empty {
  padding: 14px;
  border-radius: 10px;
  background: #f8fafc;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}

.experience-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.experience-row .el-input {
  flex: 1;
  min-width: 140px;
}

:deep(.el-dialog) {
  max-width: calc(100vw - 32px);
}

@media (max-width: 700px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .span-2 {
    grid-column: span 1;
  }
}
</style>
