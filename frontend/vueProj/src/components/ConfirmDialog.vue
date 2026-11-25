<template>
  <!-- Окно подтверждения: тёмная подложка на весь экран и белая карточка по центру (в стиле остальных окон) -->
  <Transition name="confirm">
    <div
        v-if="state.visible"
        class="fixed inset-0 z-[9998] flex items-center justify-center bg-black/40 p-4"
        @click.self="cancel"
        @keydown.esc="cancel"
    >
      <div
          ref="dialogRef"
          tabindex="-1"
          role="dialog"
          aria-modal="true"
          class="bg-white rounded-lg shadow-xl w-full max-w-md p-6 outline-none"
      >
        <div class="flex items-start gap-4">
          <!-- Иконка: красная для опасных действий (удаление), иначе жёлтая -->
          <div
              :class="[
              'flex-shrink-0 flex items-center justify-center h-10 w-10 rounded-full',
              state.danger ? 'bg-red-100 text-red-600' : 'bg-yellow-100 text-yellow-600'
            ]"
          >
            <AlertTriangle :size="22" />
          </div>
          <div class="flex-1">
            <h3 class="text-lg font-semibold text-gray-900">{{ state.title }}</h3>
            <p class="mt-2 text-sm text-gray-600">{{ state.message }}</p>
          </div>
        </div>

        <div class="mt-6 flex justify-end gap-3">
          <button
              @click="cancel"
              class="px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
          >
            {{ state.cancelText }}
          </button>
          <button
              @click="accept"
              :class="[
              'px-4 py-2 text-sm font-medium text-white rounded-lg transition-colors',
              state.danger ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-600 hover:bg-blue-700'
            ]"
          >
            {{ state.confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { AlertTriangle } from 'lucide-vue-next'
import { useConfirmState } from '../composables/useConfirm.js'

const { state, accept, cancel } = useConfirmState()
const dialogRef = ref(null)

// Когда окно открылось, переводим на него фокус, чтобы работала клавиша Esc
watch(() => state.value.visible, async (visible) => {
  if (visible) {
    await nextTick()
    dialogRef.value && dialogRef.value.focus()
  }
})
</script>

<style scoped>
.confirm-enter-active,
.confirm-leave-active {
  transition: opacity 0.15s ease;
}
.confirm-enter-from,
.confirm-leave-to {
  opacity: 0;
}
</style>
