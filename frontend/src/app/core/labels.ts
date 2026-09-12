import { ClientType, OrderStatus, Role, Urgency } from './models/models';

// Croatian labels for the enum values (specs/domain-model.md)

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrator',
  OFFICE: 'Dispečer',
  SERVICER: 'Serviser',
  CLIENT: 'Klijent',
};

export const STATUS_LABELS: Record<OrderStatus, string> = {
  DRAFT: 'Nacrt',
  PENDING: 'Na čekanju',
  IN_PROGRESS: 'U tijeku',
  RESOLVED: 'Riješen',
  CANCELLED: 'Otkazan',
};

export const URGENCY_LABELS: Record<Urgency, string> = {
  SAME_DAY: 'Isti dan',
  ONE_DAY: '1 dan',
  ONE_WEEK: '1 tjedan',
  ONE_MONTH: '1 mjesec',
  SIX_MONTHS: '6 mjeseci',
};

export const CLIENT_TYPE_LABELS: Record<ClientType, string> = {
  COMPANY: 'Tvrtka',
  INDIVIDUAL: 'Fizička osoba',
};

// For dropdowns, in the order of the enum
export const ALL_STATUSES = Object.keys(STATUS_LABELS) as OrderStatus[];
export const ALL_URGENCIES = Object.keys(URGENCY_LABELS) as Urgency[];
