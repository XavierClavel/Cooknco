// src/axios.js
import axios from 'axios';
import {toMaintenance} from "@/scripts/common";

const imageClient = axios.create({
  baseURL: import.meta.env.VITE_IMG_URL,
  withCredentials: true, // to include cookies for session-based auth
  headers: {
    'Content-Type': 'application/json',
  },
});

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true, // to include cookies for session-based auth
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  function (config) {
    const token = localStorage.getItem("authToken");
    console.log('token', token)
    if (token) config.headers.Authorization = `Bearer ${token}`;
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
    console.log(error)
    console.log("intercepted error!")
    console.log(error.status)
    if (error.code == "ERR_NETWORK" || error.status == 502) {
      console.log("backend down")
      toMaintenance()
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
