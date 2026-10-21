<template>
  <el-drawer v-model="visible" :title="drawerTitle" size="640px">
    <div class="roster-body">
      <div class="roster-summary">
        <div>
          <h3>{{ dept?.name || '部门员工' }}</h3>
          <p>共 {{ total }} 位员工（含离职）</p>
        </div>
        <el-button
          type="primary"
          :icon="Download"
          :loading="exporting"
          :disabled="!total || loading"
          @click="handleExport"
        >
          导出 Excel
        </el-button>
      </div>

      <el-table v-loading="loading" :data="rows" row-key="id" stripe class="roster-table">
        <el-table-column label="员工" min-width="150">
          <template #default="{ row }">
            <div class="emp-cell">
              <el-avatar :size="30" :src="row.image || undefined">
                {{ row.name?.slice(0, 1) }}
              </el-avatar>
              <div class="emp-meta">
                <strong>{{ row.name }}</strong>
                <span>{{ row.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="职位" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ labelOf(jobMap, row.job) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="性别" width="64">
          <template #default="{ row }">{{ labelOf(genderMap, row.gender) }}</template>
        </el-table-column>
        <el-table-column label="入职日期" width="110">
          <template #default="{ row }">{{ formatDate(row.entryDate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light" round>
              {{ labelOf(empStatusMap, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该部门还没有员工" :image-size="72" />
        </template>
      </el-table>

      <div class="roster-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          small
          background
          @current-change="fetchRoster"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from '@/utils/feedback'
import { Download } from '@element-plus/icons-vue'

import { exportEmps, getEmps } from '@/api/emp'
import { empStatusMap, genderMap, jobMap, labelOf } from '@/utils/dict'
import { saveBlob } from '@/utils/download'
import { formatDate } from '@/utils/format'

const props = defineProps({
  dept: {
    type: Object,
    default: null
  }
})
const visible = defineModel({ type: Boolean, default: false })

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const drawerTitle = computed(() =>
  props.dept?.name ? `${props.dept.name} · 员工名单` : '部门员工'
)

async function fetchRoster() {
  if (!props.dept?.id) return
  loading.value = true
  try {
    const data = await getEmps({
      deptId: props.dept.id,
      page: page.value,
      pageSize: pageSize.value
    })
    rows.value = data.rows ?? []
    total.value = data.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function resetAndFetch() {
  page.value = 1
  rows.value = []
  total.value = 0
  fetchRoster()
}

function handleSizeChange() {
  page.value = 1
  fetchRoster()
}

async function handleExport() {
  if (!props.dept?.id || exporting.value) return
  exporting.value = true
  try {
    const response = await exportEmps({ deptId: props.dept.id })
    await saveBlob(response, `${props.dept.name}-员工名单.xlsx`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(error.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

watch(visible, (open) => {
  if (open) resetAndFetch()
})
watch(
  () => props.dept?.id,
  () => {
    if (visible.value) resetAndFetch()
  }
)
</script>

<style scoped>
.roster-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
}

.roster-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--tl-border);
  border-radius: 14px;
  background: linear-gradient(135deg, #f8faff, #eef2ff);
}

.roster-summary h3 {
  font-size: 15px;
  color: var(--tl-ink);
}

.roster-summary p {
  margin-top: 4px;
  font-size: 12px;
  color: var(--tl-muted);
}

.roster-table {
  flex: 1;
}

.roster-pagination {
  display: flex;
  justify-content: flex-end;
}

.emp-cell {
  display: flex;
  align-items: center;
  gap: 9px;
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
  font-size: 10px;
  color: var(--tl-muted);
}

.emp-cell :deep(.el-avatar) {
  flex: none;
  background: #eef2ff;
  color: var(--tl-primary);
  font-size: 12px;
  font-weight: 600;
}
</style>
