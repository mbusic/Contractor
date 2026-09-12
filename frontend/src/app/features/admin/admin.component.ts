import { Component, OnInit, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BranchService } from '../../core/services/branch.service';
import { ClientService } from '../../core/services/client.service';
import { UserService } from '../../core/services/user.service';
import { AuthService } from '../../core/services/auth.service';
import {
  BranchDto, BranchRequest, ClientDto, ClientRequest, ClientUserRequest, EmployeeRequest, LocationDto,
  LocationRequest, Role, UserDto,
} from '../../core/models/models';
import { CLIENT_TYPE_LABELS, ROLE_LABELS } from '../../core/labels';
import { toApiError } from '../../core/errors';

type Tab = 'users' | 'branches' | 'clients';

const EMPLOYEE_ROLES: Role[] = ['ADMIN', 'OFFICE', 'SERVICER'];

// Field names from the backend's fieldErrors, as the forms label them
const FIELD_LABELS: Record<string, string> = {
  username: 'Korisničko ime', password: 'Lozinka', role: 'Uloga', displayName: 'Ime i prezime',
  active: 'Aktivan', name: 'Naziv', city: 'Grad', type: 'Tip', contactPerson: 'Kontakt osoba',
  phone: 'Telefon', email: 'E-mail', address: 'Adresa',
};

// Users, branches and clients. Each role sees what the backend allows (tech-stack.md "Frontend"):
// ADMIN everything; OFFICE the clients with their locations and portal users, and read-only lists of
// employees and branches. Every edit sends the row's version back.
@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2>{{ isAdmin() ? 'Admin' : 'Klijenti' }}</h2>
    <div class="tab-bar">
      <button [class.active]="tab==='users'"    (click)="switchTab('users')">Korisnici</button>
      <button [class.active]="tab==='branches'" (click)="switchTab('branches')">Poslovnice</button>
      <button [class.active]="tab==='clients'"  (click)="switchTab('clients')">Klijenti</button>
    </div>

    <div class="error" *ngIf="error">
      {{ error }}
      <div *ngFor="let e of fieldErrorLines()">{{ e }}</div>
    </div>

    <!-- USERS (employees) -->
    <div class="card" *ngIf="tab==='users'">
      <div class="page-header">
        <h3 style="margin:0">Korisnici</h3>
        <button class="btn btn-primary btn-sm" *ngIf="isAdmin()" (click)="newUser()">Dodaj +</button>
      </div>
      <form *ngIf="editingUser" class="inline-form" (ngSubmit)="saveUser()">
        <input [(ngModel)]="userForm.username" name="username" placeholder="Korisničko ime" required />
        <input [(ngModel)]="userForm.password" name="password" type="password"
               [placeholder]="editingUserId ? 'Nova lozinka (prazno = bez promjene)' : 'Lozinka'" />
        <select [(ngModel)]="userForm.role" name="role" (ngModelChange)="onRoleChange()">
          <option *ngFor="let r of employeeRoles" [ngValue]="r">{{ roleLabels[r] }}</option>
        </select>
        <input [(ngModel)]="userForm.displayName" name="displayName" placeholder="Ime i prezime" />
        <select [(ngModel)]="userForm.branchId" name="branchId" *ngIf="userForm.role !== 'ADMIN'">
          <option [ngValue]="null">-- poslovnica --</option>
          <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
        </select>
        <label *ngIf="editingUserId"><input type="checkbox" [(ngModel)]="userForm.active" name="active" /> Aktivan</label>
        <button type="submit" class="btn btn-primary btn-sm">Spremi</button>
        <button type="button" class="btn btn-secondary btn-sm" (click)="editingUser=false">Odustani</button>
      </form>
      <div class="table-scroll">
      <table class="table" style="margin-top:8px">
        <thead><tr><th>Korisničko ime</th><th>Uloga</th><th>Ime</th><th>Poslovnica</th><th>Aktivan</th><th *ngIf="isAdmin()"></th></tr></thead>
        <tbody>
          <tr *ngFor="let u of users">
            <td>{{ u.username }}</td><td>{{ roleLabels[u.role] }}</td>
            <td>{{ u.displayName }}</td><td>{{ u.branchName ?? '-' }}</td>
            <td>{{ u.active ? 'Da' : 'Ne' }}</td>
            <td *ngIf="isAdmin()" class="row-actions">
              <button class="btn btn-outline btn-sm" (click)="editUser(u)">Uredi</button>
              <button class="btn btn-danger btn-sm" *ngIf="u.active" (click)="deactivateUser(u.id)">Deaktiviraj</button>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <!-- BRANCHES -->
    <div class="card" *ngIf="tab==='branches'">
      <div class="page-header">
        <h3 style="margin:0">Poslovnice</h3>
        <button class="btn btn-primary btn-sm" *ngIf="isAdmin()" (click)="newBranch()">Dodaj +</button>
      </div>
      <form *ngIf="editingBranch" class="inline-form" (ngSubmit)="saveBranch()">
        <input [(ngModel)]="branchForm.name" name="name" placeholder="Naziv" required />
        <input [(ngModel)]="branchForm.city" name="city" placeholder="Grad" />
        <button type="submit" class="btn btn-primary btn-sm">Spremi</button>
        <button type="button" class="btn btn-secondary btn-sm" (click)="editingBranch=false">Odustani</button>
      </form>
      <table class="table" style="margin-top:8px">
        <thead><tr><th>Naziv</th><th>Grad</th><th *ngIf="isAdmin()"></th></tr></thead>
        <tbody>
          <tr *ngFor="let b of branches">
            <td>{{ b.name }}</td><td>{{ b.city }}</td>
            <td *ngIf="isAdmin()" class="row-actions">
              <button class="btn btn-outline btn-sm" (click)="editBranch(b)">Uredi</button>
              <button class="btn btn-danger btn-sm" (click)="deleteBranch(b.id)">Izbriši</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- CLIENTS -->
    <div class="card" *ngIf="tab==='clients'">
      <div class="page-header">
        <h3 style="margin:0">Klijenti</h3>
        <button class="btn btn-primary btn-sm" (click)="newClient()">Dodaj +</button>
      </div>
      <form *ngIf="editingClient" class="inline-form" (ngSubmit)="saveClient()">
        <select [(ngModel)]="clientForm.type" name="type">
          <option value="COMPANY">{{ clientTypeLabels.COMPANY }}</option>
          <option value="INDIVIDUAL">{{ clientTypeLabels.INDIVIDUAL }}</option>
        </select>
        <input [(ngModel)]="clientForm.name" name="name" placeholder="Naziv" required />
        <input [(ngModel)]="clientForm.contactPerson" name="contactPerson" placeholder="Kontakt osoba" />
        <input [(ngModel)]="clientForm.phone" name="phone" placeholder="Telefon" />
        <input [(ngModel)]="clientForm.email" name="email" placeholder="E-mail" />
        <input [(ngModel)]="clientForm.address" name="address" placeholder="Adresa za račun" />
        <button type="submit" class="btn btn-primary btn-sm">Spremi</button>
        <button type="button" class="btn btn-secondary btn-sm" (click)="editingClient=false">Odustani</button>
      </form>
      <div class="table-scroll">
      <table class="table" style="margin-top:8px">
        <thead><tr><th>Naziv</th><th>Tip</th><th>Kontakt</th><th>Telefon</th><th>Lokacije</th><th></th></tr></thead>
        <tbody>
          <ng-container *ngFor="let c of clients">
            <tr>
              <td>{{ c.name }}</td><td>{{ clientTypeLabels[c.type] }}</td>
              <td>{{ c.contactPerson }}</td><td>{{ c.phone }}</td>
              <td>
                <button class="btn btn-secondary btn-sm" (click)="toggleClient(c.id)">
                  Lokacije ({{ c.locations.length }}) i korisnici
                </button>
              </td>
              <td class="row-actions">
                <button class="btn btn-outline btn-sm" (click)="editClient(c)">Uredi</button>
                <button class="btn btn-danger btn-sm" (click)="deleteClient(c.id)">Izbriši</button>
              </td>
            </tr>
            <tr *ngIf="expandedClientId === c.id">
              <td colspan="6" class="locations-panel">
                <h4>Lokacije</h4>
                <table class="table locations-table" *ngIf="c.locations.length > 0">
                  <thead><tr><th>Naziv</th><th>Adresa</th><th>Grad</th><th></th></tr></thead>
                  <tbody>
                    <tr *ngFor="let l of c.locations">
                      <td>{{ l.name || '-' }}</td>
                      <td>{{ l.address }}</td>
                      <td>{{ l.city }}</td>
                      <td class="row-actions">
                        <button class="btn btn-outline btn-sm" (click)="editLocation(l)">Uredi</button>
                        <button class="btn btn-danger btn-sm" (click)="deleteLocation(c.id, l.id)">Izbriši</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p *ngIf="c.locations.length === 0" style="margin:4px 0 8px;color:#666;font-size:.85rem">Nema lokacija.</p>
                <form class="inline-form" (ngSubmit)="saveLocation(c.id)">
                  <input [(ngModel)]="locationForm.name" name="loc-name" placeholder="Naziv (npr. Sjedište)" />
                  <input [(ngModel)]="locationForm.address" name="loc-address" placeholder="Adresa" required />
                  <input [(ngModel)]="locationForm.city" name="loc-city" placeholder="Grad" required />
                  <button type="submit" class="btn btn-primary btn-sm">{{ editingLocationId ? 'Spremi lokaciju' : 'Dodaj lokaciju' }}</button>
                  <button type="button" class="btn btn-secondary btn-sm" *ngIf="editingLocationId" (click)="resetLocationForm()">Odustani</button>
                </form>

                <h4>Korisnici portala</h4>
                <table class="table locations-table" *ngIf="clientUsers.length > 0">
                  <thead><tr><th>Korisničko ime</th><th>Ime</th><th></th></tr></thead>
                  <tbody>
                    <tr *ngFor="let u of clientUsers">
                      <td>{{ u.username }}</td>
                      <td>{{ u.displayName }}</td>
                      <td class="row-actions">
                        <button class="btn btn-outline btn-sm" (click)="editClientUser(u)">Uredi</button>
                        <button class="btn btn-danger btn-sm" (click)="deleteClientUser(c.id, u.id)">Izbriši</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
                <p *ngIf="clientUsers.length === 0" style="margin:4px 0 8px;color:#666;font-size:.85rem">Nema korisnika portala.</p>
                <form class="inline-form" (ngSubmit)="saveClientUser(c.id)">
                  <input [(ngModel)]="clientUserForm.username" name="cu-username" placeholder="Korisničko ime" required />
                  <input [(ngModel)]="clientUserForm.password" name="cu-password" type="password"
                         [placeholder]="editingClientUserId ? 'Nova lozinka (prazno = bez promjene)' : 'Lozinka'" />
                  <input [(ngModel)]="clientUserForm.displayName" name="cu-displayName" placeholder="Ime i prezime" />
                  <button type="submit" class="btn btn-primary btn-sm">{{ editingClientUserId ? 'Spremi korisnika' : 'Dodaj korisnika' }}</button>
                  <button type="button" class="btn btn-secondary btn-sm" *ngIf="editingClientUserId" (click)="resetClientUserForm()">Odustani</button>
                </form>
              </td>
            </tr>
          </ng-container>
        </tbody>
      </table>
      </div>
    </div>
  `,
  styles: [`
    .locations-panel { background:#f9f9f9; padding:12px 16px; }
    .locations-table { margin-bottom:10px; }
    .row-actions { white-space: nowrap; }
    h4 { margin: 8px 0; font-size: .9rem; color: #444; }
  `],
})
export class AdminComponent implements OnInit {
  tab: Tab = 'clients';
  users: UserDto[] = [];
  branches: BranchDto[] = [];
  clients: ClientDto[] = [];
  clientUsers: UserDto[] = [];

  editingUser = false;
  editingBranch = false;
  editingClient = false;
  // The ID of the row being edited, null for a new one
  editingUserId: number | null = null;
  editingBranchId: number | null = null;
  editingClientId: number | null = null;
  editingLocationId: number | null = null;
  editingClientUserId: number | null = null;

  expandedClientId: number | null = null;
  error = '';
  fieldErrors: Record<string, string> = {};

  userForm: EmployeeRequest = emptyUserForm();
  branchForm: BranchRequest = { name: '', city: '' };
  clientForm: ClientRequest = emptyClientForm();
  locationForm: LocationRequest = emptyLocationForm();
  clientUserForm: ClientUserRequest = emptyClientUserForm();

  employeeRoles = EMPLOYEE_ROLES;
  roleLabels = ROLE_LABELS;
  clientTypeLabels = CLIENT_TYPE_LABELS;

  isAdmin = computed(() => this.auth.hasRole('ADMIN'));

  constructor(
    private userSvc: UserService,
    private branchSvc: BranchService,
    private clientSvc: ClientService,
    private auth: AuthService,
  ) {}

  ngOnInit() {
    if (this.isAdmin()) { this.tab = 'users'; }
    this.loadAll();
  }

  switchTab(t: Tab) {
    this.tab = t;
    this.editingUser = this.editingBranch = this.editingClient = false;
    this.clearError();
  }

  loadAll() {
    this.userSvc.getEmployees().subscribe(u => this.users = u);
    this.branchSvc.getBranches().subscribe(b => this.branches = b);
    this.clientSvc.getClients().subscribe(c => this.clients = c);
  }

  fieldErrorLines(): string[] {
    return Object.entries(this.fieldErrors).map(([field, message]) => `${FIELD_LABELS[field] ?? field}: ${message}`);
  }

  // Users (employees)

  newUser() {
    this.userForm = emptyUserForm();
    this.editingUserId = null;
    this.editingUser = true;
  }

  // An empty password keeps the current one
  editUser(u: UserDto) {
    this.userForm = {
      username: u.username, password: '', role: u.role, displayName: u.displayName,
      branchId: u.branchId, active: u.active, version: u.version,
    };
    this.editingUserId = u.id;
    this.editingUser = true;
  }

  // An admin has no branch
  onRoleChange() {
    if (this.userForm.role === 'ADMIN') { this.userForm.branchId = null; }
  }

  saveUser() {
    const saved = this.editingUserId
      ? this.userSvc.updateEmployee(this.editingUserId, this.userForm)
      : this.userSvc.createEmployee(this.userForm);
    this.run(saved, () => { this.editingUser = false; this.loadAll(); });
  }

  // Employees are never deleted: they're deactivated and can be reactivated through "Uredi"
  deactivateUser(id: number) {
    if (confirm('Deaktivirati korisnika? Više se neće moći prijaviti.')) {
      this.run(this.userSvc.deactivateEmployee(id), () => this.loadAll());
    }
  }

  // Branches

  newBranch() {
    this.branchForm = { name: '', city: '' };
    this.editingBranchId = null;
    this.editingBranch = true;
  }

  editBranch(b: BranchDto) {
    this.branchForm = { name: b.name, city: b.city, version: b.version };
    this.editingBranchId = b.id;
    this.editingBranch = true;
  }

  saveBranch() {
    const saved = this.editingBranchId
      ? this.branchSvc.updateBranch(this.editingBranchId, this.branchForm)
      : this.branchSvc.createBranch(this.branchForm);
    this.run(saved, () => { this.editingBranch = false; this.loadAll(); });
  }

  deleteBranch(id: number) {
    if (confirm('Izbrisati?')) { this.run(this.branchSvc.deleteBranch(id), () => this.loadAll()); }
  }

  // Clients

  newClient() {
    this.clientForm = emptyClientForm();
    this.editingClientId = null;
    this.editingClient = true;
  }

  editClient(c: ClientDto) {
    this.clientForm = {
      type: c.type, name: c.name, contactPerson: c.contactPerson, phone: c.phone,
      email: c.email, address: c.address, version: c.version,
    };
    this.editingClientId = c.id;
    this.editingClient = true;
  }

  saveClient() {
    const saved = this.editingClientId
      ? this.clientSvc.updateClient(this.editingClientId, this.clientForm)
      : this.clientSvc.createClient(this.clientForm);
    this.run(saved, () => { this.editingClient = false; this.loadAll(); });
  }

  deleteClient(id: number) {
    if (confirm('Izbrisati?')) {
      this.run(this.clientSvc.deleteClient(id), () => {
        if (this.expandedClientId === id) { this.expandedClientId = null; }
        this.loadAll();
      });
    }
  }

  // One client at a time is open, with its locations and portal users
  toggleClient(clientId: number) {
    this.expandedClientId = this.expandedClientId === clientId ? null : clientId;
    this.resetLocationForm();
    this.resetClientUserForm();
    this.clientUsers = [];
    if (this.expandedClientId) { this.loadClientUsers(this.expandedClientId); }
  }

  // Locations

  editLocation(l: LocationDto) {
    this.locationForm = { name: l.name, address: l.address, city: l.city, version: l.version };
    this.editingLocationId = l.id;
  }

  saveLocation(clientId: number) {
    const saved = this.editingLocationId
      ? this.clientSvc.updateLocation(clientId, this.editingLocationId, this.locationForm)
      : this.clientSvc.addLocation(clientId, this.locationForm);
    this.run(saved, () => { this.resetLocationForm(); this.reloadClients(); });
  }

  deleteLocation(clientId: number, locationId: number) {
    if (confirm('Izbrisati lokaciju?')) {
      this.run(this.clientSvc.deleteLocation(clientId, locationId), () => this.reloadClients());
    }
  }

  resetLocationForm() {
    this.locationForm = emptyLocationForm();
    this.editingLocationId = null;
  }

  // Portal users of a client

  editClientUser(u: UserDto) {
    this.clientUserForm = { username: u.username, password: '', displayName: u.displayName, version: u.version };
    this.editingClientUserId = u.id;
  }

  saveClientUser(clientId: number) {
    const saved = this.editingClientUserId
      ? this.userSvc.updateClientUser(clientId, this.editingClientUserId, this.clientUserForm)
      : this.userSvc.createClientUser(clientId, this.clientUserForm);
    this.run(saved, () => { this.resetClientUserForm(); this.loadClientUsers(clientId); });
  }

  deleteClientUser(clientId: number, userId: number) {
    if (confirm('Izbrisati korisnika portala?')) {
      this.run(this.userSvc.deleteClientUser(clientId, userId), () => this.loadClientUsers(clientId));
    }
  }

  resetClientUserForm() {
    this.clientUserForm = emptyClientUserForm();
    this.editingClientUserId = null;
  }

  private loadClientUsers(clientId: number) {
    this.userSvc.getClientUsers(clientId).subscribe(u => this.clientUsers = u);
  }

  private reloadClients() {
    this.clientSvc.getClients().subscribe(c => this.clients = c);
  }

  // Runs a change and shows a Croatian error message if it fails. done() runs only on success.
  private run(change: Observable<unknown>, done: () => void) {
    this.clearError();
    change.subscribe({
      next: () => done(),
      error: (e: HttpErrorResponse) => this.showError(e),
    });
  }

  private showError(e: HttpErrorResponse) {
    const apiError = toApiError(e);
    this.error = apiError.message;
    this.fieldErrors = apiError.fieldErrors;
  }

  private clearError() {
    this.error = '';
    this.fieldErrors = {};
  }
}

function emptyUserForm(): EmployeeRequest {
  return { username: '', password: '', role: 'OFFICE', displayName: '', branchId: null, active: true };
}

function emptyClientForm(): ClientRequest {
  return { type: 'COMPANY', name: '', contactPerson: '', phone: '', email: '', address: '' };
}

function emptyLocationForm(): LocationRequest {
  return { name: '', address: '', city: '' };
}

function emptyClientUserForm(): ClientUserRequest {
  return { username: '', password: '', displayName: '' };
}
