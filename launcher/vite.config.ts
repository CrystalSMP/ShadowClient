import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// Tauri erwartet einen festen Port und kein Bildschirm-Clearing.
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { "@shared": fileURLToPath(new URL("../shared", import.meta.url)) } },
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    // src-tauri nicht beobachten, sonst sperrt Windows die Rust-Build-Dateien (EBUSY)
    watch: { ignored: ["**/src-tauri/**"] },
  },
  envPrefix: ["VITE_", "TAURI_"],
  build: { target: "es2021", outDir: "dist", chunkSizeWarningLimit: 1200 },
});
