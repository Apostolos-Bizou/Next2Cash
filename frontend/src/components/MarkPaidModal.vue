<script setup>
import { ref, watch, computed } from 'vue'
import api from '@/api'

// -----------------------------------------------------------------
// MarkPaidModal — create a new payment against a parent transaction
//
// Props:
//   visible      : boolean — controls modal display
//   transaction  : object  — the parent transaction (must have id, amount,
//                            amountPaid, amountRemaining, type, description,
//                            counterparty, entityId, entityNumber)
//
// Emits:
//   close        — user cancelled / backdrop click
//   saved        — payment persisted; payload = { payment, transaction }
// -----------------------------------------------------------------

const props = defineProps({
  visible:     { type: Boolean, default: false },
  transaction: { type: Object,  default: null }
})
const emit = defineEmits(['close', 'saved'])

// --- Form state ---------------------------------------------------
const amount        = ref('0')
const paymentDate   = ref('')
const paymentMethod = ref('Τράπεζα')
const notes         = ref('')
const saving        = ref(false)
const errorMsg      = ref('')

const paymentMethods = [
  'Τράπεζα', 'Μετρητά', 'Κάρτα', 'Επιταγή', 'PayPal', 'Άλλο'
]

// --- Derived values from parent transaction ----------------------
const totalAmount = computed(() => Number(props.transaction?.amount) || 0)
const alreadyPaid = computed(() => Number(props.transaction?.amountPaid) || 0)
const remaining   = computed(() => Number(props.transaction?.amountRemaining) || 0)

const amountNum = computed(() => {
  const n = Number(String(amount.value).replace(',', '.'))
  return Number.isFinite(n) ? n : 0
})

const newPaidTotal     = computed(() => alreadyPaid.value + amountNum.value)
const newRemaining     = computed(() => totalAmount.value - newPaidTotal.value)
const isOverpay        = computed(() => amountNum.value > remaining.value + 0.01)
const willBeFullyPaid  = computed(() => newRemaining.value <= 0.01 && amountNum.value > 0)
const willBePartial    = computed(() => !willBeFullyPaid.value && amountNum.value > 0 && amountNum.value < remaining.value)

const isIncome = computed(() => props.transaction?.type === 'income')

const statusLabel = computed(() => {
  if (amountNum.value <= 0) return ''
  if (willBeFullyPaid.value) return isIncome.value ? 'ΕΙΣΠΡΑΧΘΗΚΕ' : 'ΕΞΟΦΛΗΜΕΝΗ'
  if (willBePartial.value)   return 'ΜΕΡΙΚΗ'
  return ''
})

const statusColor = computed(() => {
  if (willBeFullyPaid.value) return isIncome.value ? '#4FC3A1' : '#6BCB77'
  if (willBePartial.value)   return '#FFD93D'
  return '#9aa5b1'
})

const canSubmit = computed(() =>
  !saving.value && amountNum.value > 0 && paymentDate.value
)

// --- Reset form when opening --------------------------------------
watch(() => [props.visible, props.transaction], () => {
  errorMsg.value = ''
  saving.value = false
  if (!props.visible || !props.transaction) return

  // Default amount = remaining (full payment in one click)
  amount.value = remaining.value > 0 ? String(remaining.value.toFixed(2)) : '0'
  // Default date = today (YYYY-MM-DD)
  paymentDate.value = new Date().toISOString().slice(0, 10)
  // Carry over parent's payment method as suggestion
  paymentMethod.value = props.transaction.paymentMethod || 'Τράπεζα'
  notes.value = ''
}, { immediate: true })

// --- Formatters ---------------------------------------------------
function fmtMoney(v) {
  const n = Number(v) || 0
  return n.toLocaleString('el-GR', {
    style: 'currency', currency: 'EUR',
    minimumFractionDigits: 2, maximumFractionDigits: 2
  })
}

// --- Close handlers -----------------------------------------------
function onCancel() {
  if (saving.value) return
  emit('close')
}

function onBackdrop() {
  if (saving.value) return
  emit('close')
}

// --- Submit -------------------------------------------------------
async function onSubmit() {
  if (!canSubmit.value) return
  errorMsg.value = ''

  if (amountNum.value <= 0) {
    errorMsg.value = 'Το ποσό πληρωμής πρέπει να είναι μεγαλύτερο από 0.'
    return
  }
  if (isOverpay.value) {
    // Allow but require a second click (user already sees the warning)
    if (!confirm(`Το ποσό υπερβαίνει το υπόλοιπο κατά ${fmtMoney(amountNum.value - remaining.value)}. Να συνεχίσω;`)) {
      return
    }
  }

  saving.value = true
  try {
    const payload = {
      transactionId:  props.transaction.id,
      entityId:       props.transaction.entityId,
      amount:         amountNum.value,
      paymentDate:    paymentDate.value,
      paymentMethod:  paymentMethod.value,
      description:    props.transaction.description || '',
      notes:          notes.value.trim() || null
    }
    const res = await api.post('/api/payments', payload)
    if (res.data && res.data.success) {
      emit('saved', {
        payment:     res.data.payment,
        transaction: res.data.transaction
      })
    } else {
      errorMsg.value = (res.data && res.data.error) || 'Αποτυχία αποθήκευσης πληρωμής.'
    }
  } catch (e) {
    console.error('MarkPaid submit error:', e)
    errorMsg.value = e.response?.data?.error || e.message || 'Σφάλμα σύνδεσης.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-if="visible && transaction" class="modal-backdrop" @click.self="onBackdrop">
    <div class="mp-dialog">

      <!-- Header -->
      <div class="mp-header">
        <div class="mp-title">
          <span class="mp-icon">✓</span>
          <h3>Εξόφληση Συναλλαγής</h3>
        </div>
        <button class="mp-close" @click="onCancel" :disabled="saving">×</button>
      </div>

      <!-- Transaction context -->
      <div class="mp-context">
        <div class="mp-ctx-row">
          <span class="mp-ctx-label">Αρ.</span>
          <span class="mp-ctx-value mp-ctx-num">#{{ transaction.entityNumber || transaction.id }}</span>
        </div>
        <div class="mp-ctx-row" v-if="transaction.counterparty">
          <span class="mp-ctx-label">Αντισυμβαλλόμενος</span>
          <span class="mp-ctx-value">{{ transaction.counterparty }}</span>
        </div>
        <div class="mp-ctx-row" v-if="transaction.description">
          <span class="mp-ctx-label">Περιγραφή</span>
          <span class="mp-ctx-value mp-ctx-desc">{{ transaction.description }}</span>
        </div>
      </div>

      <!-- KPI tiles: total / paid / remaining -->
      <div class="mp-kpis">
        <div class="mp-kpi">
          <div class="mp-kpi-label">ΠΟΣΟ</div>
          <div class="mp-kpi-value">{{ fmtMoney(totalAmount) }}</div>
        </div>
        <div class="mp-kpi">
          <div class="mp-kpi-label">ΠΛΗΡΩΘΗΚΕ ΗΔΗ</div>
          <div class="mp-kpi-value mp-kpi-paid">{{ fmtMoney(alreadyPaid) }}</div>
        </div>
        <div class="mp-kpi">
          <div class="mp-kpi-label">ΥΠΟΛΟΙΠΟ</div>
          <div class="mp-kpi-value mp-kpi-remaining">{{ fmtMoney(remaining) }}</div>
        </div>
      </div>

      <!-- Form -->
      <div class="mp-form">
        <div class="mp-field">
          <label>Ποσό πληρωμής <span class="mp-req">*</span></label>
          <input
            type="number"
            step="0.01"
            min="0"
            v-model="amount"
            :disabled="saving"
            class="mp-input mp-input-amount"
            autofocus />
          <div v-if="isOverpay" class="mp-warn">
            ⚠ Το ποσό υπερβαίνει το υπόλοιπο κατά
            <strong>{{ fmtMoney(amountNum - remaining) }}</strong>.
          </div>
        </div>

        <div class="mp-row-2">
          <div class="mp-field">
            <label>Ημερομηνία <span class="mp-req">*</span></label>
            <input
              type="date"
              v-model="paymentDate"
              :disabled="saving"
              class="mp-input" />
          </div>
          <div class="mp-field">
            <label>Μέθοδος</label>
            <select v-model="paymentMethod" :disabled="saving" class="mp-input">
              <option v-for="m in paymentMethods" :key="m" :value="m">{{ m }}</option>
            </select>
          </div>
        </div>

        <div class="mp-field">
          <label>Σημειώσεις (προαιρετικά)</label>
          <textarea
            v-model="notes"
            :disabled="saving"
            rows="2"
            placeholder="π.χ. αριθμός εντολής, reference..."
            class="mp-input mp-textarea"></textarea>
        </div>
      </div>

      <!-- Status preview -->
      <div v-if="statusLabel" class="mp-preview" :style="{ borderLeftColor: statusColor }">
        <span class="mp-preview-label">Μετά την πληρωμή η συναλλαγή θα σημειωθεί ως:</span>
        <span class="mp-preview-status" :style="{ color: statusColor }">{{ statusLabel }}</span>
      </div>

      <!-- Error -->
      <div v-if="errorMsg" class="mp-error">⚠ {{ errorMsg }}</div>

      <!-- Footer buttons -->
      <div class="mp-footer">
        <button class="mp-btn-cancel" @click="onCancel" :disabled="saving">
          Ακύρωση
        </button>
        <button class="mp-btn-confirm" @click="onSubmit" :disabled="!canSubmit">
          <span v-if="saving">Αποθήκευση...</span>
          <span v-else>✓ Επιβεβαίωση</span>
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* Backdrop */
.modal-backdrop {
  position: fixed; inset: 0;
  background: rgba(10, 15, 25, 0.75);
  backdrop-filter: blur(3px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
  padding: 20px;
}

/* Dialog shell */
.mp-dialog {
  background: #1a2332;
  border: 1px solid #2c3e50;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
  width: 100%;
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
  color: #e0e6ed;
}

/* Header */
.mp-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #2c3e50;
}
.mp-title { display: flex; align-items: center; gap: 12px; }
.mp-title h3 { margin: 0; font-size: 1.1rem; font-weight: 600; }
.mp-icon {
  display: inline-flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; border-radius: 50%;
  background: #4FC3A1; color: #0d1f2d;
  font-weight: 700; font-size: 1.1rem;
}
.mp-close {
  background: transparent; border: none; color: #9aa5b1;
  font-size: 1.6rem; cursor: pointer; padding: 0 4px; line-height: 1;
  border-radius: 4px;
}
.mp-close:hover:not(:disabled) { color: #fff; background: #2c3e50; }
.mp-close:disabled { opacity: 0.4; cursor: not-allowed; }

/* Transaction context */
.mp-context {
  padding: 14px 24px;
  border-bottom: 1px solid #2c3e50;
  background: #111a25;
}
.mp-ctx-row {
  display: flex; gap: 12px; padding: 3px 0;
  font-size: 0.85rem;
}
.mp-ctx-label {
  color: #6c7a8a; min-width: 130px;
  text-transform: uppercase; letter-spacing: 0.5px; font-size: 0.72rem;
  align-self: center;
}
.mp-ctx-value { color: #e0e6ed; flex: 1; }
.mp-ctx-num { font-weight: 600; color: #4FC3A1; }
.mp-ctx-desc {
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* KPI tiles */
.mp-kpis {
  display: grid; grid-template-columns: 1fr 1fr 1fr;
  gap: 10px; padding: 18px 24px;
  border-bottom: 1px solid #2c3e50;
}
.mp-kpi {
  background: #0f1724;
  border: 1px solid #2c3e50;
  border-radius: 8px;
  padding: 12px 10px;
  text-align: center;
}
.mp-kpi-label {
  font-size: 0.68rem; color: #6c7a8a;
  text-transform: uppercase; letter-spacing: 0.5px;
  margin-bottom: 6px;
}
.mp-kpi-value { font-size: 1rem; font-weight: 600; color: #e0e6ed; }
.mp-kpi-paid     { color: #6BCB77; }
.mp-kpi-remaining { color: #FF6B6B; }

/* Form */
.mp-form { padding: 20px 24px 6px; }
.mp-field { margin-bottom: 14px; }
.mp-field label {
  display: block;
  font-size: 0.78rem; color: #9aa5b1;
  margin-bottom: 5px; font-weight: 500;
}
.mp-req { color: #FF6B6B; }
.mp-input {
  width: 100%; box-sizing: border-box;
  background: #0f1724;
  border: 1px solid #2c3e50;
  border-radius: 6px;
  padding: 9px 12px;
  color: #e0e6ed;
  font-size: 0.95rem;
  font-family: inherit;
  transition: border-color 0.15s;
}
.mp-input:focus {
  outline: none; border-color: #4FC3A1;
}
.mp-input:disabled { opacity: 0.6; cursor: not-allowed; }
.mp-input-amount { font-size: 1.15rem; font-weight: 600; text-align: right; }
.mp-textarea { resize: vertical; font-family: inherit; }
.mp-row-2 {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px;
}

.mp-warn {
  margin-top: 6px; padding: 7px 10px;
  background: rgba(255, 193, 7, 0.1);
  border: 1px solid rgba(255, 193, 7, 0.3);
  border-radius: 5px;
  color: #FFC107; font-size: 0.82rem;
}

/* Preview */
.mp-preview {
  margin: 8px 24px 12px;
  padding: 10px 14px;
  background: #111a25;
  border-left: 4px solid #4FC3A1;
  border-radius: 4px;
  display: flex; justify-content: space-between; align-items: center;
  gap: 12px; font-size: 0.85rem;
}
.mp-preview-label { color: #9aa5b1; }
.mp-preview-status {
  font-weight: 700; letter-spacing: 0.5px; font-size: 0.9rem;
}

/* Error */
.mp-error {
  margin: 0 24px 12px;
  padding: 9px 12px;
  background: rgba(255, 107, 107, 0.1);
  border: 1px solid rgba(255, 107, 107, 0.3);
  border-radius: 5px;
  color: #FF6B6B; font-size: 0.85rem;
}

/* Footer */
.mp-footer {
  display: flex; justify-content: flex-end; gap: 10px;
  padding: 14px 24px 20px;
  border-top: 1px solid #2c3e50;
}
.mp-btn-cancel, .mp-btn-confirm {
  padding: 9px 22px; border: none; border-radius: 6px;
  font-size: 0.9rem; font-weight: 600; cursor: pointer;
  transition: background 0.15s, transform 0.05s;
}
.mp-btn-cancel {
  background: transparent; color: #9aa5b1;
  border: 1px solid #2c3e50;
}
.mp-btn-cancel:hover:not(:disabled) { background: #2c3e50; color: #fff; }
.mp-btn-confirm {
  background: #4FC3A1; color: #0d1f2d;
}
.mp-btn-confirm:hover:not(:disabled) { background: #5fd4b3; }
.mp-btn-confirm:active:not(:disabled) { transform: translateY(1px); }
.mp-btn-confirm:disabled, .mp-btn-cancel:disabled {
  opacity: 0.5; cursor: not-allowed;
}
</style>
