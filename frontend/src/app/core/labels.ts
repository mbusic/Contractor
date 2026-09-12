import { Role } from './models/models';

// Croatian labels for the enum values (specs/domain-model.md). Other enums get theirs with the screens that show them.
export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrator',
  OFFICE: 'Dispečer',
  SERVICER: 'Serviser',
  CLIENT: 'Klijent',
};
