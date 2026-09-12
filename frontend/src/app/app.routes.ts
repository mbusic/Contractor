import { Routes } from '@angular/router';
import { authGuard, roleGuard, startPageGuard } from './core/guards/auth.guard';

const EMPLOYEES = roleGuard('ADMIN', 'OFFICE', 'SERVICER');
const OFFICE = roleGuard('ADMIN', 'OFFICE');
const CLIENT = roleGuard('CLIENT');

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent),
  },
  { path: '', pathMatch: 'full', canActivate: [authGuard, startPageGuard], children: [] },
  {
    path: 'servicer',
    canActivate: [authGuard, roleGuard('SERVICER')],
    loadComponent: () => import('./features/servicer/servicer-home.component').then(m => m.ServicerHomeComponent),
  },
  {
    path: 'orders',
    canActivate: [authGuard, EMPLOYEES],
    loadComponent: () => import('./features/orders/order-list.component').then(m => m.OrderListComponent),
  },
  {
    path: 'orders/new',
    canActivate: [authGuard, OFFICE],
    loadComponent: () => import('./features/orders/order-form.component').then(m => m.OrderFormComponent),
  },
  {
    path: 'orders/:id',
    canActivate: [authGuard, EMPLOYEES],
    loadComponent: () => import('./features/orders/order-detail.component').then(m => m.OrderDetailComponent),
  },
  {
    path: 'admin',
    canActivate: [authGuard, OFFICE],
    loadComponent: () => import('./features/admin/admin.component').then(m => m.AdminComponent),
  },
  {
    path: 'portal/orders',
    canActivate: [authGuard, CLIENT],
    loadComponent: () => import('./features/portal/portal-order-list.component').then(m => m.PortalOrderListComponent),
  },
  {
    path: 'portal/orders/new',
    canActivate: [authGuard, CLIENT],
    loadComponent: () => import('./features/portal/portal-order-form.component').then(m => m.PortalOrderFormComponent),
  },
  {
    path: 'portal/orders/:id',
    canActivate: [authGuard, CLIENT],
    loadComponent: () => import('./features/portal/portal-order-detail.component').then(m => m.PortalOrderDetailComponent),
  },
  {
    path: 'portal/locations',
    canActivate: [authGuard, CLIENT],
    loadComponent: () => import('./features/portal/portal-locations.component').then(m => m.PortalLocationsComponent),
  },
  { path: '**', redirectTo: '' },
];
