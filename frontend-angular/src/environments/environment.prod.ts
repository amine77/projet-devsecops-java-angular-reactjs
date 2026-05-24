/**
 * ══════════════════════════════════════════════════════════════
 *  ENVIRONNEMENT — Production (AWS)
 * ══════════════════════════════════════════════════════════════
 *
 *  Utilisé par : ng build --configuration production
 *
 *  Ces valeurs sont REMPLACÉES par les variables d'environnement
 *  lors du build CI/CD (GitHub Actions Phase 5) :
 *  → __API_URL__ remplacé par l'URL réelle de l'API App Runner
 *  → __COGNITO_ISSUER__ remplacé par l'URL du User Pool Cognito
 *  → __COGNITO_CLIENT_ID__ remplacé par l'ID du client Cognito
 *
 *  COGNITO VS KEYCLOAK :
 *  → En production : AWS Cognito (User Pool)
 *  → En local : Keycloak (docker-compose)
 *  → Le code Angular ne change pas — seul l'issuer URI change
 *  → angular-oauth2-oidc gère les deux (standard OIDC)
 */
export const environment = {
  production: true,

  apiUrl: '__API_URL__',  // remplacé par le CI : https://api.todo-enterprise.example.com/api

  oidc: {
    issuer: '__COGNITO_ISSUER__',  // https://cognito-idp.eu-west-3.amazonaws.com/{userPoolId}
    clientId: '__COGNITO_CLIENT_ID__',
    redirectUri: window.location.origin + '/auth/callback',
    postLogoutRedirectUri: window.location.origin,
    scope: 'openid profile email',
    responseType: 'code',
    showDebugInformation: false,
  },
};
