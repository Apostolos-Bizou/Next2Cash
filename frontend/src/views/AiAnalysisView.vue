<script setup>
import { ref } from 'vue'
import api from '@/api'

const selectedAnalysis = ref('Investor Report')
const userQuestion = ref('')
const isLoading = ref(false)
const chatHistory = ref([])

// Filters (date range + entity scope)
const dateRange = ref('ytd')
const entityScope = ref('all')
const selectedYear = ref(new Date().getFullYear())
const customFrom = ref('')
const customTo = ref('')

// Years 2017 through current year (descending)
import { computed } from 'vue'
const availableYears = computed(() => {
  const current = new Date().getFullYear()
  const years = []
  for (let y = current; y >= 2017; y--) years.push(y)
  return years
})

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
  // Split into lines for proper block-level parsing
  const rawLines = text.split('\n')
  const out = []
  let i = 0
  while (i < rawLines.length) {
    let line = rawLines[i]
    // Skip empty lines
    if (!line.trim()) { i++; continue }
    // HR (three or more dashes)
    if (/^-{3,}\s*$/.test(line)) { out.push('<hr/>'); i++; continue }
    // Headings
    let m
    if (m = line.match(/^###\s+(.+)$/)) { out.push('<h3>' + escapeHtml(m[1]) + '</h3>'); i++; continue }
    if (m = line.match(/^##\s+(.+)$/)) { out.push('<h2>' + escapeHtml(m[1]) + '</h2>'); i++; continue }
    if (m = line.match(/^#\s+(.+)$/)) { out.push('<h1>' + escapeHtml(m[1]) + '</h1>'); i++; continue }
    // Table: current line is pipe-row AND next line is separator row
    if (line.trim().startsWith('|') && i + 1 < rawLines.length && /^\s*\|[\s\-|:]+\|\s*$/.test(rawLines[i+1])) {
      // Header row
      const headerCells = splitPipeRow(line)
      i += 2 // skip header + separator
      const bodyRows = []
      while (i < rawLines.length && rawLines[i].trim().startsWith('|')) {
        bodyRows.push(splitPipeRow(rawLines[i]))
        i++
      }
      let table = '<table class="ai-table"><thead><tr>'
      for (const c of headerCells) table += '<th>' + inline(c) + '</th>'
      table += '</tr></thead><tbody>'
      for (const row of bodyRows) {
        table += '<tr>'
        for (const c of row) table += '<td>' + inline(c) + '</td>'
        table += '</tr>'
      }
      table += '</tbody></table>'
      out.push(table)
      continue
    }
    // Unordered list
    if (/^\s*[-*]\s+/.test(line)) {
      const items = []
      while (i < rawLines.length && /^\s*[-*]\s+/.test(rawLines[i])) {
        const item = rawLines[i].replace(/^\s*[-*]\s+/, '')
        items.push('<li>' + inline(item) + '</li>')
        i++
      }
      out.push('<ul>' + items.join('') + '</ul>')
      continue
    }
    // Ordered list
    if (/^\s*\d+\.\s+/.test(line)) {
      const items = []
      while (i < rawLines.length && /^\s*\d+\.\s+/.test(rawLines[i])) {
        const item = rawLines[i].replace(/^\s*\d+\.\s+/, '')
        items.push('<li>' + inline(item) + '</li>')
        i++
      }
      out.push('<ol>' + items.join('') + '</ol>')
      continue
    }
    // Regular paragraph: collect consecutive non-empty non-special lines
    const paraLines = [line]
    i++
    while (i < rawLines.length && rawLines[i].trim() && !isBlockStart(rawLines[i])) {
      paraLines.push(rawLines[i])
      i++
    }
    out.push('<p>' + paraLines.map(l => inline(l)).join('<br/>') + '</p>')
  }
  return out.join('\n')
}

function isBlockStart(line) {
  if (!line) return false
  if (/^#{1,6}\s/.test(line)) return true
  if (/^-{3,}\s*$/.test(line)) return true
  if (line.trim().startsWith('|')) return true
  if (/^\s*[-*]\s+/.test(line)) return true
  if (/^\s*\d+\.\s+/.test(line)) return true
  return false
}

function splitPipeRow(row) {
  // Strip leading/trailing pipes and whitespace, split on |
  let t = row.trim()
  if (t.startsWith('|')) t = t.substring(1)
  if (t.endsWith('|')) t = t.substring(0, t.length - 1)
  return t.split('|').map(s => s.trim())
}

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function inline(s) {
  // Apply inline formatting AFTER escaping
  let t = escapeHtml(s)
  t = t.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  t = t.replace(/\*([^*\n]+)\*/g, '<em>$1</em>')
  t = t.replace(/`([^`\n]+)`/g, '<code>$1</code>')
  return t
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

// Resolve dateRange value for backend (handles year_XXXX + custom)
function resolveDateRange() {
  if (dateRange.value === 'year_specific') return 'year_' + selectedYear.value
  if (dateRange.value === 'custom') {
    if (customFrom.value && customTo.value) return 'custom_' + customFrom.value + '_' + customTo.value
    return 'ytd' // fallback if custom incomplete
  }
  return dateRange.value
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
      entityScope: entityScope.value,
      dateRange: resolveDateRange()
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
          <div class="filter-wrap">
                      <select v-model="entityScope" class="filter-select" title="Εταιρία">
                        <option value="all">Όλες οι εταιρίες</option>
                        <option value="next2me">Next2me</option>
                        <option value="house">House</option>
                      </select>
                    </div>
                    <div class="filter-wrap">
                      <select v-model="dateRange" class="filter-select" title="Εύρος ημερομηνιών">
                        <option value="last_30_days">Τελευταίες 30 μέρες</option>
                        <option value="last_3_months">Τελευταίο τρίμηνο</option>
                        <option value="last_12_months">Τελευταίο έτος</option>
                        <option value="ytd">Τρέχον έτος (YTD)</option>
                        <option value="year_specific">Συγκεκριμένο έτος...</option>
                        <option value="all_data">Όλα τα δεδομένα</option>
                        <option value="custom">Custom...</option>
                      </select>
                    </div>
                    <div v-if="dateRange === 'year_specific'" class="filter-wrap">
                      <select v-model="selectedYear" class="filter-select">
                        <option v-for="y in availableYears" :key="y" :value="y">{{ y }}</option>
                      </select>
                    </div>
                    <div v-if="dateRange === 'custom'" class="filter-custom">
                      <input type="date" v-model="customFrom" class="filter-date" />
                      <span class="filter-dash">→</span>
                      <input type="date" v-model="customTo" class="filter-date" />
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
      <div v-if="dateRange === 'all_data'" class="cost-warning">⚠ Πλήρης ανάλυση 4822 εγγραφών — εκτιμώμενο κόστος ~€0.30-0.60/query</div>
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
  padding: 36px 44px;
  max-width: 90%;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.3);
  font-size: 1.2rem;
  line-height: 1.8;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}
.answer-content, .answer-content * { color: #000000 !important; }
.answer-content h1 { font-size: 2.4rem !important; font-weight: 900 !important; margin: 1.3em 0 0.6em 0; padding-bottom: 0.35em; border-bottom: 3px solid #000000; letter-spacing: -0.01em; line-height: 1.25; }
.answer-content h1:first-child { margin-top: 0; }
.answer-content h2 { font-size: 1.85rem !important; font-weight: 800 !important; margin: 1.5em 0 0.55em 0; letter-spacing: -0.01em; line-height: 1.3; padding-bottom: 0.25em; border-bottom: 1.5px solid #000000; }
.answer-content h3 { font-size: 1.5rem !important; font-weight: 700 !important; margin: 1.3em 0 0.45em 0; line-height: 1.35; }
.answer-content p { margin: 0.85em 0; font-size: 1.2rem; line-height: 1.8; }
.answer-content strong { font-weight: 800; }
.answer-content em { font-style: italic; color: #222 !important; }
.answer-content ul, .answer-content ol { margin: 0.6em 0 1em 0; padding-left: 1.6em; }
.answer-content li { margin: 0.5em 0; font-size: 1.2rem; line-height: 1.75; }
.answer-content code { background: #f1f3f5; padding: 3px 8px; border-radius: 4px; font-family: ui-monospace, "SF Mono", Menlo, monospace; font-size: 0.95em; color: #2d3748 !important; }
.answer-content hr { border: none; border-top: 1px solid #000000 !important; margin: 1.5em 0; opacity: 0.35; }

/* Tables from markdown */
.answer-content table.ai-table { width: 100%; border-collapse: collapse; margin: 1.2em 0 1.4em 0; font-size: 1.1rem; }
.answer-content table.ai-table th { background: #1a2332; color: #ffffff !important; padding: 12px 14px; text-align: left; font-weight: 700; border: 1px solid #1a2332; }
.answer-content table.ai-table td { padding: 10px 14px; border: 1px solid #d0d7de; vertical-align: top; }
.answer-content table.ai-table tbody tr:nth-child(even) td { background: #f6f8fa; }
.answer-content table.ai-table tbody tr:hover td { background: #eef2f6; }

.answer-meta { margin-top: 1.8em; padding-top: 1em; border-top: 1px solid #d0d7de; font-size: 0.88rem !important; color: #6c7a8a !important; font-weight: 500; }
.answer-actions { display: flex; gap: 12px; margin-top: 1.2em; }
.btn-export { padding: 12px 22px; background: #1a2332; color: #ffffff !important; border: none; border-radius: 6px; font-size: 0.95rem; font-weight: 600; cursor: pointer; transition: background 0.15s; }
.btn-export:hover { background: #2a3c52; }

/* ─────────────────────────────────────── */
/* Filter dropdowns (entity + date range)  */
/* ─────────────────────────────────────── */
.filter-wrap { position: relative; }
.filter-select { appearance: none; background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8; padding: 8px 28px 8px 12px; border-radius: 8px; font-size: 0.82rem; cursor: pointer; outline: none; min-width: 130px; }
.filter-select:focus { border-color: #4FC3A1; }
.filter-custom { display: flex; align-items: center; gap: 6px; }
.filter-date { background: #152538; border: 1px solid #2a4a6a; color: #c8d8e8; padding: 7px 10px; border-radius: 8px; font-size: 0.82rem; outline: none; font-family: inherit; }
.filter-date:focus { border-color: #4FC3A1; }
.filter-dash { color: #8899aa; font-size: 0.9rem; }
.cost-warning { background: rgba(255, 193, 7, 0.12); border: 1px solid rgba(255, 193, 7, 0.35); color: #ffd54f; padding: 10px 14px; border-radius: 8px; font-size: 0.82rem; margin-top: 8px; }
</style>
