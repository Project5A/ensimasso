import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Le développement passe par un proxy, comme la production passe par
    // Traefik : l'API est servie sur LA MÊME ORIGINE que le front. C'est ce qui
    // fait disparaître CORS comme problème — la v1 avait une liste d'origines
    // figée à http://localhost:3000, ce qui rendait le front déployé incapable
    // d'appeler le back déployé.
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: { outDir: 'dist', sourcemap: true },
})
