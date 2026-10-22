<template>
  <div class="page">
    <div class="page-header">
      <div>
        <p class="eyebrow">EMPLOYEES</p>
        <h2>员工管理</h2>
        <p class="sub">条件分页查询 · 工作经历 · 头像上传 · Excel 导出</p>
      </div>
      <div class="header-actions">
        <el-button :icon="Download" :loading="exporting" @click="handleExport">导出 Excel</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增员工</el-button>
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
          <el-form-item label="性别">
            <el-select v-model="query.gender" placeholder="全部" clearable style="width: 120px">
              <el-option
                v-for="item in genderOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="入职日期">
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
          <div class="toolbar-left">
            <span class="total-hint">共 <b>{{ total }}</b> 位员工</span>
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
          <el-table-column label="员工" min-width="190">
            <template #default="{ row }">
              <div class="emp-cell">
                <el-avatar :size="36" :src="row.image || undefined">
                  {{ row.name?.slice(0, 1) }}
                </el-avatar>
                <div class="emp-meta">
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.username }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="性别" width="80">
            <template #default="{ row }">{{ labelOf(genderMap, row.gender) }}</template>
          </el-table-column>
          <el-table-column label="职位" width="120">
            <template #default="{ row }">
              <el-tag size="small" type="info" effect="plain">{{ labelOf(jobMap, row.job) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="entryDate" label="入职日期" width="120" />
          <el-table-column label="部门" min-width="120">
            <template #default="{ row }">{{ row.deptName || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light" round>
                {{ labelOf(empStatusMap, row.status) }}
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
            <el-empty description="没有符合条件的员工" :image-size="80" />
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

    <EmpDialog v-model="dialogVisible" :emp-id="editingId" @saved="handleSaved" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '@/utils/feedback'
import { Delete, Download, Edit, Plus, RefreshLeft, Search } from '@element-plus/icons-vue'

import { deleteEmps, exportEmps, getEmps } from '@/api/emp'
import { saveBlob } from '@/utils/download'
import { empStatusMap, genderMap, genderOptions, jobMap, labelOf } from '@/utils/dict'
import EmpDialog from './components/EmpDialog.vue'

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const dateRange = ref([])
const selectedIds = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)
const query = reactive({
  name: '',
  gender: null,
  begin: null,
  end: null,
  page: 1,
  pageSize: 10
})

function buildParams() {
  return {
    name: query.name.trim() || undefined,
    gender: query.gender ?? undefined,
    begin: query.begin || undefined,
    end: query.end || undefined,
    page: query.page,
    pageSize: query.pageSize
  }
}

async function fetchList() {
  loading.value = true
  try {
    const data = await getEmps(buildParams())
    rows.value = data.rows ?? []
    total.value = data.total ?? 0
    selectedIds.value = []
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
  query.gender = null
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

function handleSaved(payload) {
  if (payload?.mode === 'create' && payload.name) {
    query.name = payload.name
    query.gender = null
    query.begin = null
    query.end = null
    dateRange.value = []
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
    await ElMessageBox.confirm(`确定删除员工「${row.name}」吗？`, '删除员工', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteEmps([row.id])
    ElMessage.success('员工已删除')
    await refreshAfterDelete(1)
  } catch {
    // 拦截器已提示
  }
}

async function handleBatchDelete() {
  const ids = [...selectedIds.value]
  if (!ids.length) return

  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 位员工吗？`, '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  try {
    await deleteEmps(ids)
    ElMessage.success('已删除选中员工')
    await refreshAfterDelete(ids.length)
  } catch {
    // 拦截器已提示
  }
}

async function handleExport() {
  if (exporting.value) return
  exporting.value = true
  try {
    const { page, pageSize, ...params } = buildParams()
    const response = await exportEmps(params)
    await saveBlob(response, '员工表.xlsx')
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(error.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.emp-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.emp-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.emp-meta strong {
  font-size: 13px;
  color: var(--tl-ink);
}

.emp-meta span {
  font-size: 11px;
  color: var(--tl-muted);
}

.emp-cell :deep(.el-avatar) {
  background: #eef2ff;
  color: var(--tl-primary);
  font-weight: 600;
}

@media (max-width: 900px) {
  .search-form {
    gap: 8px;
  }
}
</style>
