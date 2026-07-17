import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // El backend en Docker quedó mapeado al host 8082 (8080 lo usa otra app).
      "/api": "http://localhost:8082",
      // Health público de actuator, para la página /estado.
      "/actuator/health": "http://localhost:8082",
    },
  },
});
