const config = require('./config')
const SESSION_KEY = 'shiyoushu.api.session.v1:' + config.baseUrl + ':' + config.authMode
let loginTask = null
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
      fail: () => reject(new Error('无法连接后端，请确认 Java 服务已启动，并检查接口地址和网络设置'))
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
      fail: () => reject(new Error('图片上传失败或识别超时，请检查网络，也可对照照片手动输入'))
    })
  }))
}
module.exports = { request, upload }
