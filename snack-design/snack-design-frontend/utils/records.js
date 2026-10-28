const api = require('./api')
// 记录只走服务端，不静默回退本机，避免同一用户看到两套记录。
module.exports = {
  ensureResearchApi: async () => {
    const health = await api.request('/health')
    if (!health || health.apiVersion < 3 || !health.researchAvailable) throw new Error('当前连接的是旧版后端，缺少自动解析接口。请停止8080上的旧Java服务，再从 snack-design-backend 启动最新版本')
    return health
  },
  all: () => api.request('/records'),
  find: id => api.request('/records/' + encodeURIComponent(id)),
  save: (raw, kind, id) => api.request('/records' + (id ? '/' + encodeURIComponent(id) : ''), id ? 'PUT' : 'POST', { raw, kind }),
  remove: id => api.request('/records/' + encodeURIComponent(id), 'DELETE'),
  clear: () => api.request('/records', 'DELETE'),
  research: (id, foodCategory, refresh = false) => api.request('/records/' + encodeURIComponent(id) + '/research', 'POST', { foodCategory, refresh }),
  latestResearch: id => api.request('/records/' + encodeURIComponent(id) + '/research/latest'),
  researchStatus: id => api.request('/research/' + encodeURIComponent(id)),
  researchHistory: id => api.request('/records/' + encodeURIComponent(id) + '/research/history')
}
