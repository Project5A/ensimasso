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

    // Les tests tournaient en UTC — la CI comme la machine de dev.
    // Toutes les gardes de fuseau devenaient alors des tautologies : en UTC,
    // « coller un Z sur la valeur du champ » donne exactement le même
    // résultat que la conversion correcte, et dates.test.ts passait au vert
    // sur l'implémentation qu'il existe pour interdire.
    //
    // Europe/Paris est le fuseau des utilisateurs, et il a un décalage non nul
    // ET un changement d'heure : les deux propriétés dont ces tests ont besoin
    // pour dire quelque chose. La garde qui vérifie que ce réglage a bien pris
    // est dans dates.test.ts — un fuseau qui retomberait à UTC rendrait le
    // reste muet sans rien casser.
    env: { TZ: 'Europe/Paris' },
  },
})
