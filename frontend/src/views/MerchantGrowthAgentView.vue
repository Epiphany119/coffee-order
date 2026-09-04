<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi } from '@/api'
import { useMerchantStore } from '@/stores/merchant'
import type { GrowthAgentAction, GrowthAgentAnalysis } from '@/api/types'

const mstore = useMerchantStore()
const question = ref('为什么今天营业额不高？给我一个不打扰顾客的增长方案。')
const loading = ref(false)
const executing = ref(false)
const analysis = ref<GrowthAgentAnalysis | null>(null)
const actions = ref<GrowthAgentAction[]>([])

const prompts = [
  '为什么今天营业额不高？给我一个不打扰顾客的增长方案。',
  '现在适合做秒杀提醒吗？',
  '门店订单堆积时，我应该先做什么？',
  '帮我设计一个老客复购动作。'
]

function choosePrompt(value: string) {
  question.value = value
  analyze()
}

function fmtTime(value: string | number[] | null | undefined) {
  if (!value) return '--'
  if (Array.isArray(value)) return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')} ${String(value[3] || 0).padStart(2, '0')}:${String(value[4] || 0).padStart(2, '0')}`
  return String(value).replace('T', ' ').slice(0, 16)
}

function statusText(status: string) {
  return ({ PENDING: '待确认', EXECUTING: '执行中', EXECUTED: '已执行', FAILED: '执行失败', CANCELED: '已取消' } as Record<string, string>)[status] || status
}

async function loadActions() {
  if (!mstore.merchant?.id) return
  try { actions.value = await merchantApi.growthAgentActions(mstore.merchant.id) }
  catch (e: any) { ElMessage.warning(e.message || 'Agent 审计记录加载失败，请确认已执行数据库迁移') }
}

async function analyze() {
  const merchantId = mstore.merchant?.id
  if (!merchantId) return
  if (!question.value.trim()) { ElMessage.warning('先告诉 Agent 你关心的经营问题'); return }
  loading.value = true
  try {
    analysis.value = await merchantApi.growthAgentAnalyze(merchantId, question.value.trim())
  } catch (e: any) {
    ElMessage.error(e.message || 'Agent 暂时无法完成诊断')
  } finally { loading.value = false }
}

async function confirmProposal() {
  const merchantId = mstore.merchant?.id
  const proposal = analysis.value?.suggestedAction
  if (!merchantId || !proposal) return
  executing.value = true
  try {
    const created = await merchantApi.growthAgentCreateAction(merchantId, proposal)
    const executed = await merchantApi.growthAgentExecuteAction(merchantId, created.id)
    ElMessage.success(`${executed.message}：已影响 ${executed.affectedUsers} 位顾客`)
    await loadActions()
  } catch (e: any) {
    ElMessage.error(e.message || '执行失败；请检查 Agent 数据库迁移是否已执行')
  } finally { executing.value = false }
}

onMounted(async () => { await loadActions(); await analyze() })
</script>

<template>
  <div class="agent-page">
    <section class="agent-hero">
      <div>
        <p>FIKA GROWTH AGENT · TOOL CALLING</p>
        <h2>把经营问题，变成可确认的增长动作。</h2>
        <span>Agent 先读订单、履约与秒杀库存，再给建议；所有触达和发券都需你确认。</span>
      </div>
      <div class="agent-orbit"><i>✦</i><b>店长<br>Agent</b></div>
    </section>

    <section class="workspace">
      <div class="ask-panel panel">
        <div class="panel-kicker">ASK THE STORE</div>
        <h3>今天想让店长 Agent 看什么？</h3>
        <textarea v-model="question" maxlength="300" placeholder="例如：为什么今天的订单下降了？"></textarea>
        <div class="prompt-list">
          <button v-for="prompt in prompts" :key="prompt" type="button" @click="choosePrompt(prompt)">{{ prompt }}</button>
        </div>
        <button class="analyze-btn" type="button" :disabled="loading" @click="analyze">
          {{ loading ? '正在调用经营工具…' : '开始诊断 →' }}
        </button>
        <small>当前引擎：{{ analysis?.engine || '等待诊断' }}；所有执行权限仍由本页审批。</small>
      </div>

      <div class="analysis-panel panel">
        <template v-if="analysis">
          <div class="panel-top"><div><span class="live-dot"></span> 数据工具已完成</div><small>{{ analysis.storeName }}</small></div>
          <div class="intent-line">Agent 理解：{{ analysis.understanding || '经营概览' }}</div>
          <h3>{{ analysis.answer }}</h3>
          <div class="signals">
            <article v-for="item in analysis.signals" :key="item.label">
              <span>{{ item.label }}</span><b>{{ item.value }}</b><small>{{ item.note }}</small>
            </article>
          </div>
          <div class="tool-line">已调用：{{ analysis.dataSources?.join(' · ') || '订单统计 · 履约队列 · 秒杀库存' }}</div>
        </template>
        <div v-else class="analysis-empty">输入问题后，Agent 会先调用受控数据工具进行分析。</div>
      </div>
    </section>

    <section v-if="analysis" class="proposal panel">
      <div class="proposal-head"><div><p>AGENT PROPOSAL</p><h3>{{ analysis.suggestedAction.title }}</h3></div><span>需店长确认</span></div>
      <p class="proposal-summary">{{ analysis.suggestedAction.summary }}</p>
      <div class="proposal-reason"><b>为什么推荐这样做</b><span>{{ analysis.suggestedAction.reason }}</span></div>
      <div class="proposal-action">
        <div><small>受控工具</small><b>{{ analysis.suggestedAction.actionType === 'CREATE_VOUCHERS' ? '批量发券 + 站内通知' : '向近期顾客发送站内通知' }}</b></div>
        <button type="button" :disabled="executing" @click="confirmProposal">{{ executing ? '执行中…' : '确认并执行' }}</button>
      </div>
      <small class="audit-note">执行后会写入 Agent 审计记录；Agent 本身不能直接修改你的经营数据。</small>
    </section>

    <section class="history panel">
      <div class="history-head"><div><p>AGENT AUDIT LOG</p><h3>每一次动作，都留有凭据。</h3></div><button type="button" @click="loadActions">刷新记录</button></div>
      <div v-if="actions.length" class="action-list">
        <article v-for="action in actions" :key="action.id">
          <span class="action-icon">{{ action.actionType === 'CREATE_VOUCHERS' ? '▣' : '✦' }}</span>
          <div><b>{{ action.title }}</b><small>{{ action.actionType }} · {{ fmtTime(action.createdAt) }}</small></div>
          <span class="status" :class="action.status.toLowerCase()">{{ statusText(action.status) }}</span>
        </article>
      </div>
      <div v-else class="history-empty">还没有执行过 Agent 操作。先让它为今天的门店做一次诊断吧。</div>
    </section>
  </div>
</template>

<style scoped>
.agent-page{display:grid;gap:18px;max-width:1280px;margin:0 auto}.panel{background:var(--paper);border:1px solid var(--line);border-radius:22px;box-shadow:0 10px 30px rgba(17,45,36,.05)}.agent-hero{min-height:190px;padding:30px 34px;display:flex;align-items:center;justify-content:space-between;gap:24px;overflow:hidden;border-radius:25px;color:#fff;background:radial-gradient(circle at 88% 25%,rgba(117,206,166,.32),transparent 24%),linear-gradient(112deg,#11392d,#225c46)}.agent-hero p,.proposal-head p,.history-head p,.panel-kicker{margin:0;color:#ffad7f;letter-spacing:.14em;font-size:10px;font-weight:800}.agent-hero h2{font-size:29px;margin:9px 0;font-family:"DM Serif Display","Noto Sans SC",serif}.agent-hero span{color:rgba(255,255,255,.72);font-size:13px}.agent-orbit{width:112px;height:112px;flex:none;display:grid;place-items:center;position:relative;border:1px solid rgba(255,255,255,.32);border-radius:50%;background:rgba(255,255,255,.1);text-align:center}.agent-orbit:before{content:"";position:absolute;inset:10px;border-radius:50%;border:1px dashed rgba(255,255,255,.38);animation:spin 15s linear infinite}.agent-orbit i{position:absolute;right:11px;top:13px;color:#ffbf91;font-style:normal}.agent-orbit b{font-size:16px;line-height:1.3}@keyframes spin{to{transform:rotate(360deg)}}.workspace{display:grid;grid-template-columns:minmax(360px,.88fr) minmax(0,1.12fr);gap:18px}.ask-panel,.analysis-panel{padding:24px}.ask-panel h3,.analysis-panel h3,.proposal h3,.history h3{font-size:20px;margin:8px 0 14px;color:var(--ink)}textarea{box-sizing:border-box;width:100%;min-height:105px;resize:vertical;border:1px solid #dfdfd6;border-radius:14px;padding:13px;font:inherit;font-size:13px;color:var(--ink);background:#fcfbf7;outline:none}textarea:focus{border-color:var(--orange);box-shadow:0 0 0 3px #ffeadf}.prompt-list{display:flex;flex-wrap:wrap;gap:7px;margin:12px 0}.prompt-list button,.history-head button{border:1px solid #eadfd3;border-radius:999px;background:#fffaf4;color:#805e4d;padding:6px 9px;font-size:11px;cursor:pointer}.prompt-list button:hover{border-color:var(--orange);color:var(--orange)}.analyze-btn{width:100%;border:0;border-radius:12px;background:var(--pine);color:#fff;padding:12px;font-weight:800;cursor:pointer}.analyze-btn:disabled,.proposal-action button:disabled{opacity:.6;cursor:wait}.ask-panel>small,.audit-note{display:block;margin-top:10px;color:#8c928c;font-size:11px;line-height:1.5}.panel-top,.proposal-head,.history-head,.proposal-action{display:flex;align-items:center;justify-content:space-between;gap:12px}.panel-top{font-size:12px;color:#52715f}.panel-top small{color:#8c928c}.live-dot{display:inline-block;width:7px;height:7px;margin-right:5px;border-radius:50%;background:#4fc37d;box-shadow:0 0 0 4px #dcf4e5}.intent-line{margin:12px 0 -5px;padding:8px 10px;border-radius:10px;background:#f1f6f1;color:#446a54;font-size:11px}.signals{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin-top:18px}.signals article{padding:12px;border-radius:13px;background:#f7f7f2}.signals span,.signals small{display:block;color:#858c85;font-size:11px}.signals b{display:block;margin:4px 0;color:var(--pine);font-size:18px}.tool-line{margin-top:15px;color:#a16b4d;font-size:11px}.analysis-empty,.history-empty{padding:30px 0;text-align:center;color:#929892;font-size:13px}.proposal{padding:24px 27px;border-color:#f2cbb6;background:linear-gradient(110deg,#fffdf9,#fff5ee)}.proposal-head>span{padding:5px 9px;border-radius:999px;background:#fff0e4;color:#d46635;font-size:11px;font-weight:700}.proposal-summary{margin:0;max-width:740px;color:#516058;line-height:1.7;font-size:14px}.proposal-reason{margin:16px 0;padding:11px 13px;border-left:3px solid #ef8c58;background:#fffaf4;color:#6d756d;font-size:12px}.proposal-reason b{margin-right:12px;color:#9e4d2b}.proposal-action{margin-top:17px;padding-top:16px;border-top:1px solid #f1dbcc}.proposal-action small,.proposal-action b{display:block}.proposal-action small{color:#969b95;font-size:10px}.proposal-action b{margin-top:4px;color:var(--ink);font-size:13px}.proposal-action button{border:0;border-radius:11px;background:var(--orange);color:white;padding:11px 18px;font-weight:800;cursor:pointer}.history{padding:23px 26px}.history-head button{border-radius:10px;padding:8px 12px}.action-list{display:grid;gap:8px;margin-top:15px}.action-icon{display:grid;place-items:center;width:32px;height:32px;border-radius:10px;background:#fff0e4;color:var(--orange)}.action-list div{display:grid;gap:4px;min-width:0}.action-list b{font-size:13px;color:var(--ink)}.action-list small{font-size:11px;color:#90968f}.status{margin-left:auto;padding:4px 8px;border-radius:999px;background:#fff0e4;color:#bd6135;font-size:11px}.status.executed{background:#e4f4e8;color:#328053}.status.failed{background:#f9e8e8;color:#b04d4d}@media(max-width:800px){.workspace{grid-template-columns:1fr}.agent-hero{padding:25px}.agent-hero h2{font-size:24px}.signals{grid-template-columns:1fr}.agent-orbit{width:82px;height:82px;font-size:12px}.proposal-action{align-items:flex-end}}
</style>
