FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY reporting/ .
RUN dotnet restore --locked-mode && dotnet publish -c Release --no-restore -o /out
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /out .
ENV REPORTING_HOST=0.0.0.0
USER $APP_UID
EXPOSE 5001
ENTRYPOINT ["dotnet", "Cypher.Reporter.dll"]
