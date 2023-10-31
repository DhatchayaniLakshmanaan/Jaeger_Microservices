package buyorderhistory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.TimeUnit;

public class StockInfoLambda implements RequestHandler<Map<String, String>, String> {
    private static final String ALPACA_API_KEY = "PKAGH9GY39KUFP40A564";
    private static final String ALPACA_SECRET_KEY = "4WOePK0nZ5AfoHqkdGMaTKWB5A4aS3HUPRdHdhG3";
    private static final String ALPACA_BASE_URL = "https://paper-api.alpaca.markets";
    private static final String API_GATEWAY_ENDPOINT = "https://ny7fbnogel6bwcr36l5y33ehyy0hywgj.lambda-url.us-east-1.on.aws/";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final OtlpGrpcSpanExporter jaegerExporter = OtlpGrpcSpanExporter.builder()
        .setEndpoint("http://13.49.243.139:4317")
        .build();
    private static final Resource resource = Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "BUYService"));
    private static final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .setSampler(Sampler.alwaysOn())
        .setResource(resource)
        .addSpanProcessor(BatchSpanProcessor.builder(jaegerExporter).build())
        .build();
    private static final OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .buildAndRegisterGlobal();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        String symbol = input.get("symbol");
        int quantity = Integer.parseInt(input.get("quantity"));
        Tracer tracer = openTelemetry.getTracer("BUYService");
        Span span = tracer.spanBuilder("Place Stock Order").startSpan();
        span.setAttribute("symbol", symbol);
        span.setAttribute("quantity", quantity);
        try {
            String orderResponse = placeStockOrder(symbol, quantity, span);
            span.setAttribute("orderResponse", orderResponse);
           
            // Create a child span for invokeOrderHistoryLambda
            invokeOrderHistoryLambda(tracer, span);
 
            span.end();
 
            return orderResponse;
        } catch (Exception e) {
            span.end();
            return "Error: " + e.getMessage();
        }
    }

    private String placeStockOrder(String symbol, int quantity, Span parentSpan) throws IOException, InterruptedException {
        String apiUrl = ALPACA_BASE_URL + "/v2/orders";
        String requestBody = objectMapper.writeValueAsString(Map.of(
            "symbol", symbol,
            "qty", quantity,
            "side", "buy",
            "type", "market",
            "time_in_force", "gtc"
        ));

        Span span = parentSpan;
        span.updateName("HTTP POST Request");
        span.setAttribute("http.method", "POST");
 
        HttpRequest postRequest = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("APCA-API-SECRET-KEY", ALPACA_SECRET_KEY)
            .header("APCA-API-KEY-ID", ALPACA_API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        span.setAttribute("http.url", apiUrl);
        span.setAttribute("body", requestBody);
 
        try {
            long startTime = System.nanoTime();
            HttpResponse<String> postResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());
            long endTime = System.nanoTime(); // Capture the end time in nanoseconds
 
            // Calculate the elapsed time in milliseconds
            long elapsedTimeMillis = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
 
            span.setAttribute("startTimeNanos", startTime);
            span.setAttribute("endTimeNanos", endTime);
            span.setAttribute("elapsedTimeMillis", elapsedTimeMillis);
            span.setAttribute("http.status_code", Integer.toString(postResponse.statusCode()));
            span.setAttribute("http.status", postResponse.body());
            span.end();
            return postResponse.body();
        } catch (IOException | InterruptedException e) {
            span.end();
            throw e;
        }
    }

    private void invokeOrderHistoryLambda(Tracer tracer, Span parentSpan) {
        Span span = parentSpan;
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_GATEWAY_ENDPOINT))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
        span.setAttribute("http.url", API_GATEWAY_ENDPOINT);
 
        try {
            long startTime = System.currentTimeMillis();
 
            // Introduce a custom span to represent the 20-second delay
            TimeUnit.SECONDS.sleep(20);
 
            long endTime = System.currentTimeMillis();
            long timeDelay = endTime - startTime;
          
            span.setAttribute("TimeDelayMillis", timeDelay);
           
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            span.setAttribute("http.status", response.body());
 
            if (response.statusCode() == 200) {
                span.setAttribute("http.status_code", "200");
            } else {
                span.setAttribute("http.status_code", Integer.toString(response.statusCode()));
            }
 
            span.end();
        } catch (IOException | InterruptedException e) {
            span.setAttribute("error", true);
            span.setAttribute("error.message", e.getMessage());
            span.end();
        }
    }
}
