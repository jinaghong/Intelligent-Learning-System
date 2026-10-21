<template>
  <div class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">COURSES</p>
        <h2>课程管理</h2>
        <p class="sub">共 {{ courses.length }} 门课程 · 有成绩记录的课程不允许删除</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增课程</el-button>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">
          <h3>课程列表</h3>
          <p>课程数量少，接口不分页</p>
        </div>
        <el-input
          v-model="keyword"
          placeholder="按名称 / 编号筛选"
          clearable
          :prefix-icon="Search"
          style="width: 240px"
        />
      </div>
      <div class="panel-body">
        <el-table v-loading="loading" :data="filteredCourses" row-key="id" stripe>
          <el-table-column label="课程" min-width="260">
            <template #default="{ row }">
              <div class="course-cell">
                <span class="course-badge">{{ row.no }}</span>
                <div class="course-meta">
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.description || '暂无课程简介' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="价格" width="140" align="right">
            <template #default="{ row }">
              <span class="price"><i>¥</i>{{ formatMoney(row.price) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有符合条件的课程" :image-size="80" />
          </template>
        </el-table>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑课程' : '新增课程'"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <div class="form-grid">
          <el-form-item label="课程名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入课程名称" maxlength="30" />
          </el-form-item>
          <el-form-item label="课程编号" prop="no">
            <el-input v-model="form.no" placeholder="如 C009" maxlength="20" />
          </el-form-item>
          <el-form-item label="课程价格" prop="price">
            <el-input-number
              v-model="form.price"
              :min="0"
              :precision="2"
              :step="100"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </div>
        <el-form-item label="课程简介" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="课程内容、面向人群、技术栈等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '@/utils/feedback'
import { Delete, Edit, Plus, Search } from '@element-plus/icons-vue'

import { createCourse, deleteCourse, getCourses, updateCourse } from '@/api/course'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const saving = ref(false)
const courses = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  name: '',
  no: '',
  price: 0,
  description: ''
})
const rules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  no: [{ required: true, message: '请输入课程编号', trigger: 'blur' }],
  price: [{ required: true, message: '请输入课程价格', trigger: 'blur' }]
}

const filteredCourses = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  if (!key) return courses.value
  return courses.value.filter(
    (item) =>
      item.name.toLowerCase().includes(key) ||
      item.no.toLowerCase().includes(key) ||
      (item.description || '').toLowerCase().includes(key)
  )
})

async function fetchCourses() {
  loading.value = true
  try {
    courses.value = await getCourses()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = null
  form.name = ''
  form.no = ''
  form.price = 0
  form.description = ''
  formRef.value?.clearValidate()
}

function openCreate() {
  editing.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  form.id = row.id
  form.name = row.name
  form.no = row.no
  form.price = Number(row.price)
  form.description = row.description || ''
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function submit() {
  if (saving.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  const payload = {
    name: form.name.trim(),
    no: form.no.trim(),
    price: form.price,
    description: form.description.trim() || null
  }
  try {
    if (editing.value) {
      await updateCourse({ id: form.id, ...payload })
      ElMessage.success('课程已更新')
    } else {
      await createCourse(payload)
      ElMessage.success('课程已创建')
    }
    dialogVisible.value = false
    await fetchCourses()
  } catch {
    // 拦截器已提示，例如「课程编号已存在」
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除课程「${row.name}」吗？`, '删除课程', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteCourse(row.id)
    ElMessage.success('课程已删除')
    await fetchCourses()
  } catch {
    // 拦截器已提示，例如「该课程下已有成绩记录，不能删除」
  }
}

onMounted(fetchCourses)
</script>

<style scoped>
.course-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.course-badge {
  flex-shrink: 0;
  padding: 4px 9px;
  border-radius: 8px;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
  color: var(--tl-primary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.4px;
}

.course-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.course-meta strong {
  font-size: 13px;
  color: var(--tl-ink);
}

.course-meta span {
  overflow: hidden;
  font-size: 11px;
  color: var(--tl-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price {
  font-size: 14px;
  font-weight: 600;
  color: var(--tl-ink);
  font-variant-numeric: tabular-nums;
}

.price i {
  margin-right: 2px;
  font-size: 11px;
  font-style: normal;
  color: var(--tl-muted);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
</style>
