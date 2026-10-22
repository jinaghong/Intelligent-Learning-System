import request from './request'

export const getClazzs = (params) => request.get('/clazzs', { params })
export const getClazzList = () => request.get('/clazzs/list')
export const getClazzDetail = (id) => request.get(`/clazzs/${id}`)
export const createClazz = (data) => request.post('/clazzs', data)
export const updateClazz = (data) => request.put('/clazzs', data)
export const deleteClazz = (id) => request.delete(`/clazzs/${id}`)
