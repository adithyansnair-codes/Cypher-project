FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build

WORKDIR /src
COPY Cypher.Reporter.csproj ./
RUN dotnet restore

COPY . .
RUN dotnet publish --no-restore --configuration Release --output /out

FROM mcr.microsoft.com/dotnet/aspnet:10.0

WORKDIR /app
COPY --from=build /out ./

ENV REPORTING_BIND=0.0.0.0
ENV REPORTING_PORT=5001
EXPOSE 5001

ENTRYPOINT ["dotnet", "Cypher.Reporter.dll"]
