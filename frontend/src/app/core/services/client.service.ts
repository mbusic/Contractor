import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_URL } from '../api';
import { ClientDto, ClientRequest, LocationDto, LocationRequest } from '../models/models';

// Clients and their locations (/api/clients, ADMIN and OFFICE),
// plus the client user's own locations in the portal (/api/portal/locations, CLIENT)
@Injectable({ providedIn: 'root' })
export class ClientService {
  constructor(private http: HttpClient) {}

  getClients() {
    return this.http.get<ClientDto[]>(`${API_URL}/clients`);
  }

  getClient(id: number) {
    return this.http.get<ClientDto>(`${API_URL}/clients/${id}`);
  }

  createClient(request: ClientRequest) {
    return this.http.post<ClientDto>(`${API_URL}/clients`, request);
  }

  updateClient(id: number, request: ClientRequest) {
    return this.http.put<ClientDto>(`${API_URL}/clients/${id}`, request);
  }

  deleteClient(id: number) {
    return this.http.delete<void>(`${API_URL}/clients/${id}`);
  }

  addLocation(clientId: number, request: LocationRequest) {
    return this.http.post<LocationDto>(`${API_URL}/clients/${clientId}/locations`, request);
  }

  updateLocation(clientId: number, locationId: number, request: LocationRequest) {
    return this.http.put<LocationDto>(`${API_URL}/clients/${clientId}/locations/${locationId}`, request);
  }

  deleteLocation(clientId: number, locationId: number) {
    return this.http.delete<void>(`${API_URL}/clients/${clientId}/locations/${locationId}`);
  }

  // Client portal

  getPortalLocations() {
    return this.http.get<LocationDto[]>(`${API_URL}/portal/locations`);
  }

  addPortalLocation(request: LocationRequest) {
    return this.http.post<LocationDto>(`${API_URL}/portal/locations`, request);
  }
}
