// Своё окно подтверждения вместо стандартного confirm() браузера.
// Использование:  if (!(await confirmAction({ message: '...' }))) return
import { ref } from 'vue'

// Состояние окна общее для всего приложения: само окно (ConfirmDialog.vue) показывается один раз в App.vue
const state = ref({
  visible: false,
  title: '',
  message: '',
  confirmText: 'Подтвердить',
  cancelText: 'Отмена',
  danger: false
})

let resolver = null

// Открывает окно и возвращает Promise: true - нажали «подтвердить», false - «отмена» / закрыли окно
export function confirmAction({ title = 'Подтвердите действие', message = '', confirmText = 'Подтвердить', cancelText = 'Отмена', danger = false } = {}) {
  // Если окно уже открыто, предыдущий вопрос считаем отменённым
  if (resolver) {
    resolver(false)
  }
  state.value = { visible: true, title, message, confirmText, cancelText, danger }
  return new Promise((resolve) => {
    resolver = resolve
  })
}

function close(result) {
  state.value.visible = false
  if (resolver) {
    resolver(result)
    resolver = null
  }
}

// Для компонента ConfirmDialog
export function useConfirmState() {
  return {
    state,
    accept: () => close(true),
    cancel: () => close(false)
  }
}
