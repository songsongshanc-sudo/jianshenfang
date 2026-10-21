import { defineStore } from "pinia";

export const useSessionStore = defineStore("session", {
  state: () => ({
    token: localStorage.getItem("adminToken") || "",
    role: localStorage.getItem("adminRole") || "",
    storeId: localStorage.getItem("adminStoreId") || ""
  }),
  actions: {
    setSession(token: string, role: string, storeId?: string | null) {
      this.token = token;
      this.role = role;
      this.storeId = storeId || "";
      localStorage.setItem("adminToken", token);
      localStorage.setItem("adminRole", role);
      if (this.storeId) {
        localStorage.setItem("adminStoreId", this.storeId);
      } else {
        localStorage.removeItem("adminStoreId");
      }
    },
    clear() {
      this.token = "";
      this.role = "";
      this.storeId = "";
      localStorage.removeItem("adminToken");
      localStorage.removeItem("adminRole");
      localStorage.removeItem("adminStoreId");
    }
  }
});
