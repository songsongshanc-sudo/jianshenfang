import { defineConfig } from "vite";
import uni from "@dcloudio/vite-plugin-uni";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5174,
    proxy: {
      "/api": "http://127.0.0.1:8080",
      "/uploads": "http://127.0.0.1:8080",
    },
  },
});
