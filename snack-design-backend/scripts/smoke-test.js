// 运行前 mvn package。启动真实 HTTP 服务并验证文件数据库重启后持久化。
const { spawn } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const assert = require('node:assert/strict')
const net = require('node:net')
const root = path.resolve(__dirname, '..')
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'snack-smoke-'))
let processHandle, output = '', base
const wait = ms => new Promise(r => setTimeout(r, ms))
async function freePort() {
  const server = net.createServer()
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  const port = server.address().port
  await new Promise(resolve => server.close(resolve))
  return port
}
async function start(port) {
  output = ''
  const application = process.env.SMOKE_CLASSPATH
    ? ['-cp', process.env.SMOKE_CLASSPATH, 'com.shiyoushu.snack.SnackApplication']
    : ['-jar', path.join(root, 'target/snack-design-backend-0.1.0.jar')]
  processHandle = spawn('java', [...application,
    '--spring.profiles.active=local', '--app.ai.provider=deepseek', '--app.ai.deepseek-key=', '--app.ai.zero-cost-only=false', '--server.port=' + port,
    '--spring.datasource.url=jdbc:h2:file:' + path.join(temp, 'db').replace(/\\/g, '/') + ';DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0'], { cwd: root, windowsHide: true })
  processHandle.stdout.on('data', b => { output += b })
  processHandle.stderr.on('data', b => { output += b })
  base = 'http://127.0.0.1:' + port + '/api'
  for (let i = 0; i < 100; i++) {
    if (processHandle.exitCode !== null) throw new Error(output)
    try { if ((await fetch(base + '/health')).ok) return } catch (e) { /* 等待启动 */ }
    await wait(200)
  }
  throw new Error('服务启动超时\n' + output)
}
async function stop() {
  if (!processHandle || processHandle.exitCode !== null) return
  const exited = new Promise(resolve => processHandle.once('exit', resolve))
  processHandle.kill()
  await exited
}
async function request(route, method = 'GET', data, token, expected = 200) {
  const res = await fetch(base + route, { method, headers: { 'content-type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: data ? JSON.stringify(data) : undefined })
  const body = await res.json()
  assert.equal(res.status, expected, JSON.stringify(body))
  return body
}
;(async () => {
  const port = await freePort()
  await start(port)
  const health = await request('/health')
  assert.match(health.aiModel, /^DeepSeek\//)
  assert.equal(health.aiConfigured, false)
  const alice = await request('/auth/dev', 'POST', { deviceId: 'smoke-alice-device-123' })
  assert.equal((await request('/research/readiness', 'GET', undefined, alice.token)).ready, false)
  const bob = await request('/auth/dev', 'POST', { deviceId: 'smoke-bob-device-456' })
  await request('/records', 'GET', undefined, undefined, 401)
  const record = await request('/records', 'POST', { raw: '乳粉、小麦粉', kind: '虚构示例' }, alice.token)
  await request('/records/' + record.id, 'GET', undefined, bob.token, 404)
  const updated = await request('/records/' + record.id, 'PUT', { raw: '乳粉、未知配料', kind: '手动输入' }, alice.token)
  assert.equal(updated.kind, '虚构示例')
  await request('/ingredients/analyze', 'POST', { raw: updated.raw }, alice.token, 410)
  assert.equal((await request('/records/' + record.id + '/research/latest', 'GET', undefined, alice.token)).exists, false)
  await request('/records/' + record.id + '/research', 'POST', { foodCategory: '', refresh: true }, alice.token, 503)
  await stop()
  await start(port)
  const persisted = await request('/records/' + record.id, 'GET', undefined, alice.token)
  assert.equal(persisted.raw, '乳粉、未知配料')
  await request('/records', 'DELETE', undefined, alice.token)
  assert.deepEqual(await request('/records', 'GET', undefined, alice.token), [])
  // 直接加载小程序实际使用的 API 适配层，wx.request 用真实 HTTP 替身驱动。
  const frontend = path.resolve(root, '../snack-design-frontend')
  if (fs.existsSync(path.join(frontend, 'utils/records.js'))) {
    const config = require(path.join(frontend, 'utils/config.js'))
    config.baseUrl = base; config.authMode = 'dev'
    const storage = new Map()
    global.wx = {
      getStorageSync: key => storage.get(key), setStorageSync: (key, value) => storage.set(key, value), removeStorageSync: key => storage.delete(key),
      request: options => fetch(options.url, { method: options.method, headers: options.header,
        body: options.data ? JSON.stringify(options.data) : undefined })
        .then(async response => options.success({ statusCode: response.status, data: await response.json() })).catch(options.fail),
      uploadFile: options => {
        const form = new FormData()
        form.append(options.name, new Blob([fs.readFileSync(options.filePath)], { type: 'image/png' }), 'image.png')
        fetch(options.url, { method: 'POST', headers: options.header, body: form })
          .then(async response => options.success({ statusCode: response.status, data: await response.text() })).catch(options.fail)
      }
    }
    const records = require(path.join(frontend, 'utils/records.js'))
    const created = await records.save('乳粉、小麦粉', '手动输入')
    assert.equal((await records.find(created.id)).raw, '乳粉、小麦粉')
    assert.equal((await records.latestResearch(created.id)).exists, false)
    await records.save('乳粉', '手动输入', created.id)
    assert.equal((await records.all())[0].raw, '乳粉')
    await records.remove(created.id)
    assert.deepEqual(await records.all(), [])
    const ocr = require(path.join(frontend, 'utils/ocr.js'))
    if (process.env.SMOKE_OCR_IMAGE) {
      const result = await ocr.recognize(process.env.SMOKE_OCR_IMAGE)
      assert.match(result.text.replace(/\s+/g, ''), /燕麦片/)
      console.log('真实传统OCR图片上传与中英文识别通过，不需要DeepSeek Key，不调用AI')
    } else {
      const invalidImage = path.join(temp, 'invalid-image.png')
      fs.writeFileSync(invalidImage, 'not an image')
      await assert.rejects(ocr.recognize(invalidImage), /图片内容无效/)
      console.log('OCR实际上传、鉴权及图片内容校验通过，不调用AI')
    }
    console.log('前端实际 utils/api.js + utils/records.js 到 Java 数据库接口联调通过')
  }
  console.log('HTTP 联调通过：登录、鉴权、记录增改查删、用户隔离、无密钥拒绝伪造AI结果、重启后持久化')
})().catch(e => { console.error(e); process.exitCode = 1 }).finally(async () => {
  await stop()
  fs.rmSync(temp, { recursive: true, force: true })
})
