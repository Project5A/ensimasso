import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    // Sans `globals: true`, Testing Library n'installe pas son nettoyage
    // automatique : le DOM s'accumule d'un cas à l'autre et des assertions
    // deviennent vraies grâce aux rendus précédents. On le pose nous-mêmes.
    setupFiles: ['./vitest.setup.ts'],
  },
})
