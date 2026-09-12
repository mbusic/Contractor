import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

// Opens a printable document (a whole HTML page from the backend) in a new tab.
// The tab is opened right away, in the click itself, so the browser's popup blocker allows it;
// the page is put into it when the HTML arrives. On an error the tab is closed again.
export function openDocument(html: Observable<string>, onError: (error: HttpErrorResponse) => void) {
  const tab = window.open('', '_blank');
  html.subscribe({
    next: page => {
      const url = URL.createObjectURL(new Blob([page], { type: 'text/html' }));
      if (tab) {
        tab.location.href = url;
      }
      // The tab has read the blob by then
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    },
    error: error => {
      tab?.close();
      onError(error);
    },
  });
}
