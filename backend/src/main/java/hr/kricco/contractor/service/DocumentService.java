package hr.kricco.contractor.service;

import hr.kricco.contractor.entity.Costs;
import hr.kricco.contractor.entity.DocumentType;
import hr.kricco.contractor.entity.Location;
import hr.kricco.contractor.entity.Order;
import hr.kricco.contractor.entity.OrderNote;
import hr.kricco.contractor.entity.OrderPhoto;
import hr.kricco.contractor.entity.OrderStatus;
import hr.kricco.contractor.entity.Urgency;
import hr.kricco.contractor.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

// The four printable documents of an order, as whole HTML pages (layouts from the template project).
// Built from the current order data on every call, nothing is stored. The user prints or saves a PDF from the browser.
// Access is checked by the caller (OrderService). Runs inside the caller's transaction: notes and photos load lazily.
// Every value from the database is HTML-escaped: the frontend opens the page with the app's origin (services.md [S9]).
@Service
public class DocumentService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // The business works in Croatia, so dates are Croatian dates whatever the server's time zone is
    private static final ZoneId ZONE = ZoneId.of("Europe/Zagreb");

    private final String baseUrl;

    // Photos are <img> tags with an absolute URL: the page is opened as a blob, where a relative URL doesn't work
    public DocumentService(@Value("${app.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String render(Order order, DocumentType type) {
        return switch (type) {
            case QUOTE -> quote(order);
            case WORK_ORDER -> workOrder(order);
            case REPORT -> report(order);
            case INVOICE -> invoice(order);
        };
    }

    private String quote(Order o) {
        String body = """
                <table class="info">
                  <tr><th>Broj naloga</th><td>%s</td></tr>
                  <tr><th>Datum</th><td>%s</td></tr>
                  <tr><th>Poslovnica</th><td>%s</td></tr>
                  <tr><th>Klijent</th><td>%s</td></tr>
                  <tr><th>Lokacija</th><td>%s</td></tr>
                  <tr><th>Kontakt osoba</th><td>%s</td></tr>
                  <tr><th>Telefon</th><td>%s</td></tr>
                  <tr><th>Hitnost</th><td>%s</td></tr>
                </table>

                <h3>Opis radova</h3>
                <p class="description">%s</p>

                <h3>Procijenjeni troškovi</h3>
                %s

                <div class="signature-block">
                  <div class="sig-row">
                    <div>
                      <p class="sig-label">Mjesto i datum:</p>
                      <p class="sig-line">_______________________</p>
                    </div>
                    <div>
                      <p class="sig-label">Potpis i pečat:</p>
                      <p class="sig-line">_______________________</p>
                    </div>
                  </div>
                </div>
                """.formatted(
                orderNumber(o), today(), branch(o), client(o),
                location(o.getLocation()), text(o.getContactPerson()), text(o.getPhone()), urgency(o.getUrgency()),
                text(o.getDescription()),
                costTable(o.getEstimatedCosts()));
        return page("PONUDA", orderNumber(o), body);
    }

    private String workOrder(Order o) {
        String body = """
                <table class="info">
                  <tr><th>Broj naloga</th><td>%s</td></tr>
                  <tr><th>Datum</th><td>%s</td></tr>
                  <tr><th>Poslovnica</th><td>%s</td></tr>
                  <tr><th>Lokacija</th><td>%s</td></tr>
                  <tr><th>Kontakt osoba</th><td>%s</td></tr>
                  <tr><th>Telefon</th><td>%s</td></tr>
                  <tr><th>Hitnost</th><td>%s</td></tr>
                  <tr><th>Dodijeljeni serviser</th><td>%s</td></tr>
                </table>

                <h3>Opis radova</h3>
                <p class="description">%s</p>

                <h3>Fotografije</h3>
                %s

                <h3>Bilješke na terenu</h3>
                <div class="field-box"></div>
                <div class="field-box"></div>

                <h3>Troškovi (popunjava serviser)</h3>
                <table class="cost">
                  <tr><th>Radni sati</th><td></td></tr>
                  <tr><th>Broj radnika</th><td></td></tr>
                  <tr><th>Ukupno sati</th><td></td></tr>
                  <tr><th>Kilometri</th><td></td></tr>
                  <tr><th>Materijal (EUR)</th><td></td></tr>
                </table>

                <div class="signature-block">
                  <div class="sig-row">
                    <div>
                      <p class="sig-label">Datum i potpis servisera:</p>
                      <p class="sig-line">_______________________</p>
                    </div>
                    <div>
                      <p class="sig-label">Potpis naručitelja:</p>
                      <p class="sig-line">_______________________</p>
                    </div>
                  </div>
                </div>
                """.formatted(
                orderNumber(o), today(), branch(o), location(o.getLocation()),
                text(o.getContactPerson()), text(o.getPhone()), urgency(o.getUrgency()),
                servicer(o), text(o.getDescription()), photoGrid(o));
        return page("RADNI NALOG", orderNumber(o), body);
    }

    private String report(Order o) {
        String body = """
                <table class="info">
                  <tr><th>Broj naloga</th><td>%s</td></tr>
                  <tr><th>Datum</th><td>%s</td></tr>
                  <tr><th>Poslovnica</th><td>%s</td></tr>
                  <tr><th>Klijent</th><td>%s</td></tr>
                  <tr><th>Lokacija</th><td>%s</td></tr>
                  <tr><th>Serviser</th><td>%s</td></tr>
                  <tr><th>Status</th><td>%s</td></tr>
                </table>

                <h3>Opis radova</h3>
                <p class="description">%s</p>

                <h3>Bilješke</h3>
                %s

                <h3>Stvarni troškovi</h3>
                %s

                <h3>Fotografije</h3>
                %s

                <div class="signature-block">
                  <div class="sig-row">
                    <div>
                      <p class="sig-label">Mjesto i datum:</p>
                      <p class="sig-line">_______________________</p>
                    </div>
                    <div>
                      <p class="sig-label">Potpis ovlaštene osobe:</p>
                      <p class="sig-line">_______________________</p>
                    </div>
                  </div>
                </div>
                """.formatted(
                orderNumber(o), today(), branch(o), client(o), location(o.getLocation()),
                servicer(o), status(o.getStatus()), text(o.getDescription()),
                notes(o), costTable(o.getActualCosts()), photoGrid(o));
        return page("IZVJEŠTAJ O RADOVIMA", orderNumber(o), body);
    }

    private String invoice(Order o) {
        String body = """
                <table class="info">
                  <tr><th>Broj naloga</th><td>%s</td></tr>
                  <tr><th>Datum naloga</th><td>%s</td></tr>
                  <tr><th>Datum računa</th><td>%s</td></tr>
                  <tr><th>Poslovnica</th><td>%s</td></tr>
                  <tr><th>Klijent</th><td>%s</td></tr>
                  <tr><th>Lokacija</th><td>%s</td></tr>
                </table>

                <h3>Obračun troškova</h3>
                %s

                <div class="vat-box">
                  ⚠ Ovdje se upisuju zakonski obvezni podaci o PDV-u, OIB-u i broju računa.
                  (Placeholder – nije implementirano u POC verziji.)
                </div>

                <div class="signature-block">
                  <div class="sig-row">
                    <div>
                      <p class="sig-label">Mjesto i datum:</p>
                      <p class="sig-line">_______________________</p>
                    </div>
                    <div>
                      <p class="sig-label">Potpis i pečat:</p>
                      <p class="sig-line">_______________________</p>
                    </div>
                  </div>
                </div>
                """.formatted(
                orderNumber(o), date(o.getCreatedAt()), today(), branch(o), client(o), location(o.getLocation()),
                costTable(o.getActualCosts()));
        return page("RAČUN", orderNumber(o), body);
    }

    private String page(String title, String orderNumber, String body) {
        return """
                <!DOCTYPE html>
                <html lang="hr">
                <head>
                  <meta charset="UTF-8">
                  <title>%s – %s</title>
                  <style>
                    *, *::before, *::after { box-sizing: border-box; }
                    body {
                      font-family: Arial, Helvetica, sans-serif;
                      font-size: 13px; color: #222; margin: 0; padding: 32px 40px;
                    }
                    h1 {
                      color: #2e7d32; border-bottom: 2px solid #2e7d32;
                      padding-bottom: 6px; margin: 0 0 20px; font-size: 1.5rem;
                    }
                    h3 { margin: 20px 0 6px; font-size: .95rem; color: #444; }
                    p  { margin: 4px 0; }

                    table.info { border-collapse: collapse; width: 100%%; margin-bottom: 4px; }
                    table.info th {
                      text-align: left; width: 170px; padding: 5px 10px;
                      background: #f1f8e9; font-weight: 600; border: 1px solid #ddd;
                    }
                    table.info td { padding: 5px 10px; border: 1px solid #ddd; }

                    table.cost { border-collapse: collapse; width: 100%%; }
                    table.cost th, table.cost td {
                      border: 1px solid #ccc; padding: 6px 12px; font-size: .88rem;
                    }
                    table.cost th { background: #e8f5e9; text-align: left; width: 200px; }
                    table.cost td { text-align: right; }

                    .description { white-space: pre-wrap; background: #fafafa;
                      border-left: 3px solid #2e7d32; padding: 8px 12px; border-radius: 0 4px 4px 0; }

                    .photos { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
                    .photos img { width: 150px; height: 110px; object-fit: cover;
                      border: 1px solid #ccc; border-radius: 4px; }

                    .note { margin: 6px 0; padding: 6px 10px; white-space: pre-wrap;
                      border-left: 3px solid #a5d6a7; background: #f9fbe7; }
                    .note-author { font-weight: 600; }

                    .field-box { border: 1px solid #bbb; border-radius: 4px;
                      height: 60px; margin-bottom: 8px; }

                    .signature-block { margin-top: 48px; }
                    .sig-row { display: flex; gap: 80px; }
                    .sig-label { font-size: .85rem; color: #666; margin-bottom: 32px; }
                    .sig-line { border-top: 1px solid #555; width: 220px; padding-top: 4px;
                      font-size: .8rem; color: #888; }

                    .vat-box { border: 2px dashed #f57c00; padding: 14px 16px;
                      background: #fff8e1; color: #e65100; border-radius: 4px;
                      margin: 12px 0; font-size: .9rem; }

                    .print-btn {
                      position: fixed; top: 16px; right: 16px;
                      background: #2e7d32; color: white; border: none;
                      padding: 9px 22px; border-radius: 4px; cursor: pointer;
                      font-size: .9rem; font-weight: 600; box-shadow: 0 2px 6px rgba(0,0,0,.2);
                    }
                    .print-btn:hover { background: #1b5e20; }

                    @page  { margin: 18mm 20mm; }
                    @media print {
                      body { padding: 0; }
                      .print-btn { display: none; }
                    }
                  </style>
                </head>
                <body>
                  <button class="print-btn" onclick="window.print()">Ispis / PDF</button>
                  <h1>%s</h1>
                  %s
                </body>
                </html>
                """.formatted(title, orderNumber, title, body);
    }

    // Null costs (all columns empty) print as "-" like any missing value
    private String costTable(Costs costs) {
        Costs values = costs == null ? new Costs() : costs;
        return """
                <table class="cost">
                  <tr><th>Radni sati</th><td>%s</td></tr>
                  <tr><th>Broj radnika</th><td>%s</td></tr>
                  <tr><th>Ukupno sati</th><td>%s</td></tr>
                  <tr><th>Kilometri</th><td>%s km</td></tr>
                  <tr><th>Materijal</th><td>%s EUR</td></tr>
                </table>
                """.formatted(
                number(values.getWorkHours()), number(values.getNumberOfWorkers()), number(values.getTotalHours()),
                number(values.getKm()), number(values.getMaterialCost()));
    }

    private String photoGrid(Order o) {
        if (o.getPhotos().isEmpty()) {
            return "<p><em>Nema fotografija.</em></p>";
        }
        String images = o.getPhotos().stream()
                .map(this::photoTag)
                .collect(Collectors.joining());
        return "<div class=\"photos\">" + images + "</div>";
    }

    private String photoTag(OrderPhoto photo) {
        return "<img src=\"" + escape(baseUrl + "/api/files/" + photo.getFilename()) + "\" alt=\"foto\">";
    }

    // Oldest first, as a report reads from the start of the work
    private String notes(Order o) {
        if (o.getNotes().isEmpty()) {
            return "<p><em>Nema bilješki.</em></p>";
        }
        return o.getNotes().reversed().stream()
                .map(this::noteTag)
                .collect(Collectors.joining());
    }

    private String noteTag(OrderNote note) {
        return "<div class=\"note\"><span class=\"note-author\">" + escape(note.getAuthor().getDisplayName())
                + "</span> (" + date(note.getCreatedAt()) + ") &mdash; " + escape(note.getText()) + "</div>";
    }

    // A draft has no number yet
    private String orderNumber(Order o) {
        return o.getOrderNumber() == null ? "Nacrt" : escape(o.getOrderNumber());
    }

    private String branch(Order o) {
        return o.getBranch() == null ? "-" : escape(o.getBranch().getName());
    }

    private String client(Order o) {
        return o.getClient() == null ? "-" : escape(o.getClient().getName());
    }

    private String servicer(Order o) {
        User servicer = o.getAssignedServicer();
        return servicer == null ? "-" : escape(servicer.getDisplayName());
    }

    // "Skladište – Vukovarska 18, Split 21000", without the name if it has none
    private String location(Location location) {
        if (location == null) {
            return "-";
        }
        String name = location.getName() == null ? "" : location.getName() + " – ";
        return escape(name + location.getAddress() + ", " + location.getCity());
    }

    // Croatian labels from domain-model OrderStatus
    private String status(OrderStatus status) {
        return switch (status) {
            case DRAFT -> "Nacrt";
            case PENDING -> "Na čekanju";
            case IN_PROGRESS -> "U tijeku";
            case RESOLVED -> "Riješen";
            case CANCELLED -> "Otkazan";
        };
    }

    // Croatian labels from domain-model Urgency
    private String urgency(Urgency urgency) {
        if (urgency == null) {
            return "-";
        }
        return switch (urgency) {
            case SAME_DAY -> "Isti dan";
            case ONE_DAY -> "1 dan";
            case ONE_WEEK -> "1 tjedan";
            case ONE_MONTH -> "1 mjesec";
            case SIX_MONTHS -> "6 mjeseci";
        };
    }

    private String text(String value) {
        return value == null ? "-" : escape(value);
    }

    private String number(Number value) {
        return value == null ? "-" : value.toString();
    }

    private String date(Instant instant) {
        return instant.atZone(ZONE).format(DATE_FORMAT);
    }

    private String today() {
        return LocalDate.now(ZONE).format(DATE_FORMAT);
    }

    // The five characters that matter in HTML text and attribute values
    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
