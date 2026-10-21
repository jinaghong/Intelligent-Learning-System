import request from './request'

export const getStudents = (params) => request.get('/students', { params })
export const exportStudents = (params) =>
  request.get('/students/export', { params, responseType: 'blob' })
export const getStudentDetail = (id) => request.get(`/students/${id}`)
export const createStudent = (data) => request.post('/students', data)
export const updateStudent = (data) => request.put('/students', data)
export const deleteStudents = (ids) => request.delete('/students', { params: { ids: ids.join(',') } })
export const addViolation = (data) => request.post('/students/violation', data)
