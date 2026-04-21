<script setup>
import { ref } from 'vue'
import api from '@/api'

const selectedAnalysis = ref('Investor Report')
const userQuestion = ref('')
const isLoading = ref(false)
const chatHistory = ref([])

const analysisTypes = [
  'Πλήρης Ανάλυση',
  'Executive Summary',
  'Ανάλυση Υποχρεώσεων',
  'Τάσεις & Προβλέψεις',
  'Βελτιστοποίηση Κόστους',
  'Cash Flow Πρόβλεψη',
  'Board Meeting Brief',
  'Investor Report',
  'Λογιστική Ανάλυση',
]

const quickTabs = [
  { label: 'Πλήρης',       icon: '📊' },
  { label: 'Executive',    icon: '🏢' },
  { label: 'Υποχρεώσεις',  icon: '📅' },
  { label: 'Τάσεις',       icon: '📈' },
  { label: 'Κόστος',       icon: '💰' },
  { label: 'Cash Flow',    icon: '💵' },
  { label: 'Board',        icon: '👥' },
  { label: 'Investor',     icon: '🎯' },
  { label: 'Λογιστής',     icon: '📋' },
]

// ── Simple markdown renderer ──
function renderMarkdown(text) {
  if (!text) return ''
  let html = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  // Headings (process largest to smallest)
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')
  // Bold + italic
  html = html.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*([^*\n]+)\*/g, '<em>$1</em>')
  // Inline code
  html = html.replace(/`([^`\n]+)`/g, '<code>$1</code>')
  // Horizontal rule
  html = html.replace(/^---+$/gm, '<hr/>')
  // Unordered list
  html = html.replace(/^[\-\*] (.+)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>[\s\S]*?<\/li>\n?)+/g, m => '<ul>' + m + '</ul>')
  // Paragraphs from double newlines
  html = html.split(/\n\n+/).map(block => {
    if (/^<(h[1-6]|ul|ol|hr)/.test(block)) return block
    return '<p>' + block.replace(/\n/g, '<br/>') + '</p>'
  }).join('\n')
  return html
}

// ── Export PDF/Word ──
async function exportReport(historyId, format) {
  try {
    const res = await api.get('/api/ai/export/' + historyId + '/' + format, { responseType: 'blob' })
    let filename = 'Next2Cash_AI_report_' + historyId + '.' + format
    const cd = res.headers['content-disposition'] || res.headers['Content-Disposition']
    if (cd) {
      const m = cd.match(/filename\*?=(?:UTF-8'')?([^;]+)/i)
      if (m) filename = decodeURIComponent(m[1].replace(/["']/g, ''))
    }
    const blob = new Blob([res.data])
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    setTimeout(() => URL.revokeObjectURL(link.href), 1000)
  } catch (err) {
    console.error('Export failed:', err)
    alert('Σφάλμα export: ' + (err.response?.data?.error || err.message))
  }
}

const sendQuestion = async () => {
  if (!userQuestion.value.trim()) return
  const q = userQuestion.value.trim()
  userQuestion.value = ''
  isLoading.value = true
  chatHistory.value.push({ role: 'user', text: q })

  try {
    const res = await api.post('/api/ai/analyze', {
      question: q,
      analysisType: selectedAnalysis.value,
      entityScope: 'all',
      dateRange: 'ytd'
    })

    if (res.data && res.data.success) {
      const d = res.data
      const costEur = d.costEur != null ? Number(d.costEur).toFixed(4) : '—'
      const rows = d.rowsAnalyzed != null ? d.rowsAnalyzed : 0
      chatHistory.value.push({
        role: 'ai',
        text: d.answer,
        meta: 'Κόστος: €' + costEur + ' · ' + rows + ' εγγραφές · ' + d.tier,
        historyId: d.historyId
      })
    } else {
      chatHistory.value.push({ role: 'ai', text: '⚠ Σφάλμα: ' + (res.data?.error || 'unknown') })
    }
  } catch (err) {
    console.error('AI error:', err)
    const status = err.response?.status
    const errCode = err.response?.data?.error
    const errMsg = err.response?.data?.message || err.message
    let msg = 'Σφάλμα σύνδεσης'
    if (status === 403) msg = 'Μόνο ADMIN'
    else if (status === 503) msg = 'AI μη διαθέσιμο: ' + errMsg
    else if (errCode) msg = errCode + ': ' + errMsg
    chatHistory.value.push({ role: 'ai', text: '⚠ ' + msg })
  } finally {
    isLoading.value = false
  }
}

const selectTab = (tab) => {
  const map = {
    'Investor':     'Investor Report',
    'Πλήρης':       'Πλήρης Ανάλυση',
    'Executive':    'Executive Summary',
    'Υποχρεώσεις':  'Ανάλυση Υποχρεώσεων',
    'Τάσεις':       'Τάσεις & Προβλέψεις',
    'Κόστος':       'Βελτιστοποίηση Κόστους',
    'Cash Flow':    'Cash Flow Πρόβλεψη',
    'Board':        'Board Meeting Brief',
    'Λογιστής':     'Λογιστική Ανάλυση',
  }
  selectedAnalysis.value = map[tab.label] || tab.label
}

const runAnalysis = () => {
  userQuestion.value = 'Εκτέλεσε: ' + selectedAnalysis.value
  sendQuestion()
}
</script>

<template>
  <div class="ai-page">
    <div class="ai-card">

      <!-- Header -->
      <div class="ai-header">
        <div class="ai-header-left">
          <div class="ai-avatar">🤖</div>
          <div>
            <div class="ai-title">AI Financial Analyst</div>
            <div class="ai-subtitle">Ανάλυση δεδομένων με τεχνητή νοημοσύνη — powered by Claude</div>
          </div>
        </div>
        <div class="ai-header-right">
          <div class="select-wrap">
            <select v-model="selectedAnalysis" class="analysis-select">
              <option v-for="a in analysisTypes" :key="a">{{ a }}</option>
            </select>
          </div>
          <button class="btn-run" @click="runAnalysis">⚡ Ανάλυση</button>
        </div>
      </div>

      <!-- Quick Tabs -->
      <div class="quick-tabs">
        <button
          v-for="tab in quickTabs"
          :key="tab.label"
          class="quick-tab"
          @click="selectTab(tab)">
          {{ tab.icon }} {{ tab.label }}
        </button>
      </div>

      <!-- Chat History -->
      <div class="chat-area" v-if="chatHistory.length > 0">
        <div
          v-for="(msg, i) in chatHistory"
          :key="i"
          class="chat-msg"
          :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'">
          <div class="msg-icon">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
          <div v-if="msg.role === 'user'" class="msg-bubble">
            <div class="msg-text">{{ msg.text }}</div>
          </div>
          <div v-else class="answer-card">
            <div class="answer-content" v-html="renderMarkdown(msg.text)"></div>
            <div v-if="msg.meta" class="answer-meta">{{ msg.meta }}</div>
            <div v-if="msg.historyId" class="answer-actions">
              <button class="btn-export" @click="exportReport(msg.historyId, 'pdf')">
                📄 Λήψη PDF
              </button>
              <button class="btn-export" @click="exportReport(msg.historyId, 'docx')">
                📝 Λήψη Word
              </button>
            </div>
          </div>
        </div>
        <div v-if="isLoading" class="chat-msg msg-ai">
          <div class="msg-icon">🤖</div>
          <div class="msg-bubble">
            <div class="loading-dots"><span></span><span></span><span></span></div>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div class="empty-state" v-else>
        <div class="empty-icon">🤖</div>
        <div class="empty-title">Ρωτήστε τον AI Analyst</div>
        <div class="empty-hint">π.χ. «Ποιες κατηγορίες αυξήθηκαν πάνω από 20% το 2024;»</div>
      </div>

      <!-- Input -->
      <div class="chat-input-area">
        <input
          v-model="userQuestion"
          class="chat-input"
          placeholder="Ρώτησε κάτι συγκεκριμένο... π.χ. «Ποιες κατηγορίες αυξήθηκαν πάνω από 20% το 2024;»"
          @keydown.enter="sendQuestion" />
        <button class="btn-send" @click="sendQuestion" :disabled="isLoading">➤</button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.ai-page { padding: 20px 24px; background: #0d1e2e; min-height: 100vh; color: #c8d8e8; }
.ai-card { background: #1a2f45; border-radius: 12px; padding: 24px; display: flex; flex-direction: column; gap: 16px; min-height: calc(100vh - 80px); }
.ai-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.ai-header-left { display: flex; align-items: center; gap: 14px; }
.ai-avatar { font-size: 2rem; background: rgba(79,195,161,0.1); border-radius: 12px; width: 52px; height: 52px; display: flex; align-items: center; justify-content: center; }
.ai-title { font-size: 1rem; font-weight: 700; color: #e0e6ed; }
.ai-subtitle { font-size: 0.75rem; color: #8899aa; margin-top: 2px; }
.ai-header-right { display: flex; align-items: center; gap: 10px; }
.select-wrap { position: relative; }
.analysis-select { appearance: none; background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8; padding: 8px 36px 8px 12px; border-radius: 8px; font-size: 0.85rem; cursor: pointer; outline: none; min-width: 200px; }
.btn-run { background: #4FC3A1; border: none; color: #0d1e2e; padding: 8px 20px; border-radius: 8px; font-size: 0.85rem; font-weight: 700; cursor: pointer; white-space: nowrap; }
.btn-run:hover { background: #3dab8a; }

.quick-tabs { display: flex; gap: 8px; flex-wrap: wrap; padding: 12px 0; border-top: 1px solid #223d57; border-bottom: 1px solid #223d57; }
.quick-tab { background: #152538; border: 1px solid #2a4a6a; color: #8899aa; padding: 6px 14px; border-radius: 6px; font-size: 0.82rem; cursor: pointer; transition: all 0.15s; }
.quick-tab:hover { background: #1e3a52; color: #4FC3A1; border-color: #4FC3A1; }

.chat-area { flex: 1; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; padding: 8px 0; min-height: 300px; }
.chat-msg { display: flex; gap: 12px; align-items: flex-start; }
.msg-user { flex-direction: row-reverse; }
.msg-icon { font-size: 1.4rem; flex-shrink: 0; }

/* User message bubble — dark (unchanged) */
.msg-bubble { background: #152538; border-radius: 10px; padding: 12px 16px; max-width: 80%; font-size: 0.85rem; line-height: 1.6; color: #c8d8e8; }
.msg-user .msg-bubble { background: rgba(79,195,161,0.12); border: 1px solid rgba(79,195,161,0.2); color: #e0e6ed; }
.msg-text { white-space: pre-line; }

/* Loading dots */
.loading-dots { display: flex; gap: 4px; align-items: center; padding: 4px 0; }
.loading-dots span { width: 8px; height: 8px; border-radius: 50%; background: #4FC3A1; animation: bounce 1.2s infinite; }
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }

.empty-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 60px 0; color: #8899aa; }
.empty-icon { font-size: 3rem; }
.empty-title { font-size: 1rem; font-weight: 600; color: #c8d8e8; }
.empty-hint { font-size: 0.82rem; color: #8899aa; }

.chat-input-area { display: flex; gap: 10px; align-items: center; border-top: 1px solid #223d57; padding-top: 16px; }
.chat-input { flex: 1; background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8; padding: 12px 16px; border-radius: 8px; font-size: 0.85rem; outline: none; }
.chat-input:focus { border-color: #4FC3A1; }
.chat-input::placeholder { color: #4a6a88; }
.btn-send { background: #4FC3A1; border: none; color: #0d1e2e; width: 42px; height: 42px; border-radius: 8px; font-size: 1.1rem; cursor: pointer; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.btn-send:hover { background: #3dab8a; }
.btn-send:disabled { opacity: 0.5; cursor: not-allowed; }

/* ─────────────────────────────────────────────────────── */
/* AI ANSWER CARD — LIGHT THEME (only for AI replies)     */
/* ─────────────────────────────────────────────────────── */
.answer-card {
  background: #ffffff;
  color: #000000;
  border-radius: 12px;
  padding: 32px 40px;
  max-width: 85%;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.3);
  font-size: 1.05rem;
  line-height: 1.75;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}
.answer-content { color: #000000; }
.answer-content h1 { font-size: 2rem; font-weight: 900; color: #000000; margin: 1.3em 0 0.6em 0; padding-bottom: 0.35em; border-bottom: 3px solid #000000; letter-spacing: -0.01em; }
.answer-content h1:first-child { margin-top: 0; }
.answer-content h2 { font-size: 1.55rem; font-weight: 800; color: #000000; margin: 1.5em 0 0.55em 0; letter-spacing: -0.01em; }
.answer-content h3 { font-size: 1.25rem; font-weight: 700; color: #000000; margin: 1.3em 0 0.45em 0; }
.answer-content p { margin: 0.75em 0; color: #000000; font-size: 1.05rem; }
.answer-content strong { font-weight: 800; color: #000000; }
.answer-content em { font-style: italic; color: #333; }
.answer-content ul, .answer-content ol { margin: 0.5em 0 0.8em 0; padding-left: 1.4em; }
.answer-content li { margin: 0.4em 0; color: #000000; font-size: 1.05rem; }
.answer-content code { background: #f1f3f5; padding: 2px 6px; border-radius: 4px; font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 0.9em; color: #2d3748; }
.answer-content hr { border: none; border-top: 1px solid #e5e9ed; margin: 1.3em 0; }

.answer-meta { margin-top: 1.6em; padding-top: 1em; border-top: 1px solid #e5e9ed; font-size: 0.78rem; color: #6c7a8a; font-weight: 500; }
.answer-actions { display: flex; gap: 10px; margin-top: 1em; }
.btn-export { padding: 10px 18px; background: #1a2332; color: #fff; border: none; border-radius: 6px; font-size: 0.88rem; font-weight: 600; cursor: pointer; transition: background 0.15s; }
.btn-export:hover { background: #2a3c52; }
</style>
