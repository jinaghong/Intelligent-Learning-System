<template>
  <div class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">CLASSES</p>
        <h2>班级管理</h2>
        <p class="sub">条件分页查询 · 班主任关联 · 有学员的班级不允许删除</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增班级</el-button>
      </div>
    </div>

    <div class="panel">
      <div class="search-panel">
        <el-form class="search-form" :model="query" inline @submit.prevent="handleSearch">
          <el-form-item label="班级名称">
            <el-input
              v-model="query.name"
              placeholder="支持模糊查询"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="开班日期">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="table-panel">
        <div class="table-toolbar">
          <span class="total-hint">共 <b>{{ total }}</b> 个班级</span>
        </div>

        <el-table v-loading="loading" :data="rows" row-key="id" stripe>
          <el-table-column label="班级" min-width="200">
            <template #default="{ row }">
              <div class="clazz-cell">
                <span class="clazz-badge">{{ row.subject ? subjectMap[row.subject] : '班' }}</span>
                <div class="clazz-meta">
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.room || '教室待定' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="班主任" min-width="120">
            <template #default="{ row }">
              <span v-if="row.masterName">{{ row.masterName }}</span>
              <el-tag v-else size="small" type="info" effect="plain">未分配</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="学科" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ labelOf(subjectMap, row.subject) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开班日期" width="120">
            <template #default="{ row }">{{ formatDate(row.beginDate) }}</template>
          </el-table-column>
          <el-table-column label="结班日期" width="120">
            <template #default="{ row }">{{ formatDate(row.endDate) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
                {{ labelOf(clazzStatusMap, row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有符合条件的班级" :image-size="80" />
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

    <ClazzDialog v-model="dialogVisible" :clazz-id="editingId" @saved="handleSaved" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '@/utils/feedback'
import { Delete, Edit, Plus, RefreshLeft, Search } from '@element-plus/icons-vue'

import { deleteClazz, getClazzs } from '@/api/clazz'
import { clazzStatusMap, labelOf, subjectMap } from '@/utils/dict'
import { formatDate } from '@/utils/format'
import ClazzDialog from './components/ClazzDialog.vue'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const dateRange = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const query = reactive({
  name: '',
  begin: null,
  end: null,
  page: 1,
  pageSize: 10
})

function buildParams() {
  return {
    name: query.name.trim() || undefined,
    begin: query.begin || undefined,
    end: query.end || undefined,
    page: query.page,
    pageSize: query.pageSize
  }
}

async function fetchList() {
  loading.value = true
  try {
    const data = await getClazzs(buildParams())
    rows.value = data.rows ?? []
    total.value = data.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function syncDateRange() {
  query.begin = dateRange.value?.[0] || null
  query.end = dateRange.value?.[1] || null
}

function handleSearch() {
  syncDateRange()
  query.page = 1
  fetchList()
}

function handleReset() {
  query.name = ''
  dateRange.value = []
  query.begin = null
  query.end = null
  query.page = 1
  query.pageSize = 10
  fetchList()
}

function handleSizeChange() {
  query.page = 1
  fetchList()
}

function openCreate() {
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  dialogVisible.value = true
}

function handleSaved(payload) {
  if (payload?.mode === 'create' && payload.name) {
    query.name = payload.name
    dateRange.value = []
    query.begin = null
    query.end = null
    query.page = 1
  }
  fetchList()
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除班级「${row.name}」吗？`, '删除班级', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteClazz(row.id)
    ElMessage.success('班级已删除')
    if (rows.value.length <= 1 && query.page > 1) {
      query.page -= 1
    }
    await fetchList()
  } catch {
    // 拦截器已提示，例如「该班级下还有学员，不能删除」
  }
}

onMounted(fetchList)
</script>

<style scoped>
.clazz-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.clazz-badge {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 42px;
  height: 30px;
  border-radius: 9px;
  background: linear-gradient(135deg, #eef2ff, #e0e7ff);
  color: var(--tl-primary);
  font-size: 11px;
  font-weight: 600;
}

.clazz-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.clazz-meta strong {
  font-size: 13px;
  color: var(--tl-ink);
}

.clazz-meta span {
  font-size: 11px;
  color: var(--tl-muted);
}
</style>
