import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MAT_DATE_LOCALE, provideNativeDateAdapter } from '@angular/material/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideApiConfiguration } from './api';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthService } from './core/auth/auth.service';
import { FrenchPaginatorIntl } from './shared/french-paginator-intl';

registerLocaleData(localeFr);

/**
 * Root providers. No zone.js: change detection is driven by signals (zoneless, Angular's default).
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    // the generated API client calls relative URLs: same origin as the SPA (nginx or dev proxy)
    provideApiConfiguration(''),
    provideNativeDateAdapter(),
    { provide: LOCALE_ID, useValue: 'fr' },
    { provide: MAT_DATE_LOCALE, useValue: 'fr-FR' },
    { provide: MatPaginatorIntl, useClass: FrenchPaginatorIntl },
    // on a page reload the access token (kept in memory) is gone: try to renew it from the refresh cookie
    provideAppInitializer(() => inject(AuthService).restoreSession()),
  ],
};
