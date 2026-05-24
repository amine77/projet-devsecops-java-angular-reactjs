# Modèle Fonctionnel — To-Do List Enterprise

> Spécification fonctionnelle validée avant implémentation.
> Ce document fait référence pour toutes les décisions d'architecture et de code.

---

## Table des matières

1. [Contexte métier](#1-contexte-métier)
2. [Les 3 rôles](#2-les-3-rôles)
3. [Cycle de vie d'une to-do](#3-cycle-de-vie-dune-to-do)
4. [Fonctionnalités par rôle](#4-fonctionnalités-par-rôle)
5. [Matrice des permissions](#5-matrice-des-permissions)
6. [Notifications](#6-notifications)
7. [Règles métier](#7-règles-métier)
8. [Impact sur l'architecture technique](#8-impact-sur-larchitecture-technique)

---

## 1. Contexte métier

L'application gère des **actes de gestion** (to-dos) au sein d'une organisation
structurée en **unités de gestion**, chacune composée d'une équipe de Gestionnaires
supervisée par un Manager.

```
Organisation
├── Unité de gestion A
│   ├── Manager A
│   │   ├── Gestionnaire 1
│   │   ├── Gestionnaire 2
│   │   └── Gestionnaire 3
│   └── (un seul Manager par unité)
│
├── Unité de gestion B
│   ├── Manager B
│   │   ├── Gestionnaire 4
│   │   └── Gestionnaire 5
│   └── ...
│
└── Super-Administrateur (transverse — accès global)
```

**Règles organisationnelles :**
- Un Gestionnaire appartient à **une seule** unité de gestion
- Un Manager est responsable d'**une seule** équipe
- Le Super-Administrateur n'appartient à aucune unité — il a accès à tout
- Il n'y a **pas de délégation** d'approbation entre Managers

---

## 2. Les 3 rôles

| Rôle | Périmètre | Responsabilité principale |
|---|---|---|
| **Gestionnaire** | Ses propres to-dos uniquement | Saisir et gérer ses actes de gestion |
| **Manager** | Toutes les to-dos de son équipe | Valider, rejeter ou clôturer les actes |
| **Super-Administrateur** | Toute l'application | Administration, configuration, supervision globale |

---

## 3. Cycle de vie d'une to-do

### Diagramme de statuts

```
GESTIONNAIRE crée
        │
        ▼
   ┌─────────┐
   │ À FAIRE │ ◄─────────────────────────────────┐
   └────┬────┘                                    │
        │                                         │
        │  ┌─ Gestionnaire ──────────────────┐    │
        │  │  ✅ Modifier                    │    │
        │  │  ✅ Supprimer                   │    │
        │  │  ✅ Commenter                   │    │
        │  └────────────────────────────────-┘    │
        │                                         │
        │  ┌─ Manager ───────────────────────┐    │
        │  │  Valider    → VALIDÉE            │    │
        │  │  Rejeter    → REJETÉE (motif)    │    │
        │  │  Placer Done → DONE              │    │
        │  └─────────────────────────────────┘    │
        │                                         │
   ┌────▼────────────────────────────────────┐    │
   │         3 transitions possibles         │    │
   └──────┬───────────────┬──────────────────┘    │
          │               │              │         │
          ▼               ▼              ▼         │
     ┌─────────┐    ┌──────────┐   ┌──────────┐   │
     │ REJETÉE │    │ VALIDÉE  │   │   DONE   │   │
     └────┬────┘    └────┬─────┘   └──────────┘   │
          │              │                         │
          │   Gestionnaire peut :                  │
          │   ✅ Modifier                          │
          │   ❌ Supprimer                         │
          │   ✅ Commenter                         │
          │   → Retour automatique à À FAIRE ──────┘
          │
          │   Manager peut encore (depuis VALIDÉE) :
          └──────────────┴──► Placer en Done → DONE
```

### Tableau des statuts

| Statut | Qui le crée | Signification | Actions disponibles |
|---|---|---|---|
| **À faire** | Gestionnaire (création) ou retour après rejet | To-do active, visible par le Manager | Gestionnaire : Modifier, Supprimer, Commenter · Manager : Valider, Rejeter, Placer en Done |
| **Validée** | Manager | Approuvée, en cours de traitement | Manager : Placer en Done · Gestionnaire : Commenter |
| **Rejetée** | Manager | Refusée avec motif obligatoire | Gestionnaire : Modifier (→ repasse À faire), Commenter |
| **Done** | Manager | Complètement terminée | Lecture seule pour tous |

### Transitions autorisées

| De | Vers | Qui | Condition |
|---|---|---|---|
| — | À faire | Gestionnaire | Création |
| À faire | Validée | Manager | — |
| À faire | Rejetée | Manager | Motif obligatoire |
| À faire | Done | Manager | — |
| Validée | Done | Manager | — |
| Rejetée | À faire | Gestionnaire | Après modification |

---

## 4. Fonctionnalités par rôle

### GESTIONNAIRE

#### Gestion de ses to-dos

| Fonctionnalité | Statut requis | Détail |
|---|---|---|
| Créer une to-do | — | Titre, description, priorité, date d'échéance, pièces jointes |
| Modifier une to-do | **À faire** uniquement | Tous les champs éditables |
| Supprimer une to-do | **À faire** uniquement | Suppression définitive |
| Commenter une to-do | Tous statuts | Fil de discussion avec le Manager |
| Voir le motif de rejet | **Rejetée** | Motif saisi par le Manager |
| Modifier après rejet | **Rejetée** | La to-do repasse automatiquement en **À faire** |

#### Tableau de bord personnel

| Fonctionnalité | Détail |
|---|---|
| Mes to-dos actives | Liste filtrée : À faire + Validée + Rejetée |
| Mes to-dos Done | Historique personnel |
| Mes statistiques du mois | Nombre par statut |
| Mes notifications | Email + in-app |

#### Ce que le Gestionnaire ne peut PAS faire

- Voir les to-dos des collègues (même équipe)
- Approuver, rejeter ou placer en Done une to-do
- Assigner une to-do à quelqu'un d'autre
- Accéder aux rapports de l'équipe
- Supprimer une to-do rejetée

---

### MANAGER

#### Tableau de bord équipe

| Fonctionnalité | Détail |
|---|---|
| Vue d'ensemble | Toutes les to-dos de l'équipe, filtrables par statut / gestionnaire / priorité |
| File d'action | To-dos **À faire** en attente de décision — triées par date de création |
| To-dos en retard | Date d'échéance dépassée sans être Done |
| Charge par gestionnaire | Nombre de to-dos actives par membre |
| Statistiques équipe | Répartition par statut, délai moyen de traitement |

#### Actions sur les to-dos

| Action | Statut requis | Résultat | Condition |
|---|---|---|---|
| **Valider** | À faire | → Validée | — |
| **Rejeter** | À faire | → Rejetée | Motif obligatoire |
| **Placer en Done** | À faire **ou** Validée | → Done | — |
| Commenter | Tous | Fil de discussion | — |

#### Ce que le Manager ne peut PAS faire

- Créer une to-do
- Modifier le contenu d'une to-do (rôle du Gestionnaire)
- Voir les to-dos d'autres équipes
- Gérer les comptes utilisateurs
- Accéder aux rapports globaux

---

### SUPER-ADMINISTRATEUR

#### Accès aux données

| Fonctionnalité | Périmètre |
|---|---|
| Voir toutes les to-dos | Toutes unités, tous gestionnaires |
| Valider / Rejeter / Done | Sur n'importe quelle to-do |
| Tableau de bord global | Vue consolidée de l'organisation |
| Tableau de bord par équipe | Vue identique à celle du Manager |

#### Administration des utilisateurs

| Fonctionnalité | Détail |
|---|---|
| Créer un compte | Gestionnaire ou Manager, avec affectation unité/équipe |
| Modifier un compte | Rôle, unité, équipe, informations |
| Désactiver / réactiver | Sans suppression — historique conservé |
| Réinitialiser mot de passe | Via Cognito / Keycloak |
| Voir tous les utilisateurs | Filtrables par rôle, unité, statut |

#### Administration organisationnelle

| Fonctionnalité | Détail |
|---|---|
| Gérer les unités de gestion | Créer, modifier, archiver |
| Gérer les équipes | Créer, modifier, archiver |
| Affecter un Manager à une équipe | — |
| Affecter des Gestionnaires à une équipe | — |

#### Rapports globaux

| Fonctionnalité | Détail |
|---|---|
| Rapport global | Toutes unités, tous statuts, toutes périodes |
| Rapport par unité | Comparatif inter-unités |
| Rapport par Manager | Performance de chaque équipe |
| KPIs globaux | Délai moyen d'approbation, taux de complétion, actes en retard |
| Export | CSV, Excel |

#### Configuration de l'application

| Fonctionnalité | Détail |
|---|---|
| Types de to-dos | Libellés personnalisables (ex: "Demande", "Rapport", "Signalement") |
| Niveaux de priorité | Libellés et couleurs |
| Règles de rappel | Délai avant rappel automatique au Manager |
| Types de fichiers autorisés | Extensions, taille maximale |

#### Audit et traçabilité

| Fonctionnalité | Détail |
|---|---|
| Journal d'audit complet | Toutes les actions (création, modification, approbation, connexion) |
| Traçabilité des décisions | Qui a validé / rejeté quoi et quand |
| Logs de connexion | Dernière connexion par utilisateur |

---

## 5. Matrice des permissions

| Action | Gestionnaire | Manager | Super-Admin |
|---|:---:|:---:|:---:|
| **Créer une to-do** | ✅ | ❌ | ✅ |
| **Modifier une to-do (À faire)** | ✅ siennes | ❌ | ✅ |
| **Modifier une to-do (Rejetée)** | ✅ siennes | ❌ | ✅ |
| **Supprimer (À faire uniquement)** | ✅ siennes | ❌ | ✅ |
| **Valider** | ❌ | ✅ équipe | ✅ |
| **Rejeter** | ❌ | ✅ équipe | ✅ |
| **Placer en Done** | ❌ | ✅ équipe | ✅ |
| **Commenter** | ✅ siennes | ✅ équipe | ✅ |
| Voir ses to-dos | ✅ | — | ✅ |
| Voir les to-dos de son équipe | ❌ | ✅ | ✅ |
| Voir toutes les to-dos | ❌ | ❌ | ✅ |
| Tableau de bord équipe | ❌ | ✅ | ✅ |
| Tableau de bord global | ❌ | ❌ | ✅ |
| Rapports équipe | ❌ | ✅ | ✅ |
| Rapports globaux | ❌ | ❌ | ✅ |
| Gérer utilisateurs | ❌ | ❌ | ✅ |
| Gérer unités / équipes | ❌ | ❌ | ✅ |
| Configurer l'application | ❌ | ❌ | ✅ |
| Journal d'audit | ❌ | ❌ | ✅ |

---

## 6. Notifications

| Événement | Destinataire | Canal |
|---|---|---|
| To-do créée dans son équipe | Manager | In-app |
| To-do validée | Gestionnaire | Email + in-app |
| To-do rejetée (avec motif) | Gestionnaire | Email + in-app |
| To-do placée en Done | Gestionnaire | Email + in-app |
| To-do modifiée après rejet | Manager | In-app |
| To-do en retard (rappel) | Manager | Email + in-app |

**Implémentation AWS :**
- **SES** → emails transactionnels
- **SQS + Spring Boot** → notifications in-app (polling ou WebSocket)

---

## 7. Règles métier

```
RG-01  Un Gestionnaire appartient à une et une seule unité de gestion.
RG-02  Un Manager est responsable d'une et une seule équipe.
RG-03  Un Gestionnaire ne peut voir que ses propres to-dos.
RG-04  Un Manager ne peut agir que sur les to-dos de son équipe.
RG-05  La suppression d'une to-do est autorisée uniquement au statut "À faire".
RG-06  Le motif de rejet est obligatoire lors d'un rejet.
RG-07  Une to-do rejetée repasse automatiquement en "À faire" après modification.
RG-08  Seul le Manager (ou Super-Admin) peut changer le statut d'une to-do.
RG-09  Une to-do en statut "Done" est en lecture seule pour tous.
RG-10  Il n'y a qu'un seul niveau d'approbation (pas de validation multi-niveaux).
RG-11  Il n'y a pas de délégation d'approbation entre Managers.
RG-12  Les notifications sont envoyées uniquement par email et in-app (pas de SMS).
```

---

## 8. Impact sur l'architecture technique

### Entités du domaine Java 21

```java
// Entités principales (Records + Enums)

public record TaskId(UUID value) {}
public record UserId(UUID value) {}
public record UnitId(UUID value) {}
public record TeamId(UUID value) {}

public enum TaskStatus {
    A_FAIRE,      // créée par Gestionnaire
    VALIDEE,      // approuvée par Manager
    REJETEE,      // rejetée par Manager (motif obligatoire)
    DONE          // terminée par Manager
}

public enum UserRole {
    GESTIONNAIRE,
    MANAGER,
    SUPER_ADMINISTRATEUR
}

public enum Priority {
    BASSE, NORMALE, HAUTE, URGENTE
}
```

### Ports du domaine (interfaces)

```java
// Ports entrants (use cases)
interface TaskUseCase {
    TaskId createTask(CreateTaskCommand cmd);          // Gestionnaire
    void   updateTask(UpdateTaskCommand cmd);          // Gestionnaire (À faire / Rejetée)
    void   deleteTask(DeleteTaskCommand cmd);          // Gestionnaire (À faire uniquement)
    void   validateTask(ValidateTaskCommand cmd);      // Manager / Super-Admin
    void   rejectTask(RejectTaskCommand cmd);          // Manager / Super-Admin (motif requis)
    void   markAsDone(MarkAsDoneCommand cmd);          // Manager / Super-Admin
    void   commentTask(CommentTaskCommand cmd);        // Tous
}

interface UserUseCase {
    UserId createUser(CreateUserCommand cmd);          // Super-Admin
    void   updateUser(UpdateUserCommand cmd);          // Super-Admin
    void   deactivateUser(DeactivateUserCommand cmd);  // Super-Admin
}

// Ports sortants (repositories)
interface TaskRepository {
    void   save(Task task);
    Optional<Task> findById(TaskId id);
    List<Task> findByOwner(UserId ownerId);           // Gestionnaire
    List<Task> findByTeam(TeamId teamId);             // Manager
    List<Task> findAll();                             // Super-Admin
}
```

### Règles de sécurité Spring Security

```java
// Mapping rôles → endpoints
GET    /api/tasks/my            → GESTIONNAIRE, SUPER_ADMINISTRATEUR
POST   /api/tasks               → GESTIONNAIRE, SUPER_ADMINISTRATEUR
PUT    /api/tasks/{id}          → GESTIONNAIRE, SUPER_ADMINISTRATEUR
DELETE /api/tasks/{id}          → GESTIONNAIRE, SUPER_ADMINISTRATEUR
GET    /api/tasks/team          → MANAGER, SUPER_ADMINISTRATEUR
POST   /api/tasks/{id}/validate → MANAGER, SUPER_ADMINISTRATEUR
POST   /api/tasks/{id}/reject   → MANAGER, SUPER_ADMINISTRATEUR
POST   /api/tasks/{id}/done     → MANAGER, SUPER_ADMINISTRATEUR
GET    /api/admin/**            → SUPER_ADMINISTRATEUR
```

### Scopes Cognito / Keycloak

```
todo:read:own          → Gestionnaire (ses to-dos)
todo:write:own         → Gestionnaire (créer, modifier, supprimer les siennes)
todo:read:team         → Manager (to-dos de son équipe)
todo:action:team       → Manager (valider, rejeter, done)
todo:read:all          → Super-Admin
todo:action:all        → Super-Admin
admin:users            → Super-Admin
admin:config           → Super-Admin
```

---

*Document validé le 10 mai 2026 — Spécification fonctionnelle To-Do Enterprise*
