// 本地开发者工具使用。真机的 127.0.0.1 指手机自身，不能访问电脑。
// 正式上线：改为已配置 request 合法域名的 HTTPS 地址，并将 authMode 改为 wechat。
module.exports = {
  baseUrl: 'http://127.0.0.1:8080/api',
  authMode: 'dev'
}
