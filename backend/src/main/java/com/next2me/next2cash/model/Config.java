package com.next2me.next2cash.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "config")
public class Config {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "config_type", nullable = false)
    private String configType;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "config_value")
    private String configValue;

    @Column(name = "parent_key")
    private String parentKey;

    @Column(name = "icon")
    private String icon;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Explicit no-arg constructor (JPA requirement; made explicit for clarity).
    public Config() {}

    // Getters
    public UUID getId() { return id; }
    public UUID getEntityId() { return entityId; }
    public String getConfigType() { return configType; }
    public String getConfigKey() { return configKey; }
    public String getConfigValue() { return configValue; }
    public String getParentKey() { return parentKey; }
    public String getIcon() { return icon; }
    public Integer getSortOrder() { return sortOrder; }
    public Boolean getIsActive() { return isActive; }

    // Setters (added Phase H v2 — required for POST/PUT endpoints)
    public void setId(UUID id) { this.id = id; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public void setConfigType(String configType) { this.configType = configType; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public void setParentKey(String parentKey) { this.parentKey = parentKey; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}