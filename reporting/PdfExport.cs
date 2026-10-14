using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace Cypher.Reporter;

public static class PdfExport
{
    public static byte[] Render(IReadOnlyList<Incident> incidents, DateTimeOffset generatedAt)
    {
        var document = Document.Create(container =>
        {
            container.Page(page =>
            {
                page.Size(PageSizes.A4);
                page.Margin(28);
                page.DefaultTextStyle(style => style.FontSize(9));

                page.Header().Column(column =>
                {
                    column.Item().Text("CYPHER - Incident report").FontSize(17).SemiBold();
                    column.Item()
                        .Text($"Generated {generatedAt:yyyy-MM-dd HH:mm} UTC - {incidents.Count} incident(s)")
                        .FontSize(9)
                        .FontColor(Colors.Grey.Darken1);
                    column.Item().PaddingTop(6).LineHorizontal(1).LineColor(Colors.Grey.Lighten1);
                });

                page.Content().PaddingTop(10).Table(table =>
                {
                    table.ColumnsDefinition(columns =>
                    {
                        columns.ConstantColumn(30);   // id
                        columns.ConstantColumn(74);   // occurred
                        columns.ConstantColumn(52);   // severity
                        columns.ConstantColumn(62);   // status
                        columns.RelativeColumn(3);    // title
                        columns.RelativeColumn(2);    // camera
                    });

                    table.Header(header =>
                    {
                        header.Cell().Element(HeaderStyle).Text("ID");
                        header.Cell().Element(HeaderStyle).Text("Occurred");
                        header.Cell().Element(HeaderStyle).Text("Severity");
                        header.Cell().Element(HeaderStyle).Text("Status");
                        header.Cell().Element(HeaderStyle).Text("Title");
                        header.Cell().Element(HeaderStyle).Text("Camera");
                    });

                    foreach (var incident in incidents)
                    {
                        table.Cell().Element(BodyStyle).Text(incident.Id.ToString());
                        table.Cell().Element(BodyStyle).Text(incident.OccurredAt.UtcDateTime.ToString("yyyy-MM-dd HH:mm"));
                        table.Cell().Element(BodyStyle).Text(incident.Severity);
                        table.Cell().Element(BodyStyle).Text(incident.Status);
                        table.Cell().Element(BodyStyle).Text(incident.Title);
                        table.Cell().Element(BodyStyle).Text(Where(incident));
                    }
                });

                page.Footer().AlignRight().Text(text =>
                {
                    text.CurrentPageNumber();
                    text.Span(" / ");
                    text.TotalPages();
                });
            });
        });

        return document.GeneratePdf();
    }

    private static string Where(Incident incident)
    {
        var parts = new[] { incident.CameraCode, incident.Location }
            .Where(part => !string.IsNullOrWhiteSpace(part));
        return string.Join(" / ", parts);
    }

    private static IContainer HeaderStyle(IContainer container) =>
        container.Background(Colors.Grey.Lighten3).PaddingVertical(4).PaddingHorizontal(4);

    private static IContainer BodyStyle(IContainer container) =>
        container.BorderBottom(1).BorderColor(Colors.Grey.Lighten2).PaddingVertical(4).PaddingHorizontal(4);
}
