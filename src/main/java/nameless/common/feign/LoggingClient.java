package nameless.common.feign;

import com.fasterxml.jackson.core.JsonParseException;
import feign.Client;
import feign.Request;
import feign.Response;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static feign.Util.UTF_8;

/**
 * <pre> {@code
 *     @Bean
 *     public Client feignClient1() {
 *         return new LoggingClient(request -> false, null, new feign.okhttp.OkHttpClient(okHttpClient));
 *     }
 * }
 * </pre>
 */
public class LoggingClient implements Client {
    private static final Logger log = LoggerFactory.getLogger(LoggingClient.class);
    private static final int MAX_LOGGING_SIZE = 5000;
    private final HttpLoggable loggableSetting;
    private final Set<Integer> ignoredHttpCodes;
    private static final int REDIRECTION_HTTP_CODE = 300;
    private final Client client;

    public LoggingClient(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, HttpLoggable loggableSetting) {
        this(sslContextFactory, hostnameVerifier, loggableSetting, null);
    }

    /**
     *
     * @param sslContextFactory ssl context factory
     * @param hostnameVerifier hostname verifier
     * @param loggableSetting Setting for whether to log the request and response
     * @param ignoredHttpCodes Set of HTTP codes that should not be logged as errors
     */
    public LoggingClient(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier, HttpLoggable loggableSetting, Set<Integer> ignoredHttpCodes) {
        this(loggableSetting, ignoredHttpCodes, new Client.Default(sslContextFactory, hostnameVerifier));
    }

    /**
     * @param loggableSetting Setting for whether to log the request and response
     * @param ignoredHttpCodes Set of HTTP codes that should not be logged as errors
     * @param client must not be null
     */
    public LoggingClient(HttpLoggable loggableSetting, Set<Integer> ignoredHttpCodes, Client client) {
        Assert.notNull(client, "client must not be null");
        this.client = client;
        this.loggableSetting = loggableSetting;
        this.ignoredHttpCodes = ignoredHttpCodes == null ? Collections.emptySet() : ignoredHttpCodes;
    }

    public LoggingClient(Set<Integer> ignoredHttpCodes, Client client) {
        Assert.notNull(client, "client must not be null");
        this.client = client;
        this.loggableSetting = request -> false;
        this.ignoredHttpCodes = ignoredHttpCodes == null ? Collections.emptySet() : ignoredHttpCodes;
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        ThreeStates<LogInvocation> logThreeStatesConfig = FeignContext.LoggableContext.logSettings();
        boolean loggable = loggable(request, logThreeStatesConfig);
        try {
            Response response = client.execute(request, options);
            boolean isSuccess = response.status() < REDIRECTION_HTTP_CODE || ignoredHttpCodes.contains(response.status());
            if (!loggable && isSuccess) {
                return response;
            }

            String logMsgBaseTemplate = request.httpMethod() + " " + getUrlLogMsg(request, logThreeStatesConfig) + " {} {}";
            // If the feign request returned a non-success HttpCode (> 300) / Encountered exception, print out the method, url, request & response
            String requestLogMsg = getRequestLogMsg(request, logThreeStatesConfig);
            List<String> responseLogMsg = new LinkedList<>();
            responseLogMsg.add("***RESPONSE***");
            responseLogMsg.add(Integer.toString(response.status()));
            if (response.reason() != null) {
                responseLogMsg.add(response.reason());
            }
            if (response.body() != null && loggable(response.headers())) {
                Response.Body body = response.body();
                if (body.isRepeatable()) {
                    String bodyString = body.toString();
                    if (DesensitizationMode.needDesensitize(logThreeStatesConfig)) {
                        bodyString = desensitizeJsonString(bodyString, logThreeStatesConfig.getValue().sensitiveResponseFields(), logThreeStatesConfig.getValue().desensitizationMode());
                    }
                    if (bodyString.length() > MAX_LOGGING_SIZE) {
                        responseLogMsg.add(bodyString.substring(0, MAX_LOGGING_SIZE) + "...");
                    } else {
                        responseLogMsg.add(bodyString);
                    }
                } else {
                    byte[] bytes = StreamUtils.copyToByteArray(body.asInputStream());
                    Response originalResponse = response;
                    response = Response.builder()
                            .status(response.status())
                            .reason(response.reason())
                            .headers(response.headers())
                            .request(response.request())
                            .body(new InputStreamBody(bytes))
                            .build();
                    originalResponse.close();
                    if (DesensitizationMode.needDesensitize(logThreeStatesConfig)) {
                        responseLogMsg.add(desensitizeJsonString(new String(bytes), logThreeStatesConfig.getValue().sensitiveResponseFields(), logThreeStatesConfig.getValue().desensitizationMode()));
                    } else {
                        responseLogMsg.add(new String(bytes));
                    }
                }
            }

            if (loggable && isSuccess) {
                log.info(logMsgBaseTemplate, requestLogMsg, String.join(" ", responseLogMsg));
            } else {
                log.error(logMsgBaseTemplate, requestLogMsg, String.join(" ", responseLogMsg));
            }
            return response;
        } catch (Exception e) {
            String logMsgBaseTemplate = request.httpMethod() + " " + getUrlLogMsg(request, logThreeStatesConfig) + " {} {}";
            String requestLogMsg = getRequestLogMsg(request, logThreeStatesConfig);
            log.error(logMsgBaseTemplate, requestLogMsg, "Http Request has encountered exception: " + e.getMessage());
            throw e;
        }
    }

    private boolean loggable(Request request, ThreeStates<LogInvocation> logThreeStatesConfig) {
        if (logThreeStatesConfig != null && logThreeStatesConfig != ThreeStates.NONE) {
            return logThreeStatesConfig.isAvailable();
        }
        return this.loggableSetting.loggable(request);
    }

    private String getUrlLogMsg(Request request, ThreeStates<LogInvocation> logThreeStatesConfig) {
        String urlString = request.url();
        if (!DesensitizationMode.needDesensitize(logThreeStatesConfig)) {
            return urlString;
        }
        LogInvocation logInvocation = logThreeStatesConfig.getValue();
        String[] sensitiveParams = logInvocation.sensitiveParams();
        if (sensitiveParams == null) {
            return urlString;
        }
        for (String param : sensitiveParams) {
            String regex = "(?<=\\?|&)" + Pattern.quote(param) + "=([^&]*)";
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(urlString);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String value = m.group(1);
                String desensitizedValue = logInvocation.desensitizationMode().desensitize(value).toString();
                m.appendReplacement(sb, param + "=" + desensitizedValue);
            }
            m.appendTail(sb);
            urlString = sb.toString();
        }
        return urlString;
    }

    @Nonnull
    private Set<String> maskedHeaders(ThreeStates<LogInvocation> logThreeStatesConfig) {
        Set<String> maskedHeaders = new HashSet<>();
        maskedHeaders.add("authorization");
        maskedHeaders.add("api-key");
        if (logThreeStatesConfig != null && logThreeStatesConfig.isAvailable()) {
            for (String header : logThreeStatesConfig.getValue().sensitiveHeaders()) {
                maskedHeaders.add(header.toLowerCase());
            }
        }
        return maskedHeaders;
    }

    protected boolean loggable(Map<String, Collection<String>> headers) {
        Collection<String> contentTypes = headers.get("content-type");
        if (contentTypes == null) {
            return false;
        }

        for (String ct : contentTypes) {
            if (ct.startsWith("application/json") || ct.startsWith("text")) {
                return true;
            }
        }
        return false;
    }

    private String getRequestLogMsg(Request request, ThreeStates<LogInvocation> logThreeStatesConfig) throws IOException {
        List<String> logMsg = new LinkedList<>();
        logMsg.add(getHeaderLogMsg(request, logThreeStatesConfig));
        if (request.httpMethod() != Request.HttpMethod.GET) {
            logMsg.add("-d");
            byte[] bodyByte = request.body();
            if (loggable(request.headers()) && bodyByte != null && bodyByte.length > 0) {
                String body = new String(bodyByte);
                if (DesensitizationMode.needDesensitize(logThreeStatesConfig)) {
                    body = desensitizeJsonString(body, logThreeStatesConfig.getValue().sensitiveRequestBodyFields(), logThreeStatesConfig.getValue().desensitizationMode());
                }
                if (body.length() > MAX_LOGGING_SIZE) {
                    logMsg.add(body.substring(0, MAX_LOGGING_SIZE) + "...");
                } else {
                    logMsg.add(body);
                }
            }
        }
        return String.join(" ", logMsg);
    }

    static String desensitizeJsonString(String jsonString, String[] sensitiveFields, DesensitizationMode desensitizationMode) throws JsonParseException {
        for (String field : new HashSet<>(Arrays.asList(sensitiveFields))) {
            jsonString = desensitizeJsonString(jsonString, field, desensitizationMode);
        }
        return jsonString;
    }

    @SuppressWarnings("unchecked")
    static String desensitizeJsonString(String jsonString, String sensitiveField, DesensitizationMode desensitizationMode) throws JsonParseException {
        int index = jsonString.indexOf("\"" + sensitiveField + "\":");
        if (index < 0) {
            return jsonString;
        }
        StringBuilder sb = new StringBuilder(jsonString.length());
        int nextPosition = index + sensitiveField.length() + 3;
        sb.append(jsonString, 0, nextPosition);
        char nextChar = jsonString.charAt(nextPosition);
        if (nextChar == '"') {
            Object[] indexAndValue = getStringValueFromJsonSlice(jsonString, ++nextPosition);
            nextPosition = (int) indexAndValue[0];
            String value = (String) indexAndValue[1];
            sb.append('"').append(desensitizationMode.desensitize(value)).append('"');
        } else if (nextChar == '[') {
            Object indexAndValue = getArrayValueFromJsonSlice(jsonString, ++nextPosition);
            nextPosition = (int) ((Object[]) indexAndValue)[0];
            List<Object> valueList = (List<Object>) ((Object[]) indexAndValue)[1];
            sb.append('[');
            for (Object value : valueList) {
                if (sb.charAt(sb.length() - 1) != '[') {
                    sb.append(',');
                }
                if (value == null) {
                    sb.append("null");
                } else {
                    sb.append('"').append(desensitizationMode.desensitize(value)).append('"');
                }
            }
            sb.append(']');
        } else if (nextChar == 'n') {
            sb.append("null");
            nextPosition += 4;
        }
        sb.append(desensitizeJsonString(jsonString.substring(nextPosition), sensitiveField, desensitizationMode));
        return sb.toString();
    }

    static Object[] getStringValueFromJsonSlice(String jsonString, int startIndex) {
        StringBuilder sb = new StringBuilder();
        boolean isScapeChar = false;
        for (int i = startIndex; i < jsonString.length(); i++) {
            char c = jsonString.charAt(i);
            if (c == '\\') {
                isScapeChar = !isScapeChar;
                sb.append(c);
            } else if (c == '"') {
                if (!isScapeChar) {
                    return new Object[] {++i, sb.toString()};
                }
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        return new Object[]{jsonString.length(), sb.toString()};
    }

    @SuppressWarnings("unchecked")
    static Object[] getArrayValueFromJsonSlice(String jsonString, int position) throws JsonParseException {
        Object[] result = new Object[2];
        LinkedList<Object> list = new LinkedList<>();
        result[1] = list;
        switch (jsonString.charAt(position)) {
            case ' ':
            case '\n':
            case '\r':
            case '\t':
                return getArrayValueFromJsonSlice(jsonString, position + 1);
            case ']':
                result[0] = position + 1;
                return result;
            case 'n':
                list.add(null);
                Object[] partResult1 = getArrayValueFromJsonSlice(jsonString, position + 5);
                result[0] = partResult1[0];
                list.addAll((List<Object>) partResult1[1]);
                return result;
            case '"':
                Object[] partResult2 = getStringValueFromJsonSlice(jsonString, position + 1);
                result[0] = position = (int)partResult2[0];
                list.add(partResult2[1]);
                Object[] partResult2Next;
                if (jsonString.charAt(position) == ',') {
                    partResult2Next = getArrayValueFromJsonSlice(jsonString, position + 1);
                } else {
                    partResult2Next = getArrayValueFromJsonSlice(jsonString, position);
                }
                result[0] = partResult2Next[0];
                list.addAll((List<Object>) partResult2Next[1]);
                return result;
            default:
                throw new JsonParseException("Unexpected character '" + jsonString.charAt(position) + "' at index " + position);
        }
    }

    private String getHeaderLogMsg(Request request, ThreeStates<LogInvocation> loggable) {
        if (loggable == null || !loggable.isAvailable() || !loggable.getValue().logRequestHeaders()) {
            return "";
        }
        Set<String> maskHeaders = maskedHeaders(loggable);
        StringBuilder logMsg = new StringBuilder();
        for (Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
            String key = entry.getKey();
            for (String value : entry.getValue()) {
                logMsg.append(" -H \"").append(key).append(':');
                if (maskHeaders.contains(key.toLowerCase())) {
                    logMsg.append(StringUtils.repeat('*', value.length()));
                } else {
                    logMsg.append(value);
                }
                logMsg.append('"');
            }
        }
        return logMsg.toString();
    }

    protected static final class InputStreamBody implements Response.Body {

        private final InputStream inputStream;
        private final Integer length;

        public InputStreamBody(byte[] bytes) {
            this.inputStream = new ByteArrayInputStream(bytes);
            this.length = bytes.length;
        }


        @Override
        public Integer length() {
            return length;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public InputStream asInputStream() {
            return inputStream;
        }

        @Override
        public Reader asReader() {
            return new InputStreamReader(inputStream, UTF_8);
        }

        @Override
        public Reader asReader(Charset charset) {
            return new InputStreamReader(inputStream, charset);
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
