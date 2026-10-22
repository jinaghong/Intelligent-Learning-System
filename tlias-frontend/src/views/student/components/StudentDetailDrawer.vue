<template>
  <el-drawer v-model="visible" title="学员详情" size="560px">
    <div v-loading="loading" class="detail-body">
      <template v-if="detail">
        <div class="profile">
          <el-avatar :size="56">{{ detail.name?.slice(0, 1) }}</el-avatar>
          <div class="profile-meta">
            <div class="profile-name">
              <h3>{{ detail.name }}</h3>
              <el-tag size="small" effect="plain">{{ labelOf(genderMap, detail.gender) }}</el-tag>
              <el-tag size="small" type="info" effect="plain">
                {{ labelOf(degreeMap, detail.degree) }}
              </el-tag>
            </div>
            <p>学号 {{ detail.no }} · {{ detail.clazzName || '未分班' }}</p>
          </div>
        </div>

        <el-descriptions :column="2" border class="info-block">
          <el-descriptions-item label="手机号">{{ detail.phone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="毕业时间">
            {{ formatDate(detail.graduationDate) }}
          </el-descriptions-item>
          <el-descriptions-item label="身份证号">{{ detail.idCard || '—' }}</el-descriptions-item>
          <el-descriptions-item label="院校生源">
            {{ labelOf(isCollegeMap, detail.isCollege) }}
          </el-descriptions-item>
          <el-descriptions-item label="违纪次数">{{ detail.violationCount ?? 0 }} 次</el-descriptions-item>
          <el-descriptions-item label="累计扣分">
            <span :class="{ 'score-danger': detail.violationScore > 0 }">
              {{ detail.violationScore ?? 0 }} 分
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="家庭住址" :span="2">
            {{ detail.address || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">
            {{ formatDateTime(detail.createTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="violation-block">
          <div class="violation-head">
            <h4>违纪明细</h4>
            <span>{{ violations.length }} 条记录</span>
          </div>
          <el-table :data="violations" size="small" stripe>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="扣分" width="70" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="danger" effect="plain">-{{ row.point }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="事由" min-width="180" />
            <template #empty>
              <el-empty description="该学员没有违纪记录" :image-size="64" />
            </template>
          </el-table>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

import { getStudentDetail } from '@/api/student'
import { degreeMap, genderMap, isCollegeMap, labelOf } from '@/utils/dict'
import { formatDate, formatDateTime } from '@/utils/format'

const props = defineProps({
  studentId: {
    type: Number,
    default: null
  }
})
const visible = defineModel({ type: Boolean, default: false })

const loading = ref(false)
const detail = ref(null)
const violations = computed(() => detail.value?.violationList ?? [])

watch(visible, async (open) => {
  if (!open) return
  if (!props.studentId) return
  loading.value = true
  detail.value = null
  try {
    detail.value = await getStudentDetail(props.studentId)
  } catch {
    visible.value = false
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.detail-body {
  min-height: 240px;
}

.profile {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--tl-border);
  border-radius: 14px;
  background: linear-gradient(135deg, #f8faff, #eef2ff);
}

.profile :deep(.el-avatar) {
  background: var(--tl-primary);
  font-size: 20px;
  font-weight: 600;
}

.profile-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-name h3 {
  font-size: 18px;
  color: var(--tl-ink);
}

.profile-meta p {
  margin-top: 5px;
  font-size: 12px;
  color: var(--tl-muted);
}

.info-block {
  margin-top: 16px;
}

.score-danger {
  color: #dc2626;
  font-weight: 600;
}

.violation-block {
  margin-top: 22px;
}

.violation-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.violation-head h4 {
  font-size: 14px;
  color: var(--tl-ink);
}

.violation-head span {
  font-size: 12px;
  color: var(--tl-muted);
}
</style>
