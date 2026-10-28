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
  'baf68357-f959-43cf-9d8c-4170abe2ffc0': 'polaris',
};

const KEY_TO_LABEL = {
  next2me: 'Next2Me',
  house: 'House',
  next2megroup: 'Next2Me Group',
  polaris: 'Polaris',
};

// ── Derived views of the same map ──────────────────────────────────────
// Every view used to carry its own copy of these (key→UUID, key→{id,label},
// [{id,code,name}] …). Polaris was missed in those copies twice, so now the
// shapes the views need are derived here from UUID_TO_KEY / KEY_TO_LABEL.
// To add an entity: add ONE line to UUID_TO_KEY and ONE to KEY_TO_LABEL.

// key → UUID  (e.g. ENTITIES[selectedEntity] in Dashboard/Transactions/…)
export const KEY_TO_UUID = Object.fromEntries(
  Object.entries(UUID_TO_KEY).map(([id, key]) => [key, id])
);

// UUID → label (e.g. ScenariosView entityName(id))
export const UUID_TO_LABEL = Object.fromEntries(
  Object.entries(UUID_TO_KEY).map(([id, key]) => [id, KEY_TO_LABEL[key]])
);

// key → { id, label }  (ForecastView / PricingCalculatorView / DocumentsView shape)
export const ENTITY_DETAILS = Object.fromEntries(
  Object.entries(KEY_TO_UUID).map(([key, id]) => [key, { id, label: KEY_TO_LABEL[key] }])
);

// [{ id, key, code, name, label }]  — same field names /api/config/entities
// returns (id/code/name), so it doubles as the offline fallback for AdminView
// and as the option list for ProjectsView.
export const ENTITY_OPTIONS = Object.entries(KEY_TO_UUID).map(([key, id]) => ({
  id, key, code: key, name: KEY_TO_LABEL[key], label: KEY_TO_LABEL[key],
}));

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

// S100: filter an array of API-loaded entity objects (from /api/config/entities)
// by the current user's allowed entity UUIDs. Used by per-view dropdowns
// (CalendarView, etc.) to prevent restricted users (e.g. investors) from
// seeing entity names they shouldn't know about.
//
// arr: array of objects with an `id` field that is a UUID matching entities.id
// idField: field name to match on (defaults to 'id')
// Returns: filtered array (or the original if user has no restrictions)
export function filterApiEntities(arr, idField = 'id') {
  if (!Array.isArray(arr)) return arr;
  const ids = allowedEntityIds();
  if (ids === null) return arr;  // null = no restriction (admin), pass through
  const allowedSet = new Set(ids);
  return arr.filter(e => e && allowedSet.has(e[idField]));
}

export { KEY_TO_LABEL, UUID_TO_KEY };
