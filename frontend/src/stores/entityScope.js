// entityScope.js — Single source of truth for per-user entity scoping.
//
// Reads the logged-in user's entity assignments from localStorage (n2c_user)
// and exposes helpers so every view can:
//   - show ONLY the entities the user is allowed to see in dropdowns
//   - pick a correct default entity key (never a forbidden one)
//   - know if the user is "restricted" (exactly one entity) to hide the dropdown
//   - know if the user is a VIEWER (read-only) to disable mutating controls
//
// S87: introduced so restricted users (e.g. investors assigned to one entity)
// never perceive that other companies exist anywhere in the app.

const UUID_TO_KEY = {
  '58202b71-4ddb-45c9-8e3c-39e816bde972': 'next2me',
  'dea1f32c-7b30-4981-b625-633da9dbe71e': 'house',
  '50317f44-9961-4fb4-add0-7a118e32dc14': 'next2megroup',
};

const KEY_TO_LABEL = {
  next2me: 'Next2Me',
  house: 'House',
  next2megroup: 'Next2Me Group',
};

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('n2c_user') || '{}') || {};
  } catch {
    return {};
  }
}

export function allowedEntityIds() {
  const u = readUser();
  const ids = u.entityIds;
  if (!ids) return null;
  if (Array.isArray(ids)) {
    return ids.length === 0 ? null : ids;
  }
  if (typeof ids === 'string') {
    const arr = ids.split(',').map(s => s.trim()).filter(Boolean);
    return arr.length === 0 ? null : arr;
  }
  return null;
}

export function allowedEntityKeys() {
  const ids = allowedEntityIds();
  if (ids === null) return null;
  return ids.map(id => UUID_TO_KEY[id]).filter(Boolean);
}

export function isRestrictedToSingleEntity() {
  const keys = allowedEntityKeys();
  return keys !== null && keys.length === 1;
}

export function isViewer() {
  const u = readUser();
  return (u.role || '').toLowerCase() === 'viewer';
}

export function defaultEntityKey(fallback = 'next2megroup') {
  const keys = allowedEntityKeys();
  if (keys && keys.length >= 1) {
    const cur = localStorage.getItem('n2c_entity');
    if (cur && keys.includes(cur)) return cur;
    return keys[0];
  }
  return localStorage.getItem('n2c_entity') || fallback;
}

export function filterEntityMap(entityMap) {
  const keys = allowedEntityKeys();
  if (keys === null) return entityMap;
  const out = {};
  for (const k of Object.keys(entityMap)) {
    if (keys.includes(k)) out[k] = entityMap[k];
  }
  return out;
}

export { KEY_TO_LABEL, UUID_TO_KEY };
