package com.next2me.next2cash.controller;

import com.next2me.next2cash.model.Config;
import com.next2me.next2cash.repository.ConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConfigController {

    private final ConfigRepository configRepository;

    // GET /api/config?entityId=X
    // Returns all config grouped by type: categories, subcategories, accounts, paymentMethods
    @GetMapping
    public ResponseEntity<?> getConfig(@RequestParam UUID entityId) {

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
}