const api = require('./api')
// 记录只走服务端，不静默回退本机，避免同一用户看到两套记录。
module.exports = {
  all: () => api.request('/records'),
  find: id => api.request('/records/' + encodeURIComponent(id)),
  save: (raw, kind, id) => api.request('/records' + (id ? '/' + encodeURIComponent(id) : ''), id ? 'PUT' : 'POST', { raw, kind }),
  remove: id => api.request('/records/' + encodeURIComponent(id), 'DELETE'),
  clear: () => api.request('/records', 'DELETE'),
  analyze: raw => api.request('/ingredients/analyze', 'POST', { raw })
}
