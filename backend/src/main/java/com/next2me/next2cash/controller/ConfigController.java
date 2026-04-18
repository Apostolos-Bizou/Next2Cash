package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.Transaction;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.repository.ConfigRepository;
import com.next2me.next2cash.service.CardExportService;
import com.next2me.next2cash.service.CardService;
import com.next2me.next2cash.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * ConfigController
 *
 * Security model (Phase E):
 *   - Class-level @PreAuthorize: only ADMIN and USER may hit any endpoint.
 *     ACCOUNTANT and VIEWER get 403 automatically.
 *   - Per-request: user must have access to the requested entityId
 *     (admin bypass; user-with-no-assignments legacy rule applies).
 *
 * Config records are per-entity (Config.entityId is NOT NULL).
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConfigController {

    private final ConfigRepository configRepository;
    private final UserAccessService userAccessService;
    private final CardService cardService;
    private final CardExportService cardExportService;

    /**
     * GET /api/config?entityId=X
     * Returns all active config for the given entity, grouped by type:
     *   categories, subcategories, accounts, paymentMethods.
     *
     * Access control:
     *   - Throws 401 if no/invalid JWT (via UserAccessService.getCurrentUser).
     *   - Throws 400 if entityId is null (handled by @RequestParam).
     *   - Throws 403 if user is not assigned to the requested entity
     *     (admin bypass; user with zero assignments = legacy "see all").
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
    public ResponseEntity<?> getConfig(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        List<Config> all = configRepository
            .findByEntityIdAndIsActiveTrueOrderBySortOrderAsc(entityId);

        List<Map<String, Object>> categories      = new ArrayList<>();
        List<Map<String, Object>> subcategories   = new ArrayList<>();
        List<Map<String, Object>> accounts        = new ArrayList<>();
        List<Map<String, Object>> paymentMethods  = new ArrayList<>();

        for (Config c : all) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key",       c.getConfigKey());
            item.put("value",     c.getConfigValue());
            item.put("parentKey", c.getParentKey());
            item.put("icon",      c.getIcon());

            switch (c.getConfigType()) {
                case "category"       -> categories.add(item);
                case "subcategory"    -> subcategories.add(item);
                case "account"        -> accounts.add(item);
                case "payment_method" -> paymentMethods.add(item);
            }
        }

        return ResponseEntity.ok(Map.of(
            "success",        true,
            "categories",     categories,
            "subcategories",  subcategories,
            "accounts",       accounts,
            "paymentMethods", paymentMethods
        ));
    }

    // ══════ Phase H v2 — Cards (user-defined karteles with rules) ══════

    /**
     * GET /api/config/cards?entityId=X
     * List all active cards for the given entity. Read-only to VIEWER too.
     */
    @GetMapping("/cards")
    @PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
    public ResponseEntity<?> listCards(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        List<Config> cards = cardService.listCards(entityId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (Config c : cards) {
            data.add(toCardDto(c));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data",    data,
            "total",   data.size()
        ));
    }

    /**
     * GET /api/config/cards/{id}/transactions?entityId=X&limit=2000&offset=0
     * Resolve the card's rule and return matched transactions (paginated in Java).
     */
    @GetMapping("/cards/{id}/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
    public ResponseEntity<?> getCardTransactions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestParam(defaultValue = "2000") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        CardService.CardTransactions result =
            cardService.getTransactionsForCard(id, entityId, limit, offset);

        List<Map<String, Object>> txns = new ArrayList<>();
        for (Transaction t : result.transactions()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",              t.getId());
            m.put("entityNumber",    t.getEntityNumber());
            m.put("docDate",         t.getDocDate());
            m.put("type",            t.getType());
            m.put("counterparty",    t.getCounterparty());
            m.put("category",        t.getCategory());
            m.put("subcategory",     t.getSubcategory());
            m.put("description",     t.getDescription());
            m.put("amount",          t.getAmount());
            m.put("amountPaid",      t.getAmountPaid());
            m.put("amountRemaining", t.getAmountRemaining());
            m.put("paymentStatus",   t.getPaymentStatus());
            m.put("paymentMethod",   t.getPaymentMethod());
            m.put("paymentDate",     t.getPaymentDate());
            m.put("recordStatus",    t.getRecordStatus());
            txns.add(m);
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "card",    toCardDto(result.card()),
            "data",    txns,
            "total",   result.total(),
            "limit",   result.limit(),
            "offset",  result.offset()
        ));
    }

    /**
     * GET /api/config/cards/{id}/summary?entityId=X
     * Returns 5 KPI aggregates for the card (expense total, paid, unpaid,
     * income, urgent). Read-only, accessible to VIEWER too.
     */
    @GetMapping("/cards/{id}/summary")
    @PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
    public ResponseEntity<?> getCardSummary(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        CardService.CardSummary s = cardService.getCardSummary(id, entityId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total",        s.total());
        data.put("paid",         s.paid());
        data.put("unpaid",       s.unpaid());
        data.put("income",       s.income());
        data.put("urgent",       s.urgent());
        data.put("countTotal",   s.countTotal());
        data.put("countPaid",    s.countPaid());
        data.put("countUnpaid",  s.countUnpaid());
        data.put("countIncome",  s.countIncome());
        data.put("countUrgent",  s.countUrgent());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "card",    toCardDto(s.card()),
            "data",    data
        ));
    }

    /**
     * POST /api/config/cards?entityId=X
     * Create a new card. VIEWER forbidden.
     */
    @PostMapping("/cards")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> createCard(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId,
            @RequestBody Map<String, Object> payload) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        Config card = cardService.createCard(entityId, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "data",    toCardDto(card)
        ));
    }

    /**
     * PUT /api/config/cards/{id}?entityId=X
     * Partial update (only fields in payload are touched). VIEWER forbidden.
     */
    @PutMapping("/cards/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> updateCard(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody Map<String, Object> payload) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        Config card = cardService.updateCard(id, entityId, payload);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data",    toCardDto(card)
        ));
    }

    /**
     * DELETE /api/config/cards/{id}?entityId=X
     * Soft delete — sets isActive=false. VIEWER forbidden.
     */
    @DeleteMapping("/cards/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> deleteCard(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        cardService.softDeleteCard(id, entityId);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Card deleted"
        ));
    }

    /**
     * GET /api/config/cards/{id}/export/excel?entityId=X&filename=YYYY
     *
     * Downloads the card as an XLSX file with two sheets:
     *   - "Σύνοψη" with 5 KPIs and title
     *   - "Κινήσεις" with a 10-column transaction table, zebra-striped
     *
     * @param filename Optional custom filename (without extension).
     *                 If omitted, defaults to Kartela_[SANITIZED]_DD-MM-YYYY.
     *                 The frontend modal typically supplies this.
     *                 Sanitization: Greek → ASCII transliteration, non-alphanumerics
     *                 collapsed to underscores, uppercased.
     *
     * Accessible to ADMIN, USER, VIEWER (read-only op).
     */
    @GetMapping("/cards/{id}/export/excel")
    @PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
    public ResponseEntity<byte[]> exportCardExcel(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestParam(required = false) String filename) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        byte[] bytes = cardExportService.generateExcel(id, entityId);

        String safeName = resolveFilename(filename, "Kartela",
            cardExportService.sanitizeForFilename(
                cardService.getCard(id, entityId).getConfigValue()),
            ".xlsx");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", safeName);
        headers.setContentLength(bytes.length);
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /**
     * GET /api/config/cards/{id}/export/pdf?entityId=X&filename=YYYY
     *
     * Downloads the card as an A4-landscape PDF with:
     *   - Header band (CashControl / Next2Me brand + timestamp)
     *   - Supplier block with card name
     *   - 5-column KPI row (navy, green, red, navy, orange)
     *   - Full transaction table, zebra-striped
     *
     * Uses embedded DejaVuSans font for full Greek/Unicode support.
     *
     * @param filename Optional custom filename (without extension). Same rules as Excel.
     *
     * Accessible to ADMIN, USER, VIEWER.
     */
    @GetMapping("/cards/{id}/export/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','USER','VIEWER')")
    public ResponseEntity<byte[]> exportCardPdf(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestParam(required = false) String filename) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        byte[] bytes = cardExportService.generatePdf(id, entityId);

        String safeName = resolveFilename(filename, "Kartela",
            cardExportService.sanitizeForFilename(
                cardService.getCard(id, entityId).getConfigValue()),
            ".pdf");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", safeName);
        headers.setContentLength(bytes.length);
        headers.setCacheControl("no-cache, no-store, must-revalidate");

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /**
     * Resolves the final filename for an export.
     * If the user supplied a custom name via the modal, sanitize and use it.
     * Otherwise fall back to the default pattern: [prefix]_[cardName]_DD-MM-YYYY.ext
     */
    private String resolveFilename(String userSupplied, String defaultPrefix,
                                    String sanitizedCardName, String ext) {
        if (userSupplied != null && !userSupplied.isBlank()) {
            String clean = cardExportService.sanitizeForFilename(userSupplied);
            return clean + ext;
        }
        String today = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        return defaultPrefix + "_" + sanitizedCardName + "_" + today + ext;
    }

    // ─── helper ───

    private static Map<String, Object> toCardDto(Config c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          c.getId());
        m.put("configKey",   c.getConfigKey());
        m.put("configValue", c.getConfigValue());
        m.put("parentKey",   c.getParentKey());
        m.put("icon",        c.getIcon());
        m.put("sortOrder",   c.getSortOrder());
        return m;
    }
}