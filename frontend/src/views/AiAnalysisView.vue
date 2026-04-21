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
  { label: 'Υποχρεώσεις', icon: '📅' },
  { label: 'Τάσεις',      icon: '📈' },
  { label: 'Κόστος',      icon: '⚙' },
  { label: 'Cash Flow',   icon: '💰' },
  { label: 'Board',       icon: '👥' },
  { label: 'Investor',    icon: '🎯' },
  { label: 'Λογιστής',    icon: '📋' },
]

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
      entityScope: 'all',      // default: both entities
      dateRange: 'ytd'          // default: year-to-date
    })

    if (res.data && res.data.success) {
      const d = res.data
      // Format cost για εμφάνιση
      const costEur = d.costEur != null ? Number(d.costEur).toFixed(4) : '—'
      const rows = d.rowsAnalyzed != null ? d.rowsAnalyzed : 0
      chatHistory.value.push({
        role: 'ai',
        text: d.answer,
        meta: `Κόστος: €${costEur} · ${rows} εγγραφές · ${d.tier}`
      })
    } else {
      chatHistory.value.push({
        role: 'ai',
        text: `⚠ Σφάλμα: ${res.data?.error || 'unknown'}`
      })
    }
  } catch (err) {
    console.error('AI error:', err)
    const status = err.response?.status
    const errCode = err.response?.data?.error
    const errMsg = err.response?.data?.message || err.message
    let msg = 'Σφάλμα σύνδεσης'
    if (status === 403) msg = 'Μόνο ADMIN μπορούν να χρησιμοποιήσουν το AI'
    else if (status === 503) msg = `AI μη διαθέσιμο: ${errMsg}`
    else if (errCode) msg = `${errCode}: ${errMsg}`
    chatHistory.value.push({ role: 'ai', text: `⚠ ${msg}` })
  } finally {
    isLoading.value = false
  }
}

const selectTab = (tab) => {
  selectedAnalysis.value = tab.label === 'Investor' ? 'Investor Report' :
    tab.label === 'Πλήρης' ? 'Πλήρης Ανάλυση' :
    tab.label === 'Executive' ? 'Executive Summary' :
    tab.label === 'Υποχρεώσεις' ? 'Ανάλυση Υποχρεώσεων' :
    tab.label === 'Τάσεις' ? 'Τάσεις & Προβλέψεις' :
    tab.label === 'Κόστος' ? 'Βελτιστοποίηση Κόστους' :
    tab.label === 'Cash Flow' ? 'Cash Flow Πρόβλεψη' :
    tab.label === 'Board' ? 'Board Meeting Brief' :
    tab.label === 'Λογιστής' ? 'Λογιστική Ανάλυση' : tab.label
}

const runAnalysis = () => {
  userQuestion.value = `Εκτέλεσε: ${selectedAnalysis.value}`
  sendQuestion()
}
</script>

<template>
  <div class="ai-page">

    <!-- ── Main Card ── -->
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
          <button class="btn-run" @click="runAnalysis">
            ✏ Ανάλυση
          </button>
        </div>
      </div>

      <!-- Quick Tabs -->
      <div class="quick-tabs">
        <button
          v-for="tab in quickTabs"
          :key="tab.label"
          class="quick-tab"
          @click="selectTab(tab)"
        >
          {{ tab.icon }} {{ tab.label }}
        </button>
      </div>

      <!-- Chat History -->
      <div class="chat-area" v-if="chatHistory.length > 0">
        <div
          v-for="(msg, i) in chatHistory"
          :key="i"
          class="chat-msg"
          :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
        >
          <div class="msg-icon">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="msg-bubble">
            <div class="msg-text" style="white-space: pre-line">{{ msg.text }}</div>
          </div>
        </div>
        <div v-if="isLoading" class="chat-msg msg-ai">
          <div class="msg-icon">🤖</div>
          <div class="msg-bubble">
            <div class="loading-dots">
              <span></span><span></span><span></span>
            </div>
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
          @keydown.enter="sendQuestion"
        />
        <button class="btn-send" @click="sendQuestion" :disabled="isLoading">
          ➤
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.ai-page {
  padding: 20px 24px;
  background: #0d1e2e;
  min-height: 100vh;
  color: #c8d8e8;
}

.ai-card {
  background: #1a2f45;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 80px);
}

/* Header */
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.ai-header-left { display: flex; align-items: center; gap: 14px; }
.ai-avatar { font-size: 2rem; background: rgba(79,195,161,0.1); border-radius: 12px; width: 52px; height: 52px; display: flex; align-items: center; justify-content: center; }
.ai-title { font-size: 1rem; font-weight: 700; color: #e0e6ed; }
.ai-subtitle { font-size: 0.75rem; color: #8899aa; margin-top: 2px; }
.ai-header-right { display: flex; align-items: center; gap: 10px; }

.select-wrap { position: relative; }
.analysis-select {
  appearance: none;
  background: #152538;
  border: 1px solid #2a4a6a;
  color: #c8d8e8;
  padding: 8px 36px 8px 12px;
  border-radius: 8px;
  font-size: 0.85rem;
  cursor: pointer;
  outline: none;
  min-width: 200px;
}

.btn-run {
  background: #4FC3A1;
  border: none;
  color: #0d1e2e;
  padding: 8px 20px;
  border-radius: 8px;
  font-size: 0.85rem;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}
.btn-run:hover { background: #3dab8a; }

/* Quick Tabs */
.quick-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 12px 0;
  border-top: 1px solid #223d57;
  border-bottom: 1px solid #223d57;
}
.quick-tab {
  background: #152538;
  border: 1px solid #2a4a6a;
  color: #8899aa;
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 0.82rem;
  cursor: pointer;
  transition: all 0.15s;
}
.quick-tab:hover {
  background: #1e3a52;
  color: #4FC3A1;
  border-color: #4FC3A1;
}

/* Chat Area */
.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding: 8px 0;
  min-height: 300px;
}
.chat-msg {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.msg-user { flex-direction: row-reverse; }
.msg-icon { font-size: 1.4rem; flex-shrink: 0; }
.msg-bubble {
  background: #152538;
  border-radius: 10px;
  padding: 12px 16px;
  max-width: 80%;
  font-size: 0.85rem;
  line-height: 1.6;
  color: #c8d8e8;
}
.msg-user .msg-bubble {
  background: rgba(79,195,161,0.12);
  border: 1px solid rgba(79,195,161,0.2);
  color: #e0e6ed;
}
.msg-text { white-space: pre-line; }

/* Loading dots */
.loading-dots { display: flex; gap: 4px; align-items: center; padding: 4px 0; }
.loading-dots span {
  width: 8px; height: 8px; border-radius: 50%;
  background: #4FC3A1; animation: bounce 1.2s infinite;
}
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* Empty State */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 0;
  color: #8899aa;
}
.empty-icon { font-size: 3rem; }
.empty-title { font-size: 1rem; font-weight: 600; color: #c8d8e8; }
.empty-hint { font-size: 0.82rem; color: #8899aa; }

/* Input */
.chat-input-area {
  display: flex;
  gap: 10px;
  align-items: center;
  border-top: 1px solid #223d57;
  padding-top: 16px;
}
.chat-input {
  flex: 1;
  background: #152538;
  border: 1px solid #2a4a6a;
  color: #c8d8e8;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 0.85rem;
  outline: none;
}
.chat-input:focus { border-color: #4FC3A1; }
.chat-input::placeholder { color: #4a6a88; }
.btn-send {
  background: #4FC3A1;
  border: none;
  color: #0d1e2e;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  font-size: 1.1rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.btn-send:hover { background: #3dab8a; }
.btn-send:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
