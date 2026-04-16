<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>

<template>
  <section class="page">
    <header class="page__header">
      <h2 class="page__title">{{ title }}</h2>
      <p v-if="description" class="page__description">{{ description }}</p>
    </header>

    <div class="page__body">
      <slot>
        <div class="empty-state">
          <div class="empty-state__icon">·</div>
          <p>No data yet. This view will be implemented soon.</p>
        </div>
      </slot>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page__header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page__title {
  font-size: 1.35rem;
  margin: 0;
}

.page__description {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.page__body {
  background: var(--bg-card);
  border: 1px solid var(--border-subtle);
  border-radius: 10px;
  padding: 24px;
  min-height: 320px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px 24px;
  color: var(--text-muted);
}

.empty-state__icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #EEF2F7;
  color: var(--navy-800);
  display: grid;
  place-items: center;
  font-size: 28px;
  font-weight: 700;
}
</style>
