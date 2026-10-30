const config = require('./config')
const SESSION_KEY = 'shiyoushu.api.session.v1:' + config.baseUrl + ':' + config.authMode
let loginTask = null
function networkError(cause, stage) {
  const nativeMessage = cause && typeof cause.errMsg === 'string' ? cause.errMsg.slice(0, 512) : ''
  let reason = 'unknown'
  if (/not in domain list|domain.*not allowed|域名.*不合法/i.test(nativeMessage)) reason = 'domain_not_allowed'
  else if (/cleartext|app transport security|insecure http/i.test(nativeMessage)) reason = 'insecure_http_blocked'
  else if (/local network|本地网络|permission|privacy|权限/i.test(nativeMessage)) reason = 'permission_denied'
  else if (/timeout|timed out|超时/i.test(nativeMessage)) reason = 'timeout'
  else if (/ECONNREFUSED|connection refused|连接被拒绝/i.test(nativeMessage)) reason = 'connection_refused'
  const error = new Error('网络错误，请稍后重试')
  error.code = 'NETWORK_ERROR'
  error.networkReason = reason
  error.nativeMessage = nativeMessage // 仅供本地断点查看，不展示、存储或上传原生错误内容。
  console.warn('[network-error]', { stage, reason })
  return error
}
function send(path, method, data, token) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: config.baseUrl + path, method, data, timeout: 15000,
      header: { 'content-type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
      success: res => {
        if (res.statusCode >= 200 && res.statusCode < 300) resolve(res.data)
        else {
          const error = new Error(res.data && res.data.message || '服务请求失败，请稍后重试')
          error.status = res.statusCode; reject(error)
        }
      },
      fail: cause => reject(networkError(cause, path.startsWith('/auth/') ? 'session' : 'request'))
    })
  })
}
async function login() {
  if (loginTask) return loginTask
  loginTask = (async () => {
    let session
    if (config.authMode === 'dev') {
      let deviceId = wx.getStorageSync('shiyoushu.dev.device.v1')
      if (!deviceId) {
        deviceId = 'dev_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2)
        wx.setStorageSync('shiyoushu.dev.device.v1', deviceId)
      }
      session = await send('/auth/dev', 'POST', { deviceId })
    } else {
      const code = await new Promise((resolve, reject) => wx.login({
        success: r => r.code ? resolve(r.code) : reject(new Error('无法获取微信登录凭证')),
        fail: () => reject(new Error('微信登录失败，请重试'))
      }))
      session = await send('/auth/wechat', 'POST', { code })
    }
    wx.setStorageSync(SESSION_KEY, session)
    return session.token
  })()
  try { return await loginTask } finally { loginTask = null }
}
async function withSession(work) {
  const session = wx.getStorageSync(SESSION_KEY)
  const token = session && session.expiresAt > Date.now() + 10000 ? session.token : await login()
  try { return await work(token) }
  catch (e) {
    if (e.status !== 401) throw e
    wx.removeStorageSync(SESSION_KEY)
    return work(await login())
  }
}
function request(path, method = 'GET', data) {
  return withSession(token => send(path, method, data, token))
}
function upload(path, filePath) {
  return withSession(token => new Promise((resolve, reject) => {
    wx.uploadFile({
      url: config.baseUrl + path, filePath, name: 'image', timeout: 60000,
      header: { Authorization: 'Bearer ' + token },
      success: res => {
        let data
        try { data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data } catch (e) { data = null }
        if (res.statusCode >= 200 && res.statusCode < 300 && data) resolve(data)
        else {
          const error = new Error(data && data.message || '图片识别上传失败，请稍后重试或手动录入')
          error.status = res.statusCode; reject(error)
        }
      },
      fail: cause => reject(networkError(cause, 'upload'))
    })
  }))
}
module.exports = { request, upload }
