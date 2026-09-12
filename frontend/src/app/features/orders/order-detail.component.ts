import { Component, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import { OrderService } from '../../core/services/order.service';
import { UserService } from '../../core/services/user.service';
import { BranchService } from '../../core/services/branch.service';
import { ClientService } from '../../core/services/client.service';
import { AuthService } from '../../core/services/auth.service';
import {
  BranchDto, ClientDto, CostsDto, CostsRequest, LocationDto, OrderDto, OrderRequest, OrderStatus, UserDto,
} from '../../core/models/models';
import { ALL_URGENCIES, STATUS_LABELS, URGENCY_LABELS } from '../../core/labels';
import { toApiError } from '../../core/errors';
import { locationText } from '../../core/format';

const MAX_PHOTOS = 6;
const PHOTO_TYPES = 'image/jpeg,image/png,image/gif,image/webp';

// Every change sends the order's current version. The response carries the new one, so the page keeps it in "order".
// Who may change the order: ADMIN and OFFICE always, a SERVICER only while it's assigned to them.
@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div *ngIf="order">
      <!-- Header row -->
      <div class="card order-header">
        <div class="header-fields">
          <div><label>Rb.</label><span>{{ order.id }}</span></div>
          <div><label>Poslovnica</label><span>{{ order.branch?.name ?? '-' }}</span></div>
          <div><label>Broj naloga</label><span>{{ order.orderNumber ?? 'Nacrt' }}</span></div>
          <div><label>Hitnost</label><span>{{ order.urgency ? urgencyLabels[order.urgency] : '-' }}</span></div>
          <div><label>Status</label>
            <span class="status-badge" [class]="order.status">{{ statusLabel(order.status) }}</span>
          </div>
        </div>
        <div class="header-actions">
          <a routerLink="/orders" class="btn btn-secondary btn-sm">Natrag</a>
        </div>
      </div>

      <div class="card error-card" *ngIf="error">
        <span class="error">{{ error }}</span>
        <button class="btn btn-outline btn-sm" *ngIf="versionConflict" (click)="reload()">Osvježi</button>
      </div>

      <!-- Contact info -->
      <div class="card">
        <h3>Informacije o nalogu</h3>
        <ng-container *ngIf="!editing; else editForm">
          <div class="info-grid">
            <div class="info-item"><label>Lokacija</label><p>{{ locationText(order.location) }}</p></div>
            <div class="info-item"><label>Kontakt osoba</label><p>{{ order.contactPerson || '-' }}</p></div>
            <div class="info-item"><label>Telefon</label><p>{{ order.phone || '-' }}</p></div>
            <div class="info-item"><label>E-mail</label><p>{{ order.email || '-' }}</p></div>
            <div class="info-item"><label>Klijent</label><p>{{ order.client?.name || '-' }}</p></div>
            <div class="info-item"><label>Serviser</label>
              <p>{{ order.assignedServicer?.displayName || 'Nije dodijeljen' }}</p>
            </div>
          </div>
          <div style="margin-top:12px">
            <label style="font-size:.78rem;color:#888;text-transform:uppercase">Opis naloga</label>
            <p style="margin:4px 0">{{ order.description || '-' }}</p>
          </div>
        </ng-container>

        <ng-template #editForm>
          <div class="info-grid">
            <div class="form-group">
              <label>Poslovnica</label>
              <select [(ngModel)]="editData.branchId">
                <option [ngValue]="null">-- odaberi --</option>
                <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>Klijent</label>
              <select [(ngModel)]="editData.clientId" (ngModelChange)="onEditClientChange()">
                <option [ngValue]="null">-- odaberi --</option>
                <option *ngFor="let c of clients" [ngValue]="c.id">{{ c.name }}</option>
              </select>
            </div>
            <div class="form-group">
              <label>Lokacija</label>
              <select [(ngModel)]="editData.locationId">
                <option [ngValue]="null">-- odaberi lokaciju --</option>
                <option *ngFor="let l of editLocations" [ngValue]="l.id">{{ locationText(l) }}</option>
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
            <div class="form-group">
              <label>Hitnost</label>
              <select [(ngModel)]="editData.urgency">
                <option [ngValue]="null">-- odaberi --</option>
                <option *ngFor="let u of urgencyOptions" [ngValue]="u">{{ urgencyLabels[u] }}</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label>Opis naloga</label>
            <textarea [(ngModel)]="editData.description" rows="3"></textarea>
          </div>
        </ng-template>

        <!-- Office action buttons -->
        <div class="action-bar" *ngIf="isOffice()">
          <ng-container *ngIf="!editing">
            <button class="btn btn-primary btn-sm" (click)="startEdit()">Uredi</button>
            <button class="btn btn-danger btn-sm" (click)="deleteOrder()">Izbriši nalog</button>
          </ng-container>
          <ng-container *ngIf="editing">
            <button class="btn btn-primary btn-sm" (click)="saveEdit()">Spremi</button>
            <button class="btn btn-secondary btn-sm" (click)="editing=false">Odustani</button>
          </ng-container>
        </div>

        <!-- Servicer action buttons -->
        <div class="action-bar" *ngIf="isServicer() && canAccept()">
          <button class="btn btn-primary btn-sm" (click)="accept()">Prihvati nalog</button>
        </div>
      </div>

      <!-- Servicer assignment (office): a PENDING order becomes IN_PROGRESS, an IN_PROGRESS one gets another servicer -->
      <div class="card" *ngIf="isOffice() && canAssign()">
        <h3>Serviser</h3>
        <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
          <select [(ngModel)]="selectedServicerId" style="padding:7px 10px;border:1px solid #ccc;border-radius:4px">
            <option [ngValue]="null">-- odaberi servisera --</option>
            <option *ngFor="let s of servicers" [ngValue]="s.id">{{ s.displayName }}</option>
          </select>
          <button class="btn btn-outline btn-sm" (click)="assign()" [disabled]="!selectedServicerId">Dodijeli servisera</button>
        </div>
      </div>

      <!-- Status change: only the statuses the backend allows next -->
      <div class="card" *ngIf="order.allowedNextStatuses.length > 0">
        <h3>Promjena statusa</h3>
        <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
          <select [(ngModel)]="selectedStatus" style="padding:7px 10px;border:1px solid #ccc;border-radius:4px">
            <option [ngValue]="null">-- odaberi status --</option>
            <option *ngFor="let s of order.allowedNextStatuses" [ngValue]="s">{{ statusLabel(s) }}</option>
          </select>
          <button class="btn btn-outline btn-sm" (click)="changeStatus()" [disabled]="!selectedStatus">Promijeni status</button>
        </div>
      </div>

      <!-- Cost table -->
      <div class="card">
        <h3>Troškovi</h3>
        <div class="table-scroll">
        <table class="cost-table">
          <thead>
            <tr>
              <th>Stavka</th>
              <th>Procijenjeno</th>
              <th>Stvarno</th>
              <th>Odstupanje</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Radni sati</td>
              <td>
                <span *ngIf="!editingCost || !isOffice()">{{ order.estimatedCosts.workHours ?? '-' }}</span>
                <input *ngIf="editingCost && isOffice()" [(ngModel)]="cost.estimated.workHours" type="number" min="0" step="0.5" style="width:80px" />
              </td>
              <td><span *ngIf="!editingCost">{{ order.actualCosts.workHours ?? '-' }}</span>
                  <input *ngIf="editingCost" [(ngModel)]="cost.actual.workHours" type="number" min="0" step="0.5" style="width:80px" /></td>
              <td [class]="varianceClass(order.costDifference.workHours)">{{ variance(order.costDifference.workHours) }}</td>
            </tr>
            <tr>
              <td>Broj radnika</td>
              <td>
                <span *ngIf="!editingCost || !isOffice()">{{ order.estimatedCosts.numberOfWorkers ?? '-' }}</span>
                <input *ngIf="editingCost && isOffice()" [(ngModel)]="cost.estimated.numberOfWorkers" type="number" min="0" style="width:80px" />
              </td>
              <td><span *ngIf="!editingCost">{{ order.actualCosts.numberOfWorkers ?? '-' }}</span>
                  <input *ngIf="editingCost" [(ngModel)]="cost.actual.numberOfWorkers" type="number" min="0" style="width:80px" /></td>
              <td [class]="varianceClass(order.costDifference.numberOfWorkers)">{{ variance(order.costDifference.numberOfWorkers) }}</td>
            </tr>
            <tr>
              <td>Ukupno sati</td>
              <td>{{ order.estimatedCosts.totalHours ?? '-' }}</td>
              <td>{{ order.actualCosts.totalHours ?? '-' }}</td>
              <td [class]="varianceClass(order.costDifference.totalHours)">{{ variance(order.costDifference.totalHours) }}</td>
            </tr>
            <tr>
              <td>Kilometri</td>
              <td>
                <span *ngIf="!editingCost || !isOffice()">{{ order.estimatedCosts.km ?? '-' }}</span>
                <input *ngIf="editingCost && isOffice()" [(ngModel)]="cost.estimated.km" type="number" min="0" style="width:80px" />
              </td>
              <td><span *ngIf="!editingCost">{{ order.actualCosts.km ?? '-' }}</span>
                  <input *ngIf="editingCost" [(ngModel)]="cost.actual.km" type="number" min="0" style="width:80px" /></td>
              <td [class]="varianceClass(order.costDifference.km)">{{ variance(order.costDifference.km) }}</td>
            </tr>
            <tr>
              <td>Materijal (EUR)</td>
              <td>
                <span *ngIf="!editingCost || !isOffice()">{{ order.estimatedCosts.materialCost ?? '-' }}</span>
                <input *ngIf="editingCost && isOffice()" [(ngModel)]="cost.estimated.materialCost" type="number" min="0" step="0.01" style="width:80px" />
              </td>
              <td><span *ngIf="!editingCost">{{ order.actualCosts.materialCost ?? '-' }}</span>
                  <input *ngIf="editingCost" [(ngModel)]="cost.actual.materialCost" type="number" min="0" step="0.01" style="width:80px" /></td>
              <td [class]="varianceClass(order.costDifference.materialCost)">{{ variance(order.costDifference.materialCost) }}</td>
            </tr>
          </tbody>
        </table>
        </div>
        <div class="muted" style="font-size:.78rem;margin-top:6px">Ukupno sati = radni sati × broj radnika</div>
        <div class="field-error" *ngFor="let e of costFieldErrors()">{{ e }}</div>

        <!-- Office edits estimated and actual, the servicer only actual -->
        <div class="action-bar" *ngIf="canChange() && !editingCost">
          <button class="btn btn-outline btn-sm" (click)="startCostEdit()">
            {{ isOffice() ? 'Uredi troškove' : 'Unesi stvarne troškove' }}
          </button>
        </div>
        <div class="action-bar" *ngIf="editingCost">
          <button class="btn btn-primary btn-sm" (click)="saveCost()">Spremi troškove</button>
          <button class="btn btn-secondary btn-sm" (click)="editingCost=false">Odustani</button>
        </div>
      </div>

      <!-- Photos -->
      <div class="card">
        <h3>Fotografije</h3>
        <div class="photo-grid">
          <div class="photo-thumb" *ngFor="let p of order.photos">
            <img [src]="p.url" [alt]="'Fotografija'" />
            <button class="photo-delete" *ngIf="canChange()" (click)="deletePhoto(p.id)">×</button>
          </div>
          <div *ngIf="order.photos.length === 0" style="color:#888">Nema fotografija.</div>
        </div>
        <div class="action-bar" *ngIf="canChange() && order.photos.length < maxPhotos">
          <label class="btn btn-outline btn-sm" style="cursor:pointer">
            Dodaj fotografiju
            <input type="file" [accept]="photoTypes" style="display:none" (change)="uploadPhoto($event)" />
          </label>
          <span class="muted" style="font-size:.78rem;align-self:center">JPEG, PNG, GIF ili WebP, najviše 10 MB</span>
        </div>
      </div>

      <!-- Notes -->
      <div class="card">
        <h3 class="notes-title" (click)="toggleNotesOrder()" title="Promijeni redoslijed">
          Bilješke <span class="muted" style="font-size:.8rem">{{ notesNewestFirst ? '(najnovije prve ↓)' : '(najstarije prve ↑)' }}</span>
        </h3>
        <div *ngFor="let n of sortedNotes()" class="note-item">
          <span class="note-author">{{ n.authorName }}</span>
          <span class="note-date">{{ n.createdAt | date:'dd.MM.yyyy HH:mm' }}</span>
          <p>{{ n.text }}</p>
        </div>
        <div *ngIf="order.notes.length === 0" style="color:#888;margin-bottom:8px">Nema bilješki.</div>
        <div class="action-bar" *ngIf="canChange()">
          <textarea [(ngModel)]="newNote" rows="2" maxlength="2000" placeholder="Nova bilješka..." style="width:100%;margin-bottom:8px;padding:8px;border:1px solid #ccc;border-radius:4px"></textarea>
          <button class="btn btn-outline btn-sm" (click)="addNote()" [disabled]="!newNote.trim()">Bilješke +</button>
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
    .note-item { border-left: 3px solid #2e7d32; padding: 6px 12px; margin-bottom: 10px; background: #f9f9f9; border-radius: 0 4px 4px 0; }
    .note-author { font-weight: 600; font-size: .85rem; }
    .note-date { font-size: .78rem; color: #888; margin-left: 8px; }
    .note-item p { margin: 4px 0 0; white-space: pre-line; }
    .notes-title { cursor: pointer; user-select: none; }
    .error-card { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; .error { margin: 0; } }
  `],
})
export class OrderDetailComponent implements OnInit {
  order: OrderDto | null = null;
  loading = true;
  editing = false;
  editingCost = false;
  editData: OrderRequest = emptyRequest();
  editLocations: LocationDto[] = [];
  cost: { estimated: CostsRequest; actual: CostsRequest } = { estimated: emptyCosts(), actual: emptyCosts() };
  newNote = '';
  notesNewestFirst = true;
  selectedStatus: OrderStatus | null = null;
  selectedServicerId: number | null = null;
  servicers: UserDto[] = [];
  branches: BranchDto[] = [];
  clients: ClientDto[] = [];
  error = '';
  versionConflict = false;
  fieldErrors: Record<string, string> = {};
  urgencyOptions = ALL_URGENCIES;
  urgencyLabels = URGENCY_LABELS;
  maxPhotos = MAX_PHOTOS;
  photoTypes = PHOTO_TYPES;

  isOffice = computed(() => this.auth.hasRole('OFFICE', 'ADMIN'));
  isServicer = computed(() => this.auth.hasRole('SERVICER'));

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private orderSvc: OrderService,
    private userSvc: UserService,
    private branchSvc: BranchService,
    private clientSvc: ClientService,
    private auth: AuthService,
  ) {}

  ngOnInit() {
    this.reload();
    if (this.isOffice()) {
      this.userSvc.getEmployees('SERVICER').subscribe(s => this.servicers = s.filter(servicer => servicer.active));
      this.branchSvc.getBranches().subscribe(b => this.branches = b);
      this.clientSvc.getClients().subscribe(c => this.clients = c);
    }
  }

  reload() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.orderSvc.getOrder(id).subscribe({
      next: o => { this.show(o); this.loading = false; this.clearError(); },
      error: e => { this.showError(e); this.loading = false; },
    });
  }

  statusLabel(s: OrderStatus) { return STATUS_LABELS[s]; }

  locationText(location: LocationDto | null): string {
    return locationText(location);
  }

  canChange(): boolean {
    return this.isOffice() || this.isAssignedToMe();
  }

  // Unassigned PENDING: any servicer can take it
  canAccept(): boolean {
    return this.order?.status === 'PENDING' && !this.order.assignedServicer;
  }

  canAssign(): boolean {
    return this.order?.status === 'PENDING' || this.order?.status === 'IN_PROGRESS';
  }

  // Info

  startEdit() {
    if (!this.order) { return; }
    this.editData = requestFrom(this.order);
    this.editLocations = this.locationsOf(this.editData.clientId);
    this.fieldErrors = {};
    this.editing = true;
  }

  onEditClientChange() {
    this.editLocations = this.locationsOf(this.editData.clientId);
    this.editData.locationId = null;
  }

  saveEdit() {
    this.run(this.orderSvc.updateOrder(this.order!.id, this.editData), () => this.editing = false);
  }

  deleteOrder() {
    if (!confirm('Sigurno želite izbrisati nalog?')) { return; }
    this.orderSvc.deleteOrder(this.order!.id).subscribe({
      next: () => this.router.navigate(['/orders']),
      error: e => this.showError(e),
    });
  }

  // Servicer and status

  accept() {
    this.run(this.orderSvc.acceptOrder(this.order!.id));
  }

  assign() {
    if (!this.selectedServicerId) { return; }
    this.run(this.orderSvc.assignServicer(this.order!.id, this.selectedServicerId, this.order!.version));
  }

  changeStatus() {
    if (!this.selectedStatus) { return; }
    this.run(this.orderSvc.changeStatus(this.order!.id, this.selectedStatus, this.order!.version));
  }

  // Costs

  startCostEdit() {
    if (!this.order) { return; }
    this.cost = {
      estimated: costsRequestOf(this.order.estimatedCosts),
      actual: costsRequestOf(this.order.actualCosts),
    };
    this.fieldErrors = {};
    this.editingCost = true;
  }

  // Actual costs have their own endpoint. The office's estimated costs are part of the order,
  // so they go with a full order PUT afterwards, with the version from the first response.
  saveCost() {
    const order = this.order!;
    const savedActual = this.orderSvc.updateActualCosts(order.id, this.cost.actual, order.version);
    const saved = this.isOffice()
      ? savedActual.pipe(switchMap(updated => this.saveEstimated(updated)))
      : savedActual;
    this.run(saved, () => this.editingCost = false);
  }

  costFieldErrors(): string[] {
    return Object.entries(this.fieldErrors)
      .filter(([field]) => field.startsWith('costs.') || field.startsWith('estimatedCosts.'))
      .map(([, message]) => message);
  }

  variance(difference: number | null): string {
    if (difference == null) { return '-'; }
    return (difference > 0 ? '+' : '') + difference;
  }

  varianceClass(difference: number | null): string {
    if (difference == null) { return ''; }
    return difference > 0 ? 'variance-pos' : difference < 0 ? 'variance-neg' : '';
  }

  // Photos

  uploadPhoto(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) { return; }
    this.run(this.orderSvc.addPhoto(this.order!.id, file));
  }

  deletePhoto(photoId: number) {
    if (!confirm('Izbrisati fotografiju?')) { return; }
    this.orderSvc.deletePhoto(this.order!.id, photoId).subscribe({
      next: () => this.reload(),
      error: e => this.showError(e),
    });
  }

  // Notes: the backend sends them newest first

  toggleNotesOrder() {
    this.notesNewestFirst = !this.notesNewestFirst;
  }

  sortedNotes() {
    const notes = this.order?.notes ?? [];
    return this.notesNewestFirst ? notes : [...notes].reverse();
  }

  addNote() {
    if (!this.newNote.trim()) { return; }
    this.run(this.orderSvc.addNote(this.order!.id, this.newNote), () => this.newNote = '');
  }

  // Runs a change and shows the order from the response. done() runs only on success.
  private run(change: Observable<OrderDto>, done: () => void = () => {}) {
    this.clearError();
    change.subscribe({
      next: o => { this.show(o); done(); },
      error: e => this.showError(e),
    });
  }

  private saveEstimated(order: OrderDto): Observable<OrderDto> {
    const request = { ...requestFrom(order), estimatedCosts: this.cost.estimated };
    return this.orderSvc.updateOrder(order.id, request);
  }

  private show(order: OrderDto) {
    this.order = order;
    this.selectedStatus = null;
    this.selectedServicerId = order.assignedServicer?.id ?? null;
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

  private isAssignedToMe(): boolean {
    const me = this.auth.currentUser()?.userId;
    return this.isServicer() && !!this.order?.assignedServicer && this.order.assignedServicer.id === me;
  }

  private locationsOf(clientId: number | null): LocationDto[] {
    return this.clients.find(c => c.id === clientId)?.locations ?? [];
  }
}

// PUT is a full replace, so every field of the order goes back, with the version it was read with
function requestFrom(order: OrderDto): OrderRequest {
  return {
    branchId: order.branch?.id ?? null,
    clientId: order.client?.id ?? null,
    locationId: order.location?.id ?? null,
    contactPerson: order.contactPerson,
    phone: order.phone,
    email: order.email,
    description: order.description,
    urgency: order.urgency,
    estimatedCosts: costsRequestOf(order.estimatedCosts),
    version: order.version,
  };
}

function costsRequestOf(costs: CostsDto): CostsRequest {
  return {
    km: costs.km,
    workHours: costs.workHours,
    numberOfWorkers: costs.numberOfWorkers,
    materialCost: costs.materialCost,
  };
}

function emptyCosts(): CostsRequest {
  return { km: null, workHours: null, numberOfWorkers: null, materialCost: null };
}

function emptyRequest(): OrderRequest {
  return {
    branchId: null, clientId: null, locationId: null, contactPerson: null, phone: null,
    email: null, description: null, urgency: null, estimatedCosts: null,
  };
}
