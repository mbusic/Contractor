import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { OrderService } from '../../core/services/order.service';
import { OrderStatus, PortalOrderSummaryDto } from '../../core/models/models';
import { STATUS_LABELS, URGENCY_LABELS } from '../../core/labels';
import { toApiError } from '../../core/errors';

// A client user's orders: their own drafts and everything submitted for their client. Based on the order list.
@Component({
  selector: 'app-portal-order-list',
  standalone: true,
  imports: [CommonModule, RouterLink, DatePipe],
  template: `
    <div class="page-header">
      <h2>Moji nalozi</h2>
      <a routerLink="/portal/orders/new" class="btn btn-primary">Novi nalog +</a>
    </div>

    <div class="error" *ngIf="error">{{ error }}</div>

    <div class="card table-scroll" style="padding:0">
      <table class="table">
        <thead>
          <tr>
            <th>Rb.</th>
            <th>Broj naloga</th>
            <th>Hitnost</th>
            <th>Status</th>
            <th>Lokacija</th>
            <th>Datum</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of orders; let i = index" style="cursor:pointer" [routerLink]="['/portal/orders', o.id]">
            <td>{{ i + 1 }}</td>
            <td>{{ o.orderNumber ?? 'Nacrt' }}</td>
            <td>{{ o.urgency ? urgencyLabels[o.urgency] : '-' }}</td>
            <td><span class="status-badge" [class]="o.status">{{ statusLabel(o.status) }}</span></td>
            <td>{{ o.locationText ?? '-' }}</td>
            <td>{{ o.createdAt | date:'dd.MM.yyyy' }}</td>
          </tr>
          <tr *ngIf="orders.length === 0">
            <td colspan="6" style="text-align:center;color:#888;padding:24px">Nema naloga.</td>
          </tr>
        </tbody>
      </table>
    </div>
  `,
})
export class PortalOrderListComponent implements OnInit {
  orders: PortalOrderSummaryDto[] = [];
  urgencyLabels = URGENCY_LABELS;
  error = '';

  constructor(private orderSvc: OrderService) {}

  ngOnInit() {
    this.orderSvc.getPortalOrders().subscribe({
      next: list => this.orders = list,
      error: e => this.error = toApiError(e).message,
    });
  }

  statusLabel(status: OrderStatus) { return STATUS_LABELS[status]; }
}
