import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

/**
 * Le DOM est remis à zéro entre deux cas.
 *
 * <p>Testing Library installe ce nettoyage toute seule — mais seulement quand
 * Vitest tourne avec `globals: true`, ce qui n'est pas le cas ici. Sans lui, le
 * document s'accumule d'un cas à l'autre, et une assertion comme
 * `getAllByText('Gala')` est satisfaite par les rendus des cas PRÉCÉDENTS.
 *
 * <p>Ce n'est pas théorique : dans `liens.test.tsx`, la garde qui existait
 * précisément pour prouver qu'un bloc s'était affiché — « sans ces deux
 * assertions, le cas serait passé au vert avec des noms de types erronés » —
 * ne pouvait pas échouer, parce que huit rendus antérieurs portaient le même
 * texte. Une garde anti-test-vide vide.
 *
 * <p>Le poser ici plutôt que fichier par fichier : c'est le genre d'oubli qui
 * se répète, et qui ne se voit jamais puisqu'il rend les tests plus verts.
 */
afterEach(cleanup)
