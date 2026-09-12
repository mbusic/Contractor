import { Component, OnInit, computed } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { OrderService } from '../../core/services/order.service';
import { AuthService } from '../../core/services/auth.service';
import { OrderStatus, OrderSummaryDto } from '../../core/models/models';
import { ALL_STATUSES, STATUS_LABELS, URGENCY_LABELS } from '../../core/labels';
import { toApiError } from '../../core/errors';

// A servicer's list has two views: orders assigned to them, and unassigned PENDING orders they can accept
type ServicerView = 'mine' | 'available';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DatePipe],
  template: `
    <div class="page-header">
      <h2>Nalozi</h2>
      <a routerLink="/orders/new" class="btn btn-primary" *ngIf="canCreate()">Dodaj nalog +</a>
    </div>

    <div class="tab-bar" *ngIf="isServicer()">
      <button [class.active]="view==='mine'" (click)="setView('mine')">Moji nalozi</button>
      <button [class.active]="view==='available'" (click)="setView('available')">Dostupni nalozi</button>
    </div>

    <div class="filter-row">
      <label for="status-filter">Status</label>
      <select id="status-filter" [(ngModel)]="statusFilter" (ngModelChange)="setStatusFilter($event)">
        <option value="">Svi</option>
        <option *ngFor="let s of allStatuses" [value]="s">{{ statusLabel(s) }}</option>
      </select>
    </div>

    <div class="error" *ngIf="error">{{ error }}</div>

    <div class="card table-scroll" style="padding:0">
      <table class="table">
        <thead>
          <tr>
            <th>Rb.</th>
            <th>Poslovnica</th>
            <th>Broj naloga</th>
            <th>Hitnost</th>
            <th>Status</th>
            <th>Klijent</th>
            <th>Lokacija</th>
            <th>Serviser</th>
            <th>Datum</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of filtered; let i = index" style="cursor:pointer" [routerLink]="['/orders', o.id]">
            <td>{{ i + 1 }}</td>
            <td>{{ o.branchName ?? '-' }}</td>
            <td>{{ o.orderNumber ?? 'Nacrt' }}</td>
            <td>{{ urgencyLabel(o) }}</td>
            <td><span class="status-badge" [class]="o.status">{{ statusLabel(o.status) }}</span></td>
            <td>{{ o.clientName ?? '-' }}</td>
            <td>{{ o.locationText ?? '-' }}</td>
            <td>{{ o.assignedServicerName ?? '-' }}</td>
            <td>{{ o.createdAt | date:'dd.MM.yyyy' }}</td>
          </tr>
          <tr *ngIf="filtered.length === 0">
            <td colspan="9" style="text-align:center;color:#888;padding:24px">Nema naloga.</td>
          </tr>
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .filter-row { display:flex; align-items:center; gap:8px; margin-bottom:12px;
      label { font-size:.85rem; color:#555; }
      select { padding:6px 10px; border:1px solid #ccc; border-radius:4px; } }
  `],
})
export class OrderListComponent implements OnInit {
  orders: OrderSummaryDto[] = [];
  filtered: OrderSummaryDto[] = [];
  statusFilter = '';
  view: ServicerView = 'mine';
  allStatuses = ALL_STATUSES;
  error = '';

  canCreate = computed(() => this.auth.hasRole('OFFICE', 'ADMIN'));
  isServicer = computed(() => this.auth.hasRole('SERVICER'));

  constructor(
    private orderSvc: OrderService,
    private auth: AuthService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  // The filter and the servicer's view are query parameters, so the servicer home tiles can link to them
  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      this.statusFilter = params['status'] ?? '';
      this.view = params['view'] === 'available' ? 'available' : 'mine';
      this.applyFilter();
    });
    this.orderSvc.getOrders().subscribe({
      next: list => {
        this.orders = list;
        this.applyFilter();
      },
      error: e => this.error = toApiError(e).message,
    });
  }

  setView(view: ServicerView) {
    this.router.navigate([], { queryParams: { view }, queryParamsHandling: 'merge' });
  }

  setStatusFilter(status: string) {
    this.router.navigate([], { queryParams: { status: status || null }, queryParamsHandling: 'merge' });
  }

  applyFilter() {
    this.filtered = this.orders
      .filter(o => this.inServicerView(o))
      .filter(o => !this.statusFilter || o.status === this.statusFilter);
  }

  statusLabel(status: OrderStatus) { return STATUS_LABELS[status]; }

  urgencyLabel(order: OrderSummaryDto) { return order.urgency ? URGENCY_LABELS[order.urgency] : '-'; }

  // The backend sends a servicer their own orders plus the unassigned PENDING ones
  private inServicerView(order: OrderSummaryDto): boolean {
    if (!this.isServicer()) {
      return true;
    }
    const isMine = order.assignedServicerId === this.auth.currentUser()?.userId;
    return this.view === 'mine' ? isMine : !isMine;
  }
}
