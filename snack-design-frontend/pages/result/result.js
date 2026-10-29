const store = require('../../utils/records')
const { samples, getDemo } = require('../../utils/demo')
const { splitIngredients, allergenClues } = require('../../utils/ingredients')
const groupTypes = [
  { id: 'structure', title: '构成与口感' }, { id: 'flavour', title: '甜味与香气' },
  { id: 'preservation', title: '保鲜与加工' }, { id: 'nutrition', title: '营养相关' },
  { id: 'other', title: '其他作用' }, { id: 'pending', title: '待核实配料' }
]
function summaryPoints(value) {
  if (typeof value !== 'string') return []
  const lines = value.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
  return lines.map((line, id) => {
    const text = line.replace(/^(?:[•●\-]\s*|\d+[.、)]\s*)/, '')
    const match = text.match(/^([^：:]{1,18}[：:])\s*(.*)$/)
    return { id, bullet: text !== line || lines.length > 1, lead: match ? match[1] : '', text: match ? match[2] : text }
  })
}
function dateLabel(time) {
  const d = new Date(time), pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
Page({
  data: { providerLabel: '当前AI', demoMode: false, demoId: '', demoTitle: '', demoSamples: [], recommendation: null, record: null, report: null, findings: [], groups: [], tabs: [], filter: 'all', count: 0, allergens: '', loading: true, busy: false, error: '', foodCategory: '', history: [], showingHistory: false, reportDate: '' },
  onLoad(q) {
    this.recordId = q.id
    this.autoStart = q.auto === '1'
    this.demoId = q.demo || ''
    this.setData({ foodCategory: q.category || '', demoMode: !!q.demo, demoSamples: q.demo ? samples : [] })
  },
  async onShow() {
    clearTimeout(this.timer)
    this.stopped = false
    if (this.data.demoMode) { this.renderDemo(this.demoId); return }
    const version = this.version = (this.version || 0) + 1
    this.setData({ loading: true, error: '', showingHistory: false, record: null, recommendation: null, report: null, findings: [], groups: [], tabs: [], busy: false, history: [], filter: 'all' })
    try {
      const record = await store.find(this.recordId)
      if (version !== this.version || this.stopped) return
      this.setData({ record, count: splitIngredients(record.raw).length, allergens: allergenClues(record.raw).join('、') })
      const health = await store.ensureResearchApi()
      this.aiModel = health.aiModel
      this.setData({ publicSearch: health.aiModel === 'public-search/EuropePMC', providerLabel: (health.aiModel || '').startsWith('DeepSeek/') ? 'DeepSeek' : '当前AI' })
      const latest = await store.latestResearch(this.recordId)
      if (version !== this.version || this.stopped) return
      const auto = this.autoStart
      this.autoStart = false
      const sameCategory = latest.exists && latest.report.foodCategory === this.data.foodCategory && !this.modeChanged(latest.report)
      if (latest.exists && (!auto || sameCategory)) {
        this.display(latest.report)
        this.setData({ foodCategory: latest.report.foodCategory || '' })
        if (this.data.busy) this.schedule(latest.report.id)
      }
      this.setData({ loading: false })
      if (auto && (!latest.exists || !sameCategory || latest.report.status === 'failed')) await this.query(false)
    } catch (e) { if (!this.stopped && version === this.version) this.setData({ error: e.message || '解析读取失败，请重试' }) }
    finally { if (!this.stopped && version === this.version) this.setData({ loading: false }) }
  },
  renderDemo(id) {
    const demo = getDemo(id)
    this.demoId = demo.sample.id
    this.setData({ record: demo.record, demoId: demo.sample.id, demoTitle: demo.sample.title, loading: false, error: '', history: [], showingHistory: false,
      count: demo.report.findings.length, foodCategory: demo.report.foodCategory, allergens: allergenClues(demo.record.raw).join('、') })
    this.display(demo.report)
  },
  switchDemo(e) { if (this.data.demoMode) this.renderDemo(e.currentTarget.dataset.id) },
  onHide() { this.pause() }, onUnload() { this.pause() },
  pause() { this.stopped = true; this.version = (this.version || 0) + 1; clearTimeout(this.timer) },
  modeChanged(report) {
    if ((this.aiModel || '').startsWith('DeepSeek/') && report && report.status === 'done') return report.model !== this.aiModel
    const cloud = this.aiModel === 'openrouter/free' || /:free$/.test(this.aiModel || '')
    return cloud && report && report.status === 'done' && !(report.model || '').includes('免费AI + 公开文献')
  },
  display(report) {
    const findings = report ? report.findings.map((finding, id) => ({ ...finding, id, group: finding.group || 'other', benefitPoints: summaryPoints(finding.benefit), riskPoints: summaryPoints(finding.risk) })) : []
    const groups = groupTypes.map(group => ({ ...group, rows: findings.filter(row => row.group === group.id) })).filter(group => group.rows.length)
    const recommendation = report && report.status === 'done' ? report.recommendation || {
      action: 'check_first', title: '先核对，再决定', audience: '所有用户，尤其有过敏或特殊饮食需求者',
      reason: '这份记录尚无经过建议规则校验的结论，请核对标签、食用量与个人限制。',
      conditions: '不能仅凭配料名称保证“吃一点没事”或判定整款食品能吃/不能吃。AI建议不能替代专业意见。', sourceUrls: []
    } : null
    const publicSearch = !!report && report.model === 'public-search/EuropePMC'
    this.setData({ report, publicSearch, modeChanged: this.modeChanged(report), recommendation: publicSearch ? null : recommendation, findings, groups, tabs: [{ id: 'all', name: '全部' }, ...groups.map(g => ({ id: g.id, name: g.title }))], filter: 'all',
      busy: !!report && ['queued', 'running'].includes(report.status), reportDate: report ? dateLabel(report.createdAt) : '',
      error: report && report.status === 'failed' ? report.error : '' })
  },
  filter(e) { this.setData({ filter: e.currentTarget.dataset.id }) },
  start() { if (!this.data.busy && !this.data.loading) this.query(true) },
  async query(refresh = true) {
    if (this.data.demoMode || this.data.busy) return
    const version = this.version
    this.setData({ busy: true, error: '', showingHistory: false, history: [] })
    try {
      await store.ensureResearchApi()
      const report = await store.research(this.recordId, this.data.foodCategory, refresh)
      if (this.stopped || version !== this.version) return
      this.display(report)
      if (this.data.busy) this.schedule(report.id)
    } catch (e) { if (!this.stopped && version === this.version) this.setData({ busy: false, error: e.message || '自动解析启动失败' }) }
  },
  schedule(id) { clearTimeout(this.timer); if (!this.stopped) this.timer = setTimeout(() => this.poll(id), 3000) },
  async poll(id) {
    if (this.stopped) return
    const version = this.version
    try {
      const report = await store.researchStatus(id)
      if (this.stopped || version !== this.version) return
      this.display(report)
      if (this.data.busy) this.schedule(id)
    } catch (e) { if (!this.stopped && version === this.version) this.setData({ error: (e.message || '无法获取进度') + '。请刷新进度，后台任务不会取消。' }) }
  },
  refresh() { if (this.data.report && this.data.busy) this.poll(this.data.report.id); else this.onShow() },
  async history() {
    if (this.data.demoMode) return
    try {
      const reports = await store.researchHistory(this.recordId)
      if (!this.stopped) this.setData({ history: reports.map(r => ({ ...r, date: dateLabel(r.createdAt), label: ({ done: '已完成', failed: '失败', queued: '排队中', running: '查询中' })[r.status] })) })
    } catch (e) { if (!this.stopped) this.setData({ error: e.message || '历史查询读取失败' }) }
  },
  historical(e) {
    if (this.data.busy) return
    const report = this.data.history.find(r => r.id === e.currentTarget.dataset.id)
    if (report) { this.display(report); this.setData({ showingHistory: true }); if (this.data.busy) this.schedule(report.id) }
  },
  edit() {
    if (this.data.demoMode) wx.navigateTo({ url: '/pages/edit/edit' })
    else if (this.data.record) wx.navigateTo({ url: '/pages/edit/edit?id=' + this.data.record.id })
  },
  home() { wx.switchTab({ url: '/pages/home/home' }) },
  source(e) { wx.setClipboardData({ data: e.currentTarget.dataset.url, success: () => wx.showToast({ title: '来源链接已复制', icon: 'none' }) }) }
})
