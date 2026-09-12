// The API contract (specs/rest-api.md "Data shapes"), one interface per backend DTO.
// Requests end in Request, responses in Dto. Enum values stay in English, the UI shows Croatian labels (labels.ts).
// Every editable row has a version: a write sends back the version it read (optimistic locking).

export type Role = 'ADMIN' | 'OFFICE' | 'SERVICER' | 'CLIENT';
export type ClientType = 'COMPANY' | 'INDIVIDUAL';
export type OrderStatus = 'DRAFT' | 'PENDING' | 'IN_PROGRESS' | 'RESOLVED' | 'CANCELLED';
export type Urgency = 'SAME_DAY' | 'ONE_DAY' | 'ONE_WEEK' | 'ONE_MONTH' | 'SIX_MONTHS';

// Errors: RFC 9457 Problem Details. fieldErrors only for a failed Bean Validation check.
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  fieldErrors?: { field: string; message: string }[];
}

// Auth

export interface LoginRequest {
  username: string;
  password: string;
}

// branchId is set for OFFICE and SERVICER, clientId for CLIENT
export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  role: Role;
  displayName: string;
  branchId: number | null;
  clientId: number | null;
}

// Users

export interface UserDto {
  id: number;
  username: string;
  role: Role;
  displayName: string;
  branchId: number | null;
  branchName: string | null;
  clientId: number | null;
  clientName: string | null;
  active: boolean;
  version: number;
}

// password: required on create, empty on update keeps the current one. version: required on update.
export interface EmployeeRequest {
  username: string;
  password: string | null;
  role: Role;
  displayName: string;
  branchId: number | null;
  active: boolean;
  version?: number;
}

export interface ClientUserRequest {
  username: string;
  password: string | null;
  displayName: string;
  version?: number;
}

// Branches and clients

export interface BranchDto {
  id: number;
  name: string;
  city: string | null;
  version: number;
}

export interface BranchRequest {
  name: string;
  city: string | null;
  version?: number;
}

export interface LocationDto {
  id: number;
  name: string | null;
  address: string;
  city: string;
  version: number;
}

export interface LocationRequest {
  name: string | null;
  address: string;
  city: string;
  version?: number;
}

export interface ClientDto {
  id: number;
  type: ClientType;
  name: string;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  locations: LocationDto[];
  version: number;
}

export interface ClientRequest {
  type: ClientType;
  name: string;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  version?: number;
}

// The client as shown on an order, without its locations
export interface ClientSummaryDto {
  id: number;
  type: ClientType;
  name: string;
}

// Orders

// totalHours is calculated (work hours x workers). In costDifference every field is actual - estimated, or null.
export interface CostsDto {
  km: number | null;
  workHours: number | null;
  numberOfWorkers: number | null;
  totalHours: number | null;
  materialCost: number | null;
}

export interface CostsRequest {
  km: number | null;
  workHours: number | null;
  numberOfWorkers: number | null;
  materialCost: number | null;
}

export interface ServicerDto {
  id: number;
  displayName: string;
}

// Newest first in OrderDto.notes
export interface NoteDto {
  id: number;
  text: string;
  authorId: number;
  authorName: string;
  createdAt: string;
}

// url is relative: /api/files/<uuid>.<ext>
export interface PhotoDto {
  id: number;
  url: string;
}

// One row of the order list. locationText is "address, city".
export interface OrderSummaryDto {
  id: number;
  orderNumber: string | null;
  status: OrderStatus;
  urgency: Urgency | null;
  branchId: number | null;
  branchName: string | null;
  clientName: string | null;
  locationText: string | null;
  assignedServicerId: number | null;
  assignedServicerName: string | null;
  createdAt: string;
}

// allowedNextStatuses is empty when the user may not change the order
export interface OrderDto {
  id: number;
  orderNumber: string | null;
  status: OrderStatus;
  allowedNextStatuses: OrderStatus[];
  urgency: Urgency | null;
  branch: BranchDto | null;
  client: ClientSummaryDto | null;
  location: LocationDto | null;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  description: string | null;
  assignedServicer: ServicerDto | null;
  estimatedCosts: CostsDto;
  actualCosts: CostsDto;
  costDifference: CostsDto;
  notes: NoteDto[];
  photos: PhotoDto[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

// All fields optional while DRAFT. Client and location are required once submitted.
export interface OrderRequest {
  branchId: number | null;
  clientId: number | null;
  locationId: number | null;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  description: string | null;
  urgency: Urgency | null;
  estimatedCosts: CostsRequest | null;
  version?: number;
}

// Client portal: no costs, notes, servicer or branch

export interface PortalOrderSummaryDto {
  id: number;
  orderNumber: string | null;
  status: OrderStatus;
  urgency: Urgency | null;
  locationText: string | null;
  createdAt: string;
}

export interface PortalOrderDto {
  id: number;
  orderNumber: string | null;
  status: OrderStatus;
  urgency: Urgency | null;
  location: LocationDto | null;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  description: string | null;
  photos: PhotoDto[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

// The location must be one of the client's, and is required on submit
export interface PortalOrderRequest {
  locationId: number | null;
  contactPerson: string | null;
  phone: string | null;
  email: string | null;
  description: string | null;
  urgency: Urgency | null;
  version?: number;
}
