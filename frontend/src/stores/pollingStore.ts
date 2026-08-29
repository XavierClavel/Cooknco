// stores/usePollingStore.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import apiClient from "@/scripts/axios";

export const usePollingStore = defineStore('polling', () => {
  const data = ref<any>({usersPending: []})
  const isLoading = ref(false)
  const error = ref<Error | null>(null)
  let intervalId: ReturnType<typeof setInterval> | null = null

  const fetchData = async () => {
    isLoading.value = true
    error.value = null
    try {
      const response = await apiClient.get('/notification') // replace with your URL
      data.value = response.data
      console.log(data.value)
    } catch (err: any) {
      error.value = err
    } finally {
      isLoading.value = false
    }
  }


  const startPolling = () => {
    console.log("starting polling")
    if (intervalId !== null) return // prevent duplicates
    fetchData()
    intervalId = setInterval(fetchData, 5 * 60 * 1000)
  }

  const stopPolling = () => {
    if (intervalId) {
      clearInterval(intervalId)
      intervalId = null
    }
  }

  return { data, isLoading, error, fetchData, startPolling, stopPolling }
})
