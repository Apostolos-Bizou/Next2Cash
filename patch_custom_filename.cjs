// patch_custom_filename.cjs
// Adds optional customFileName parameter to upload endpoint
// Run from: C:\Users\akage\Documents\Next2Cash
const fs = require('fs');
const FILE = 'backend/src/main/java/com/next2me/next2cash/controller/DocumentController.java';
let src = fs.readFileSync(FILE, 'utf8');
src = src.replace(/\r\n/g, '\n');

// Idempotent check
if (src.includes('customFileName')) {
  console.error('[ABORT] already patched \u2014 customFileName exists');
  process.exit(2);
}

let patchCount = 0;

// PATCH 1: Add customFileName parameter to method signature
const sigAnchor = '@RequestParam MultipartFile file) throws IOException {';
if (!src.includes(sigAnchor)) {
  console.error('[ABORT] P1: cannot find method signature anchor');
  process.exit(3);
}
src = src.replace(sigAnchor,
  '@RequestParam MultipartFile file,\n' +
  '            @RequestParam(required = false) String customFileName) throws IOException {');
patchCount++;
console.log('[P1] customFileName parameter added to signature');

// PATCH 2: After autoFileName is generated, check for customFileName override
// Anchor: the line that builds autoFileName
const autoAnchor = 'String autoFileName = String.format("%s_%s_%d.%s",';
if (!src.includes(autoAnchor)) {
  console.error('[ABORT] P2: cannot find autoFileName anchor');
  process.exit(3);
}

// Find the line AFTER "safeCounterparty, docDateStr, seq, fileExt);"
const afterAutoAnchor = 'safeCounterparty, docDateStr, seq, fileExt);';
if (!src.includes(afterAutoAnchor)) {
  console.error('[ABORT] P2: cannot find autoFileName closing line');
  process.exit(3);
}
src = src.replace(afterAutoAnchor, afterAutoAnchor + `

        // Phase M.2.2: custom filename override (if provided by frontend)
        if (customFileName != null && !customFileName.isBlank()) {
            // Sanitize custom name: remove dangerous chars, keep extension
            String safeName = customFileName.trim()
                .replaceAll("[\\\\\\\\/:*?\\"<>|]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
            // Ensure it has the correct extension
            if (!safeName.toLowerCase().endsWith("." + fileExt)) {
                safeName = safeName.replaceAll("\\\\.[^.]*$", "") + "." + fileExt;
            }
            if (safeName.length() > 100) {
                safeName = safeName.substring(0, 96) + "." + fileExt;
            }
            if (!safeName.isBlank() && !safeName.equals("." + fileExt)) {
                autoFileName = safeName;
            }
        }`);
patchCount++;
console.log('[P2] customFileName override logic added');

// POST-CHECK
const checks = ['customFileName', 'Phase M.2.2', 'safeName', 'required = false'];
for (const c of checks) {
  if (!src.includes(c)) {
    console.error('[ABORT] post-check missing: ' + c);
    process.exit(4);
  }
}

fs.writeFileSync(FILE, src, 'utf8');
console.log('[OK] ' + patchCount + ' patches applied to ' + FILE);
