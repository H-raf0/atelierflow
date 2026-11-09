import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { Shell } from './core/layout/shell';

/** Every feature page is lazy loaded: its code is downloaded the first time the route is visited. */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Connexion — AtelierFlow',
    loadComponent: () => import('./features/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'board' },
      {
        path: 'board',
        title: 'Ordres de travail — AtelierFlow',
        loadComponent: () => import('./features/work-orders/board-page').then((m) => m.BoardPage),
      },
      {
        path: 'work-orders/:id',
        title: 'Ordre de travail — AtelierFlow',
        loadComponent: () =>
          import('./features/work-orders/work-order-detail-page').then((m) => m.WorkOrderDetailPage),
      },
      {
        path: 'equipment',
        title: 'Parc machines — AtelierFlow',
        loadComponent: () => import('./features/equipment/equipment-list-page').then((m) => m.EquipmentListPage),
      },
      {
        path: 'dashboard',
        title: 'Tableau de bord — AtelierFlow',
        loadComponent: () => import('./features/dashboard/dashboard-page').then((m) => m.DashboardPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
