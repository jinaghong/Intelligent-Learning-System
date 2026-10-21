<template>
  <div class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">STUDENTS</p>
        <h2>学员管理</h2>
        <p class="sub">条件分页查询 · 违纪累计 · 详情明细 · 批量删除</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增学员</el-button>
      </div>
    </div>

    <div class="panel">
      <div class="search-panel">
        <el-form class="search-form" :model="query" inline @submit.prevent="handleSearch">
          <el-form-item label="姓名">
            <el-input
              v-model="query.name"
              placeholder="支持模糊查询"
              clearable
              style="width: 180px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="学历">
            <el-select v-model="query.degree" placeholder="全部" clearable style="width: 130px">
              <el-option
                v-for="item in degreeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select
              v-model="query.clazzId"
              placeholder="全部"
              clearable
              filterable
              style="width: 200px"
            >
              <el-option
                v-for="clazz in clazzOptions"
                :key="clazz.id"
                :label="clazz.name"
                :value="clazz.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="table-panel">
        <div class="table-toolbar">
          <div class="toolbar-left">
            <span class="total-hint">共 <b>{{ total }}</b> 位学员</span>
            <el-tag v-if="selectedIds.length" type="info" effect="plain">
              已选 {{ selectedIds.length }} 项
            </el-tag>
          </div>
          <div class="toolbar-right">
            <el-button
              type="danger"
              plain
              :icon="Delete"
              :disabled="!selectedIds.length"
              @click="handleBatchDelete"
            >
              批量删除
            </el-button>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="rows"
          row-key="id"
          stripe
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column label="学员" min-width="180">
            <template #default="{ row }">
              <div class="student-cell">
                <el-avatar :size="34">{{ row.name?.slice(0, 1) }}</el-avatar>
                <div class="student-meta">
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.no }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="性别" width="70">
            <template #default="{ row }">{{ labelOf(genderMap, row.gender) }}</template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" width="130">
            <template #default="{ row }">{{ row.phone || '—' }}</template>
          </el-table-column>
          <el-table-column label="学历" width="90">
            <template #default="{ row }">
              <el-tag size="small" type="info" effect="plain">
                {{ labelOf(degreeMap, row.degree) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="班级" min-width="140">
            <template #default="{ row }">{{ row.clazzName || '未分班' }}</template>
          </el-table-column>
          <el-table-column label="违纪" width="130">
            <template #default="{ row }">
              <el-tag v-if="row.violationCount" size="small" type="danger" effect="plain">
                {{ row.violationCount }} 次 / {{ row.violationScore }} 分
              </el-tag>
              <span v-else class="no-violation">无</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="286" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" :icon="Warning" @click="openViolation(row)">违纪</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有符合条件的学员" :image-size="80" />
          </template>
        </el-table>

        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="query.page"
            v-model:page-size="query.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="total"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="fetchList"
            @size-change="handleSizeChange"
          />
        </div>
      </div>
    </div>

    <StudentDialog
      v-model="dialogVisible"
      :student-id="editingId"
      :clazz-options="clazzOptions"
      @saved="handleSaved"
    />
    <StudentDetailDrawer v-model="detailVisible" :student-id="detailId" />
    <ViolationDialog v-model="violationVisible" :student="violationStudent" @saved="fetchList" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '@/utils/feedback'
import {
  Delete,
  Edit,
  Plus,
  RefreshLeft,
  Search,
  View,
  Warning
} from '@element-plus/icons-vue'

import { getClazzList } from '@/api/clazz'
import { deleteStudents, getStudents } from '@/api/student'
import { degreeMap, degreeOptions, genderMap, labelOf } from '@/utils/dict'
import StudentDialog from './components/StudentDialog.vue'
import StudentDetailDrawer from './components/StudentDetailDrawer.vue'
import ViolationDialog from './components/ViolationDialog.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const clazzOptions = ref([])
const selectedIds = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const detailVisible = ref(false)
const detailId = ref(null)
const violationVisible = ref(false)
const violationStudent = ref(null)
const query = reactive({
  name: '',
  degree: null,
  clazzId: null,
  page: 1,
  pageSize: 10
})

function buildParams() {
  return {
    name: query.name.trim() || undefined,
    degree: query.degree ?? undefined,
    clazzId: query.clazzId ?? undefined,
    page: query.page,
    pageSize: query.pageSize
  }
}

async function fetchList() {
  loading.value = true
  try {
    const data = await getStudents(buildParams())
    rows.value = data.rows ?? []
    total.value = data.total ?? 0
    selectedIds.value = []
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function fetchClazzOptions() {
  try {
    clazzOptions.value = await getClazzList()
  } catch {
    // 拦截器已提示
  }
}

function handleSearch() {
  query.page = 1
  fetchList()
}

function handleReset() {
  query.name = ''
  query.degree = null
  query.clazzId = null
  query.page = 1
  query.pageSize = 10
  fetchList()
}

function handleSizeChange() {
  query.page = 1
  fetchList()
}

function onSelectionChange(selection) {
  selectedIds.value = selection.map((item) => item.id)
}

function openCreate() {
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  dialogVisible.value = true
}

function openDetail(row) {
  detailId.value = row.id
  detailVisible.value = true
}

function openViolation(row) {
  violationStudent.value = row
  violationVisible.value = true
}

function handleSaved(payload) {
  if (payload?.mode === 'create' && payload.name) {
    query.name = payload.name
    query.degree = null
    query.clazzId = null
    query.page = 1
  }
  fetchList()
}

async function refreshAfterDelete(deletedCount) {
  if (rows.value.length <= deletedCount && query.page > 1) {
    query.page -= 1
  }
  await fetchList()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除学员「${row.name}」吗？`, '删除学员', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteStudents([row.id])
    ElMessage.success('学员已删除')
    await refreshAfterDelete(1)
  } catch {
    // 拦截器已提示
  }
}

async function handleBatchDelete() {
  const ids = [...selectedIds.value]
  if (!ids.length) return

  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 位学员吗？`, '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteStudents(ids)
    ElMessage.success('已删除选中学员')
    await refreshAfterDelete(ids.length)
  } catch {
    // 拦截器已提示
  }
}

onMounted(async () => {
  await Promise.all([fetchList(), fetchClazzOptions()])
})
</script>

<style scoped>
.student-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.student-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.student-meta strong {
  font-size: 13px;
  color: var(--tl-ink);
}

.student-meta span {
  font-size: 11px;
  color: var(--tl-muted);
}

.student-cell :deep(.el-avatar) {
  background: #eef2ff;
  color: var(--tl-primary);
  font-weight: 600;
}

.no-violation {
  font-size: 12px;
  color: var(--tl-muted);
}

:deep(.el-table .el-button + .el-button) {
  margin-left: 8px;
}
</style>
