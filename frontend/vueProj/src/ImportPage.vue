<script setup>
import { ref, onMounted } from 'vue'
import { Upload, FileText, X, Loader2, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { api } from './api.js'
import { useAuth } from './composables/useAuth.js'
import { showApiError, showApiSuccess } from './utils/errorHandler.js'

const { isAdmin } = useAuth()

const fileInput = ref(null)
const selectedFile = ref(null)
const isDragging = ref(false)
const uploading = ref(false)
const history = ref([])
const loadingHistory = ref(false)
const currentPage = ref(1)
const totalPages = ref(0)

function isYaml(file) {
  const name = file.name.toLowerCase()
  return name.endsWith('.yaml') || name.endsWith('.yml')
}

function selectFile(file) {
  if (!file) return
  if (!isYaml(file)) {
    showApiError({ title: 'Некорректный файл', message: 'Допускаются только файлы формата .yaml или .yml' })
    return
  }
  selectedFile.value = file
}

function handleFileSelect(event) {
  selectFile(event.target.files[0])
  event.target.value = ''
}

function handleDrop(event) {
  isDragging.value = false
  selectFile(event.dataTransfer.files[0])
}

function removeFile() {
  selectedFile.value = null
}

function formatFileSize(bytes) {
  if (bytes < 1024) return bytes + ' Б'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' КБ'
  return (bytes / (1024 * 1024)).toFixed(1) + ' МБ'
}

async function uploadFile() {
  if (!selectedFile.value) return

  uploading.value = true
  try {
    const result = await api.uploadImportFile(selectedFile.value)
    showApiSuccess(`Импорт завершён (операция №${result.id}): добавлено объектов - ${result.addedCount}`)
    selectedFile.value = null
  } catch (error) {
    showApiError({ title: error.title || 'Ошибка импорта', message: error.message })
  } finally {
    uploading.value = false
    currentPage.value = 1
    await loadHistory(true)
  }
}

const historyCache = new Map()

async function loadHistory(force = false) {
  const page = currentPage.value

  if (!force && historyCache.has(page)) {
    const cached = historyCache.get(page)
    history.value = cached.content
    totalPages.value = cached.totalPages
    return
  }

  if (force) {
    historyCache.clear()
  }

  loadingHistory.value = true
  try {
    const data = await api.getImportHistory(page)
    historyCache.set(page, { content: data.content, totalPages: data.totalPages })
    history.value = data.content
    totalPages.value = data.totalPages
  } catch (error) {
    showApiError({ title: error.title || 'Ошибка', message: error.message })
  } finally {
    loadingHistory.value = false
  }
}

function changePage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
  loadHistory()
}

function statusClass(status) {
  switch (status) {
    case 'SUCCESS': return 'bg-green-100 text-green-800'
    case 'FAILED': return 'bg-red-100 text-red-800'
    default: return 'bg-blue-100 text-blue-800'
  }
}

function statusText(status) {
  switch (status) {
    case 'SUCCESS': return 'Успешно'
    case 'FAILED': return 'Ошибка'
    case 'IN_PROGRESS': return 'Выполняется'
    default: return status
  }
}

function formatDate(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(() => {
  loadHistory(true)
})
</script>

<template>
  <div class="bg-gray-50 p-6">
    <div class="max-w-6xl mx-auto space-y-6">
      <div class="bg-white rounded-lg shadow-sm p-6">
        <h2 class="text-2xl font-bold text-gray-900 mb-1">Импорт объектов из YAML-файла</h2>
        <p class="text-sm text-gray-500 mb-4">
          Файл импортируется целиком одной транзакцией: если хотя бы одна запись не пройдёт проверку,
          не будет добавлено ни одного объекта.
        </p>

        <div
            @dragover.prevent
            @drop.prevent="handleDrop"
            @dragenter.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            class="border-2 border-dashed rounded-lg p-8 text-center transition-colors"
            :class="isDragging ? 'border-blue-500 bg-blue-50' : 'border-gray-300 hover:border-blue-400'"
        >
          <input
              ref="fileInput"
              type="file"
              accept=".yaml,.yml"
              class="hidden"
              @change="handleFileSelect"
          />

          <Upload class="mx-auto h-12 w-12 text-gray-400 mb-4" />
          <p class="text-gray-600 mb-2">
            Перетащите файл сюда или
            <button @click="fileInput?.click()" class="text-blue-600 hover:text-blue-700 font-medium">
              выберите файл
            </button>
          </p>
          <p class="text-gray-400 text-sm">Один файл формата .yaml или .yml</p>
        </div>

        <div v-if="selectedFile" class="mt-4 flex items-center justify-between p-3 bg-gray-50 rounded-lg">
          <div class="flex items-center gap-3">
            <FileText class="h-5 w-5 text-gray-500" />
            <span class="text-sm font-medium text-gray-700">{{ selectedFile.name }}</span>
            <span class="text-xs text-gray-400">{{ formatFileSize(selectedFile.size) }}</span>
          </div>
          <button @click="removeFile" class="p-1 hover:bg-gray-200 rounded transition-colors">
            <X class="h-4 w-4 text-gray-500" />
          </button>
        </div>

        <div class="mt-4">
          <button
              @click="uploadFile"
              :disabled="!selectedFile || uploading"
              class="px-6 py-2 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
          >
            <Loader2 v-if="uploading" class="h-4 w-4 animate-spin" />
            {{ uploading ? 'Импорт...' : 'Импортировать' }}
          </button>
        </div>
      </div>

      <div class="bg-white rounded-lg shadow-sm p-6">
        <div class="flex justify-between items-center mb-1">
          <h2 class="text-2xl font-bold text-gray-900">История импорта</h2>
          <button
              @click="loadHistory(true)"
              class="p-2 hover:bg-gray-100 rounded-lg transition-colors"
              :disabled="loadingHistory"
              title="Обновить"
          >
            <RefreshCw class="h-5 w-5 text-gray-500" :class="{ 'animate-spin': loadingHistory }" />
          </button>
        </div>
        <p class="text-sm text-gray-500 mb-4">
          {{ isAdmin ? 'Показаны операции всех пользователей.' : 'Показаны только ваши операции.' }}
        </p>

        <div v-if="history.length === 0" class="text-center py-8 text-gray-500">
          {{ loadingHistory ? 'Загрузка истории...' : 'История импорта пуста' }}
        </div>

        <div v-else class="overflow-x-auto">
          <table class="w-full">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">ID</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Статус</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Пользователь</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Файл</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Добавлено объектов</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Начало</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Завершение</th>
                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Ошибка</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              <tr v-for="item in history" :key="item.id" class="hover:bg-gray-50 align-top">
                <td class="px-4 py-3 text-sm text-gray-900">{{ item.id }}</td>
                <td class="px-4 py-3 text-sm">
                  <span class="px-2 py-1 rounded-full text-xs font-medium" :class="statusClass(item.status)">
                    {{ statusText(item.status) }}
                  </span>
                </td>
                <td class="px-4 py-3 text-sm text-gray-600">{{ item.username }}</td>
                <td class="px-4 py-3 text-sm text-gray-600">{{ item.fileName || '-' }}</td>
                <td class="px-4 py-3 text-sm text-gray-600">
                  {{ item.status === 'SUCCESS' ? item.addedCount : '-' }}
                </td>
                <td class="px-4 py-3 text-sm text-gray-600">{{ formatDate(item.startTime) }}</td>
                <td class="px-4 py-3 text-sm text-gray-600">{{ formatDate(item.endTime) }}</td>
                <td class="px-4 py-3 text-xs text-red-600 max-w-md break-words">
                  {{ item.status === 'FAILED' ? item.errorMessage : '' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="totalPages > 1" class="mt-4 flex justify-center items-center gap-2">
          <button
              @click="changePage(currentPage - 1)"
              :disabled="currentPage <= 1"
              class="p-1 border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50"
          >
            <ChevronLeft class="h-5 w-5" />
          </button>
          <span class="px-3 py-1 text-sm text-gray-600">{{ currentPage }} / {{ totalPages }}</span>
          <button
              @click="changePage(currentPage + 1)"
              :disabled="currentPage >= totalPages"
              class="p-1 border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50"
          >
            <ChevronRight class="h-5 w-5" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
