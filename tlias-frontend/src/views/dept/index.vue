<template>
  <div class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">DEPARTMENTS</p>
        <h2>部门管理</h2>
        <p class="sub">共 {{ depts.length }} 个部门 · 有员工的部门不允许删除</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增部门</el-button>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">
          <h3>部门列表</h3>
          <p>部门数量少，接口不分页</p>
        </div>
        <el-input
          v-model="keyword"
          placeholder="按名称筛选"
          clearable
          :prefix-icon="Search"
          style="width: 220px"
        />
      </div>
      <div class="panel-body">
        <el-table v-loading="loading" :data="filteredDepts" row-key="id" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="部门名称" min-width="200">
            <template #default="{ row }">
              <div class="dept-name">
                <span class="dept-badge">{{ row.name.slice(0, 1) }}</span>
                <strong>{{ row.name }}</strong>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="更新时间" min-width="180">
            <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="还没有部门，点击右上角新增" :image-size="80" />
          </template>
        </el-table>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑部门' : '新增部门'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="部门名称" prop="name">
          <el-input
            v-model="form.name"
            placeholder="请输入部门名称"
            maxlength="20"
            show-word-limit
            @keyup.enter="submit"
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

import { createDept, deleteDept, getDepts, updateDept } from '@/api/dept'

const loading = ref(false)
const saving = ref(false)
const depts = ref([])
const keyword = ref('')
const dialogVisible = ref(false)
const editing = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  name: ''
})
const rules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

const filteredDepts = computed(() => {
  const key = keyword.value.trim()
  if (!key) return depts.value
  return depts.value.filter((item) => item.name.includes(key))
})

async function fetchDepts() {
  loading.value = true
  try {
    depts.value = await getDepts()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  form.id = null
  form.name = ''
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  form.id = row.id
  form.name = row.name
  dialogVisible.value = true
}

async function submit() {
  if (saving.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (editing.value) {
      await updateDept({ id: form.id, name: form.name.trim() })
      ElMessage.success('部门已更新')
    } else {
      await createDept({ name: form.name.trim() })
      ElMessage.success('部门已创建')
    }
    dialogVisible.value = false
    await fetchDepts()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」吗？`, '删除部门', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteDept(row.id)
    ElMessage.success('部门已删除')
    await fetchDepts()
  } catch {
    // 拦截器已提示，例如「该部门下还有员工，不能删除」
  }
}

function formatDateTime(value) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

onMounted(fetchDepts)
</script>

<style scoped>
.dept-name {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dept-badge {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
  color: var(--tl-primary);
  font-size: 13px;
  font-weight: 600;
}
</style>
