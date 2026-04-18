package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Config;
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

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConfigController {

    private final ConfigRepository configRepository;
    private final UserAccessService userAccessService;
    private final CardService cardService;
    private final CardExportService cardExportService;

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

    // ══════ Phase H v2 + Phase K — Cards (karteles with rules) ══════

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
     *
     * Phase K: now returns a unified timeline (transactions + payments) as
     * CardRow objects. Each row has a recordSource field ("TRANSACTION" or
     * "PAYMENT") that the frontend uses for styling.
     *
     * Response shape is kept flat for simple frontend rendering.
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

        CardService.CardRows result =
            cardService.getRowsForCard(id, entityId, limit, offset);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CardService.CardRow r : result.rows()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordSource",    r.recordSource());
            m.put("id",              r.id());
            m.put("entityNumber",    r.entityNumber());
            m.put("docDate",         r.docDate());
            m.put("type",            r.type());
            m.put("counterparty",    r.counterparty());
            m.put("category",        r.category());
            m.put("subcategory",     r.subcategory());
            m.put("description",     r.description());
            m.put("amount",          r.amount());
            m.put("amountPaid",      r.amountPaid());
            m.put("amountRemaining", r.amountRemaining());
            m.put("paymentStatus",   r.paymentStatus());
            m.put("paymentMethod",   r.paymentMethod());
            m.put("paymentDate",     r.paymentDate());
            m.put("recordStatus",    r.recordStatus());
            if (r.parentTransactionId() != null) {
                m.put("parentTransactionId", r.parentTransactionId());
            }
            rows.add(m);
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "card",    toCardDto(result.card()),
            "data",    rows,
            "total",   result.total(),
            "limit",   result.limit(),
            "offset",  result.offset()
        ));
    }

    /**
     * GET /api/config/cards/{id}/summary?entityId=X
     * Phase K: adds paymentsTotal + countPayments (6th KPI).
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
        data.put("total",          s.total());
        data.put("paid",           s.paid());
        data.put("unpaid",         s.unpaid());
        data.put("income",         s.income());
        data.put("urgent",         s.urgent());
        data.put("paymentsTotal",  s.paymentsTotal());
        data.put("countTotal",     s.countTotal());
        data.put("countPaid",      s.countPaid());
        data.put("countUnpaid",    s.countUnpaid());
        data.put("countIncome",    s.countIncome());
        data.put("countUrgent",    s.countUrgent());
        data.put("countPayments",  s.countPayments());

        return ResponseEntity.ok(Map.of(
            "success", true,
            "card",    toCardDto(s.card()),
            "data",    data
        ));
    }

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

    // ─── Export endpoints (unchanged — Phase K.3 will migrate to rows) ───

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