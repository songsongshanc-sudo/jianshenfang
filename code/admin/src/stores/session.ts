import { defineStore } from "pinia";

export const useSessionStore = defineStore("session", {
  state: () => ({
    token: localStorage.getItem("adminToken") || "",
    role: localStorage.getItem("adminRole") || ""
  }),
  actions: {
    setSession(token: string, role: string) {
      this.token = token;
      this.role = role;
      localStorage.setItem("adminToken", token);
      localStorage.setItem("adminRole", role);
    },
    clear() {
      this.token = "";
      this.role = "";
      localStorage.removeItem("adminToken");
      localStorage.removeItem("adminRole");
    }
  }
});
