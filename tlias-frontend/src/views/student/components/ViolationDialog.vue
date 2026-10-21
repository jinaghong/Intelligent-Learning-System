<template>
  <el-dialog
    v-model="visible"
    title="违纪处理"
    width="480px"
    :close-on-click-modal="false"
  >
    <div v-if="student" class="violation-summary">
      <span class="student-name">{{ student.name }}</span>
      <span>当前累计 {{ student.violationCount ?? 0 }} 次 / {{ student.violationScore ?? 0 }} 分</span>
    </div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="扣分" prop="point">
        <el-input-number
          v-model="form.point"
          :min="1"
          :max="100"
          :step="1"
          controls-position="right"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="违纪事由" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="100"
          show-word-limit
          placeholder="如：晚自习无故缺席"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="danger" :loading="saving" @click="submit">记违纪</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import { addViolation } from '@/api/student'

const props = defineProps({
  student: {
    type: Object,
    default: null
  }
})
const emit = defineEmits(['saved'])
const visible = defineModel({ type: Boolean, default: false })

const saving = ref(false)
const formRef = ref()
const form = reactive({
  point: 1,
  description: ''
})
const rules = {
  point: [{ required: true, message: '请输入扣分', trigger: 'blur' }],
  description: [{ required: true, message: '请输入违纪事由', trigger: 'blur' }]
}

watch(visible, (open) => {
  if (!open) return
  form.point = 1
  form.description = ''
  formRef.value?.clearValidate()
})

async function submit() {
  if (saving.value || !props.student) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await addViolation({
      studentId: props.student.id,
      point: form.point,
      description: form.description.trim()
    })
    ElMessage.success('违纪已记录，累计扣分已更新')
    visible.value = false
    emit('saved')
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.violation-summary {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
}

.student-name {
  font-size: 14px;
  font-weight: 600;
}
</style>
