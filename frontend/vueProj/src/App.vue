<script setup>
// ref делает переменную «следимой»: когда она меняется, страница обновляется сама
import { ref } from 'vue'
import { LogOut, User } from 'lucide-vue-next'
// Подключаем таблицу, окно со специальными операциями, импорт и контейнер уведомлений
import MainTable from './MainTable.vue'
import SpecialOperations from './SpecialOperations.vue'
import ImportPage from './ImportPage.vue'
import AuthPage from './AuthPage.vue'
import ToastContainer from './components/ToastContainer.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
// ЛР2: данные о том, кто вошёл в систему
import { useAuth } from './composables/useAuth.js'

const { isAuthenticated, username, isAdmin, logout } = useAuth()

// Какая вкладка открыта сейчас: 'table', 'operations' или 'import'. Сначала открыта таблица
const currentTab = ref('table')

// Вкладки верхней панели
const tabs = [
  { id: 'table', label: 'Таблица объектов' },
  { id: 'operations', label: 'Специальные операции' },
  { id: 'import', label: 'Импорт данных' }
]

// Выход из системы: после него снова показывается страница входа
async function handleLogout() {
  await logout()
  currentTab.value = 'table'
}

// После входа открываем таблицу
function handleAuthenticated() {
  currentTab.value = 'table'
}
</script>

<template>
  <!-- Пока пользователь не вошёл, показываем только страницу входа / регистрации -->
  <AuthPage v-if="!isAuthenticated" @authenticated="handleAuthenticated" />

  <!-- Главный блок страницы: на всю высоту экрана, светло-серый фон -->
  <div v-else class="min-h-screen bg-gray-50">
    <!-- Здесь показываются всплывающие уведомления -->
    <ToastContainer />
    <!-- Окно подтверждения опасных действий (удаление и т.п.) -->
    <ConfirmDialog />
    <!-- Верхняя панель с вкладками и данными о пользователе -->
    <nav class="bg-white shadow-sm">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex justify-between h-16">
          <div class="flex">
            <div class="flex space-x-8">
              <!-- Кнопка вкладки. При нажатии меняем currentTab.
                   Если эта вкладка открыта, кнопка синяя, иначе серая -->
              <button
                  v-for="tab in tabs"
                  :key="tab.id"
                  @click="currentTab = tab.id"
                  :class="[
                  'inline-flex items-center px-4 py-2 border-b-2 text-sm font-medium transition-colors',
                  currentTab === tab.id
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                ]"
              >
                {{ tab.label }}
              </button>
            </div>
          </div>

          <!-- Кто вошёл и кнопка выхода -->
          <div class="flex items-center gap-4">
            <div class="flex items-center gap-2 text-sm text-gray-600">
              <User :size="18" />
              <span>{{ username }}</span>
              <span
                  v-if="isAdmin"
                  class="px-2 py-0.5 bg-purple-100 text-purple-700 rounded-full text-xs font-medium"
              >
                Админ
              </span>
            </div>
            <button
                @click="handleLogout"
                class="flex items-center gap-1 px-3 py-1.5 text-sm text-gray-600 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
            >
              <LogOut :size="16" />
              Выйти
            </button>
          </div>
        </div>
      </div>
    </nav>

    <!-- Показываем только ту часть, которая выбрана в currentTab.
         Остальные части при этом полностью удаляются со страницы -->
    <MainTable v-if="currentTab === 'table'" />
    <SpecialOperations v-if="currentTab === 'operations'" />
    <ImportPage v-if="currentTab === 'import'" />
  </div>
</template>

<!-- Своих стилей здесь нет, всё оформление через классы Tailwind -->
<style scoped></style>
