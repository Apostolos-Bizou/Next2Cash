import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * UI store — holds global UI state like sidebar collapse.
 */
export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(false)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return { sidebarCollapsed, toggleSidebar }
})
