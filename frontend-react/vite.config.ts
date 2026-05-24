import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

/**
 * ══════════════════════════════════════════════════════════════
 *  CONFIGURATION VITE — vite.config.ts
 * ══════════════════════════════════════════════════════════════
 *
 *  VITE vs CRA (Create React App) :
 *  → Vite utilise ES modules natifs en développement (pas de bundle)
 *  → HMR (Hot Module Replacement) quasi-instantané (< 50ms vs 3-5s avec CRA)
 *  → Build de production avec Rollup (optimisé, tree-shaking, code splitting)
 *  → CRA est déprécié depuis 2022 → Vite est le standard
 *
 *  PROXY :
 *  → En développement, /api/* est proxifié vers localhost:8080
 *  → Évite les problèmes CORS en développement local
 *  → En production : le vrai domaine de l'API est utilisé
 * ══════════════════════════════════════════════════════════════
 */
export default defineConfig({
  plugins: [react()],

  // Aliases de chemins (équivalent de paths dans tsconfig)
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@api': resolve(__dirname, 'src/api'),
      '@types': resolve(__dirname, 'src/types'),
      '@hooks': resolve(__dirname, 'src/hooks'),
      '@components': resolve(__dirname, 'src/components'),
      '@context': resolve(__dirname, 'src/context'),
    },
  },

  // Serveur de développement
  server: {
    port: 3000,
    // Proxy API → Spring Boot (évite CORS en dev)
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },

  // Configuration des tests Vitest
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    globals: true,
  },
});
