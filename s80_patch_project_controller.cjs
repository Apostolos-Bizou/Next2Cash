#!/usr/bin/env node
/**
 * S80 Patch v2: Add proactive status whitelist validation to ProjectController.
 *
 * ASCII-only (no box-drawing chars) to avoid mojibake on PowerShell Cp1252 console.
 *
 * - In createProject: validate dto.status (if provided) BEFORE constructing Project
 * - In updateProject: validate dto.status (if provided) BEFORE p.setStatus(...)
 *
 * Validation uses ProjectStatus.isValid() and throws IllegalArgumentException
 * with a clear message including the list of valid values.
 *
 * GlobalExceptionHandler catches IllegalArgumentException and returns:
 *   HTTP 400 + { "success": false, "error": "..." }
 *
 * Idempotent: re-running detects the existing validation and skips.
 */

const fs = require('fs');
const path = require('path');

const TARGET = path.resolve(
  __dirname,
  'backend',
  'src',
  'main',
  'java',
  'com',
  'next2me',
  'next2cash',
  'controller',
  'ProjectController.java'
);

const BACKUP = TARGET + '.s80.bak';

// === 1. Read & sanity check ===
if (!fs.existsSync(TARGET)) {
  console.error('FATAL: target file not found:', TARGET);
  process.exit(1);
}

let src = fs.readFileSync(TARGET, 'utf8');
// CRLF -> LF for reliable string matching (will restore on write)
const hadCRLF = src.includes('\r\n');
src = src.replace(/\r\n/g, '\n');

console.log('Read:', TARGET);
console.log('  Size:', src.length, 'bytes, CRLF=', hadCRLF);

// === 2. Idempotency check ===
if (src.includes('ProjectStatus.isValid')) {
  console.log('SKIP: ProjectController already contains ProjectStatus.isValid() - patch already applied');
  process.exit(0);
}

// === 3. Backup ===
fs.writeFileSync(BACKUP, hadCRLF ? src.replace(/\n/g, '\r\n') : src, 'utf8');
console.log('Backup:', BACKUP);

// === 4. Add import ===
const importAnchor = 'import com.next2me.next2cash.model.Transaction;';
const newImport = 'import com.next2me.next2cash.model.ProjectStatus;';

if (!src.includes(importAnchor)) {
  console.error('FATAL: import anchor not found:', importAnchor);
  process.exit(2);
}
if (!src.includes(newImport)) {
  src = src.replace(importAnchor, importAnchor + '\n' + newImport);
  console.log('Added import: ProjectStatus');
} else {
  console.log('Import ProjectStatus already present');
}

// === 5. Patch createProject ===
// Anchor: the duplicate-name check just before "Project p = new Project();"
const createAnchor = `        if (projectRepository.findByName(dto.name).isPresent()) {
            response.put("success", false);
            response.put("error", "Project with this name already exists");
            return ResponseEntity.status(409).body(response);
        }

        Project p = new Project();`;

const createReplacement = `        if (projectRepository.findByName(dto.name).isPresent()) {
            response.put("success", false);
            response.put("error", "Project with this name already exists");
            return ResponseEntity.status(409).body(response);
        }
        // S80: proactive status whitelist check (before hitting DB CHECK constraint)
        if (dto.status != null && !dto.status.isBlank()) {
            String requested = dto.status.toUpperCase();
            if (!ProjectStatus.isValid(requested)) {
                throw new IllegalArgumentException(
                    "Invalid value '" + dto.status + "' for field 'status'. Allowed: "
                        + ProjectStatus.validValuesAsString());
            }
        }

        Project p = new Project();`;

if (!src.includes(createAnchor)) {
  console.error('FATAL: createProject anchor not matched - file structure changed?');
  process.exit(3);
}
const createMatches = src.split(createAnchor).length - 1;
if (createMatches !== 1) {
  console.error('FATAL: createProject anchor matched', createMatches, 'times (must be 1)');
  process.exit(4);
}
src = src.replace(createAnchor, createReplacement);
console.log('Patched: createProject status validation');

// === 6. Patch updateProject ===
const updateAnchor =
  '        if (dto.status != null && !dto.status.isBlank()) p.setStatus(dto.status.toUpperCase());';

const updateReplacement =
  `        // S80: proactive status whitelist check (before hitting DB CHECK constraint)
        if (dto.status != null && !dto.status.isBlank()) {
            String requested = dto.status.toUpperCase();
            if (!ProjectStatus.isValid(requested)) {
                throw new IllegalArgumentException(
                    "Invalid value '" + dto.status + "' for field 'status'. Allowed: "
                        + ProjectStatus.validValuesAsString());
            }
            p.setStatus(requested);
        }`;

if (!src.includes(updateAnchor)) {
  console.error('FATAL: updateProject anchor not matched');
  console.error('Looking for line:', updateAnchor);
  process.exit(5);
}
const updateMatches = src.split(updateAnchor).length - 1;
if (updateMatches !== 1) {
  console.error('FATAL: updateProject anchor matched', updateMatches, 'times (must be 1)');
  process.exit(6);
}
src = src.replace(updateAnchor, updateReplacement);
console.log('Patched: updateProject status validation');

// === 7. Post-write verification ===
const checks = [
  { name: 'import ProjectStatus',         pattern: 'import com.next2me.next2cash.model.ProjectStatus;' },
  { name: 'S80 comment present',          pattern: 'S80: proactive status whitelist check (before hitting DB CHECK constraint)' },
  { name: 'isValid called',               pattern: 'ProjectStatus.isValid(requested)' },
  { name: 'validValuesAsString called',   pattern: 'ProjectStatus.validValuesAsString()' },
];

let allOk = true;
for (const c of checks) {
  const count = src.split(c.pattern).length - 1;
  if (count < 1) {
    console.error('VERIFY FAIL:', c.name, '(not found)');
    allOk = false;
  } else {
    console.log('VERIFY OK:', c.name, '(count=' + count + ')');
  }
}

// Sanity: the OLD status-setter pattern should NOT exist anymore in updateProject
if (src.includes('if (dto.status != null && !dto.status.isBlank()) p.setStatus(dto.status.toUpperCase());')) {
  console.error('VERIFY FAIL: old updateProject status setter still present');
  allOk = false;
}

// ASCII-only sanity (this patched output must have no mojibake)
let nonAscii = 0;
for (let i = 0; i < src.length; i++) {
  if (src.charCodeAt(i) > 127) nonAscii++;
}
if (nonAscii > 0) {
  console.log('NOTE: patched file contains', nonAscii, 'non-ASCII chars (likely original file content, not from patch)');
}

if (!allOk) {
  console.error('Restoring backup...');
  fs.copyFileSync(BACKUP, TARGET);
  process.exit(7);
}

// === 8. Write ===
const out = hadCRLF ? src.replace(/\n/g, '\r\n') : src;
fs.writeFileSync(TARGET, out, 'utf8');
console.log('Wrote:', TARGET);
console.log('  Size:', out.length, 'bytes');
console.log('OK - patch applied successfully');
