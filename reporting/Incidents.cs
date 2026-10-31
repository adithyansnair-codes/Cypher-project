using Npgsql;
using MySqlConnector;
using System.Data.Common;

namespace Cypher.Reporter;

/// <summary>One incident, flattened with its camera and assignee.</summary>
public sealed record Incident(
    long Id,
    string Title,
    string? Description,
    string Severity,
    string Status,
    DateTimeOffset OccurredAt,
    DateTimeOffset? ResolvedAt,
    string? CameraName,
    string? CameraCode,
    string? Location,
    string? AssignedTo);

/// <summary>
/// Connection settings. The variable names are the ones the Spring Boot
/// gateway already uses (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD), so
/// one .env file configures both services.
/// </summary>
public static class Database
{
    public static bool IsMySql => Setting("DB_ENGINE", "postgres").ToLowerInvariant() switch
    {
        "mysql" => true,
        "postgres" or "postgresql" => false,
        var engine => throw new InvalidOperationException($"Unsupported DB_ENGINE: {engine}")
    };

    public static string ConnectionString()
    {
        if (IsMySql)
        {
            return new MySqlConnectionStringBuilder
            {
                Server = Setting("DB_HOST", "localhost"),
                Port = uint.Parse(Setting("DB_PORT", "3306")),
                Database = Setting("DB_NAME", "cypher"),
                UserID = Setting("DB_USER", "cypher_user"),
                Password = Setting("DB_PASSWORD", ""),
                DateTimeKind = MySqlDateTimeKind.Utc,
                SslMode = Enum.Parse<MySqlSslMode>(Setting("DB_SSL_MODE", "Required"), true)
            }.ConnectionString;
        }
        return new NpgsqlConnectionStringBuilder
        {
            Host = Setting("DB_HOST", "localhost"),
            Port = int.Parse(Setting("DB_PORT", "5432")),
            Database = Setting("DB_NAME", "cypher"),
            Username = Setting("DB_USER", "cypher_user"),
            Password = Setting("DB_PASSWORD", "proj577!")
        }.ConnectionString;
    }

    public static DbConnection Connect(string connectionString) => IsMySql
        ? new MySqlConnection(connectionString) : new NpgsqlConnection(connectionString);

    public static bool Reachable(string connectionString)
    {
        try
        {
            using var connection = Database.Connect(connectionString);
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = "SELECT 1";
            command.ExecuteScalar();
            return true;
        }
        catch (Exception)
        {
            return false;
        }
    }

    private static string Setting(string key, string fallback)
    {
        var value = Environment.GetEnvironmentVariable(key);
        return string.IsNullOrWhiteSpace(value) ? fallback : value;
    }
}

public static class IncidentReader
{
    private const string Sql = """
        SELECT i.incident_id,
               i.title,
               i.description,
               i.severity,
               i.status,
               i.occurred_at,
               i.resolved_at,
               c.camera_name,
               c.camera_code,
               c.location,
               NULLIF(TRIM(CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))), '') AS assigned_to
          FROM incidents i
          LEFT JOIN cameras c ON c.camera_id = i.camera_id
          LEFT JOIN users   u ON u.user_id   = i.assigned_to
         WHERE (@status = '' OR i.status = @status)
           AND (@severity = '' OR i.severity = @severity)
         ORDER BY i.occurred_at DESC
         LIMIT @limit
        """;

    /// <summary>Newest first. An empty status/severity means "no filter".</summary>
    public static List<Incident> Read(string connectionString, string? status, string? severity, int limit)
    {
        using var connection = Database.Connect(connectionString);
        connection.Open();

        using var command = connection.CreateCommand();
        command.CommandText = Sql;
        if (Database.IsMySql)
        {
            using var timezone = connection.CreateCommand();
            timezone.CommandText = "SET time_zone = '+00:00'";
            timezone.ExecuteNonQuery();
        }
        AddParameter(command, "status", (status ?? string.Empty).Trim().ToUpperInvariant());
        AddParameter(command, "severity", (severity ?? string.Empty).Trim().ToUpperInvariant());
        AddParameter(command, "limit", Math.Clamp(limit, 1, 5000));

        var incidents = new List<Incident>();
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            incidents.Add(new Incident(
                Id: reader.GetInt64(0),
                Title: reader.GetString(1),
                Description: reader.IsDBNull(2) ? null : reader.GetString(2),
                Severity: reader.GetString(3),
                Status: reader.GetString(4),
                OccurredAt: UtcDate(reader, 5),
                ResolvedAt: reader.IsDBNull(6) ? null : UtcDate(reader, 6),
                CameraName: reader.IsDBNull(7) ? null : reader.GetString(7),
                CameraCode: reader.IsDBNull(8) ? null : reader.GetString(8),
                Location: reader.IsDBNull(9) ? null : reader.GetString(9),
                AssignedTo: reader.IsDBNull(10) ? null : reader.GetString(10)));
        }

        return incidents;
    }
    private static void AddParameter(DbCommand command, string name, object value)
    {
        var parameter = command.CreateParameter();
        parameter.ParameterName = name;
        parameter.Value = value;
        command.Parameters.Add(parameter);
    }

    private static DateTimeOffset UtcDate(DbDataReader reader, int ordinal) =>
        new(DateTime.SpecifyKind(reader.GetDateTime(ordinal), DateTimeKind.Utc));

}
