import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

/**
 * Point d'entrée Angular 20.
 * bootstrapApplication remplace platformBrowserDynamic().bootstrapModule(AppModule)
 * dans le mode standalone (Angular 15+).
 */
bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
