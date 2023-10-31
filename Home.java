package home;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class StockInfoLambda implements RequestHandler<Map<String, String>, String> {

    // Alpaca API credentials
    private static final String ALPACA_API_KEY = "PKAGH9GY39KUFP40A564";
    private static final String ALPACA_SECRET_KEY = "4WOePK0nZ5AfoHqkdGMaTKWB5A4aS3HUPRdHdhG3";
    private static final String ALPACA_BASE_URL = "https://paper-api.alpaca.markets"; // Use 'https://api.alpaca.markets' for live trading

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    // Create an OTLP gRPC exporter for Jaeger
    private static final OtlpGrpcSpanExporter jaegerExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://16.171.176.138:4317") // Specify the correct Jaeger endpoint
            .build();

    // Set the resource for telemetry data (e.g., service name)
    private static final Resource resource = Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "AlpacaHOMEService"));

    // Create a tracer provider with samplers and exporters for tracing
    private static final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setSampler(Sampler.alwaysOn())
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(jaegerExporter).build())
            .build();

    // Build and register OpenTelemetry globally
    private static final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .buildAndRegisterGlobal();

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        String symbol = input.get("symbol");
        Tracer tracer = openTelemetry.getTracer("AlpacaBuyService");
        Span span = tracer.spanBuilder("Retrieve Stock Info").startSpan();
        span.setAttribute("symbol", symbol);

        try {
            String stockInfo = retrieveStockInfo(symbol);
            span.end(); // End the span
            return stockInfo; // Assuming retrieveStockInfo returns a JSON string
        } catch (Exception e) {
            span.end(); // End the span in case of an error
            return "{\"error\": \"" + e.getMessage() + "\"}"; // Return an error message as JSON
        }
    }

    // Retrieve stock information for a given symbol
    private String retrieveStockInfo(String symbol) throws IOException, InterruptedException {
        String apiUrl = ALPACA_BASE_URL + "/v2/assets/" + symbol;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("APCA-API-KEY-ID", ALPACA_API_KEY)
                .header("APCA-API-SECRET-KEY", ALPACA_SECRET_KEY)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw e;
        }
    }
}
