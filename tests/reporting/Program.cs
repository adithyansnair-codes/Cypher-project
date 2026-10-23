using System.Text;
using ClosedXML.Excel;
using Cypher.Reporter;

static void Check(bool condition, string message)
{
    if (!condition) throw new Exception(message);
}

var now = DateTimeOffset.UtcNow;
var incident = new Incident(42, "Knife, \"gate\"", "line one\nline two", "CRITICAL", "OPEN", now,
    null, "Entrance", "CAM-001", "Campus", null);
var rows = new[] { incident };
var csv = Encoding.UTF8.GetString(CsvExport.Render(rows));
Check(csv.Contains("\"Knife, \"\"gate\"\"\""), "CSV must escape commas and quotes");
Check(csv.Contains("\"line one\nline two\""), "CSV must quote multiline fields");
Check(CsvExport.Render(Array.Empty<Incident>()).Length > 50, "Empty CSV must retain headers");
using (var stream = new MemoryStream(XlsxExport.Render(rows)))
using (var workbook = new XLWorkbook(stream))
{
    var sheet = workbook.Worksheet("Incidents");
    Check(sheet.Cell(2, 1).GetValue<long>() == 42, "XLSX incident ID mismatch");
    Check(sheet.Cell(2, 5).GetString() == incident.Title, "XLSX title mismatch");
    Check(sheet.Cell(2, 11).IsEmpty(), "Unresolved incident must have no resolved timestamp");
}
QuestPDF.Settings.License = QuestPDF.Infrastructure.LicenseType.Community;
var pdf = PdfExport.Render(rows, now);
Check(Encoding.ASCII.GetString(pdf, 0, 5) == "%PDF-" && pdf.Length > 1000, "PDF did not render");
Check(PdfExport.Render(Array.Empty<Incident>(), now).Length > 1000, "Empty report did not render");
Console.WriteLine("PASS: CSV escaping/headers, XLSX contents/nulls, PDF populated/empty exports");
