import { LocationDto } from './models/models';

// A location as one line: "Skladište – Vukovarska 18, Split 21000", or without the name if it has none
export function locationText(location: LocationDto | null): string {
  if (!location) {
    return '-';
  }
  const name = location.name ? location.name + ' – ' : '';
  return `${name}${location.address}, ${location.city}`;
}
