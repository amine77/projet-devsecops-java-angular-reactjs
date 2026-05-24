/**
 * ══════════════════════════════════════════════════════════════
 *  CONFIGURATION DES TESTS — test-setup.ts (React / Vitest)
 * ══════════════════════════════════════════════════════════════
 *
 *  Ce fichier est exécuté avant chaque suite de tests.
 *  Configuré dans vite.config.ts → test.setupFiles.
 *
 *  VITEST vs JEST :
 *  → Jest : le standard historique, très répandu
 *  → Vitest : même API que Jest mais intégré à Vite
 *    Avantages : même config TS, même résolution des alias (@api, @types...)
 *    Plus rapide grâce aux ES modules natifs
 *
 *  @testing-library/jest-dom :
 *  → Ajoute des matchers custom pour le DOM :
 *    expect(element).toBeInTheDocument()
 *    expect(button).toBeDisabled()
 *    expect(input).toHaveValue('foo')
 *  → Rend les assertions plus lisibles que les matchers natifs
 */
import '@testing-library/jest-dom';
