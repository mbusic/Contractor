import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_URL } from '../api';
import { BranchDto, BranchRequest } from '../models/models';

// /api/branches: employees read, ADMIN writes
@Injectable({ providedIn: 'root' })
export class BranchService {
  constructor(private http: HttpClient) {}

  getBranches() {
    return this.http.get<BranchDto[]>(`${API_URL}/branches`);
  }

  getBranch(id: number) {
    return this.http.get<BranchDto>(`${API_URL}/branches/${id}`);
  }

  createBranch(request: BranchRequest) {
    return this.http.post<BranchDto>(`${API_URL}/branches`, request);
  }

  updateBranch(id: number, request: BranchRequest) {
    return this.http.put<BranchDto>(`${API_URL}/branches/${id}`, request);
  }

  deleteBranch(id: number) {
    return this.http.delete<void>(`${API_URL}/branches/${id}`);
  }
}
