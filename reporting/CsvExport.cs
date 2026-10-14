using System.Globalization;
using System.Text;

namespace Cypher.Reporter;

public static class CsvExport
{
    private static readonly string[] Headers =
    {
        "incident_id", "occurred_at", "severity", "status", "title", "description",
        "camera_code", "camera_name", "location", "assigned_to", "resolved_at"
    };

    public static byte[] Render(IEnumerable<Incident> incidents)
    {
        var text = new StringBuilder();
        text.AppendLine(string.Join(',', Headers));

        foreach (var incident in incidents)
        {
            text.AppendLine(string.Join(',', new[]
            {
                incident.Id.ToString(CultureInfo.InvariantCulture),
                Iso(incident.OccurredAt),
                incident.Severity,
                incident.Status,
                incident.Title,
                incident.Description ?? string.Empty,
                incident.CameraCode ?? string.Empty,
                incident.CameraName ?? string.Empty,
                incident.Location ?? string.Empty,
                incident.AssignedTo ?? string.Empty,
                incident.ResolvedAt is null ? string.Empty : Iso(incident.ResolvedAt.Value)
            }.Select(Escape)));
        }

        // A UTF-8 BOM so Excel opens the file with the right encoding.
        var preamble = Encoding.UTF8.GetPreamble();
        var body = Encoding.UTF8.GetBytes(text.ToString());
        var bytes = new byte[preamble.Length + body.Length];
        preamble.CopyTo(bytes, 0);
        body.CopyTo(bytes, preamble.Length);
        return bytes;
    }

    private static string Iso(DateTimeOffset value) =>
        value.UtcDateTime.ToString("yyyy-MM-dd HH:mm:ss'Z'", CultureInfo.InvariantCulture);

    private static string Escape(string field) =>
        field.Contains(',') || field.Contains('"') || field.Contains('\n') || field.Contains('\r')
            ? '"' + field.Replace("\"", "\"\"") + '"'
            : field;
}
