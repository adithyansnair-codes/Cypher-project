using Cypher.Reporter;

var builder = WebApplication.CreateBuilder(args);

// Loopback only: the Spring Boot gateway is the single public surface and
// proxies /api/reports/** here, so this service never needs to be exposed.
var port = Environment.GetEnvironmentVariable("REPORTING_PORT") ?? "5001";
var host = Environment.GetEnvironmentVariable("REPORTING_HOST") ?? "127.0.0.1";
builder.WebHost.UseUrls($"http://{host}:{port}");

// QuestPDF's community licence covers this project. Without this line the
// library refuses to render at all.
QuestPDF.Settings.License = QuestPDF.Infrastructure.LicenseType.Community;

var app = builder.Build();
var connectionString = Database.ConnectionString();

app.MapGet("/", () => Results.Json(new
{
    service = "cypher-reporter",
    endpoints = new[]
    {
        "GET /health",
        "GET /reports/incidents.csv|xlsx|pdf?status=&severity=&limit="
    }
}));

app.MapGet("/health", () => Results.Json(new
{
    status = "UP",
    service = "cypher-reporter",
    database = Database.Reachable(connectionString) ? "UP" : "DOWN"
}));

app.MapGet("/reports/incidents.{format}", (string format, string? status, string? severity, int? limit) =>
{
    var normalized = format.ToLowerInvariant();
    if (normalized is not ("csv" or "xlsx" or "pdf"))
    {
        return Results.BadRequest(new { error = "format must be one of csv, xlsx, pdf" });
    }

    try
    {
        var incidents = IncidentReader.Read(connectionString, status, severity, limit ?? 500);
        var (bytes, contentType) = normalized switch
        {
            "csv" => (CsvExport.Render(incidents), "text/csv"),
            "xlsx" => (XlsxExport.Render(incidents),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            _ => (PdfExport.Render(incidents, DateTimeOffset.UtcNow), "application/pdf")
        };

        var fileName = $"cypher-incidents-{DateTimeOffset.UtcNow:yyyyMMdd-HHmm}.{normalized}";
        return Results.File(bytes, contentType, fileName);
    }
    catch (Npgsql.NpgsqlException e)
    {
        return Results.Json(
            new { error = "database unavailable", detail = e.Message },
            statusCode: StatusCodes.Status503ServiceUnavailable);
    }
});

app.Run();
