import { defineStore } from 'pinia';
import apiClient from "@/plugins/axios";
import {applyAccountLocale} from "@/scripts/localization";

export const useAuthStore = defineStore('auth', {
  state: () => ({
    isAuthenticated: false,
    username: null,
    id: null,
    iconVersion: null,
    isAdmin: false,
  }),
  actions: {
    logout() {
      this.isAuthenticated = false;
      this.username = null;
      this.id = null;
      this.iconVersion = null;
    },
    login() {
      this.checkAuth()
    },
    setImgVersion(version) {
      this.iconVersion = version
    },
    async checkAuth() {
      if (this.isAuthenticated) return
      try {
        console.log("auth checked")
        const response = await apiClient.get(`/auth/me`, {
          withCredentials: true  // Ensure cookies are sent with the request
        });
        this.isAuthenticated = response.status === 200;
        this.username = response.data.username
        this.id = response.data.id
        this.iconVersion = response.data.version
        this.isAdmin = response.data.role == 'ADMIN'
        // Not awaited: the language the account saved is worth applying, but nothing on the
        // page is waiting for it and the cookie has already given us a usable one
        applyAccountLocale()
      } catch {
        this.isAuthenticated = false;
      }
    }
  },
});
