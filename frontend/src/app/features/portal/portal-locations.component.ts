import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ClientService } from '../../core/services/client.service';
import { LocationDto, LocationRequest } from '../../core/models/models';
import { toApiError } from '../../core/errors';

// The client user's work sites. They can add new ones; changes and deletes go through the office.
// Based on the locations panel of the admin page.
@Component({
  selector: 'app-portal-locations',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2>Lokacije</h2>
    <div class="card">
      <div class="error" *ngIf="error">{{ error }}</div>
      <table class="table" *ngIf="locations.length > 0">
        <thead><tr><th>Naziv</th><th>Adresa</th><th>Grad</th></tr></thead>
        <tbody>
          <tr *ngFor="let l of locations">
            <td>{{ l.name || '-' }}</td>
            <td>{{ l.address }}</td>
            <td>{{ l.city }}</td>
          </tr>
        </tbody>
      </table>
      <p *ngIf="locations.length === 0" style="margin:4px 0 8px;color:#666;font-size:.85rem">Nema lokacija.</p>
      <h3>Nova lokacija</h3>
      <form class="inline-form" (ngSubmit)="saveLocation()">
        <input [(ngModel)]="locationForm.name" name="loc-name" placeholder="Naziv (npr. Sjedište)" />
        <input [(ngModel)]="locationForm.address" name="loc-address" placeholder="Adresa" required />
        <input [(ngModel)]="locationForm.city" name="loc-city" placeholder="Grad" required />
        <button type="submit" class="btn btn-primary btn-sm">Dodaj lokaciju</button>
      </form>
    </div>
  `,
})
export class PortalLocationsComponent implements OnInit {
  locations: LocationDto[] = [];
  locationForm: LocationRequest = { name: '', address: '', city: '' };
  error = '';

  constructor(private clientSvc: ClientService) {}

  ngOnInit() {
    this.clientSvc.getPortalLocations().subscribe(l => this.locations = l);
  }

  saveLocation() {
    this.clientSvc.addPortalLocation(this.locationForm).subscribe({
      next: location => {
        this.locations = [...this.locations, location];
        this.locationForm = { name: '', address: '', city: '' };
        this.error = '';
      },
      error: e => this.error = toApiError(e).message,
    });
  }
}
