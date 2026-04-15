<script setup>
import PagePlaceholder from '@/components/PagePlaceholder.vue'

const kpis = [
  { label: 'Cash position', value: '— —', hint: 'consolidated group' },
  { label: 'Inflows (MTD)',  value: '— —', hint: 'collected to date' },
  { label: 'Outflows (MTD)', value: '— —', hint: 'settled to date' },
  { label: 'Open obligations', value: '—', hint: 'awaiting action' }
]
</script>

<template>
  <PagePlaceholder
    title="Dashboard"
    description="Overview of the group's cash position, recent movements, and pending actions."
  >
    <div class="kpi-grid">
      <div v-for="k in kpis" :key="k.label" class="kpi">
        <div class="kpi__label">{{ k.label }}</div>
        <div class="kpi__value">{{ k.value }}</div>
        <div class="kpi__hint">{{ k.hint }}</div>
      </div>
    </div>
  </PagePlaceholder>
</template>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}

.kpi {
  background: #F7F9FC;
  border: 1px solid var(--border-subtle);
  border-radius: 8px;
  padding: 16px;
}

.kpi__label {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
}

.kpi__value {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--navy-800);
  margin: 8px 0 4px;
}

.kpi__hint {
  font-size: 12px;
  color: var(--text-muted);
}
</style>
