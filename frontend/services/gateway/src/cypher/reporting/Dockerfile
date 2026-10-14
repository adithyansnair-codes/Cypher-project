FROM ://microsoft.com AS build
WORKDIR /src
COPY Cypher.Reporting.csproj .
RUN dotnet restore
COPY . .
RUN dotnet publish -c Release -o /out --no-restore

FROM ://microsoft.com
WORKDIR /app
COPY --from=build /out .
ENV ASPNETCORE_URLS=http://+:5001
EXPOSE 5001
ENTRYPOINT ["dotnet", "Cypher.Reporting.dll"]
