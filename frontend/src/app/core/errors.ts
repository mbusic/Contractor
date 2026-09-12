import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from './models/models';

// Turns an API error (Problem Details, specs/rest-api.md) into Croatian text for the UI.
// The backend's messages are English, so the known ones are translated here.
export interface ApiError {
  message: string;
  // Field name (e.g. "name" or "costs.km") -> Croatian text, from a failed Bean Validation check
  fieldErrors: Record<string, string>;
  // Someone else saved the row in between: the page has to reload it
  versionConflict: boolean;
}

const VERSION_CONFLICT = 'Changed by someone else. Reload and try again.';

// Backend detail texts. Patterns for the ones with a value inside.
const DETAIL_TEXTS: [RegExp, string][] = [
  [/^Changed by someone else/, 'Netko je u međuvremenu promijenio podatke. Osvježite i pokušajte ponovno.'],
  [/^Invalid request content/, 'Neispravni podaci. Provjerite označena polja.'],
  [/^Failed to read request/, 'Neispravan zahtjev.'],
  [/^Maximum upload size exceeded/, 'Datoteka je prevelika (najviše 10 MB).'],
  [/^Access denied/, 'Nemate pravo na ovu radnju.'],
  [/^Invalid credentials/, 'Pogrešno korisničko ime ili lozinka.'],
  [/^An admin has no branch/, 'Administrator nema poslovnicu.'],
  [/^An order can have at most (\d+) photos/, 'Nalog može imati najviše $1 fotografija.'],
  [/^Branch is required/, 'Poslovnica je obavezna za ovu ulogu.'],
  [/^Branch not found/, 'Poslovnica nije pronađena.'],
  [/^Branch has orders/, 'Poslovnica ima naloge i ne može se izbrisati.'],
  [/^Branch has users/, 'Poslovnica ima korisnike i ne može se izbrisati.'],
  [/^Client is required/, 'Klijent je obavezan.'],
  [/^Client not found/, 'Klijent nije pronađen.'],
  [/^Client has orders/, 'Klijent ima naloge i ne može se izbrisati.'],
  [/^Client has users/, 'Klijent ima korisnike portala i ne može se izbrisati.'],
  [/^Client user not found/, 'Korisnik portala nije pronađen.'],
  [/^Location doesn't belong to the client/, 'Lokacija ne pripada klijentu.'],
  [/^Location is required/, 'Lokacija je obavezna.'],
  [/^Location not found/, 'Lokacija nije pronađena.'],
  [/^Location is used by orders/, 'Lokacija se koristi na nalozima i ne može se izbrisati.'],
  [/^Only JPEG, PNG, GIF or WebP images are allowed/, 'Dozvoljene su samo slike JPEG, PNG, GIF ili WebP.'],
  [/^The file is not a valid JPEG, PNG, GIF or WebP image/, 'Datoteka nije ispravna slika (JPEG, PNG, GIF ili WebP).'],
  [/^Password is required/, 'Lozinka je obavezna.'],
  [/^Password is too long/, 'Lozinka je predugačka.'],
  [/^Role must be ADMIN, OFFICE or SERVICER/, 'Uloga mora biti administrator, dispečer ili serviser.'],
  [/^Servicer is not active/, 'Serviser nije aktivan.'],
  [/^Servicer not found/, 'Serviser nije pronađen.'],
  [/^User is not a servicer/, 'Korisnik nije serviser.'],
  [/^Version is required/, 'Nedostaje verzija podataka. Osvježite stranicu.'],
  [/^Order is already taken or not pending/, 'Nalog je već preuzet ili više nije na čekanju.'],
  [/^Assign a servicer first/, 'Najprije dodijelite servisera.'],
  [/^Only a draft can be changed/, 'Mijenjati se može samo nacrt.'],
  [/^Only PENDING and IN_PROGRESS orders can be assigned/, 'Serviser se može dodijeliti samo nalogu na čekanju ili u tijeku.'],
  [/^Status change from .* is not allowed/, 'Ova promjena statusa nije dozvoljena.'],
  [/^Username is taken/, 'Korisničko ime je zauzeto.'],
  [/^You can't deactivate your own account/, 'Ne možete deaktivirati vlastiti račun.'],
  [/^You can't remove your own admin role/, 'Ne možete sebi ukloniti ulogu administratora.'],
  [/^You can't change this order/, 'Ne možete mijenjati ovaj nalog.'],
  [/^You can't see this order/, 'Nemate pristup ovom nalogu.'],
  [/^You can't open this document/, 'Ovaj dokument ne možete otvoriti.'],
  [/^Order not found/, 'Nalog nije pronađen.'],
  [/^Photo not found/, 'Fotografija nije pronađena.'],
  [/^Employee not found/, 'Djelatnik nije pronađen.'],
];

// Hibernate Validator's default messages for the constraints the requests use
const FIELD_TEXTS: [RegExp, string][] = [
  [/^must not be blank/, 'Obavezno polje.'],
  [/^must not be null/, 'Obavezno polje.'],
  [/^must be a well-formed email address/, 'Neispravna e-mail adresa.'],
  [/^size must be between 0 and (\d+)/, 'Najviše $1 znakova.'],
  [/^must be greater than or equal to 0/, 'Ne smije biti negativno.'],
  [/^numeric value out of bounds/, 'Prevelik broj.'],
];

export function toApiError(error: HttpErrorResponse): ApiError {
  const problem = asProblem(error.error);
  const detail = problem?.detail ?? '';
  return {
    message: translate(detail, DETAIL_TEXTS) ?? textForStatus(error.status),
    fieldErrors: fieldErrorsOf(problem),
    versionConflict: detail === VERSION_CONFLICT,
  };
}

// A call made with responseType 'text' (the documents) gets the error body as a string
function asProblem(body: unknown): ProblemDetail | null {
  const parsed = typeof body === 'string' ? parseJson(body) : body;
  if (parsed && typeof parsed === 'object' && 'detail' in parsed) {
    return parsed as ProblemDetail;
  }
  return null;
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function fieldErrorsOf(problem: ProblemDetail | null): Record<string, string> {
  const result: Record<string, string> = {};
  for (const fieldError of problem?.fieldErrors ?? []) {
    result[fieldError.field] = translate(fieldError.message, FIELD_TEXTS) ?? 'Neispravna vrijednost.';
  }
  return result;
}

// $1 in the Croatian text is replaced by the first captured value, e.g. the photo limit
function translate(text: string, table: [RegExp, string][]): string | null {
  for (const [pattern, croatian] of table) {
    const match = pattern.exec(text);
    if (match) {
      const value = match[1] ?? '';
      return croatian.replace('$1', value);
    }
  }
  return null;
}

// For errors without a known detail, e.g. the backend isn't reachable
function textForStatus(status: number): string {
  switch (status) {
    case 0:
      return 'Poslužitelj nije dostupan. Pokušajte kasnije.';
    case 403:
      return 'Nemate pravo na ovu radnju.';
    case 404:
      return 'Nije pronađeno.';
    case 413:
      return 'Datoteka je prevelika (najviše 10 MB).';
    default:
      return 'Došlo je do greške. Pokušajte ponovno.';
  }
}
