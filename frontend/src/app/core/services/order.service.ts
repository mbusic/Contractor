import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_URL } from '../api';
import {
  CostsRequest,
  OrderDto,
  OrderRequest,
  OrderStatus,
  OrderSummaryDto,
  PortalOrderDto,
  PortalOrderRequest,
  PortalOrderSummaryDto,
} from '../models/models';

// Orders for employees (/api/orders) and for client users in the portal (/api/portal/orders).
// Writes that change the order send the version it was read with, else the backend answers 409.
// The document endpoints come with the document views (roadmap step 9).
@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private http: HttpClient) {}

  getOrders() {
    return this.http.get<OrderSummaryDto[]>(`${API_URL}/orders`);
  }

  getOrder(id: number) {
    return this.http.get<OrderDto>(`${API_URL}/orders/${id}`);
  }

  // Always a new DRAFT. "Submit" is changeStatus(PENDING) afterwards.
  createOrder(request: OrderRequest) {
    return this.http.post<OrderDto>(`${API_URL}/orders`, request);
  }

  // Full replace of the order data and the estimated costs
  updateOrder(id: number, request: OrderRequest) {
    return this.http.put<OrderDto>(`${API_URL}/orders/${id}`, request);
  }

  deleteOrder(id: number) {
    return this.http.delete<void>(`${API_URL}/orders/${id}`);
  }

  changeStatus(id: number, status: OrderStatus, version: number) {
    return this.http.patch<OrderDto>(`${API_URL}/orders/${id}/status`, { status, version });
  }

  // A servicer takes an unassigned PENDING order. No version: the backend stops two servicers at once.
  acceptOrder(id: number) {
    return this.http.post<OrderDto>(`${API_URL}/orders/${id}/accept`, null);
  }

  assignServicer(id: number, servicerId: number, version: number) {
    return this.http.put<OrderDto>(`${API_URL}/orders/${id}/assignment`, { servicerId, version });
  }

  updateActualCosts(id: number, costs: CostsRequest, version: number) {
    return this.http.put<OrderDto>(`${API_URL}/orders/${id}/actual-costs`, { costs, version });
  }

  addNote(id: number, text: string) {
    return this.http.post<OrderDto>(`${API_URL}/orders/${id}/notes`, { text });
  }

  addPhoto(id: number, file: File) {
    return this.http.post<OrderDto>(`${API_URL}/orders/${id}/photos`, photoForm(file));
  }

  deletePhoto(id: number, photoId: number) {
    return this.http.delete<void>(`${API_URL}/orders/${id}/photos/${photoId}`);
  }

  // Client portal: only the client's own orders, changes only while DRAFT

  getPortalOrders() {
    return this.http.get<PortalOrderSummaryDto[]>(`${API_URL}/portal/orders`);
  }

  getPortalOrder(id: number) {
    return this.http.get<PortalOrderDto>(`${API_URL}/portal/orders/${id}`);
  }

  createPortalOrder(request: PortalOrderRequest) {
    return this.http.post<PortalOrderDto>(`${API_URL}/portal/orders`, request);
  }

  updatePortalOrder(id: number, request: PortalOrderRequest) {
    return this.http.put<PortalOrderDto>(`${API_URL}/portal/orders/${id}`, request);
  }

  submitPortalOrder(id: number, version: number) {
    return this.http.post<PortalOrderDto>(`${API_URL}/portal/orders/${id}/submit`, { version });
  }

  addPortalPhoto(id: number, file: File) {
    return this.http.post<PortalOrderDto>(`${API_URL}/portal/orders/${id}/photos`, photoForm(file));
  }

  deletePortalPhoto(id: number, photoId: number) {
    return this.http.delete<void>(`${API_URL}/portal/orders/${id}/photos/${photoId}`);
  }
}

// Multipart body with one part "file", as the photo endpoints expect
function photoForm(file: File): FormData {
  const form = new FormData();
  form.append('file', file);
  return form;
}
