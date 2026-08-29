import axios from 'axios';
import {toMaintenance} from "@/scripts/common";

/**
 * Client-side HTTP. Server-rendered pages do not use these instances — they go
 * through $fetch against the internal backend URL (see composables/useApi.ts),
 * because there is no session to forward and no localStorage to read.
 *
 * Base URLs are resolved lazily per request via an interceptor rather than
 * baked into axios.create(): import.meta.env.VITE_* does not exist under Nitro,
 * and useRuntimeConfig() cannot be called at module scope.
 */
const imageClient = axios.create({
  withCredentials: true, // to include cookies for session-based auth
  headers: {
    'Content-Type': 'application/json',
  },
});

const apiClient = axios.create({
  withCredentials: true, // to include cookies for session-based auth
  headers: {
    'Content-Type': 'application/json',
  },
});

imageClient.interceptors.request.use(
  function (config) {
    config.baseURL ||= useRuntimeConfig().public.imgUrl
    return config
  },
  function (error) {
    return Promise.reject(error)
  }
)

apiClient.interceptors.request.use(
  function (config) {
    config.baseURL ||= useRuntimeConfig().public.apiUrl
    return config
  },
  function (error) {
    return Promise.reject(error)
  }
)

apiClient.interceptors.response.use(
  function (response){
    return response
  },
  function (error) {
    if (error.code == "ERR_NETWORK" || error.status == 502) {
      if (import.meta.client) toMaintenance()
    } else {
      return Promise.reject(error)
    }
  }
);

export {
  apiClient,
  imageClient,
}

export default apiClient;
