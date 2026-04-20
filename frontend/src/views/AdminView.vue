<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import api from '@/api'

// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  SESSION #2 β€” Full Admin Panel (Users tab)
//  18 Apr 2026 β€” Edit/Create/Delete/Reset Password + Entity Assignment
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•

const activeTab = ref('users')

const tabs = [
  { id: 'users',      label: 'Ξ§ΟΞ®ΟƒΟ„ΞµΟ‚',    icon: 'π‘¥' },
  { id: 'categories', label: 'ΞΞ±Ο„Ξ·Ξ³ΞΏΟΞ―ΞµΟ‚', icon: 'π“' },
  { id: 'accounts',   label: 'Ξ›ΞΏΞ³Ξ±ΟΞΉΞ±ΟƒΞΌΞΏΞ―', icon: 'π“‹' },
  { id: 'banks',      label: 'Ξ¤ΟΞ¬Ο€ΞµΞ¶ΞµΟ‚',   icon: 'π¦' },
  { id: 'audit',      label: 'Audit Log',  icon: 'π“' },
]

// Current user (for self-detection)
const currentUser = ref(null)
const isAdmin = computed(() => currentUser.value?.role === 'admin')
const currentUsername = computed(() => currentUser.value?.username)

// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  USERS STATE
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
const users = ref([])
const usersLoading = ref(false)
const usersError = ref(null)

const ROLE_LABELS = {
  admin:      'Ξ”ΞΉΞ±Ο‡ΞµΞΉΟΞΉΟƒΟ„Ξ®Ο‚',
  user:       'Ξ§ΟΞ®ΟƒΟ„Ξ·Ο‚',
  accountant: 'Ξ›ΞΏΞ³ΞΉΟƒΟ„Ξ®Ο‚',
  viewer:     'ΞΞµΞ±Ο„Ξ®Ο‚'
}

const ROLE_CLASSES = {
  admin:      'role-red',
  user:       'role-blue',
  accountant: 'role-orange',
  viewer:     'role-gray'
}

// Roles available in dropdowns (admin excluded β€” can only be set via SQL)
const ASSIGNABLE_ROLES = ['user', 'accountant', 'viewer']

// Roles that require entity assignment
const RESTRICTED_ROLES = ['accountant', 'viewer']

// M.6: All available sections for checkbox selection
const ALL_SECTIONS = [
  { key: 'dashboard',      label: 'Ξ Ξ―Ξ½Ξ±ΞΊΞ±Ο‚ Ξ•Ξ»Ξ­Ξ³Ο‡ΞΏΟ…' },
  { key: 'new-entry',      label: 'ΞΞ­Ξ± ΞΞ±Ο„Ξ±Ο‡ΟΟΞΉΟƒΞ·' },
  { key: 'transactions',   label: 'ΞΞΉΞ½Ξ®ΟƒΞµΞΉΟ‚' },
  { key: 'payments',       label: 'Ξ Ξ»Ξ·ΟΟ‰ΞΌΞ­Ο‚' },
  { key: 'obligations',    label: 'Ξ¥Ο€ΞΏΟ‡ΟΞµΟΟƒΞµΞΉΟ‚' },
  { key: 'karteles',       label: 'ΞΞ±ΟΟ„Ξ­Ξ»ΞµΟ‚' },
  { key: 'zip-export',     label: 'ZIP Export' },
  { key: 'reports',        label: 'Ξ‘Ξ½Ξ±Ο†ΞΏΟΞ­Ο‚' },
  { key: 'report-builder', label: 'Report Builder' },
  { key: 'ai-analysis',    label: 'AI Ξ‘Ξ½Ξ¬Ξ»Ο…ΟƒΞ·' },
  { key: 'admin',          label: 'Admin Panel' },
]

// M.6: Default sections per role
const DEFAULT_SECTIONS = {
  admin: null,
  user: null,
  accountant: ['zip-export'],
  viewer: ['dashboard', 'ai-analysis'],
}

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
      usersError.value = res.data.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ± Ο†ΟΟΟ„Ο‰ΟƒΞ·Ο‚'
    }
  } catch (e) {
    console.error('fetchUsers error:', e)
    if (e.response?.status === 403) {
      usersError.value = 'Ξ”ΞµΞ½ Ξ­Ο‡ΞµΟ„Ξµ Ξ΄ΞΉΞΊΞ±Ξ―Ο‰ΞΌΞ± Ο€ΟΟΟƒΞ²Ξ±ΟƒΞ·Ο‚ ΟƒΞµ Ξ±Ο…Ο„Ξ® Ο„Ξ· ΟƒΞµΞ»Ξ―Ξ΄Ξ±'
    } else {
      usersError.value = e.response?.data?.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ± ΟƒΟΞ½Ξ΄ΞµΟƒΞ·Ο‚'
    }
  } finally {
    usersLoading.value = false
  }
}

async function fetchEntities() {
  try {
    const res = await api.get('/api/config/entities')
    if (res.data.success && res.data.data) {
      entities.value = res.data.data
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
  if (!iso) return 'Ξ ΞΏΟ„Ξ­'
  try {
    const d = new Date(iso)
    return d.toLocaleDateString('el-GR', { day: '2-digit', month: '2-digit', year: '2-digit' })
  } catch {
    return 'β€”'
  }
}

function userInitial(u) {
  const name = u.displayName || u.username || '?'
  return name.charAt(0).toUpperCase()
}

function isSelf(u) {
  return currentUsername.value && currentUsername.value === u.username
}

// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  CREATE USER
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
const newUser = ref({
  username: '',
  displayName: '',
  email: '',
  password: '',
  role: 'user'
})
const newEntityIds = ref([])
const newSections = ref([])
const creating = ref(false)
const createError = ref(null)

const createNeedsEntities = computed(() => RESTRICTED_ROLES.includes(newUser.value.role))

function toggleNewEntity(entityId) {
  const idx = newEntityIds.value.indexOf(entityId)
  if (idx >= 0) {
    newEntityIds.value.splice(idx, 1)
  } else {
    newEntityIds.value.push(entityId)
  }
}

function toggleNewSection(sectionKey) {
  const idx = newSections.value.indexOf(sectionKey)
  if (idx >= 0) {
    newSections.value.splice(idx, 1)
  } else {
    newSections.value.push(sectionKey)
  }
}

// Auto-set default sections when role changes in create form
watch(() => newUser.value.role, (role) => {
  const defaults = DEFAULT_SECTIONS[role]
  newSections.value = defaults ? [...defaults] : []
})

async function createUser() {
  createError.value = null
  if (!newUser.value.username.trim()) {
    createError.value = 'Username Ο…Ο€ΞΏΟ‡ΟΞµΟ‰Ο„ΞΉΞΊΟ'
    return
  }
  if (!newUser.value.password || newUser.value.password.length < 8) {
    createError.value = 'Ξ ΞΊΟ‰Ξ΄ΞΉΞΊΟΟ‚ Ο€ΟΞ­Ο€ΞµΞΉ Ξ½Ξ± ΞµΞ―Ξ½Ξ±ΞΉ Ο„ΞΏΟ…Ξ»Ξ¬Ο‡ΞΉΟƒΟ„ΞΏΞ½ 8 Ο‡Ξ±ΟΞ±ΞΊΟ„Ξ®ΟΞµΟ‚'
    return
  }
  if (createNeedsEntities.value && newEntityIds.value.length === 0) {
    createError.value = 'Ξ ΟΟΞ»ΞΏΟ‚ "' + ROLE_LABELS[newUser.value.role] + '" Ξ±Ο€Ξ±ΞΉΟ„ΞµΞ― Ο„ΞΏΟ…Ξ»Ξ¬Ο‡ΞΉΟƒΟ„ΞΏΞ½ ΞΌΞ―Ξ± ΞµΟ„Ξ±ΞΉΟΞµΞ―Ξ±.'
    return
  }
  creating.value = true
  try {
    // STEP 1: Create the user
    // M.6: Build allowedSections JSON string (null = all)
    const sectionsPayload = newSections.value.length > 0
      ? JSON.stringify(newSections.value)
      : (DEFAULT_SECTIONS[newUser.value.role] ? JSON.stringify(DEFAULT_SECTIONS[newUser.value.role]) : null)

    const res = await api.post('/api/admin/users', {
      username:        newUser.value.username.trim(),
      password:        newUser.value.password,
      displayName:     newUser.value.displayName.trim() || newUser.value.username.trim(),
      email:           newUser.value.email.trim() || null,
      role:            newUser.value.role,
      allowedSections: sectionsPayload
    })
    if (res.data.success) {
      // STEP 2: Assign entities if any selected (for ALL roles)
      if (newEntityIds.value.length > 0) {
        const newUserId = res.data.data?.id
        if (newUserId) {
          try {
            await api.put('/api/admin/users/' + newUserId + '/entities', {
              entityIds: newEntityIds.value
            })
          } catch (entErr) {
            console.error('Entity assignment failed:', entErr)
            alert('Ξ Ο‡ΟΞ®ΟƒΟ„Ξ·Ο‚ Ξ΄Ξ·ΞΌΞΉΞΏΟ…ΟΞ³Ξ®ΞΈΞ·ΞΊΞµ Ξ±Ξ»Ξ»Ξ¬ Ξ· Ξ±Ξ½Ο„ΞΉΟƒΟ„ΞΏΞ―Ο‡ΞΉΟƒΞ· ΞµΟ„Ξ±ΞΉΟΞµΞΉΟΞ½ Ξ±Ο€Ξ­Ο„Ο…Ο‡Ξµ. ΞΞ¬Ξ½Ο„Ξµ Ξ•Ο€ΞµΞΎΞµΟΞ³Ξ±ΟƒΞ―Ξ± Ξ³ΞΉΞ± Ξ½Ξ± Ο„Ξ·Ξ½ ΞµΟ€Ξ±Ξ½Ξ±Ξ»Ξ¬Ξ²ΞµΟ„Ξµ.')
          }
        }
      }
      newUser.value = { username: '', displayName: '', email: '', password: '', role: 'user' }
      newEntityIds.value = []
      newSections.value = []
      await fetchUsers()
      alert('Ξ§ΟΞ®ΟƒΟ„Ξ·Ο‚ Ξ΄Ξ·ΞΌΞΉΞΏΟ…ΟΞ³Ξ®ΞΈΞ·ΞΊΞµ ΞµΟ€ΞΉΟ„Ο…Ο‡ΟΟ‚.')
    } else {
      createError.value = res.data.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ± Ξ΄Ξ·ΞΌΞΉΞΏΟ…ΟΞ³Ξ―Ξ±Ο‚'
    }
  } catch (e) {
    createError.value = e.response?.data?.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ± ΟƒΟΞ½Ξ΄ΞµΟƒΞ·Ο‚'
  } finally {
    creating.value = false
  }
}

// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  EDIT USER MODAL
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
const editModalOpen = ref(false)
const editUser = ref(null)
const editForm = ref({
  displayName: '',
  email: '',
  role: 'user',
  isActive: true
})
const editEntityIds = ref([])
const editSections = ref([])
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

  // M.6: Load allowed sections
  if (u.allowedSections) {
    try {
      editSections.value = JSON.parse(u.allowedSections)
    } catch {
      editSections.value = []
    }
  } else {
    editSections.value = []
  }

  // M.6: Always fetch entity assignments for all roles
  fetchUserEntities(u.id)
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

function toggleEditSection(sectionKey) {
  const idx = editSections.value.indexOf(sectionKey)
  if (idx >= 0) {
    editSections.value.splice(idx, 1)
  } else {
    editSections.value.push(sectionKey)
  }
}

function resetEditSectionsToDefaults() {
  const defaults = DEFAULT_SECTIONS[editForm.value.role]
  editSections.value = defaults ? [...defaults] : []
}

const needsEntities = computed(() => RESTRICTED_ROLES.includes(editForm.value.role))

async function saveEditUser() {
  editError.value = null

  if (needsEntities.value && editEntityIds.value.length === 0) {
    editError.value = 'Ξ ΟΟΞ»ΞΏΟ‚ "' + ROLE_LABELS[editForm.value.role] + '" Ξ±Ο€Ξ±ΞΉΟ„ΞµΞ― Ο„ΞΏΟ…Ξ»Ξ¬Ο‡ΞΉΟƒΟ„ΞΏΞ½ ΞΌΞ―Ξ± ΞµΟ„Ξ±ΞΉΟΞµΞ―Ξ±.'
    return
  }

  editSaving.value = true
  try {
    // M.6: Always save entity assignments (for all roles)
    await api.put('/api/admin/users/' + editUser.value.id + '/entities', {
      entityIds: editEntityIds.value
    })

    // M.6: Build allowedSections for save
    const editSectionsPayload = editSections.value.length > 0
      ? JSON.stringify(editSections.value)
      : null

    const payload = {
      displayName:     editForm.value.displayName || null,
      email:           editForm.value.email || null,
      role:            editForm.value.role,
      isActive:        editForm.value.isActive,
      allowedSections: editSectionsPayload
    }
    const res = await api.put('/api/admin/users/' + editUser.value.id, payload)

    if (res.data.success) {
      // M.6: Entity assignment already saved above for all roles

      if (newPasswordField.value && newPasswordField.value.length > 0) {
        if (newPasswordField.value.length < 8) {
          editError.value = 'Ξ§ΟΞ®ΟƒΟ„Ξ·Ο‚ ΞµΞ½Ξ·ΞΌΞµΟΟΞΈΞ·ΞΊΞµ Ξ‘Ξ›Ξ›Ξ‘ ΞΏ ΞΊΟ‰Ξ΄ΞΉΞΊΟΟ‚ Ξ΄ΞµΞ½ Ξ¬Ξ»Ξ»Ξ±ΞΎΞµ (Ξ±Ο€Ξ±ΞΉΟ„ΞµΞ― Ο„ΞΏΟ…Ξ»Ξ¬Ο‡ΞΉΟƒΟ„ΞΏΞ½ 8 Ο‡Ξ±ΟΞ±ΞΊΟ„Ξ®ΟΞµΟ‚)'
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
      alert('Ξ Ο‡ΟΞ®ΟƒΟ„Ξ·Ο‚ ΞµΞ½Ξ·ΞΌΞµΟΟΞΈΞ·ΞΊΞµ ΞµΟ€ΞΉΟ„Ο…Ο‡ΟΟ‚')
    } else {
      editError.value = res.data.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ± ΞµΞ½Ξ·ΞΌΞ­ΟΟ‰ΟƒΞ·Ο‚'
    }
  } catch (e) {
    editError.value = e.response?.data?.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ± ΟƒΟΞ½Ξ΄ΞµΟƒΞ·Ο‚'
  } finally {
    editSaving.value = false
  }
}

// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  DELETE USER
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
async function deleteUser(u) {
  if (isSelf(u)) {
    alert('Ξ”ΞµΞ½ ΞΌΟ€ΞΏΟΞµΞ―Ο„Ξµ Ξ½Ξ± Ξ΄ΞΉΞ±Ξ³ΟΞ¬ΟΞµΟ„Ξµ Ο„ΞΏΞ½ ΞµΞ±Ο…Ο„Ο ΟƒΞ±Ο‚')
    return
  }
  if (!confirm('Ξ‘Ο€ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞ―Ξ·ΟƒΞ· Ο‡ΟΞ®ΟƒΟ„Ξ· "' + (u.displayName || u.username) + '";\n\nΞ Ο‡ΟΞ®ΟƒΟ„Ξ·Ο‚ Ξ΄ΞµΞ½ ΞΈΞ± ΞΌΟ€ΞΏΟΞµΞ― Ξ½Ξ± ΟƒΟ…Ξ½Ξ΄ΞµΞΈΞµΞ― Ξ±Ξ»Ξ»Ξ¬ Ο„Ξ± Ξ΄ΞµΞ΄ΞΏΞΌΞ­Ξ½Ξ± Ο„ΞΏΟ… ΞΈΞ± Ξ΄ΞΉΞ±Ο„Ξ·ΟΞ·ΞΈΞΏΟΞ½.')) {
    return
  }
  try {
    const res = await api.delete('/api/admin/users/' + u.id)
    if (res.data.success) {
      await fetchUsers()
      alert('Ξ Ο‡ΟΞ®ΟƒΟ„Ξ·Ο‚ Ξ±Ο€ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞΉΞ®ΞΈΞ·ΞΊΞµ')
    } else {
      alert('Ξ£Ο†Ξ¬Ξ»ΞΌΞ±: ' + (res.data.error || ''))
    }
  } catch (e) {
    alert('Ξ£Ο†Ξ¬Ξ»ΞΌΞ±: ' + (e.response?.data?.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ± ΟƒΟΞ½Ξ΄ΞµΟƒΞ·Ο‚'))
  }
}

// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  SELF CHANGE PASSWORD
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
const selfPasswordOpen = ref(false)
const selfOldPassword = ref('')
const selfNewPassword = ref('')
const selfPasswordError = ref(null)
const selfPasswordSaving = ref(false)

async function changeMyPassword() {
  selfPasswordError.value = null
  if (!selfOldPassword.value || !selfNewPassword.value) {
    selfPasswordError.value = 'Ξ£Ο…ΞΌΟ€Ξ»Ξ·ΟΟΟƒΟ„Ξµ ΟΞ»Ξ± Ο„Ξ± Ο€ΞµΞ΄Ξ―Ξ±'
    return
  }
  if (selfNewPassword.value.length < 8) {
    selfPasswordError.value = 'Ξ Ξ½Ξ­ΞΏΟ‚ ΞΊΟ‰Ξ΄ΞΉΞΊΟΟ‚ Ο€ΟΞ­Ο€ΞµΞΉ Ξ½Ξ± Ξ­Ο‡ΞµΞΉ Ο„ΞΏΟ…Ξ»Ξ¬Ο‡ΞΉΟƒΟ„ΞΏΞ½ 8 Ο‡Ξ±ΟΞ±ΞΊΟ„Ξ®ΟΞµΟ‚'
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
      alert('Ξ ΞΊΟ‰Ξ΄ΞΉΞΊΟΟ‚ ΟƒΞ±Ο‚ Ξ¬Ξ»Ξ»Ξ±ΞΎΞµ ΞµΟ€ΞΉΟ„Ο…Ο‡ΟΟ‚')
    } else {
      selfPasswordError.value = res.data.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ±'
    }
  } catch (e) {
    selfPasswordError.value = e.response?.data?.error || 'Ξ£Ο†Ξ¬Ξ»ΞΌΞ± ΟƒΟΞ½Ξ΄ΞµΟƒΞ·Ο‚'
  } finally {
    selfPasswordSaving.value = false
  }
}

// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  FILTERED USERS LIST (USER role sees only self)
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
const visibleUsers = computed(() => {
  if (!currentUser.value) return []
  if (currentUser.value.role === 'admin') return users.value
  if (currentUser.value.role === 'user') {
    return users.value.filter(u => u.username === currentUser.value.username)
  }
  return []
})

// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  MOUNT
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
//  M.7 β€” Config Management State (Categories, Subcategories, Banks)
// β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•β•
const ENTITIES_MAP = {
  next2me: '58202b71-4ddb-45c9-8e3c-39e816bde972',
  house:   'dea1f32c-7b30-4981-b625-633da9dbe71e',
  polaris: '50317f44-9961-4fb4-add0-7a118e32dc14',
}
const selectedEntity = ref(localStorage.getItem('n2c_entity') || 'next2me')
const adminEntityId = computed(() => ENTITIES_MAP[selectedEntity.value])

// Config items state
const configLoading = ref(false)
const allConfigItems = ref([])

const adminCategories = computed(() =>
  allConfigItems.value.filter(c => c.configType === 'category').sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
)
const activeCategories = computed(() => adminCategories.value.filter(c => c.isActive))
const adminSubcategories = computed(() =>
  allConfigItems.value.filter(c => c.configType === 'subcategory').sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
)

// Subcategory filter
const subcatFilter = ref('')
const filteredSubcats = computed(() => {
  if (!subcatFilter.value) return adminSubcategories.value
  return adminSubcategories.value.filter(s => s.parentKey === subcatFilter.value)
})

function countSubcats(categoryKey) {
  return adminSubcategories.value.filter(s => s.parentKey === categoryKey && s.isActive).length
}

// New category form
const newCatKey = ref('')
const newCatValue = ref('')

// New subcategory form
const newSubcatParent = ref('')
const newSubcatKey = ref('')
const newSubcatValue = ref('')

// Banks state
const banksLoading = ref(false)
const adminBanks = ref([])

async function loadAdminConfig() {
  configLoading.value = true
  try {
    const res = await api.get('/api/config/items', { params: { entityId: adminEntityId.value } })
    if (res.data.success) {
      allConfigItems.value = res.data.data || []
    }
  } catch (e) {
    console.error('loadAdminConfig error:', e)
  } finally {
    configLoading.value = false
  }
}

async function addCategory() {
  if (!newCatKey.value.trim()) return
  try {
    const res = await api.post('/api/config/items', {
      configType: 'category',
      configKey: newCatKey.value.trim(),
      configValue: newCatValue.value.trim() || newCatKey.value.trim()
    }, { params: { entityId: adminEntityId.value } })
    if (res.data.success) {
      newCatKey.value = ''
      newCatValue.value = ''
      await loadAdminConfig()
    }
  } catch (e) {
    alert('Ξ£Ο†Ξ¬Ξ»ΞΌΞ±: ' + (e.response?.data?.error || e.message))
  }
}

async function addSubcategory() {
  if (!newSubcatKey.value.trim() || !newSubcatParent.value) return
  try {
    const res = await api.post('/api/config/items', {
      configType: 'subcategory',
      configKey: newSubcatKey.value.trim(),
      configValue: newSubcatValue.value.trim() || newSubcatKey.value.trim(),
      parentKey: newSubcatParent.value
    }, { params: { entityId: adminEntityId.value } })
    if (res.data.success) {
      newSubcatKey.value = ''
      newSubcatValue.value = ''
      await loadAdminConfig()
    }
  } catch (e) {
    alert('Ξ£Ο†Ξ¬Ξ»ΞΌΞ±: ' + (e.response?.data?.error || e.message))
  }
}

async function deactivateConfig(item) {
  if (!confirm('Ξ‘Ο€ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞ―Ξ·ΟƒΞ· "' + item.configKey + '";\n\nΞ”ΞµΞ½ ΞΈΞ± ΞµΞΌΟ†Ξ±Ξ½Ξ―Ξ¶ΞµΟ„Ξ±ΞΉ Ο€Ξ»Ξ­ΞΏΞ½ ΟƒΟ„ΞΉΟ‚ Ξ½Ξ­ΞµΟ‚ ΞΊΞ±Ο„Ξ±Ο‡Ο‰ΟΞ®ΟƒΞµΞΉΟ‚.')) return
  try {
    await api.delete('/api/config/items/' + item.id, { params: { entityId: adminEntityId.value } })
    await loadAdminConfig()
  } catch (e) {
    alert('Ξ£Ο†Ξ¬Ξ»ΞΌΞ±: ' + (e.response?.data?.error || e.message))
  }
}

async function reactivateConfig(item) {
  try {
    await api.put('/api/config/items/' + item.id, { isActive: true }, { params: { entityId: adminEntityId.value } })
    await loadAdminConfig()
  } catch (e) {
    alert('Ξ£Ο†Ξ¬Ξ»ΞΌΞ±: ' + (e.response?.data?.error || e.message))
  }
}

async function loadBankAccounts() {
  banksLoading.value = true
  try {
    const res = await api.get('/api/bank-accounts', { params: { entityId: adminEntityId.value } })
    if (res.data.success) {
      adminBanks.value = res.data.accounts || res.data.data || []
    }
  } catch (e) {
    console.error('loadBankAccounts error:', e)
  } finally {
    banksLoading.value = false
  }
}

const newBank = ref({
  accountLabel: '',
  bankName: '',
  accountType: 'checking',
  currentBalance: 0,
  currency: 'EUR'
})
const bankCreating = ref(false)

async function createBankAccount() {
  if (!newBank.value.accountLabel.trim()) return
  bankCreating.value = true
  try {
    const res = await api.post('/api/bank-accounts', {
      ...newBank.value,
      entityId: adminEntityId.value,
      bankName: newBank.value.bankName || newBank.value.accountLabel
    })
    if (res.data.success) {
      newBank.value = { accountLabel: '', bankName: '', accountType: 'checking', currentBalance: 0, currency: 'EUR' }
      await loadBankAccounts()
    } else {
      alert('\u03A3\u03C6\u03AC\u03BB\u03BC\u03B1: ' + (res.data.error || ''))
    }
  } catch (e) {
    alert('\u03A3\u03C6\u03AC\u03BB\u03BC\u03B1: ' + (e.response?.data?.error || e.message))
  } finally {
    bankCreating.value = false
  }
}

async function updateBankBalance(bank) {
  const newBal = bank._editBalance
  if (newBal === undefined || newBal === null) return
  try {
    const res = await api.put('/api/bank-accounts/' + bank.id, {
      currentBalance: newBal,
      entityId: adminEntityId.value
    })
    if (res.data.success) {
      bank.currentBalance = newBal
      bank._editBalance = newBal
    }
  } catch (e) {
    alert('\u03A3\u03C6\u03AC\u03BB\u03BC\u03B1: ' + (e.response?.data?.error || e.message))
  }
}

function formatBankDate(d) {
  if (!d) return '\u2014'
  try {
    const dt = new Date(d)
    if (isNaN(dt.getTime())) return '\u2014'
    return dt.toLocaleDateString('el-GR', { day: '2-digit', month: '2-digit', year: '2-digit' })
  } catch { return '\u2014' }
}

const groupedSubcats = computed(() => {
  const subs = filteredSubcats.value
  const groups = {}
  for (const s of subs) {
    const key = s.parentKey || '\u03A7\u03C9\u03C1\u03AF\u03C2 \u03BA\u03B1\u03C4\u03B7\u03B3\u03BF\u03C1\u03AF\u03B1'
    if (!groups[key]) groups[key] = []
    groups[key].push(s)
  }
  const catOrder = activeCategories.value.map(c => c.configKey)
  return Object.keys(groups)
    .sort((a, b) => {
      const ia = catOrder.indexOf(a)
      const ib = catOrder.indexOf(b)
      return (ia === -1 ? 999 : ia) - (ib === -1 ? 999 : ib)
    })
    .map(cat => ({ category: cat, items: groups[cat] }))
})

// Listen for entity changes
function onEntityChanged() {
  selectedEntity.value = localStorage.getItem('n2c_entity') || 'next2me'
  if (activeTab.value === 'categories' || activeTab.value === 'accounts') {
    loadAdminConfig()
  } else if (activeTab.value === 'banks') {
    loadBankAccounts()
  }
}

// Watch tab changes to auto-load data
watch(activeTab, (tab) => {
  if (tab === 'categories' || tab === 'accounts') {
    if (allConfigItems.value.length === 0) loadAdminConfig()
  } else if (tab === 'banks') {
    if (adminBanks.value.length === 0) loadBankAccounts()
  }
})

onMounted(async () => {
  await fetchCurrentUser()
  await Promise.all([
    fetchUsers(),
    fetchEntities()
  ])
  window.addEventListener('entity-changed', onEntityChanged)
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
        Ξ©Ο‚ Ξ§ΟΞ®ΟƒΟ„Ξ·Ο‚ Ξ²Ξ»Ξ­Ο€ΞµΟ„Ξµ ΞΌΟΞ½ΞΏ Ο„Ξ± ΟƒΟ„ΞΏΞΉΟ‡ΞµΞ―Ξ± ΟƒΞ±Ο‚. Ξ“ΞΉΞ± Ξ±Ξ»Ξ»Ξ±Ξ³Ξ® ΞΊΟ‰Ξ΄ΞΉΞΊΞΏΟ Ο‡ΟΞ·ΟƒΞΉΞΌΞΏΟ€ΞΏΞΉΞ®ΟƒΟ„Ξµ Ο„ΞΏ ΞΊΞΏΟ…ΞΌΟ€Ξ― "Ξ‘Ξ»Ξ»Ξ±Ξ³Ξ® ΞΟ‰Ξ΄ΞΉΞΊΞΏΟ ΞΞΏΟ…".
      </div>

      <div v-if="isAdmin" class="card">
        <h2>ΞΞ­ΞΏΟ‚ Ξ§ΟΞ®ΟƒΟ„Ξ·Ο‚</h2>
        <div class="form-row">
          <input v-model="newUser.username" placeholder="Username *" class="input" />
          <input v-model="newUser.displayName" placeholder="Ξ•ΞΌΟ†Ξ±Ξ½ΞΉΞ¶ΟΞΌΞµΞ½ΞΏ ΞΞ½ΞΏΞΌΞ±" class="input" />
          <input v-model="newUser.email" placeholder="Email (Ο€ΟΞΏΞ±ΞΉΟΞµΟ„ΞΉΞΊΟ)" type="email" class="input" />
        </div>
        <div class="form-row">
          <input v-model="newUser.password" placeholder="ΞΟ‰Ξ΄ΞΉΞΊΟΟ‚ (min 8 Ο‡Ξ±ΟΞ±ΞΊΟ„Ξ®ΟΞµΟ‚) *" type="password" class="input" />
          <select v-model="newUser.role" class="input">
            <option v-for="r in ASSIGNABLE_ROLES" :key="r" :value="r">{{ ROLE_LABELS[r] }}</option>
          </select>
          <button class="btn btn-primary" :disabled="creating" @click="createUser">
            {{ creating ? 'Ξ”Ξ·ΞΌΞΉΞΏΟ…ΟΞ³Ξ―Ξ±...' : '+ Ξ”Ξ·ΞΌΞΉΞΏΟ…ΟΞ³Ξ―Ξ±' }}
          </button>
        </div>
        <div class="form-group" style="margin-top: 12px;">
          <label>Ξ•Ο„Ξ±ΞΉΟΞµΞ―ΞµΟ‚ Ο€ΞΏΟ… ΞΈΞ± Ξ²Ξ»Ξ­Ο€ΞµΞΉ ΞΏ Ο‡ΟΞ®ΟƒΟ„Ξ·Ο‚ <small v-if="!createNeedsEntities">(ΞΊΞµΞ½Ο = ΟΞ»ΞµΟ‚)</small><small v-else>*</small></label>
          <div class="entity-checkboxes">
            <label v-for="e in entities" :key="e.id" class="checkbox-label">
              <input
                type="checkbox"
                :checked="newEntityIds.includes(e.id)"
                @change="toggleNewEntity(e.id)"
              />
              {{ e.name }}
            </label>
          </div>
          <small v-if="newEntityIds.length === 0 && createNeedsEntities" class="help-text" style="color: #fbbf24;">
            Ξ¥Ο€ΞΏΟ‡ΟΞµΟ‰Ο„ΞΉΞΊΟ: ΞµΟ€ΞΉΞ»Ξ­ΞΎΟ„Ξµ Ο„ΞΏΟ…Ξ»Ξ¬Ο‡ΞΉΟƒΟ„ΞΏΞ½ ΞΌΞ―Ξ± ΞµΟ„Ξ±ΞΉΟΞµΞ―Ξ±
          </small>
          <small v-if="newEntityIds.length === 0 && !createNeedsEntities" class="help-text">
            ΞΞµΞ½Ο = ΟΞ»ΞµΟ‚ ΞΏΞΉ ΞµΟ„Ξ±ΞΉΟΞµΞ―ΞµΟ‚
          </small>
        </div>
        <div v-if="createError" class="error">{{ createError }}</div>
        <!-- M.6: Allowed sections checkboxes for create -->
        <div class="form-group" style="margin-top: 12px;">
          <label>Ξ£ΞµΞ»Ξ―Ξ΄ΞµΟ‚ Ο€ΞΏΟ… ΞΈΞ± Ξ²Ξ»Ξ­Ο€ΞµΞΉ ΞΏ Ο‡ΟΞ®ΟƒΟ„Ξ·Ο‚
            <small v-if="!DEFAULT_SECTIONS[newUser.role]">(ΞΊΞµΞ½Ο = ΟΞ»ΞµΟ‚)</small>
          </label>
          <div class="section-checkboxes">
            <label v-for="s in ALL_SECTIONS" :key="s.key" class="checkbox-label">
              <input
                type="checkbox"
                :checked="newSections.includes(s.key)"
                @change="toggleNewSection(s.key)"
              />
              {{ s.label }}
            </label>
          </div>
          <small class="help-text">ΞΞµΞ½Ο = Ο€Ξ»Ξ®ΟΞ·Ο‚ Ο€ΟΟΟƒΞ²Ξ±ΟƒΞ· (Ξ³ΞΉΞ± Admin/User)</small>
        </div>
        <p v-if="newUser.role === 'user'" class="warning">
          Ξ ΟΟΞ»ΞΏΟ‚ "Ξ§ΟΞ®ΟƒΟ„Ξ·Ο‚" Ξ΄Ξ―Ξ½ΞµΞΉ ΟƒΟ‡ΞµΞ΄ΟΞ½ Ο€Ξ»Ξ®ΟΞ· Ο€ΟΟΟƒΞ²Ξ±ΟƒΞ· ΟƒΟ„ΞΏ ΟƒΟΟƒΟ„Ξ·ΞΌΞ± (ΞµΞΊΟ„ΟΟ‚ Ξ±Ο€Ο Ξ΄ΞΉΞ±Ο‡ΞµΞ―ΟΞΉΟƒΞ· Ξ¬Ξ»Ξ»Ο‰Ξ½ Ο‡ΟΞ·ΟƒΟ„ΟΞ½).
        </p>
      </div>

      <div class="card">
        <h2>Ξ Ξ›ΞΏΞ³Ξ±ΟΞΉΞ±ΟƒΞΌΟΟ‚ ΞΞΏΟ…</h2>
        <button v-if="!selfPasswordOpen" class="btn btn-secondary" @click="selfPasswordOpen = true">
          Ξ‘Ξ»Ξ»Ξ±Ξ³Ξ® ΞΟ‰Ξ΄ΞΉΞΊΞΏΟ ΞΞΏΟ…
        </button>
        <div v-else class="form-column">
          <input v-model="selfOldPassword" placeholder="Ξ¤ΟΞ­Ο‡Ο‰Ξ½ ΞΟ‰Ξ΄ΞΉΞΊΟΟ‚" type="password" class="input" />
          <input v-model="selfNewPassword" placeholder="ΞΞ­ΞΏΟ‚ ΞΟ‰Ξ΄ΞΉΞΊΟΟ‚ (min 8 Ο‡Ξ±ΟΞ±ΞΊΟ„Ξ®ΟΞµΟ‚)" type="password" class="input" />
          <div v-if="selfPasswordError" class="error">{{ selfPasswordError }}</div>
          <div class="form-row">
            <button class="btn btn-primary" :disabled="selfPasswordSaving" @click="changeMyPassword">
              {{ selfPasswordSaving ? 'Ξ‘Ο€ΞΏΞΈΞ®ΞΊΞµΟ…ΟƒΞ·...' : 'Ξ‘Ο€ΞΏΞΈΞ®ΞΊΞµΟ…ΟƒΞ·' }}
            </button>
            <button class="btn btn-secondary" @click="selfPasswordOpen = false; selfOldPassword=''; selfNewPassword=''; selfPasswordError=null">
              Ξ‘ΞΊΟΟΟ‰ΟƒΞ·
            </button>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <h2>Ξ§ΟΞ®ΟƒΟ„ΞµΟ‚ Ξ£Ο…ΟƒΟ„Ξ®ΞΌΞ±Ο„ΞΏΟ‚</h2>
          <button class="btn btn-secondary btn-sm" @click="fetchUsers" :disabled="usersLoading">
            {{ usersLoading ? '...' : 'Ξ‘Ξ½Ξ±Ξ½Ξ­Ο‰ΟƒΞ·' }}
          </button>
        </div>

        <div v-if="usersLoading" class="loading">Ξ¦ΟΟΟ„Ο‰ΟƒΞ·...</div>
        <div v-else-if="usersError" class="error">{{ usersError }}</div>
        <div v-else-if="!visibleUsers.length" class="empty">ΞΞ±Ξ½Ξ­Ξ½Ξ±Ο‚ Ο‡ΟΞ®ΟƒΟ„Ξ·Ο‚</div>
        <div v-else class="user-list">
          <div v-for="u in visibleUsers" :key="u.id" class="user-item">
            <div class="user-avatar">{{ userInitial(u) }}</div>
            <div class="user-info">
              <div class="user-name">
                <span class="active-dot" :class="{ on: u.isActive !== false, off: u.isActive === false }"></span>
                {{ u.displayName || u.username }}
                <span v-if="isSelf(u)" class="self-badge">(ΞµΟƒΞµΞ―Ο‚)</span>
              </div>
              <div class="user-meta">
                @{{ u.username }} Β· Ξ¤ΞµΞ»ΞµΟ…Ο„Ξ±Ξ―ΞΏ login: {{ formatLastLogin(u.lastLogin) }}
              </div>
            </div>
            <span class="role-badge" :class="ROLE_CLASSES[u.role]">{{ ROLE_LABELS[u.role] || u.role }}</span>
            <div v-if="isAdmin" class="user-actions">
              <button class="btn-icon" title="Ξ•Ο€ΞµΞΎΞµΟΞ³Ξ±ΟƒΞ―Ξ±" @click="openEditModal(u)">βοΈ</button>
              <button
                v-if="!isSelf(u) && u.isActive !== false"
                class="btn-icon danger"
                title="Ξ‘Ο€ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞ―Ξ·ΟƒΞ·"
                @click="deleteUser(u)"
              >π—‘οΈ</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="!isAdmin" class="tab-content">
      <div class="notice notice-warning">
        ΞΟΞ½ΞΏ ΞΏΞΉ Ξ”ΞΉΞ±Ο‡ΞµΞΉΟΞΉΟƒΟ„Ξ­Ο‚ Ξ­Ο‡ΞΏΟ…Ξ½ Ο€ΟΟΟƒΞ²Ξ±ΟƒΞ· ΟƒΞµ Ξ±Ο…Ο„Ξ® Ο„Ξ· ΟƒΞµΞ»Ξ―Ξ΄Ξ±.
      </div>
    </div>

    <!-- β•β•β• CATEGORIES TAB β•β•β• -->
    <div v-else-if="activeTab === 'categories'" class="tab-content">
      <div class="card">
        <div class="card-header">
          <h2>ΞΞ±Ο„Ξ·Ξ³ΞΏΟΞ―ΞµΟ‚ β€” {{ selectedEntity === 'next2me' ? 'Next2Me' : selectedEntity === 'house' ? 'House' : 'Polaris' }}</h2>
          <button class="btn btn-secondary btn-sm" @click="loadAdminConfig" :disabled="configLoading">
            {{ configLoading ? '...' : 'Ξ‘Ξ½Ξ±Ξ½Ξ­Ο‰ΟƒΞ·' }}
          </button>
        </div>

        <div v-if="configLoading" class="loading">Ξ¦ΟΟΟ„Ο‰ΟƒΞ·...</div>
        <div v-else>
          <!-- Add new category -->
          <div class="inline-form">
            <input v-model="newCatKey" placeholder="ΞΞ½ΞΏΞΌΞ± ΞΊΞ±Ο„Ξ·Ξ³ΞΏΟΞ―Ξ±Ο‚ (Ο€.Ο‡. ΞΞ•Ξ¤Ξ‘Ξ¦ΞΞ΅Ξ•Ξ£)" class="input" />
            <input v-model="newCatValue" placeholder="Ξ•ΞΌΟ†Ξ±Ξ½ΞΉΞ¶ΟΞΌΞµΞ½ΞΏ (Ο€ΟΞΏΞ±ΞΉΟΞµΟ„ΞΉΞΊΟ)" class="input" />
            <button class="btn btn-primary btn-sm" @click="addCategory" :disabled="!newCatKey.trim()">+ Ξ ΟΞΏΟƒΞΈΞ®ΞΊΞ·</button>
          </div>

          <div v-if="adminCategories.length === 0" class="empty">Ξ”ΞµΞ½ Ξ²ΟΞ­ΞΈΞ·ΞΊΞ±Ξ½ ΞΊΞ±Ο„Ξ·Ξ³ΞΏΟΞ―ΞµΟ‚</div>
          <div v-else class="config-list">
            <div v-for="cat in adminCategories" :key="cat.id" class="config-item" :class="{ inactive: !cat.isActive }">
              <div class="config-info">
                <span class="config-key">{{ cat.configKey }}</span>
                <span v-if="cat.configValue && cat.configValue !== cat.configKey" class="config-val">β†’ {{ cat.configValue }}</span>
                <span class="config-count">{{ countSubcats(cat.configKey) }} Ο…Ο€ΞΏΞΊΞ±Ο„.</span>
              </div>
              <div class="config-actions">
                <span v-if="!cat.isActive" class="inactive-badge">Ξ‘Ξ½ΞµΞ½ΞµΟΞ³Ξ®</span>
                <button v-if="cat.isActive" class="btn-icon danger" title="Ξ‘Ο€ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞ―Ξ·ΟƒΞ·" @click="deactivateConfig(cat)">π—‘οΈ</button>
                <button v-else class="btn-icon" title="Ξ•Ο€Ξ±Ξ½ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞ―Ξ·ΟƒΞ·" @click="reactivateConfig(cat)">β™»οΈ</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- β•β•β• SUBCATEGORIES (ACCOUNTS) TAB β•β•β• -->
    <div v-else-if="activeTab === 'accounts'" class="tab-content">
      <div class="card">
        <div class="card-header">
          <h2>Ξ¥Ο€ΞΏΞΊΞ±Ο„Ξ·Ξ³ΞΏΟΞ―ΞµΟ‚ β€” {{ selectedEntity === 'next2me' ? 'Next2Me' : selectedEntity === 'house' ? 'House' : 'Polaris' }}</h2>
          <button class="btn btn-secondary btn-sm" @click="loadAdminConfig" :disabled="configLoading">
            {{ configLoading ? '...' : 'Ξ‘Ξ½Ξ±Ξ½Ξ­Ο‰ΟƒΞ·' }}
          </button>
        </div>

        <div v-if="configLoading" class="loading">Ξ¦ΟΟΟ„Ο‰ΟƒΞ·...</div>
        <div v-else>
          <!-- Filter by parent category -->
          <div class="filter-row">
            <label>Ξ¦Ξ―Ξ»Ο„ΟΞΏ ΞΊΞ±Ο„Ξ·Ξ³ΞΏΟΞ―Ξ±Ο‚:</label>
            <select v-model="subcatFilter" class="input" style="max-width:300px">
              <option value="">β€” ΞΞ»ΞµΟ‚ β€”</option>
              <option v-for="cat in activeCategories" :key="cat.configKey" :value="cat.configKey">{{ cat.configKey }}</option>
            </select>
          </div>

          <!-- Add new subcategory -->
          <div class="inline-form">
            <select v-model="newSubcatParent" class="input" style="max-width:220px">
              <option value="">β€” ΞΞ±Ο„Ξ·Ξ³ΞΏΟΞ―Ξ± β€”</option>
              <option v-for="cat in activeCategories" :key="cat.configKey" :value="cat.configKey">{{ cat.configKey }}</option>
            </select>
            <input v-model="newSubcatKey" placeholder="ΞΞ½ΞΏΞΌΞ± Ο…Ο€ΞΏΞΊΞ±Ο„Ξ·Ξ³ΞΏΟΞ―Ξ±Ο‚" class="input" />
            <input v-model="newSubcatValue" placeholder="Ξ•ΞΌΟ†Ξ±Ξ½ΞΉΞ¶ΟΞΌΞµΞ½ΞΏ (Ο€ΟΞΏΞ±ΞΉΟ.)" class="input" />
            <button class="btn btn-primary btn-sm" @click="addSubcategory" :disabled="!newSubcatKey.trim() || !newSubcatParent">+ Ξ ΟΞΏΟƒΞΈΞ®ΞΊΞ·</button>
          </div>

          <div v-if="filteredSubcats.length === 0" class="empty">Ξ”ΞµΞ½ Ξ²ΟΞ­ΞΈΞ·ΞΊΞ±Ξ½ Ο…Ο€ΞΏΞΊΞ±Ο„Ξ·Ξ³ΞΏΟΞ―ΞµΟ‚</div>
          <div v-else>
            <div v-for="group in groupedSubcats" :key="group.category" class="subcat-group">
              <div class="subcat-group-header">
                <span class="subcat-group-title">{{ group.category }}</span>
                <span class="config-count">{{ group.items.length }} Ο…Ο€ΞΏΞΊΞ±Ο„.</span>
              </div>
              <div class="config-list">
                <div v-for="sub in group.items" :key="sub.id" class="config-item" :class="{ inactive: !sub.isActive }">
                  <div class="config-info">
                    <span class="config-key">{{ sub.configKey }}</span>
                    <span v-if="sub.configValue && sub.configValue !== sub.configKey" class="config-val">β†’ {{ sub.configValue }}</span>
                  </div>
                  <div class="config-actions">
                    <span v-if="!sub.isActive" class="inactive-badge">Ξ‘Ξ½ΞµΞ½ΞµΟΞ³Ξ®</span>
                    <button v-if="sub.isActive" class="btn-icon danger" title="Ξ‘Ο€ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞ―Ξ·ΟƒΞ·" @click="deactivateConfig(sub)">π—‘οΈ</button>
                    <button v-else class="btn-icon" title="Ξ•Ο€Ξ±Ξ½ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞ―Ξ·ΟƒΞ·" @click="reactivateConfig(sub)">β™»οΈ</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- β•β•β• BANKS TAB β•β•β• -->
    <div v-else-if="activeTab === 'banks'" class="tab-content">
      <!-- Create new bank account -->
      <div class="card">
        <h2>β• ΞΞ­ΞΏΟ‚ Ξ¤ΟΞ±Ο€ΞµΞ¶ΞΉΞΊΟΟ‚ Ξ›ΞΏΞ³Ξ±ΟΞΉΞ±ΟƒΞΌΟΟ‚</h2>
        <div class="bank-form">
          <div class="form-group">
            <label>Ξ•Ο„ΞΉΞΊΞ­Ο„Ξ± <span class="req">*</span></label>
            <input v-model="newBank.accountLabel" placeholder="Ο€.Ο‡. Eurobank Ξ¤ΟΞµΟ‡ΞΏΟΞΌΞµΞ½ΞΏΟ‚" class="input" />
          </div>
          <div class="form-group">
            <label>Ξ¤ΟΞ¬Ο€ΞµΞ¶Ξ±</label>
            <input v-model="newBank.bankName" placeholder="Ο€.Ο‡. Eurobank" class="input" />
          </div>
          <div class="form-group">
            <label>Ξ¤ΟΟ€ΞΏΟ‚</label>
            <select v-model="newBank.accountType" class="input">
              <option value="checking">Ξ¤ΟΞµΟ‡ΞΏΟΞΌΞµΞ½ΞΏΟ‚</option>
              <option value="savings">Ξ¤Ξ±ΞΌΞΉΞµΟ…Ο„Ξ®ΟΞΉΞΏ</option>
              <option value="cash">ΞΞµΟ„ΟΞ·Ο„Ξ¬</option>
              <option value="credit">Ξ ΞΉΟƒΟ„Ο‰Ο„ΞΉΞΊΞ®</option>
              <option value="revolut">Revolut</option>
            </select>
          </div>
          <div class="form-group">
            <label>Ξ‘ΟΟ‡ΞΉΞΊΟ Ξ¥Ο€ΟΞ»ΞΏΞΉΟ€ΞΏ (β‚¬)</label>
            <input v-model.number="newBank.currentBalance" type="number" step="0.01" placeholder="0" class="input" />
          </div>
          <div class="form-group">
            <label>ΞΟΞΌΞΉΟƒΞΌΞ±</label>
            <select v-model="newBank.currency" class="input">
              <option value="EUR">EUR</option>
              <option value="USD">USD</option>
              <option value="GBP">GBP</option>
            </select>
          </div>
          <div class="form-group" style="align-self:end">
            <button class="btn btn-primary" @click="createBankAccount" :disabled="!newBank.accountLabel.trim() || bankCreating">
              {{ bankCreating ? '...' : '+ Ξ”Ξ·ΞΌΞΉΞΏΟ…ΟΞ³Ξ―Ξ±' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Bank accounts list -->
      <div class="card">
        <div class="card-header">
          <h2>π¦ Ξ¤ΟΞ±Ο€ΞµΞ¶ΞΉΞΊΞΏΞ― Ξ›ΞΏΞ³Ξ±ΟΞΉΞ±ΟƒΞΌΞΏΞ―</h2>
          <div style="display:flex;align-items:center;gap:12px">
            <span class="help-text">Ξ•Ξ½Ξ·ΞΌΞµΟΟΟƒΟ„Ξµ Ο…Ο€ΟΞ»ΞΏΞΉΟ€Ξ± Ο‡ΞµΞΉΟΞΏΞΊΞ―Ξ½Ξ·Ο„Ξ±</span>
            <button class="btn btn-secondary btn-sm" @click="loadBankAccounts" :disabled="banksLoading">
              {{ banksLoading ? '...' : 'Ξ‘Ξ½Ξ±Ξ½Ξ­Ο‰ΟƒΞ·' }}
            </button>
          </div>
        </div>

        <div v-if="banksLoading" class="loading">Ξ¦ΟΟΟ„Ο‰ΟƒΞ·...</div>
        <div v-else-if="adminBanks.length === 0" class="empty">Ξ”ΞµΞ½ Ξ²ΟΞ­ΞΈΞ·ΞΊΞ±Ξ½ Ξ»ΞΏΞ³Ξ±ΟΞΉΞ±ΟƒΞΌΞΏΞ―</div>
        <div v-else class="config-list">
          <div v-for="b in adminBanks" :key="b.id" class="bank-item" :class="{ inactive: b.active === false }">
            <div class="bank-icon-wrap">
              <i class="fas" :class="b.accountType === 'cash' ? 'fa-wallet' : 'fa-university'" style="color:var(--accent);font-size:1.1rem"></i>
            </div>
            <div class="bank-info">
              <div class="bank-label">
                <span class="active-dot" :class="{ on: b.active !== false, off: b.active === false }"></span>
                {{ b.accountLabel }}
              </div>
              <div class="bank-meta">{{ b.bankName }} Β· {{ b.accountType }} Β· {{ b.currency }} Β· Ξ•Ξ½Ξ·ΞΌ: {{ formatBankDate(b.balanceDate) }}</div>
            </div>
            <div class="bank-balance-area">
              <span class="balance-badge" :class="{ negative: b.currentBalance < 0 }">
                {{ Number(b.currentBalance || 0).toLocaleString('el-GR', {minimumFractionDigits:2}) }} β‚¬
              </span>
              <input
                v-model.number="b._editBalance"
                type="number" step="0.01"
                class="balance-input"
                @focus="b._editBalance = b._editBalance ?? b.currentBalance"
              />
              <button class="btn-balance-save" title="Ξ‘Ο€ΞΏΞΈΞ®ΞΊΞµΟ…ΟƒΞ· Ο…Ο€ΞΏΞ»ΞΏΞ―Ο€ΞΏΟ…" @click="updateBankBalance(b)">
                <i class="fas fa-check"></i>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- β•β•β• AUDIT LOG TAB β•β•β• -->
    <div v-else-if="activeTab === 'audit'" class="tab-content">
      <div class="card">
        <div class="card-header">
          <h2>Audit Log</h2>
        </div>
        <div class="notice notice-info">
          Ξ¤ΞΏ Audit Log ΞΈΞ± ΞµΞ½ΞµΟΞ³ΞΏΟ€ΞΏΞΉΞ·ΞΈΞµΞ― ΟƒΞµ ΞµΟ€ΟΞΌΞµΞ½Ξ· Ο†Ξ¬ΟƒΞ· (M.6 β€” Security & Polish).
        </div>
      </div>
    </div>

    <!-- β•β•β• FALLBACK β•β•β• -->
    <div v-else class="tab-content">
      <div class="notice notice-info">
        Ξ— ΟƒΞµΞ»Ξ―Ξ΄Ξ± <strong>{{ tabs.find(t => t.id === activeTab)?.label }}</strong> Ξ΄ΞµΞ½ ΞµΞ―Ξ½Ξ±ΞΉ Ξ±ΞΊΟΞΌΞ± Ξ΄ΞΉΞ±ΞΈΞ­ΟƒΞΉΞΌΞ·.
      </div>
    </div>

    <div v-if="editModalOpen" class="modal-overlay" @click.self="closeEditModal">
      <div class="modal-card">
        <button class="modal-close" @click="closeEditModal">Γ—</button>
        <h2>Ξ•Ο€ΞµΞΎΞµΟΞ³Ξ±ΟƒΞ―Ξ± Ξ§ΟΞ®ΟƒΟ„Ξ·</h2>

        <div class="form-group">
          <label>Username</label>
          <input :value="editUser?.username" disabled class="input input-disabled" />
        </div>

        <div class="form-group">
          <label>Ξ•ΞΌΟ†Ξ±Ξ½ΞΉΞ¶ΟΞΌΞµΞ½ΞΏ ΞΞ½ΞΏΞΌΞ±</label>
          <input v-model="editForm.displayName" class="input" />
        </div>

        <div class="form-group">
          <label>Email</label>
          <input v-model="editForm.email" type="email" class="input" />
        </div>

        <div v-if="!isSelf(editUser)" class="form-group">
          <label>ΞΞ­ΞΏΟ‚ ΞΟ‰Ξ΄ΞΉΞΊΟΟ‚ <small>(ΞΊΞµΞ½Ο = Ο‡Ο‰ΟΞ―Ο‚ Ξ±Ξ»Ξ»Ξ±Ξ³Ξ®, min 8 Ο‡Ξ±ΟΞ±ΞΊΟ„Ξ®ΟΞµΟ‚)</small></label>
          <input v-model="newPasswordField" type="password" placeholder="Ξ‘Ο†Ξ®ΟƒΟ„Ξµ ΞΊΞµΞ½Ο Ξ±Ξ½ Ξ΄ΞµΞ½ Ξ±Ξ»Ξ»Ξ¬Ξ¶ΞµΞΉ" class="input" />
          <small class="help-text">Admin reset - Ξ΄ΞµΞ½ Ξ±Ο€Ξ±ΞΉΟ„ΞµΞ―Ο„Ξ±ΞΉ ΞΏ Ο„ΟΞ­Ο‡Ο‰Ξ½ ΞΊΟ‰Ξ΄ΞΉΞΊΟΟ‚ Ο„ΞΏΟ… Ο‡ΟΞ®ΟƒΟ„Ξ·</small>
        </div>
        <div v-else class="form-group notice-box">
          <strong>Ξ‘Ξ»Ξ»Ξ±Ξ³Ξ® Ξ”ΞΉΞΊΞΏΟ Ξ£Ξ±Ο‚ ΞΟ‰Ξ΄ΞΉΞΊΞΏΟ:</strong>
          ΞΞ»ΞµΞ―ΟƒΟ„Ξµ Ξ±Ο…Ο„Ο Ο„ΞΏ Ο€Ξ±ΟΞ¬ΞΈΟ…ΟΞΏ ΞΊΞ±ΞΉ Ο‡ΟΞ·ΟƒΞΉΞΌΞΏΟ€ΞΏΞΉΞ®ΟƒΟ„Ξµ Ο„ΞΏ ΞΊΞΏΟ…ΞΌΟ€Ξ― "Ξ‘Ξ»Ξ»Ξ±Ξ³Ξ® ΞΟ‰Ξ΄ΞΉΞΊΞΏΟ ΞΞΏΟ…" Ο€Ξ¬Ξ½Ο‰ (Ξ±Ο€Ξ±ΞΉΟ„ΞµΞ― Ο„ΟΞ­Ο‡ΞΏΞ½Ο„Ξ± ΞΊΟ‰Ξ΄ΞΉΞΊΟ Ξ³ΞΉΞ± Ξ±ΟƒΟ†Ξ¬Ξ»ΞµΞΉΞ±).
        </div>

        <div class="form-group" v-if="!isSelf(editUser)">
          <label>Ξ΅ΟΞ»ΞΏΟ‚</label>
          <select v-model="editForm.role" class="input">
            <option v-for="r in ASSIGNABLE_ROLES" :key="r" :value="r">{{ ROLE_LABELS[r] }}</option>
            <option v-if="editUser?.role === 'admin'" value="admin">Ξ”ΞΉΞ±Ο‡ΞµΞΉΟΞΉΟƒΟ„Ξ®Ο‚</option>
          </select>
        </div>
        <div v-else class="form-group">
          <label>Ξ΅ΟΞ»ΞΏΟ‚</label>
          <input :value="ROLE_LABELS[editUser?.role]" disabled class="input input-disabled" />
          <small>Ξ”ΞµΞ½ ΞΌΟ€ΞΏΟΞµΞ―Ο„Ξµ Ξ½Ξ± Ξ±Ξ»Ξ»Ξ¬ΞΎΞµΟ„Ξµ Ο„ΞΏΞ½ Ξ΄ΞΉΞΊΟ ΟƒΞ±Ο‚ ΟΟΞ»ΞΏ</small>
        </div>

        <div class="form-group">
          <label>Ξ•Ο„Ξ±ΞΉΟΞµΞ―ΞµΟ‚ Ο€ΞΏΟ… Ξ²Ξ»Ξ­Ο€ΞµΞΉ <small v-if="!needsEntities">(ΞΊΞµΞ½Ο = ΟΞ»ΞµΟ‚)</small><small v-else>*</small></label>
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
          <small v-if="editEntityIds.length === 0 && needsEntities" class="error">
            Ξ¥Ο€ΞΏΟ‡ΟΞµΟ‰Ο„ΞΉΞΊΟ: ΞµΟ€ΞΉΞ»Ξ­ΞΎΟ„Ξµ Ο„ΞΏΟ…Ξ»Ξ¬Ο‡ΞΉΟƒΟ„ΞΏΞ½ ΞΌΞ―Ξ± ΞµΟ„Ξ±ΞΉΟΞµΞ―Ξ±
          </small>
          <small v-if="editEntityIds.length === 0 && !needsEntities" class="help-text">
            ΞΞµΞ½Ο = ΟΞ»ΞµΟ‚ ΞΏΞΉ ΞµΟ„Ξ±ΞΉΟΞµΞ―ΞµΟ‚
          </small>
        </div>

        <!-- M.6: Allowed sections checkboxes for edit -->
        <div class="form-group">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
            <label style="margin-bottom:0">Ξ£ΞµΞ»Ξ―Ξ΄ΞµΟ‚ Ο€ΞΏΟ… Ξ²Ξ»Ξ­Ο€ΞµΞΉ
              <small v-if="editSections.length === 0">(ΟΞ»ΞµΟ‚)</small>
            </label>
            <button type="button" class="btn btn-secondary btn-sm" @click="resetEditSectionsToDefaults" style="padding:4px 10px;font-size:.75rem">
              Defaults
            </button>
          </div>
          <div class="section-checkboxes">
            <label v-for="s in ALL_SECTIONS" :key="s.key" class="checkbox-label">
              <input
                type="checkbox"
                :checked="editSections.includes(s.key)"
                @change="toggleEditSection(s.key)"
              />
              {{ s.label }}
            </label>
          </div>
          <small class="help-text">ΞΞµΞ½Ο = Ο€Ξ»Ξ®ΟΞ·Ο‚ Ο€ΟΟΟƒΞ²Ξ±ΟƒΞ· (Ξ³ΞΉΞ± Admin/User)</small>
        </div>

        <div class="form-group" v-if="!isSelf(editUser)">
          <label>ΞΞ±Ο„Ξ¬ΟƒΟ„Ξ±ΟƒΞ·</label>
          <select v-model="editForm.isActive" class="input">
            <option :value="true">Ξ•Ξ½ΞµΟΞ³ΟΟ‚</option>
            <option :value="false">Ξ‘Ξ½ΞµΞ½ΞµΟΞ³ΟΟ‚</option>
          </select>
        </div>

        <div v-if="editForm.role === 'user'" class="warning">
          Ξ ΟΟΞ»ΞΏΟ‚ "Ξ§ΟΞ®ΟƒΟ„Ξ·Ο‚" Ξ΄Ξ―Ξ½ΞµΞΉ ΟƒΟ‡ΞµΞ΄ΟΞ½ Ο€Ξ»Ξ®ΟΞ· Ο€ΟΟΟƒΞ²Ξ±ΟƒΞ· ΟƒΟ„ΞΏ ΟƒΟΟƒΟ„Ξ·ΞΌΞ±.
        </div>

        <div v-if="editError" class="error">{{ editError }}</div>

        <div class="modal-actions">
          <button class="btn btn-secondary" @click="closeEditModal">Ξ‘ΞΊΟΟΟ‰ΟƒΞ·</button>
          <button class="btn btn-primary" :disabled="editSaving" @click="saveEditUser">
            {{ editSaving ? 'Ξ‘Ο€ΞΏΞΈΞ®ΞΊΞµΟ…ΟƒΞ·...' : 'Ξ‘Ο€ΞΏΞΈΞ®ΞΊΞµΟ…ΟƒΞ·' }}
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
.checkbox-label { display: flex; align-items: center; gap: 10px; cursor: pointer; font-size: 0.9rem; color: #ffffff; font-weight: 500; }
.checkbox-label input[type="checkbox"] { width: 16px; height: 16px; cursor: pointer; }

/* M.6: Section checkboxes grid */
.section-checkboxes { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 8px; padding: 12px; background: var(--bg-input, #111827); border-radius: 6px; border: 1px solid var(--border, #374151); }

/* M.7 β€” Config management styles */
.inline-form { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.inline-form .input { flex: 1; min-width: 160px; }
.filter-row { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.filter-row label { font-size: .85rem; color: var(--text-muted, #9ca3af); white-space: nowrap; }
.config-list { display: flex; flex-direction: column; gap: 6px; }
.config-item { display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; background: var(--bg-input, #111827); border: 1px solid var(--border, #374151); border-radius: 6px; transition: opacity .2s; }
.config-item.inactive { opacity: .45; }
.config-info { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; flex: 1; min-width: 0; }
.config-key { font-weight: 600; color: var(--text-primary, #e5e7eb); font-size: .92rem; }
.config-val { font-size: .82rem; color: var(--text-muted, #9ca3af); }
.config-count { font-size: .75rem; color: var(--accent, #3b82f6); background: rgba(59,130,246,.1); padding: 2px 8px; border-radius: 999px; }
.config-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.parent-badge { font-size: .72rem; padding: 2px 8px; border-radius: 999px; background: rgba(251,146,60,.15); color: #fb923c; font-weight: 600; }
.inactive-badge { font-size: .72rem; padding: 2px 8px; border-radius: 999px; background: rgba(239,68,68,.15); color: #f87171; }
.balance-badge { font-size: .85rem; font-weight: 600; color: #10b981; font-family: monospace; }
.balance-badge.negative { color: #f87171; }

/* M.7.2 β€” Bank form + grouped subcategories */
.bank-form { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; align-items: end; }
.bank-item { display: flex; align-items: center; gap: 14px; padding: 12px 16px; background: var(--bg-input, #111827); border: 1px solid var(--border, #374151); border-radius: 6px; transition: opacity .2s; }
.bank-item.inactive { opacity: .45; }
.bank-icon-wrap { width: 40px; height: 40px; display: flex; align-items: center; justify-content: center; background: rgba(59,130,246,.1); border-radius: 8px; flex-shrink: 0; }
.bank-info { flex: 1; min-width: 0; }
.bank-label { font-weight: 600; color: var(--text-primary, #e5e7eb); display: flex; align-items: center; gap: 8px; }
.bank-meta { font-size: .78rem; color: var(--text-muted, #9ca3af); margin-top: 2px; }
.bank-balance-area { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.balance-input { width: 100px; padding: 6px 10px; background: var(--bg-input, #111827); border: 1px solid var(--border, #374151); border-radius: 4px; color: var(--text-primary); font-size: .88rem; font-family: monospace; text-align: right; }
.balance-input:focus { border-color: var(--accent); outline: none; }
.btn-balance-save { width: 32px; height: 32px; border-radius: 50%; border: none; background: var(--accent, #3b82f6); color: white; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: .85rem; transition: opacity .15s; }
.btn-balance-save:hover { opacity: .85; }
.subcat-group { margin-bottom: 20px; }
.subcat-group-header { display: flex; align-items: center; gap: 10px; padding: 8px 14px; background: rgba(59,130,246,.08); border-left: 3px solid var(--accent, #3b82f6); border-radius: 4px; margin-bottom: 6px; }
.subcat-group-title { font-weight: 700; font-size: .92rem; color: var(--accent, #3b82f6); }
</style>
