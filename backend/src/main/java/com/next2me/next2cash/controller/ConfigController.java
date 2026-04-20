package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.model.User;
import com.next2me.next2cash.model.CompanyEntity;
import com.next2me.next2cash.repository.CompanyEntityRepository;
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
    private final CompanyEntityRepository companyEntityRepository;
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

            String configType = c.getConfigType();
            if ("category".equals(configType)) {
                categories.add(item);
            } else if ("subcategory".equals(configType)) {
                subcategories.add(item);
            } else if ("account".equals(configType)) {
                accounts.add(item);
            } else if ("payment_method".equals(configType)) {
                paymentMethods.add(item);
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


    @GetMapping("/entities")
    @PreAuthorize("hasAnyRole('ADMIN','USER','ACCOUNTANT','VIEWER')")
    public ResponseEntity<?> listEntities() {
        List<CompanyEntity> active = companyEntityRepository.findAll()
            .stream()
            .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
            .sorted((a, b) -> Integer.compare(
                a.getSortOrder() != null ? a.getSortOrder() : 0,
                b.getSortOrder() != null ? b.getSortOrder() : 0))
            .toList();

        List<Map<String, Object>> data = new ArrayList<>();
        for (CompanyEntity e : active) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("code", e.getCode());
            m.put("name", e.getName());
            m.put("icon", e.getIcon());
            m.put("color", e.getColor());
            m.put("currency", e.getCurrency());
            m.put("country", e.getCountry());
            data.add(m);
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data,
            "total", data.size()
        ));
    }

    // β•β•β•β•β•β• Phase H v2 + Phase K β€” Cards (karteles with rules) β•β•β•β•β•β•

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
            m.put("blobFileIds",     r.blobFileIds());
            m.put("entityId",        r.entityId());
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

    // β”€β”€β”€ Export endpoints (unchanged β€” Phase K.3 will migrate to rows) β”€β”€β”€

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

    // ===================================================================
    //  M.7 β€” CRUD for Config items (categories, subcategories, etc.)
    // ===================================================================

    /**
     * GET /api/config/items?entityId=X[&configType=category]
     * Returns ALL config items (including inactive) for admin management.
     */
    @GetMapping("/items")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<?> listAllConfigItems(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId,
            @RequestParam(required = false) String configType) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        List<Config> items;
        if (configType != null && !configType.isBlank()) {
            items = configRepository.findByEntityIdAndConfigTypeOrderBySortOrderAsc(entityId, configType);
        } else {
            items = configRepository.findByEntityIdOrderBySortOrderAsc(entityId);
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Config c : items) {
            data.add(toConfigItemDto(c));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data",    data,
            "total",   data.size()
        ));
    }

    /**
     * POST /api/config/items?entityId=X
     * Create a new config item (category, subcategory, payment_method, account).
     */
    @PostMapping("/items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createConfigItem(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam UUID entityId,
            @RequestBody Map<String, Object> payload) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        String type = (String) payload.get("configType");
        String key  = (String) payload.get("configKey");
        if (type == null || type.isBlank() || key == null || key.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error",   "configType and configKey are required"
            ));
        }

        Config c = new Config();
        c.setEntityId(entityId);
        c.setConfigType(type);
        c.setConfigKey(key);
        c.setConfigValue((String) payload.getOrDefault("configValue", key));
        c.setParentKey((String) payload.get("parentKey"));
        c.setIcon((String) payload.get("icon"));
        c.setIsActive(true);

        // Auto sort_order: max + 1
        Integer maxSort = configRepository.findMaxSortOrder(entityId, type);
        c.setSortOrder(maxSort != null ? maxSort + 1 : 1);

        Config saved = configRepository.save(c);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "data",    toConfigItemDto(saved)
        ));
    }

    /**
     * PUT /api/config/items/{id}?entityId=X
     * Update an existing config item.
     */
    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateConfigItem(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId,
            @RequestBody Map<String, Object> payload) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        Config c = configRepository.findByIdAndEntityId(id, entityId)
            .orElse(null);
        if (c == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "error",   "Config item not found"
            ));
        }

        if (payload.containsKey("configKey"))   c.setConfigKey((String) payload.get("configKey"));
        if (payload.containsKey("configValue")) c.setConfigValue((String) payload.get("configValue"));
        if (payload.containsKey("parentKey"))   c.setParentKey((String) payload.get("parentKey"));
        if (payload.containsKey("icon"))        c.setIcon((String) payload.get("icon"));
        if (payload.containsKey("sortOrder"))   c.setSortOrder(((Number) payload.get("sortOrder")).intValue());
        if (payload.containsKey("isActive"))    c.setIsActive((Boolean) payload.get("isActive"));

        Config saved = configRepository.save(c);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data",    toConfigItemDto(saved)
        ));
    }

    /**
     * DELETE /api/config/items/{id}?entityId=X
     * Soft-delete (set isActive=false).
     */
    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteConfigItem(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @RequestParam UUID entityId) {

        User currentUser = userAccessService.getCurrentUser(authHeader);
        userAccessService.assertCanAccessEntity(currentUser, entityId);

        Config c = configRepository.findByIdAndEntityId(id, entityId)
            .orElse(null);
        if (c == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "error",   "Config item not found"
            ));
        }

        c.setIsActive(false);
        configRepository.save(c);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Config item deactivated"
        ));
    }

    private static Map<String, Object> toConfigItemDto(Config c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",          c.getId());
        m.put("configType",  c.getConfigType());
        m.put("configKey",   c.getConfigKey());
        m.put("configValue", c.getConfigValue());
        m.put("parentKey",   c.getParentKey());
        m.put("icon",        c.getIcon());
        m.put("sortOrder",   c.getSortOrder());
        m.put("isActive",    c.getIsActive());
        return m;
    }

    // β”€β”€β”€ helper β”€β”€β”€

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
