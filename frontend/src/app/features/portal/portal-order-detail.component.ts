import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { OrderService } from '../../core/services/order.service';
import { ClientService } from '../../core/services/client.service';
import { DocumentType, LocationDto, OrderStatus, PortalOrderDto, PortalOrderRequest } from '../../core/models/models';
import { ALL_URGENCIES, DOCUMENT_LABELS, STATUS_LABELS, URGENCY_LABELS } from '../../core/labels';
import { openDocument } from '../../core/documents';
import { toApiError } from '../../core/errors';
import { locationText } from '../../core/format';

const MAX_PHOTOS = 6;
const PHOTO_TYPES = 'image/jpeg,image/png,image/gif,image/webp';

// A client user's order, based on the order detail. No costs, notes or servicer (they're internal).
// Only a DRAFT can be changed: edit, photos, and submit ("Pošalji"). Every change sends the order's version.
@Component({
  selector: 'app-portal-order-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div *ngIf="order">
      <!-- Header row -->
      <div class="card order-header">
        <div class="header-fields">
          <div><label>Broj naloga</label><span>{{ order.orderNumber ?? 'Nacrt' }}</span></div>
          <div><label>Hitnost</label><span>{{ order.urgency ? urgencyLabels[order.urgency] : '-' }}</span></div>
          <div><label>Status</label>
            <span class="status-badge" [class]="order.status">{{ statusLabel(order.status) }}</span>
          </div>
          <div><label>Kreiran</label><span>{{ order.createdAt | date:'dd.MM.yyyy' }}</span></div>
        </div>
        <div class="header-actions">
          <a routerLink="/portal/orders" class="btn btn-secondary btn-sm">Natrag</a>
        </div>
      </div>

      <div class="card error-card" *ngIf="error">
        <span class="error">{{ error }}</span>
        <button class="btn btn-outline btn-sm" *ngIf="versionConflict" (click)="reload()">Osvježi</button>
      </div>

      <!-- Order info -->
      <div class="card">
        <h3>Informacije o nalogu</h3>
        <ng-container *ngIf="!editing; else editForm">
          <div class="info-grid">
            <div class="info-item"><label>Lokacija</label><p>{{ locationText(order.location) }}</p></div>
            <div class="info-item"><label>Kontakt osoba</label><p>{{ order.contactPerson || '-' }}</p></div>
            <div class="info-item"><label>Telefon</label><p>{{ order.phone || '-' }}</p></div>
            <div class="info-item"><label>E-mail</label><p>{{ order.email || '-' }}</p></div>
          </div>
          <div style="margin-top:12px">
            <label style="font-size:.78rem;color:#888;text-transform:uppercase">Opis naloga</label>
            <p style="margin:4px 0">{{ order.description || '-' }}</p>
          </div>
        </ng-container>

        <ng-template #editForm>
          <div class="info-grid">
            <div class="form-group">
              <label>Lokacija</label>
              <select [(ngModel)]="editData.locationId">
                <option [ngValue]="null">-- odaberi lokaciju --</option>
                <option *ngFor="let l of locations" [ngValue]="l.id">{{ locationText(l) }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>Hitnost</label>
              <select [(ngModel)]="editData.urgency">
                <option [ngValue]="null">-- odaberi --</option>
                <option *ngFor="let u of urgencyOptions" [ngValue]="u">{{ urgencyLabels[u] }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>Kontakt osoba</label>
              <input [(ngModel)]="editData.contactPerson" />
              <div class="field-error" *ngIf="fieldErrors['contactPerson']">{{ fieldErrors['contactPerson'] }}</div>
            </div>
            <div class="form-group">
              <label>Telefon</label>
              <input [(ngModel)]="editData.phone" />
              <div class="field-error" *ngIf="fieldErrors['phone']">{{ fieldErrors['phone'] }}</div>
            </div>
            <div class="form-group">
              <label>E-mail</label>
              <input [(ngModel)]="editData.email" />
              <div class="field-error" *ngIf="fieldErrors['email']">{{ fieldErrors['email'] }}</div>
            </div>
          </div>
          <div class="form-group">
            <label>Opis naloga</label>
            <textarea [(ngModel)]="editData.description" rows="3"></textarea>
          </div>
        </ng-template>

        <div class="action-bar" *ngIf="isDraft()">
          <ng-container *ngIf="!editing">
            <button class="btn btn-primary btn-sm" (click)="submit()">Pošalji</button>
            <button class="btn btn-outline btn-sm" (click)="startEdit()">Uredi</button>
          </ng-container>
          <ng-container *ngIf="editing">
            <button class="btn btn-primary btn-sm" (click)="saveEdit()">Spremi</button>
            <button class="btn btn-secondary btn-sm" (click)="editing=false">Odustani</button>
          </ng-container>
        </div>
        <p class="muted" style="font-size:.85rem;margin:12px 0 0" *ngIf="!isDraft()">
          Poslani nalog više ne možete mijenjati. Za izmjene se javite uredu.
        </p>
      </div>

      <!-- Photos -->
      <div class="card">
        <h3>Fotografije</h3>
        <div class="photo-grid">
          <div class="photo-thumb" *ngFor="let p of order.photos">
            <img [src]="p.url" [alt]="'Fotografija'" />
            <button class="photo-delete" *ngIf="isDraft()" (click)="deletePhoto(p.id)">×</button>
          </div>
          <div *ngIf="order.photos.length === 0" style="color:#888">Nema fotografija.</div>
        </div>
        <div class="action-bar" *ngIf="isDraft() && order.photos.length < maxPhotos">
          <label class="btn btn-outline btn-sm" style="cursor:pointer">
            Dodaj fotografiju
            <input type="file" [accept]="photoTypes" style="display:none" (change)="uploadPhoto($event)" />
          </label>
          <span class="muted" style="font-size:.78rem;align-self:center">JPEG, PNG, GIF ili WebP, najviše 10 MB</span>
        </div>
      </div>

      <!-- Documents: every type except the work order, which is the servicer's internal sheet -->
      <div class="card">
        <h3>Ispis dokumenata</h3>
        <div style="display:flex;flex-wrap:wrap;gap:8px">
          <button class="btn btn-secondary btn-sm" *ngFor="let t of documentTypes" (click)="openDoc(t)">{{ documentLabels[t] }}</button>
        </div>
      </div>
    </div>

    <div *ngIf="!order && !loading" style="color:#888;padding:24px">{{ error || 'Nalog nije pronađen.' }}</div>
  `,
  styles: [`
    .order-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
    .header-fields { display: flex; flex-wrap: wrap; gap: 16px 32px; }
    .header-fields > div { label { font-size:.75rem; color:#888; display:block; } span { font-weight:600; } }
    .action-bar { margin-top: 16px; display: flex; gap: 8px; flex-wrap: wrap; }
    .error-card { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; .error { margin: 0; } }
  `],
})
export class PortalOrderDetailComponent implements OnInit {
  order: PortalOrderDto | null = null;
  loading = true;
  editing = false;
  editData: PortalOrderRequest = {
    locationId: null, contactPerson: null, phone: null, email: null, description: null, urgency: null,
  };
  locations: LocationDto[] = [];
  error = '';
  versionConflict = false;
  fieldErrors: Record<string, string> = {};
  urgencyOptions = ALL_URGENCIES;
  urgencyLabels = URGENCY_LABELS;
  maxPhotos = MAX_PHOTOS;
  photoTypes = PHOTO_TYPES;
  documentTypes: DocumentType[] = ['QUOTE', 'REPORT', 'INVOICE'];
  documentLabels = DOCUMENT_LABELS;

  constructor(
    private route: ActivatedRoute,
    private orderSvc: OrderService,
    private clientSvc: ClientService,
  ) {}

  ngOnInit() {
    this.reload();
    this.clientSvc.getPortalLocations().subscribe(l => this.locations = l);
  }

  reload() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.orderSvc.getPortalOrder(id).subscribe({
      next: o => { this.order = o; this.loading = false; this.clearError(); },
      error: e => { this.showError(e); this.loading = false; },
    });
  }

  statusLabel(s: OrderStatus) { return STATUS_LABELS[s]; }

  locationText(location: LocationDto | null) { return locationText(location); }

  isDraft(): boolean {
    return this.order?.status === 'DRAFT';
  }

  // PUT is a full replace, so every field goes back, with the version it was read with
  startEdit() {
    const order = this.order!;
    this.editData = {
      locationId: order.location?.id ?? null,
      contactPerson: order.contactPerson,
      phone: order.phone,
      email: order.email,
      description: order.description,
      urgency: order.urgency,
      version: order.version,
    };
    this.fieldErrors = {};
    this.editing = true;
  }

  saveEdit() {
    this.run(this.orderSvc.updatePortalOrder(this.order!.id, this.editData), () => this.editing = false);
  }

  // DRAFT -> PENDING. The backend needs a location and gives the order its number.
  submit() {
    if (!confirm('Poslati nalog? Nakon slanja ga više ne možete mijenjati.')) { return; }
    this.run(this.orderSvc.submitPortalOrder(this.order!.id, this.order!.version));
  }

  uploadPhoto(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) { return; }
    this.run(this.orderSvc.addPortalPhoto(this.order!.id, file));
  }

  deletePhoto(photoId: number) {
    if (!confirm('Izbrisati fotografiju?')) { return; }
    this.orderSvc.deletePortalPhoto(this.order!.id, photoId).subscribe({
      next: () => this.reload(),
      error: e => this.showError(e),
    });
  }

  openDoc(type: DocumentType) {
    this.clearError();
    openDocument(this.orderSvc.getPortalDocument(this.order!.id, type), e => this.showError(e));
  }

  // Runs a change and shows the order from the response. done() runs only on success.
  private run(change: Observable<PortalOrderDto>, done: () => void = () => {}) {
    this.clearError();
    change.subscribe({
      next: o => { this.order = o; done(); },
      error: e => this.showError(e),
    });
  }

  private showError(error: HttpErrorResponse) {
    const apiError = toApiError(error);
    this.error = apiError.message;
    this.versionConflict = apiError.versionConflict;
    this.fieldErrors = apiError.fieldErrors;
  }

  private clearError() {
    this.error = '';
    this.versionConflict = false;
    this.fieldErrors = {};
  }
}
