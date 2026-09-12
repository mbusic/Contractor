import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { OrderService } from '../../core/services/order.service';
import { ClientService } from '../../core/services/client.service';
import { AuthService } from '../../core/services/auth.service';
import { LocationDto, LocationRequest, PortalOrderDto, PortalOrderRequest } from '../../core/models/models';
import { ALL_URGENCIES, URGENCY_LABELS } from '../../core/labels';
import { toApiError } from '../../core/errors';
import { locationText } from '../../core/format';

// A new order in the portal, based on the order form. The client is always the user's own.
// "Pošalji" creates the draft and submits it (it needs a location); "Spremi kao nacrt" only creates it.
@Component({
  selector: 'app-portal-order-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-header">
      <h2>Novi nalog</h2>
      <button class="btn btn-secondary" (click)="back()">Odustani</button>
    </div>

    <div class="card">
      <div class="info-grid">
        <div class="form-group">
          <label>Lokacija</label>
          <select [(ngModel)]="form.locationId">
            <option [ngValue]="null">-- odaberi lokaciju --</option>
            <option *ngFor="let l of locations" [ngValue]="l.id">{{ locationText(l) }}</option>
          </select>
          <button type="button" class="btn btn-outline btn-sm" style="margin-top:6px"
                  *ngIf="!addingLocation" (click)="addingLocation = true">Nova lokacija +</button>
        </div>

        <div class="form-group">
          <label>Hitnost</label>
          <select [(ngModel)]="form.urgency">
            <option [ngValue]="null">-- odaberi --</option>
            <option *ngFor="let u of urgencyOptions" [ngValue]="u">{{ urgencyLabels[u] }}</option>
          </select>
        </div>
      </div>

      <form *ngIf="addingLocation" class="inline-form" (ngSubmit)="saveLocation()">
        <input [(ngModel)]="locationForm.name" name="loc-name" placeholder="Naziv (npr. Vikendica)" />
        <input [(ngModel)]="locationForm.address" name="loc-address" placeholder="Adresa" required />
        <input [(ngModel)]="locationForm.city" name="loc-city" placeholder="Grad" required />
        <button type="submit" class="btn btn-primary btn-sm">Dodaj lokaciju</button>
        <button type="button" class="btn btn-secondary btn-sm" (click)="addingLocation = false">Odustani</button>
      </form>

      <div class="info-grid">
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
      </div>

      <div class="form-group">
        <label>Opis naloga</label>
        <textarea [(ngModel)]="form.description" rows="4"></textarea>
      </div>

      <div class="error" *ngIf="error">{{ error }}</div>

      <button class="btn btn-primary" (click)="submit()" [disabled]="saving">
        {{ saving ? 'Slanje...' : 'Pošalji' }}
      </button>
      <button class="btn btn-outline" (click)="saveDraft()" [disabled]="saving">Spremi kao nacrt</button>
    </div>
  `,
})
export class PortalOrderFormComponent implements OnInit {
  locations: LocationDto[] = [];
  urgencyOptions = ALL_URGENCIES;
  urgencyLabels = URGENCY_LABELS;
  addingLocation = false;
  saving = false;
  error = '';
  fieldErrors: Record<string, string> = {};

  form: PortalOrderRequest = {
    locationId: null, contactPerson: '', phone: '', email: '', description: '', urgency: null,
  };
  locationForm: LocationRequest = { name: '', address: '', city: '' };

  constructor(
    private orderSvc: OrderService,
    private clientSvc: ClientService,
    private auth: AuthService,
    private router: Router,
  ) {}

  // The user is usually the contact person
  ngOnInit() {
    this.form.contactPerson = this.auth.currentUser()?.displayName ?? '';
    this.clientSvc.getPortalLocations().subscribe(locations => {
      this.locations = locations;
      if (locations.length === 1) { this.form.locationId = locations[0].id; }
    });
  }

  locationText(location: LocationDto) { return locationText(location); }

  // The new location is picked right away
  saveLocation() {
    this.clientSvc.addPortalLocation(this.locationForm).subscribe({
      next: location => {
        this.locations = [...this.locations, location];
        this.form.locationId = location.id;
        this.locationForm = { name: '', address: '', city: '' };
        this.addingLocation = false;
        this.error = '';
      },
      error: e => this.showError(e),
    });
  }

  submit() {
    if (!this.checkRequired()) { return; }
    if (!this.form.locationId) {
      this.error = 'Za slanje naloga odaberite lokaciju.';
      return;
    }
    this.create(true);
  }

  saveDraft() {
    if (!this.checkRequired()) { return; }
    this.create(false);
  }

  back() { this.router.navigate(['/portal/orders']); }

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
    this.orderSvc.createPortalOrder(this.form).subscribe({
      next: draft => submitAfterCreate ? this.submitDraft(draft) : this.openOrder(draft),
      error: e => { this.showError(e); this.saving = false; },
    });
  }

  // If the submit fails, the draft is already saved: open it anyway, so it isn't created twice
  private submitDraft(draft: PortalOrderDto) {
    this.orderSvc.submitPortalOrder(draft.id, draft.version).subscribe({
      next: order => this.openOrder(order),
      error: () => this.openOrder(draft),
    });
  }

  private openOrder(order: PortalOrderDto) {
    this.router.navigate(['/portal/orders', order.id]);
  }

  private showError(e: HttpErrorResponse) {
    const apiError = toApiError(e);
    this.error = apiError.message;
    this.fieldErrors = apiError.fieldErrors;
  }
}
