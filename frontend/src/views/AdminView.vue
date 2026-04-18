<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '@/api'

// ═══════════════════════════════════════════════════════════════════
//  SESSION #2 — Full Admin Panel (Users tab)
//  18 Apr 2026 — Edit/Create/Delete/Reset Password + Entity Assignment
// ═══════════════════════════════════════════════════════════════════

const activeTab = ref('users')

const tabs = [
  { id: 'users',      label: 'Χρήστες',    icon: '👥' },
  { id: 'categories', label: 'Κατηγορίες', icon: '📁' },
  { id: 'accounts',   label: 'Λογαριασμοί', icon: '📋' },
  { id: 'banks',      label: 'Τράπεζες',   icon: '🏦' },
  { id: 'audit',      label: 'Audit Log',  icon: '📜' },
]

// Current user (for self-detection)
const currentUser = ref(null)
const isAdmin = computed(() => currentUser.value?.role === 'admin')
const currentUsername = computed(() => currentUser.value?.username)

// ═══════════════════════════════════════════════════════════════════
//  USERS STATE
// ═══════════════════════════════════════════════════════════════════
const users = ref([])
const usersLoading = ref(false)
const usersError = ref(null)

const ROLE_LABELS = {
  admin:      'Διαχειριστής',
  user:       'Χρήστης',
  accountant: 'Λογιστής',
  viewer:     'Θεατής'
}

const ROLE_CLASSES = {
  admin:      'role-red',
  user:       'role-blue',
  accountant: 'role-orange',
  viewer:     'role-gray'
}

// Roles available in dropdowns (admin excluded — can only be set via SQL)
const ASSIGNABLE_ROLES = ['user', 'accountant', 'viewer']

// Roles that require entity assignment
const RESTRICTED_ROLES = ['accountant', 'viewer']

// Entities list (for assignment dropdowns)
const entities = ref([])

async function fetchCurrentUser() {
  try {
    const res = await api.get('/api/auth/me')
    // API returns user object directly (no success wrapper)
    if (res.data && res.data.username && res.data.role) {
      currentUser.value = res.data
    } else if (res.data && res.data.success && (res.data.data || res.data.user)) {
      // Fallback in case backend changes to wrapped format
      currentUser.value = res.data.data || res.data.user
    }
  } catch (e) {
    console.error('fetchCurrentUser error:', e)
  }
}

async function fetchUsers() {
  usersLoading.value = true
  usersError.value = null
  try {
    const res = await api.get('/api/admin/users')
    if (res.data.success) {
      users.value = res.data.data
    } else {
      usersError.value = res.data.error || 'Σφάλμα φόρτωσης'
    }
  } catch (e) {
    console.error('fetchUsers error:', e)
    if (e.response?.status === 403) {
      usersError.value = 'Δεν έχετε δικαίωμα πρόσβασης σε αυτή τη σελίδα'
    } else {
      usersError.value = e.response?.data?.error || 'Σφάλμα σύνδεσης'
    }
  } finally {
    usersLoading.value = false
  }
}

async function fetchEntities() {
  try {
    const res = await api.get('/api/config')
    if (res.data.success && res.data.entities) {
      entities.value = res.data.entities
      return
    }
  } catch (e) {
    // Silent fallback
  }
  // Hardcoded fallback (matches current database state)
  entities.value = [
    { id: '58202b71-4ddb-45c9-8e3c-39e816bde972', code: 'next2me', name: 'Next2Me' },
    { id: 'dea1f32c-7b30-4981-b625-633da9dbe71e', code: 'house',   name: 'House'   },
    { id: '50317f44-9961-4fb4-add0-7a118e32dc14', code: 'polaris', name: 'Polaris' }
  ]
}

function formatLastLogin(iso) {
  if (!iso) return 'Ποτέ'
  try {
    const d = new Date(iso)
    return d.toLocaleDateString('el-GR', { day: '2-digit', month: '2-digit', year: '2-digit' })
  } catch {
    return '—'
  }
}

function userInitial(u) {
  const name = u.displayName || u.username || '?'
  return name.charAt(0).toUpperCase()
}

function isSelf(u) {
  return currentUsername.value && currentUsername.value === u.username
}

// ═══════════════════════════════════════════════════════════════════
//  CREATE USER
// ═══════════════════════════════════════════════════════════════════
const newUser = ref({
  username: '',
  displayName: '',
  email: '',
  password: '',
  role: 'user'
})
const creating = ref(false)
const createError = ref(null)

async function createUser() {
  createError.value = null
  if (!newUser.value.username.trim()) {
    createError.value = 'Username υποχρεωτικό'
    return
  }
  if (!newUser.value.password || newUser.value.password.length < 8) {
    createError.value = 'Ο κωδικός πρέπει να είναι τουλάχιστον 8 χαρακτήρες'
    return
  }
  creating.value = true
  try {
    const res = await api.post('/api/admin/users', {
      username:    newUser.value.username.trim(),
      password:    newUser.value.password,
      displayName: newUser.value.displayName.trim() || newUser.value.username.trim(),
      email:       newUser.value.email.trim() || null,
      role:        newUser.value.role
    })
    if (res.data.success) {
      newUser.value = { username: '', displayName: '', email: '', password: '', role: 'user' }
      await fetchUsers()
      alert('Χρήστης δημιουργήθηκε επιτυχώς.\n\nΑν ο ρόλος απαιτεί αντιστοίχιση εταιρείας (Λογιστής/Θεατής), κάντε Επεξεργασία για να αντιστοιχίσετε εταιρείες.')
    } else {
      createError.value = res.data.error || 'Σφάλμα δημιουργίας'
    }
  } catch (e) {
    createError.value = e.response?.data?.error || 'Σφάλμα σύνδεσης'
  } finally {
    creating.value = false
  }
}

// ═══════════════════════════════════════════════════════════════════
//  EDIT USER MODAL
// ═══════════════════════════════════════════════════════════════════
const editModalOpen = ref(false)
const editUser = ref(null)
const editForm = ref({
  displayName: '',
  email: '',
  role: 'user',
  isActive: true
})
const editEntityIds = ref([])
const editSaving = ref(false)
const editError = ref(null)
const newPasswordField = ref('')

function openEditModal(u) {
  editUser.value = u
  editForm.value = {
    displayName: u.displayName || '',
    email:       u.email || '',
    role:        u.role,
    isActive:    u.isActive !== false
  }
  editEntityIds.value = []
  newPasswordField.value = ''
  editError.value = null

  if (RESTRICTED_ROLES.includes(u.role)) {
    fetchUserEntities(u.id)
  }
  editModalOpen.value = true
}

function closeEditModal() {
  editModalOpen.value = false
  editUser.value = null
  editError.value = null
  newPasswordField.value = ''
}

async function fetchUserEntities(userId) {
  try {
    const res = await api.get('/api/admin/users/' + userId + '/entities')
    if (res.data.success) {
      editEntityIds.value = (res.data.data || []).map(e => e.id)
    }
  } catch (e) {
    console.error('fetchUserEntities error:', e)
  }
}

function toggleEntity(entityId) {
  const idx = editEntityIds.value.indexOf(entityId)
  if (idx >= 0) {
    editEntityIds.value.splice(idx, 1)
  } else {
    editEntityIds.value.push(entityId)
  }
}

const needsEntities = computed(() => RESTRICTED_ROLES.includes(editForm.value.role))

async function saveEditUser() {
  editError.value = null

  if (needsEntities.value && editEntityIds.value.length === 0) {
    editError.value = 'Ο ρόλος "' + ROLE_LABELS[editForm.value.role] + '" απαιτεί τουλάχιστον μία εταιρεία.'
    return
  }

  editSaving.value = true
  try {
    if (RESTRICTED_ROLES.includes(editForm.value.role)) {
      await api.put('/api/admin/users/' + editUser.value.id + '/entities', {
        entityIds: editEntityIds.value
      })
    }

    const payload = {
      displayName: editForm.value.displayName || null,
      email:       editForm.value.email || null,
      role:        editForm.value.role,
      isActive:    editForm.value.isActive
    }
    const res = await api.put('/api/admin/users/' + editUser.value.id, payload)

    if (res.data.success) {
      if (!RESTRICTED_ROLES.includes(editForm.value.role)) {
        try {
          await api.put('/api/admin/users/' + editUser.value.id + '/entities', {
            entityIds: []
          })
        } catch {
          // Silent
        }
      }

      if (newPasswordField.value && newPasswordField.value.length > 0) {
        if (newPasswordField.value.length < 8) {
          editError.value = 'Χρήστης ενημερώθηκε ΑΛΛΑ ο κωδικός δεν άλλαξε (απαιτεί τουλάχιστον 8 χαρακτήρες)'
          await fetchUsers()
          editSaving.value = false
          return
        }
        await api.post('/api/admin/users/' + editUser.value.id + '/reset-password', {
          newPassword: newPasswordField.value
        })
      }

      await fetchUsers()
      closeEditModal()
      alert('Ο χρήστης ενημερώθηκε επιτυχώς')
    } else {
      editError.value = res.data.error || 'Σφάλμα ενημέρωσης'
    }
  } catch (e) {
    editError.value = e.response?.data?.error || 'Σφάλμα σύνδεσης'
  } finally {
    editSaving.value = false
  }
}

// ═══════════════════════════════════════════════════════════════════
//  DELETE USER
// ═══════════════════════════════════════════════════════════════════
async function deleteUser(u) {
  if (isSelf(u)) {
    alert('Δεν μπορείτε να διαγράψετε τον εαυτό σας')
    return
  }
  if (!confirm('Απενεργοποίηση χρήστη "' + (u.displayName || u.username) + '";\n\nΟ χρήστης δεν θα μπορεί να συνδεθεί αλλά τα δεδομένα του θα διατηρηθούν.')) {
    return
  }
  try {
    const res = await api.delete('/api/admin/users/' + u.id)
    if (res.data.success) {
      await fetchUsers()
      alert('Ο χρήστης απενεργοποιήθηκε')
    } else {
      alert('Σφάλμα: ' + (res.data.error || ''))
    }
  } catch (e) {
    alert('Σφάλμα: ' + (e.response?.data?.error || 'Σφάλμα σύνδεσης'))
  }
}

// ═══════════════════════════════════════════════════════════════════
//  SELF CHANGE PASSWORD
// ═══════════════════════════════════════════════════════════════════
const selfPasswordOpen = ref(false)
const selfOldPassword = ref('')
const selfNewPassword = ref('')
const selfPasswordError = ref(null)
const selfPasswordSaving = ref(false)

async function changeMyPassword() {
  selfPasswordError.value = null
  if (!selfOldPassword.value || !selfNewPassword.value) {
    selfPasswordError.value = 'Συμπληρώστε όλα τα πεδία'
    return
  }
  if (selfNewPassword.value.length < 8) {
    selfPasswordError.value = 'Ο νέος κωδικός πρέπει να έχει τουλάχιστον 8 χαρακτήρες'
    return
  }
  selfPasswordSaving.value = true
  try {
    const res = await api.post('/api/auth/change-password', {
      oldPassword: selfOldPassword.value,
      newPassword: selfNewPassword.value
    })
    if (res.data.success) {
      selfOldPassword.value = ''
      selfNewPassword.value = ''
      selfPasswordOpen.value = false
      alert('Ο κωδικός σας άλλαξε επιτυχώς')
    } else {
      selfPasswordError.value = res.data.error || 'Σφάλμα'
    }
  } catch (e) {
    selfPasswordError.value = e.response?.data?.error || 'Σφάλμα σύνδεσης'
  } finally {
    selfPasswordSaving.value = false
  }
}

// ═══════════════════════════════════════════════════════════════════
//  FILTERED USERS LIST (USER role sees only self)
// ═══════════════════════════════════════════════════════════════════
const visibleUsers = computed(() => {
  if (!currentUser.value) return []
  if (currentUser.value.role === 'admin') return users.value
  if (currentUser.value.role === 'user') {
    return users.value.filter(u => u.username === currentUser.value.username)
  }
  return []
})

// ═══════════════════════════════════════════════════════════════════
//  MOUNT
// ═══════════════════════════════════════════════════════════════════
onMounted(async () => {
  await fetchCurrentUser()
  await Promise.all([
    fetchUsers(),
    fetchEntities()
  ])
})
</script>

<template>
  <div class="admin-page">
    <h1 class="admin-title">Admin Panel</h1>

    <div class="tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-btn"
        :class="{ active: activeTab === tab.id }"
        :disabled="!isAdmin && tab.id !== 'users'"
        @click="activeTab = tab.id"
      >
        <span>{{ tab.icon }}</span> {{ tab.label }}
        <span v-if="tab.id === 'users'" class="tab-count">{{ visibleUsers.length }}</span>
      </button>
    </div>

    <div v-if="activeTab === 'users'" class="tab-content">

      <div v-if="currentUser && currentUser.role === 'user'" class="notice notice-info">
        Ως Χρήστης βλέπετε μόνο τα στοιχεία σας. Για αλλαγή κωδικού χρησιμοποιήστε το κουμπί "Αλλαγή Κωδικού Μου".
      </div>

      <div v-if="isAdmin" class="card">
        <h2>Νέος Χρήστης</h2>
        <div class="form-row">
          <input v-model="newUser.username" placeholder="Username *" class="input" />
          <input v-model="newUser.displayName" placeholder="Εμφανιζόμενο Όνομα" class="input" />
          <input v-model="newUser.email" placeholder="Email (προαιρετικό)" type="email" class="input" />
        </div>
        <div class="form-row">
          <input v-model="newUser.password" placeholder="Κωδικός (min 8 χαρακτήρες) *" type="password" class="input" />
          <select v-model="newUser.role" class="input">
            <option v-for="r in ASSIGNABLE_ROLES" :key="r" :value="r">{{ ROLE_LABELS[r] }}</option>
          </select>
          <button class="btn btn-primary" :disabled="creating" @click="createUser">
            {{ creating ? 'Δημιουργία...' : '+ Δημιουργία' }}
          </button>
        </div>
        <div v-if="createError" class="error">{{ createError }}</div>
        <p v-if="newUser.role === 'user'" class="warning">
          Ο ρόλος "Χρήστης" δίνει σχεδόν πλήρη πρόσβαση στο σύστημα (εκτός από διαχείριση άλλων χρηστών).
        </p>
        <p v-if="RESTRICTED_ROLES.includes(newUser.role)" class="notice">
          Μετά τη δημιουργία, κάντε κλικ στο κουμπί Επεξεργασία για να αντιστοιχίσετε εταιρεία/ες.
        </p>
      </div>

      <div class="card">
        <h2>Ο Λογαριασμός Μου</h2>
        <button v-if="!selfPasswordOpen" class="btn btn-secondary" @click="selfPasswordOpen = true">
          Αλλαγή Κωδικού Μου
        </button>
        <div v-else class="form-column">
          <input v-model="selfOldPassword" placeholder="Τρέχων Κωδικός" type="password" class="input" />
          <input v-model="selfNewPassword" placeholder="Νέος Κωδικός (min 8 χαρακτήρες)" type="password" class="input" />
          <div v-if="selfPasswordError" class="error">{{ selfPasswordError }}</div>
          <div class="form-row">
            <button class="btn btn-primary" :disabled="selfPasswordSaving" @click="changeMyPassword">
              {{ selfPasswordSaving ? 'Αποθήκευση...' : 'Αποθήκευση' }}
            </button>
            <button class="btn btn-secondary" @click="selfPasswordOpen = false; selfOldPassword=''; selfNewPassword=''; selfPasswordError=null">
              Ακύρωση
            </button>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <h2>Χρήστες Συστήματος</h2>
          <button class="btn btn-secondary btn-sm" @click="fetchUsers" :disabled="usersLoading">
            {{ usersLoading ? '...' : 'Ανανέωση' }}
          </button>
        </div>

        <div v-if="usersLoading" class="loading">Φόρτωση...</div>
        <div v-else-if="usersError" class="error">{{ usersError }}</div>
        <div v-else-if="!visibleUsers.length" class="empty">Κανένας χρήστης</div>
        <div v-else class="user-list">
          <div v-for="u in visibleUsers" :key="u.id" class="user-item">
            <div class="user-avatar">{{ userInitial(u) }}</div>
            <div class="user-info">
              <div class="user-name">
                <span class="active-dot" :class="{ on: u.isActive !== false, off: u.isActive === false }"></span>
                {{ u.displayName || u.username }}
                <span v-if="isSelf(u)" class="self-badge">(εσείς)</span>
              </div>
              <div class="user-meta">
                @{{ u.username }} · Τελευταίο login: {{ formatLastLogin(u.lastLogin) }}
              </div>
            </div>
            <span class="role-badge" :class="ROLE_CLASSES[u.role]">{{ ROLE_LABELS[u.role] || u.role }}</span>
            <div v-if="isAdmin" class="user-actions">
              <button class="btn-icon" title="Επεξεργασία" @click="openEditModal(u)">✏️</button>
              <button
                v-if="!isSelf(u) && u.isActive !== false"
                class="btn-icon danger"
                title="Απενεργοποίηση"
                @click="deleteUser(u)"
              >🗑️</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="!isAdmin" class="tab-content">
      <div class="notice notice-warning">
        Μόνο οι Διαχειριστές έχουν πρόσβαση σε αυτή τη σελίδα.
      </div>
    </div>

    <div v-else class="tab-content">
      <div class="notice notice-info">
        Η σελίδα <strong>{{ tabs.find(t => t.id === activeTab)?.label }}</strong> θα ενεργοποιηθεί σε επόμενο deploy (Session #3).
      </div>
    </div>

    <div v-if="editModalOpen" class="modal-overlay" @click.self="closeEditModal">
      <div class="modal-card">
        <button class="modal-close" @click="closeEditModal">×</button>
        <h2>Επεξεργασία Χρήστη</h2>

        <div class="form-group">
          <label>Username</label>
          <input :value="editUser?.username" disabled class="input input-disabled" />
        </div>

        <div class="form-group">
          <label>Εμφανιζόμενο Όνομα</label>
          <input v-model="editForm.displayName" class="input" />
        </div>

        <div class="form-group">
          <label>Email</label>
          <input v-model="editForm.email" type="email" class="input" />
        </div>

        <div v-if="!isSelf(editUser)" class="form-group">
          <label>Νέος Κωδικός <small>(κενό = χωρίς αλλαγή, min 8 χαρακτήρες)</small></label>
          <input v-model="newPasswordField" type="password" placeholder="Αφήστε κενό αν δεν αλλάζει" class="input" />
          <small class="help-text">Admin reset - δεν απαιτείται ο τρέχων κωδικός του χρήστη</small>
        </div>
        <div v-else class="form-group notice-box">
          <strong>Αλλαγή Δικού Σας Κωδικού:</strong>
          Κλείστε αυτό το παράθυρο και χρησιμοποιήστε το κουμπί "Αλλαγή Κωδικού Μου" πάνω (απαιτεί τρέχοντα κωδικό για ασφάλεια).
        </div>

        <div class="form-group" v-if="!isSelf(editUser)">
          <label>Ρόλος</label>
          <select v-model="editForm.role" class="input">
            <option v-for="r in ASSIGNABLE_ROLES" :key="r" :value="r">{{ ROLE_LABELS[r] }}</option>
            <option v-if="editUser?.role === 'admin'" value="admin">Διαχειριστής</option>
          </select>
        </div>
        <div v-else class="form-group">
          <label>Ρόλος</label>
          <input :value="ROLE_LABELS[editUser?.role]" disabled class="input input-disabled" />
          <small>Δεν μπορείτε να αλλάξετε τον δικό σας ρόλο</small>
        </div>

        <div v-if="needsEntities" class="form-group">
          <label>Εταιρείες που βλέπει *</label>
          <div class="entity-checkboxes">
            <label v-for="e in entities" :key="e.id" class="checkbox-label">
              <input
                type="checkbox"
                :checked="editEntityIds.includes(e.id)"
                @change="toggleEntity(e.id)"
              />
              {{ e.name }}
            </label>
          </div>
          <small v-if="editEntityIds.length === 0" class="error">
            Υποχρεωτικό: επιλέξτε τουλάχιστον μία εταιρεία
          </small>
        </div>

        <div class="form-group" v-if="!isSelf(editUser)">
          <label>Κατάσταση</label>
          <select v-model="editForm.isActive" class="input">
            <option :value="true">Ενεργός</option>
            <option :value="false">Ανενεργός</option>
          </select>
        </div>

        <div v-if="editForm.role === 'user'" class="warning">
          Ο ρόλος "Χρήστης" δίνει σχεδόν πλήρη πρόσβαση στο σύστημα.
        </div>

        <div v-if="editError" class="error">{{ editError }}</div>

        <div class="modal-actions">
          <button class="btn btn-secondary" @click="closeEditModal">Ακύρωση</button>
          <button class="btn btn-primary" :disabled="editSaving" @click="saveEditUser">
            {{ editSaving ? 'Αποθήκευση...' : 'Αποθήκευση' }}
          </button>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
.admin-page { padding: 24px; max-width: 1400px; margin: 0 auto; }
.admin-title { font-size: 1.6rem; font-weight: 700; margin-bottom: 24px; color: var(--text-primary, #e5e7eb); }
.tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--border, #374151); margin-bottom: 24px; overflow-x: auto; }
.tab-btn { padding: 10px 18px; background: transparent; border: none; color: var(--text-muted, #9ca3af); cursor: pointer; font-size: 0.9rem; border-bottom: 2px solid transparent; transition: all 0.15s; white-space: nowrap; }
.tab-btn:hover:not(:disabled) { color: var(--text-primary, #e5e7eb); }
.tab-btn.active { color: var(--accent, #3b82f6); border-bottom-color: var(--accent, #3b82f6); }
.tab-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.tab-count { margin-left: 6px; padding: 2px 8px; background: var(--bg-secondary, #374151); border-radius: 999px; font-size: 0.75rem; }
.tab-content { min-height: 400px; }
.card { background: var(--bg-card, #1f2937); border: 1px solid var(--border, #374151); border-radius: 8px; padding: 20px; margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.card h2 { font-size: 1.05rem; font-weight: 600; color: var(--accent, #3b82f6); margin-bottom: 16px; }
.form-row { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.form-row .input { flex: 1; min-width: 180px; }
.form-column { display: flex; flex-direction: column; gap: 12px; max-width: 400px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; font-size: 0.82rem; color: var(--text-muted, #9ca3af); margin-bottom: 6px; }
.form-group label small { font-weight: normal; opacity: 0.7; }
.input { padding: 10px 14px; background: var(--bg-input, #111827); border: 1px solid var(--border, #374151); border-radius: 6px; color: var(--text-primary, #e5e7eb); font-size: 0.9rem; width: 100%; box-sizing: border-box; }
.input:focus { outline: none; border-color: var(--accent, #3b82f6); }
.input-disabled { opacity: 0.5; cursor: not-allowed; }
.btn { padding: 10px 18px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; font-weight: 500; transition: opacity 0.15s; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-primary { background: var(--accent, #3b82f6); color: white; }
.btn-primary:hover:not(:disabled) { background: var(--accent-hover, #2563eb); }
.btn-secondary { background: var(--bg-secondary, #374151); color: var(--text-primary, #e5e7eb); }
.btn-sm { padding: 6px 12px; font-size: 0.8rem; }
.btn-icon { padding: 6px 8px; background: transparent; border: 1px solid var(--border, #374151); border-radius: 4px; cursor: pointer; font-size: 0.9rem; color: var(--text-primary, #e5e7eb); }
.btn-icon:hover { background: var(--bg-secondary, #374151); }
.btn-icon.danger:hover { background: rgba(239, 68, 68, 0.15); border-color: #ef4444; }
.user-list { display: flex; flex-direction: column; gap: 8px; }
.user-item { display: flex; align-items: center; gap: 14px; padding: 12px; background: var(--bg-input, #111827); border-radius: 6px; border: 1px solid var(--border, #374151); }
.user-avatar { width: 38px; height: 38px; border-radius: 50%; background: var(--accent, #3b82f6); color: white; display: flex; align-items: center; justify-content: center; font-weight: 700; flex-shrink: 0; }
.user-info { flex: 1; min-width: 0; }
.user-name { font-weight: 600; color: var(--text-primary, #e5e7eb); display: flex; align-items: center; gap: 8px; }
.user-meta { font-size: 0.78rem; color: var(--text-muted, #9ca3af); margin-top: 2px; }
.user-actions { display: flex; gap: 6px; }
.self-badge { font-size: 0.72rem; color: var(--accent, #3b82f6); font-weight: normal; font-style: italic; }
.active-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.active-dot.on { background: #10b981; }
.active-dot.off { background: #ef4444; }
.role-badge { padding: 4px 10px; border-radius: 999px; font-size: 0.72rem; font-weight: 600; text-transform: uppercase; }
.role-red    { background: rgba(239, 68, 68, 0.2);  color: #f87171; }
.role-blue   { background: rgba(59, 130, 246, 0.2); color: #60a5fa; }
.role-orange { background: rgba(251, 146, 60, 0.2); color: #fb923c; }
.role-gray   { background: rgba(156, 163, 175, 0.2); color: #d1d5db; }
.loading, .empty { padding: 16px; text-align: center; color: var(--text-muted, #9ca3af); font-size: 0.9rem; }
.error { padding: 12px; color: #f87171; background: rgba(239, 68, 68, 0.1); border-radius: 6px; font-size: 0.85rem; margin: 8px 0; }
.warning { padding: 12px; color: #fbbf24; background: rgba(251, 191, 36, 0.1); border-radius: 6px; text-align: left; font-size: 0.85rem; margin: 8px 0; }
.notice { padding: 12px 16px; border-radius: 6px; font-size: 0.88rem; margin-bottom: 16px; }
.notice-info { background: rgba(59, 130, 246, 0.1); border: 1px solid rgba(59, 130, 246, 0.3); color: #93c5fd; }
.notice-warning { background: rgba(251, 191, 36, 0.1); border: 1px solid rgba(251, 191, 36, 0.3); color: #fde68a; }
.help-text { display: block; margin-top: 4px; font-size: 0.78rem; color: var(--text-muted, #9ca3af); font-style: italic; }
.notice-box { padding: 12px; background: rgba(59, 130, 246, 0.1); border: 1px solid rgba(59, 130, 246, 0.3); color: #93c5fd; border-radius: 6px; font-size: 0.85rem; line-height: 1.5; }
.notice-box strong { display: block; margin-bottom: 6px; color: #bfdbfe; }
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0, 0, 0, 0.7); display: flex; justify-content: center; align-items: center; z-index: 1000; padding: 20px; }
.modal-card { background: var(--bg-card, #1f2937); border: 1px solid var(--border, #374151); border-radius: 10px; padding: 28px; max-width: 500px; width: 100%; position: relative; max-height: 90vh; overflow-y: auto; }
.modal-close { position: absolute; top: 14px; right: 14px; background: transparent; border: none; color: var(--text-muted, #9ca3af); font-size: 1.5rem; cursor: pointer; line-height: 1; }
.modal-actions { display: flex; gap: 12px; justify-content: flex-end; margin-top: 20px; }
.entity-checkboxes { display: flex; flex-direction: column; gap: 10px; padding: 12px; background: var(--bg-input, #111827); border-radius: 6px; border: 1px solid var(--border, #374151); }
.checkbox-label { display: flex; align-items: center; gap: 10px; cursor: pointer; font-size: 0.9rem; color: var(--text-primary, #e5e7eb); }
.checkbox-label input[type="checkbox"] { width: 16px; height: 16px; cursor: pointer; }
</style>
