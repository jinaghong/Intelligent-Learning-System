import request from './request'

export const getEmps = (params) => request.get('/emps', { params })
export const getEmpDetail = (id) => request.get(`/emps/${id}`)
export const createEmp = (data) => request.post('/emps', data)
export const updateEmp = (data) => request.put('/emps', data)
export const deleteEmps = (ids) => request.delete('/emps', { params: { ids: ids.join(',') } })
export const exportEmps = (params) => request.get('/emps/export', { params, responseType: 'blob' })
