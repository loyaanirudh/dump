import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class GraphQLResponseTransformer extends ResponseTemplateTransformer {

    private static final Handlebars handlebars = new Handlebars();

    public GraphQLResponseTransformer() {
        super(true); // Enable global response templating
    }

    @Override
    public String getName() {
        return "graphql-transformer";
    }

    @Override
    public Response transform(ServeEvent serveEvent, Request request, ResponseDefinition responseDefinition, Parameters parameters) {
        String mockDataFilePath = parameters.getString("mockDataFilePath");
        String responseFormat = parameters.getString("responseFormat");
        String notFoundResponseFormat = parameters.getString("notFoundResponseFormat");

        try (InputStream mockDataStream = getClass().getResourceAsStream("/mockdata/" + mockDataFilePath)) {
            String mockData = mockDataStream != null ? IOUtils.toString(mockDataStream, "UTF-8") : null;

            if (mockData != null) {
                ObjectMapper objectMapper = new ObjectMapper();
                List<Map<String, Object>> mockDataList = objectMapper.readValue(mockData, List.class);
                String requestBody = request.getBodyAsString();

                if (responseFormat == null && notFoundResponseFormat == null) {
                    // Return the full mock data as the response
                    return Response.Builder.like(responseDefinition).but().body(mockData).build();
                } else {
                    int requestedId = getRequestedIdFromRequest(requestBody);
                    String body = processRequest(mockDataList, responseFormat, notFoundResponseFormat, requestedId);
                    return Response.Builder.like(responseDefinition).but().body(body).build();
                }
            } else {
                log.error("Mock data file not found: {}", mockDataFilePath);
                return Response.Builder.like(responseDefinition).but().body("{}").build();
            }
        } catch (IOException e) {
            log.error("Failed to process GraphQL request: {}", e.getMessage());
            return Response.Builder.like(responseDefinition).but().body("{}").build();
        }
    }

    private String processRequest(List<Map<String, Object>> mockDataList, String responseTemplate, String notFoundResponseTemplate, int requestedId) throws IOException {
        if (responseTemplate == null || notFoundResponseTemplate == null) {
            return "{}"; // Return an empty response if templates are not provided
        }

        for (Map<String, Object> mockEntry : mockDataList) {
            if (mockEntry.get("id").equals(requestedId)) {
                Template template = handlebars.compileInline(responseTemplate);
                return template.apply(mockEntry);
            }
        }

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("id", requestedId);
        Template template = handlebars.compileInline(notFoundResponseTemplate);
        return template.apply(errorResponse);
    }

    private int getRequestedIdFromRequest(String requestBody) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> requestMap = objectMapper.readValue(requestBody, HashMap.class);
            Map<String, Object> variables = (Map<String, Object>) requestMap.get("variables");
            return (int) variables.get("id");
        } catch (IOException e) {
            log.error("Failed to extract ID from request: {}", e.getMessage());
            return -1;
        }
    }
}
