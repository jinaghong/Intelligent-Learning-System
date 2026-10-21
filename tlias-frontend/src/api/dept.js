import request from './request'

export const getDepts = () => request.get('/depts')
export const getDeptDetail = (id) => request.get(`/depts/${id}`)
export const createDept = (data) => request.post('/depts', data)
export const updateDept = (data) => request.put('/depts', data)
export const deleteDept = (id) => request.delete(`/depts/${id}`)
