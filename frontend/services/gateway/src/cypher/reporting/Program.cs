using System.Data;
using System.Text;
using ClosedXML.Excel;
using Npgsql;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.UseUrls("http://0.0.0");
QuestPDF.Settings.License = LicenseType.Community;

var connectionString = builder.Configuration.GetConnectionString("Cypher")
    ?? "Host=localhost;Port=5432;Database=cypher;Username=cypher;Password=cypher_dev_password";
builder.Services.AddSingleton(new ReportRepository(connectionString));
builder.Services.AddHostedService<AlertProcessor>();

var app = builder.Build();

app.MapGet("/health", () => Results.Ok(new { status = "healthy", service = "reporting" }));
app.MapGet("/api/reports/incidents.json", async (ReportRepository repository, CancellationToken cancellationToken) =>
    Results.Ok(await repository.GetIncidentsAsync(cancellationToken)));

app.MapGet("/api/reports/incidents.csv", async (ReportRepository repository, CancellationToken cancellationToken) =>
{
    var incidents = await repository.GetIncidentsAsync(cancellationToken);
    var csv = new StringBuilder("Id,Type,Priority,Status,Asset,Confidence,Camera,Location,Summary,CreatedAt\n");
    foreach (var incident in incidents)
    {
        csv.AppendLine(string.Join(',',
            incident.Id, Escape(incident.IncidentType),
            Escape(incident.Priority), Escape(incident.Status),
            Escape(incident.AssetType),
            incident.Confidence.ToString("0.000", System.Globalization.CultureInfo.InvariantCulture),
            Escape(incident.CameraName), Escape(incident.Location),
            Escape(incident.Summary), Escape(incident.CreatedAt)));
    }
    return Results.File(Encoding.UTF8.GetBytes(csv.ToString()), "text/csv", "cypher-incidents.csv");
});

app.MapGet("/api/reports/incidents.xlsx", async (ReportRepository repository, CancellationToken cancellationToken) =>
{
    var incidents = await repository.GetIncidentsAsync(cancellationToken);
    using var workbook = new XLWorkbook();
    var sheet = workbook.Worksheets.Add("Incidents");
    var headers = new[] { "Id", "Type", "Priority", "Status", "Asset", "Confidence", "Camera", "Location", "Summary", "Created At" };
    for (var column = 0; column < headers.Length; column++)
    {
        sheet.Cell(1, column + 1).Value = headers[column];
        sheet.Cell(1, column + 1).Style.Font.Bold = true;
        sheet.Cell(1, column + 1).Style.Fill.BackgroundColor = XLColor.FromHtml("#12304A");
        sheet.Cell(1, column + 1).Style.Font.FontColor = XLColor.White;
    }
    for (var row = 0; row < incidents.Count; row++)
    {
    for (var row = 0; row < incidents.Count; row++)
    {
        var item = incidents[row];
        sheet.Cell(row + 2, 1).Value = item.Id;
        sheet.Cell(row + 2, 2).Value = item.IncidentType;
        sheet.Cell(row + 2, 3).Value = item.Priority;
        sheet.Cell(row + 2, 4).Value = item.Status;
        sheet.Cell(row + 2, 5).Value = item.AssetType;
        sheet.Cell(row + 2, 6).Value = item.Confidence;
        sheet.Cell(row + 2, 7).Value = item.CameraName;
        sheet.Cell(row + 2, 8).Value = item.Location;
        sheet.Cell(row + 2, 9).Value = item.Summary;
        sheet.Cell(row + 2, 10).Value = item.CreatedAt;
    }
    sheet.Columns().AdjustToContents();
    using var stream = new MemoryStream();
    workbook.SaveAs(stream);
    return Results.File(stream.ToArray(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "cypher-incidents.xlsx");
});
app.MapGet("/api/reports/incidents.pdf", async (ReportRepository repository, CancellationToken cancellationToken) =>
{
    var incidents = await repository.GetIncidentsAsync(cancellationToken);
    using var stream = new MemoryStream();
    Document.Create(container => container.Page(page =>
    {
        page.Margin(30);
        page.Header().Text("Project Cypher - Incident Report").FontSize(18).Bold().FontColor(Colors.Blue.Darken3);
        page.Content().PaddingTop(16).Table(table =>
        {
            table.ColumnsDefinition(columns =>
            {
                columns.ConstantColumn(32); columns.ConstantColumn(62);
                columns.ConstantColumn(62);
                columns.ConstantColumn(60); columns.RelativeColumn();
                columns.ConstantColumn(92);
            });
            table.Header(header =>
            {
            table.Header(header =>
            {
                foreach (var value in new[] { "ID", "Type", "Priority", "Status", "Camera", "Created" })
                {
                    header.Cell().Background(Colors.Blue.Darken3).Padding(4).Text(value).FontColor(Colors.White).FontSize(8).Bold();
                }
            });
            foreach (var incident in incidents)
            {
                var values = new[] { incident.Id.ToString(), incident.IncidentType, incident.Priority, incident.Status, incident.CameraName, incident.CreatedAt };
                foreach (var value in values)
                {
                    table.Cell().BorderBottom(1).BorderColor(Colors.Grey.Lighten2).Padding(4).Text(value).FontSize(8);
                }
            }
        });
        page.Footer().AlignCenter().Text(text => text.Span("Generated by Cypher Reporting Service").FontSize(8).FontColor(Colors.Grey.Darken1));
    })).GeneratePdf(stream);
    return Results.File(stream.ToArray(), "application/pdf", "cypher-incidents.pdf");
});

app.Run();

static string Escape(string? value)
{
    var text = value ?? "";
    return text.Contains(',') || text.Contains('"') || text.Contains('\n') ? $"\"{text.Replace("\"", "\"\"")}\"" : text;
}

public sealed record IncidentRecord(long Id, string IncidentType, string Priority, string Status, string AssetType,
    double Confidence, string CameraName, string Location, string Summary, string CreatedAt);

public sealed class ReportRepository
{
    private readonly string connectionString;

    public ReportRepository(string connectionString) =>
    this.connectionString = connectionString;

    public async Task<List<IncidentRecord>> GetIncidentsAsync(CancellationToken cancellationToken)
    {
        var result = new List<IncidentRecord>();
        await using var connection = new NpgsqlConnection(connectionString);
        await connection.OpenAsync(cancellationToken);
        await using var command = new NpgsqlCommand("""
            SELECT i.id, i.with, i.incident_type, i.priority, i.status, i.asset_type, i.confidence,
                   c.name, COALESCE(c.location, ''), i.summary, i.created_at
            FROM incidents i JOIN cameras c ON c.id = i.camera_id
            ORDER BY i.created_at DESC
            """, connection);
        await using var reader = await command.ExecuteReaderAsync(CommandBehavior.SequentialAccess, cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            result.Add(new IncidentRecord(
                reader.GetInt64(0), reader.GetString(1),
        connectionString = configuration.GetConnectionString("Cypher")
            ?? "Host=localhost;Port=5432;Database=cypher;Username=cypher;Password=cypher_dev_password";
        this.logger = logger;
        pollSeconds = Math.Max(2, configuration.GetValue<int?>("REPORTING_POLL_SECONDS") ?? 5);
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try { await ProcessPendingAlerts(stoppingToken); }
            catch (Exception exception) { logger.LogError(exception, "Alert processing cycle failed"); }
    private async Task ProcessPendingAlerts(CancellationToken cancellationToken)
    {
        await using var connection = new NpgsqlConnection(connectionString);
        await connection.OpenAsync(cancellationToken);
        await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
        await using var command = new NpgsqlCommand("""
            UPDATE alerts SET status = 'SENT', processed_at = NOW()
            WHERE id IN (SELECT id FROM alerts WHERE status = 'PENDING'
            ORDER BY created_at LIMIT 25 FOR UPDATE SKIP LOCKED)
            RETURNING id, alert_type, incident_id
            """, connection, transaction);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var processed = 0;
        while (await reader.ReadAsync(cancellationToken))
        {
        while (await reader.ReadAsync(cancellationToken))
        {
            processed++;
            logger.LogInformation("Dispatching {AlertType} for incident {IncidentId}", reader.GetString(1), reader.GetInt64(2));
        }
        await reader.CloseAsync();
        await transaction.CommitAsync(cancellationToken);
        if (processed > 0) logger.LogInformation("Processed {Count} alert(s)", processed);
    }
}
