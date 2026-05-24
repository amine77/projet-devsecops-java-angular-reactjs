/**
 * Modèles utilisateur — correspondance avec le JWT Keycloak/Cognito
 * et les claims custom (teamId, unitId).
 */
export type UserRole = 'GESTIONNAIRE' | 'MANAGER' | 'SUPER_ADMINISTRATEUR';

/**
 * Représentation de l'utilisateur connecté, extraite du JWT.
 *
 * CLAIMS JWT KEYCLOAK :
 * → sub                     : identifiant unique (keycloakId)
 * → preferred_username      : nom d'utilisateur
 * → email                   : adresse email
 * → realm_access.roles      : rôles (GESTIONNAIRE, MANAGER, SUPER_ADMINISTRATEUR)
 * → teamId                  : claim custom ajouté dans le realm (keycloak/todo-realm.json)
 * → unitId                  : claim custom ajouté dans le realm
 */
export interface CurrentUser {
  readonly sub: string;           // keycloakId
  readonly preferredUsername: string;
  readonly email: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly role: UserRole;
  readonly teamId: string;        // UUID
  readonly unitId: string;        // UUID
}

/** Vérifie si l'utilisateur peut créer des tâches */
export function canCreateTask(role: UserRole): boolean {
  return role === 'GESTIONNAIRE' || role === 'SUPER_ADMINISTRATEUR';
}

/** Vérifie si l'utilisateur peut changer le statut d'une tâche */
export function canChangeTaskStatus(role: UserRole): boolean {
  return role === 'MANAGER' || role === 'SUPER_ADMINISTRATEUR';
}

/** Vérifie si l'utilisateur a un accès global (toutes les équipes) */
export function hasGlobalAccess(role: UserRole): boolean {
  return role === 'SUPER_ADMINISTRATEUR';
}
