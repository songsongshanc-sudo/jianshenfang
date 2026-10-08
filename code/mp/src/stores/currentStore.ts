import { defineStore } from "pinia";

export const useCurrentStore = defineStore("currentStore", {
  state: () => ({
    storeId: (uni.getStorageSync("storeId") as string) || "",
  }),
  actions: {
    setStoreId(storeId: string) {
      this.storeId = storeId;
      uni.setStorageSync("storeId", storeId);
    },
  },
});
