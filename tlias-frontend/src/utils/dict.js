export const genderOptions = [
  { label: '男', value: 1 },
  { label: '女', value: 2 }
]

export const jobOptions = [
  { label: '班主任', value: 1 },
  { label: '讲师', value: 2 },
  { label: '学工主管', value: 3 },
  { label: '教研主管', value: 4 },
  { label: '咨询师', value: 5 }
]

export const empStatusOptions = [
  { label: '在职', value: 1 },
  { label: '离职', value: 2 }
]

export const genderMap = Object.fromEntries(genderOptions.map((item) => [item.value, item.label]))
export const jobMap = Object.fromEntries(jobOptions.map((item) => [item.value, item.label]))
export const empStatusMap = Object.fromEntries(empStatusOptions.map((item) => [item.value, item.label]))

export const labelOf = (map, value) => map[value] ?? '—'
