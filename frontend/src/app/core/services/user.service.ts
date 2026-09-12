import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_URL } from '../api';
import { ClientUserRequest, EmployeeRequest, Role, UserDto } from '../models/models';

// Employees (/api/users, ADMIN writes, OFFICE reads) and client users (/api/clients/{id}/users, ADMIN and OFFICE)
@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  // Active and deactivated employees. With a role, only that role (e.g. SERVICER for the assignment dropdown).
  getEmployees(role?: Role) {
    const params = role ? new HttpParams().set('role', role) : undefined;
    return this.http.get<UserDto[]>(`${API_URL}/users`, { params });
  }

  createEmployee(request: EmployeeRequest) {
    return this.http.post<UserDto>(`${API_URL}/users`, request);
  }

  updateEmployee(id: number, request: EmployeeRequest) {
    return this.http.put<UserDto>(`${API_URL}/users/${id}`, request);
  }

  // Employees are never deleted: DELETE deactivates the account
  deactivateEmployee(id: number) {
    return this.http.delete<void>(`${API_URL}/users/${id}`);
  }

  getClientUsers(clientId: number) {
    return this.http.get<UserDto[]>(`${API_URL}/clients/${clientId}/users`);
  }

  createClientUser(clientId: number, request: ClientUserRequest) {
    return this.http.post<UserDto>(`${API_URL}/clients/${clientId}/users`, request);
  }

  updateClientUser(clientId: number, userId: number, request: ClientUserRequest) {
    return this.http.put<UserDto>(`${API_URL}/clients/${clientId}/users/${userId}`, request);
  }

  deleteClientUser(clientId: number, userId: number) {
    return this.http.delete<void>(`${API_URL}/clients/${clientId}/users/${userId}`);
  }
}
