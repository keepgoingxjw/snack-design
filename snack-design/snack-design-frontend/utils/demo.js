// 仅用于C端首次体验的预设虚构配方，不是实时AI响应，不走后端，不写入个人记录。
const { seed, analyze, splitIngredients } = require('./ingredients')
const samples = [
  { id: 'oat-original', title: '原味燕麦脆饼', label: '原味', raw: seed },
  { id: 'oat-cocoa', title: '可可燕麦脆饼', label: '可可味', raw: seed + '、可可粉' },
  { id: 'oat-nut', title: '坚果燕麦脆饼', label: '坚果味', raw: seed + '、花生' }
].map(sample => ({ ...sample, count: splitIngredients(sample.raw).length }))
const extra = {
  '可可粉': {
    group: 'flavour', role: '增加可可风味', benefit: '在这个虚构配方中提供可可风味和色泽，不代表产品有额外健康益处。',
    risk: '可可风味不等于低糖或低脂；仍需看完整配方、营养成分表和食用量。',
    source: '参考：烘焙原料教材', url: 'https://opentextbc.ca/ingredients/'
  },
  '花生': {
    group: 'structure', role: '增加颗粒口感', benefit: '在这个虚构配方中增加坚果风味和颗粒口感，实际营养贡献取决于添加量。',
    risk: '花生过敏者应避开含花生的食品；还需核对实物标签中的交叉接触提示。',
    source: '参考：FDA 食物过敏原', url: 'https://www.fda.gov/food/food-labeling-nutrition/food-allergies-what-you-need-know'
  }
}
function getDemo(id) {
  const sample = samples.find(item => item.id === id) || samples[0]
  const findings = analyze(sample.raw).map(row => {
    const item = { ...row, ...(extra[row.name] || {}) }
    return {
      name: item.name, group: item.group, role: item.role, summary: item.benefit, benefit: item.benefit, risk: item.risk, dosage: null,
      sources: item.url ? [{ title: item.source, url: item.url, quote: '', textVerified: false }] : [],
      verification: '预设的通用知识演示文案，参考资料未在本次实时核对，不是对真实商品的评审。'
    }
  })
  return {
    sample: { ...sample },
    record: { id: sample.id, raw: sample.raw, kind: '虚构示例 · 仅供体验' },
    report: {
      id: 'demo-' + sample.id, recordId: sample.id, status: 'done', foodCategory: '虚构燕麦脆饼', createdAt: 0, model: 'preset-demo', findings,
      recommendation: {
        action: 'check_first', title: '先核对过敏原，再按个人需要选择',
        audience: sample.id === 'oat-nut' ? '尤其注意小麦、牛奶或花生过敏者' : '尤其注意小麦或牛奶过敏者',
        reason: '这个示例配方含小麦粉、乳粉' + (sample.id === 'oat-nut' ? '和花生' : '') + '，对相应成分过敏者应避开含该成分的食品。其他人也需结合营养成分表和食用量选择。',
        conditions: '这是预设演示建议，不是实时AI查询，不代表真实商品已被核实安全，不能据此保证“吃一点没事”。', sourceUrls: []
      },
      disclaimer: '虚构配方，仅展示配料解析的使用方式；不是实时查询或真实商品结论。'
    }
  }
}
module.exports = { samples, getDemo }
