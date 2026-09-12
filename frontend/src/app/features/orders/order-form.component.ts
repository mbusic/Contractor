import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { OrderService } from '../../core/services/order.service';
import { BranchService } from '../../core/services/branch.service';
import { ClientService } from '../../core/services/client.service';
import { BranchDto, ClientDto, LocationDto, OrderDto, OrderRequest } from '../../core/models/models';
import { ALL_URGENCIES, URGENCY_LABELS } from '../../core/labels';
import { toApiError } from '../../core/errors';
import { locationText } from '../../core/format';

// A new order for ADMIN and OFFICE. The backend always creates a DRAFT;
// "Kreiraj" then submits it (status PENDING), which needs a client and a location and gives the order number.
// The estimated costs are entered on the order detail.
@Component({
  selector: 'app-order-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="page-header">
      <h2>Novi nalog</h2>
      <button class="btn btn-secondary" (click)="back()">Odustani</button>
    </div>

    <div class="card">
      <div class="info-grid">
        <div class="form-group">
          <label>Poslovnica</label>
          <select [(ngModel)]="form.branchId">
            <option [ngValue]="null">-- odaberi --</option>
            <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>Klijent</label>
          <select [(ngModel)]="form.clientId" (ngModelChange)="onClientChange()">
            <option [ngValue]="null">-- odaberi --</option>
            <option *ngFor="let c of clients" [ngValue]="c.id">{{ c.name }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>Lokacija</label>
          <select [(ngModel)]="form.locationId" [disabled]="clientLocations.length === 0">
            <option [ngValue]="null">-- odaberi lokaciju --</option>
            <option *ngFor="let l of clientLocations" [ngValue]="l.id">{{ locationText(l) }}</option>
          </select>
          <div class="muted" style="font-size:.78rem;margin-top:3px" *ngIf="form.clientId && clientLocations.length === 0">
            Klijent nema lokacija. Dodajte ih na stranici <a routerLink="/admin">klijenata</a>.
          </div>
        </div>

        <div class="form-group">
          <label>Kontakt osoba</label>
          <input [(ngModel)]="form.contactPerson" type="text" />
          <div class="field-error" *ngIf="fieldErrors['contactPerson']">{{ fieldErrors['contactPerson'] }}</div>
        </div>

        <div class="form-group">
          <label>Telefon</label>
          <input [(ngModel)]="form.phone" type="text" />
          <div class="field-error" *ngIf="fieldErrors['phone']">{{ fieldErrors['phone'] }}</div>
        </div>

        <div class="form-group">
          <label>E-mail</label>
          <input [(ngModel)]="form.email" type="email" />
          <div class="field-error" *ngIf="fieldErrors['email']">{{ fieldErrors['email'] }}</div>
        </div>

        <div class="form-group">
          <label>Hitnost</label>
          <select [(ngModel)]="form.urgency">
            <option [ngValue]="null">-- odaberi --</option>
            <option *ngFor="let u of urgencyOptions" [ngValue]="u">{{ urgencyLabels[u] }}</option>
          </select>
        </div>
      </div>

      <div class="form-group">
        <label>Opis naloga</label>
        <textarea [(ngModel)]="form.description" rows="4"></textarea>
      </div>

      <div class="error" *ngIf="error">{{ error }}</div>

      <button class="btn btn-primary" (click)="submit()" [disabled]="saving">
        {{ saving ? 'Kreiranje...' : 'Kreiraj' }}
      </button>
      <button class="btn btn-outline" (click)="saveDraft()" [disabled]="saving">Spremi kao nacrt</button>
    </div>
  `,
})
export class OrderFormComponent implements OnInit {
  branches: BranchDto[] = [];
  clients: ClientDto[] = [];
  clientLocations: LocationDto[] = [];
  urgencyOptions = ALL_URGENCIES;
  urgencyLabels = URGENCY_LABELS;
  saving = false;
  error = '';
  fieldErrors: Record<string, string> = {};

  form: OrderRequest = {
    branchId: null, clientId: null, locationId: null,
    contactPerson: '', phone: '', email: '',
    description: '', urgency: null, estimatedCosts: null,
  };

  constructor(
    private orderSvc: OrderService,
    private branchSvc: BranchService,
    private clientSvc: ClientService,
    private router: Router,
  ) {}

  ngOnInit() {
    this.branchSvc.getBranches().subscribe(b => this.branches = b);
    this.clientSvc.getClients().subscribe(c => this.clients = c);
  }

  // The client's contact details are the usual start for the order's contact
  onClientChange() {
    const client = this.clients.find(c => c.id === this.form.clientId);
    this.clientLocations = client?.locations ?? [];
    this.form.locationId = this.clientLocations.length === 1 ? this.clientLocations[0].id : null;
    if (client) {
      this.form.contactPerson = client.contactPerson ?? '';
      this.form.phone = client.phone ?? '';
      this.form.email = client.email ?? '';
    }
  }

  submit() {
    if (!this.checkRequired()) { return; }
    if (!this.form.clientId || !this.form.locationId) {
      this.error = 'Za slanje naloga odaberite klijenta i lokaciju.';
      return;
    }
    this.create(true);
  }

  saveDraft() {
    if (!this.checkRequired()) { return; }
    this.create(false);
  }

  back() { this.router.navigate(['/orders']); }

  locationText(location: LocationDto) { return locationText(location); }

  private checkRequired(): boolean {
    if (!this.form.description) {
      this.error = 'Opis naloga je obavezan.';
      return false;
    }
    return true;
  }

  private create(submitAfterCreate: boolean) {
    this.saving = true;
    this.error = '';
    this.fieldErrors = {};
    this.orderSvc.createOrder(this.form).subscribe({
      next: draft => submitAfterCreate ? this.submitDraft(draft) : this.openOrder(draft),
      error: e => {
        const apiError = toApiError(e);
        this.error = apiError.message;
        this.fieldErrors = apiError.fieldErrors;
        this.saving = false;
      },
    });
  }

  // If the submit fails, the draft is already saved: open it anyway, so it isn't created twice
  private submitDraft(draft: OrderDto) {
    this.orderSvc.changeStatus(draft.id, 'PENDING', draft.version).subscribe({
      next: order => this.openOrder(order),
      error: () => this.openOrder(draft),
    });
  }

  private openOrder(order: OrderDto) {
    this.router.navigate(['/orders', order.id]);
  }
}
