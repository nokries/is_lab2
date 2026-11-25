import { ref } from 'vue'

const state = ref({
  visible: false,
  title: '',
  message: '',
  confirmText: 'Подтвердить',
  cancelText: 'Отмена',
  danger: false
})

let resolver = null

export function confirmAction({ title = 'Подтвердите действие', message = '', confirmText = 'Подтвердить', cancelText = 'Отмена', danger = false } = {}) {
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

export function useConfirmState() {
  return {
    state,
    accept: () => close(true),
    cancel: () => close(false)
  }
}
