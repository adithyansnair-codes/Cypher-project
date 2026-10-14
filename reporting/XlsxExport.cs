using ClosedXML.Excel;

namespace Cypher.Reporter;

public static class XlsxExport
{
    private static readonly string[] Headers =
    {
        "ID", "Occurred (UTC)", "Severity", "Status", "Title", "Description",
        "Camera", "Code", "Location", "Assigned to", "Resolved (UTC)"
    };

    public static byte[] Render(IReadOnlyList<Incident> incidents)
    {
        using var workbook = new XLWorkbook();
        var sheet = workbook.Worksheets.Add("Incidents");

        for (var column = 0; column < Headers.Length; column++)
        {
            sheet.Cell(1, column + 1).Value = Headers[column];
        }

        var heading = sheet.Range(1, 1, 1, Headers.Length);
        heading.Style.Font.Bold = true;
        heading.Style.Fill.BackgroundColor = XLColor.FromHtml("#E8EEF2");

        var row = 2;
        foreach (var incident in incidents)
        {
            sheet.Cell(row, 1).Value = incident.Id;
            sheet.Cell(row, 2).Value = incident.OccurredAt.UtcDateTime;
            sheet.Cell(row, 3).Value = incident.Severity;
            sheet.Cell(row, 4).Value = incident.Status;
            sheet.Cell(row, 5).Value = incident.Title;
            sheet.Cell(row, 6).Value = incident.Description ?? string.Empty;
            sheet.Cell(row, 7).Value = incident.CameraName ?? string.Empty;
            sheet.Cell(row, 8).Value = incident.CameraCode ?? string.Empty;
            sheet.Cell(row, 9).Value = incident.Location ?? string.Empty;
            sheet.Cell(row, 10).Value = incident.AssignedTo ?? string.Empty;
            if (incident.ResolvedAt is not null)
            {
                sheet.Cell(row, 11).Value = incident.ResolvedAt.Value.UtcDateTime;
            }
            row++;
        }

        sheet.Column(2).Style.DateFormat.Format = "yyyy-mm-dd hh:mm";
        sheet.Column(11).Style.DateFormat.Format = "yyyy-mm-dd hh:mm";
        sheet.SheetView.FreezeRows(1);
        sheet.Columns().AdjustToContents();

        using var stream = new MemoryStream();
        workbook.SaveAs(stream);
        return stream.ToArray();
    }
}
