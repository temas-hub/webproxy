package com.temas.webproxy;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

/**
 * Created by azhdanov on 28.06.2019.
 */
@RestController
public class Service {
    private static final int DEFAULT_BUFFER_SIZE = 200480; // ..bytes = 200KB.

    private final static String urlPreffix = "http://192.168.86.230:8080/mirror?url=";
    private String lastRequestedUrl = null;

    @RequestMapping(value = "/test")
    public void test(HttpServletRequest request,
                       HttpServletResponse response) {
        BufferedWriter outputWriter = null;
        try {
            outputWriter = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
            outputWriter.write("Hello");
            response.setHeader("Content-Length", String.valueOf("Hello".getBytes("UTF-8").length));
            response.setHeader("Connection", "Keep-Alive");
            outputWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @RequestMapping(value = "/mirror")
    public void mirrorRest(@RequestBody(required = false) String body,
                                     @RequestParam("url") String remotePath,
                                     HttpMethod method,
                                     HttpServletRequest request,
                                     HttpServletResponse response)
            throws URISyntaxException {

        System.out.println("Processing remotePath: " + remotePath);
        lastRequestedUrl = remotePath;

        mirror(body, remotePath, method, request, response);
    }

    private void mirror(String body, String remotePath, HttpMethod method, HttpServletRequest request, HttpServletResponse response) throws URISyntaxException {
        System.out.println("Mirroring remotePath: " + remotePath);
        URI uri = new URI(remotePath);
        uri = UriComponentsBuilder.fromUri(uri)
                .build(true).toUri();

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.set(headerName, request.getHeader(headerName));
        }

        HttpEntity<String> httpEntity = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        try {
            ResponseEntity<Resource> responseEntity = restTemplate.exchange(uri, method, httpEntity, Resource.class);
            HttpHeaders responseHeaders = responseEntity.getHeaders();
            for (Map.Entry<String, List<String>> header : responseHeaders.entrySet()) {
                if (!header.getKey().equals("Content-Length")) {
                    header.getValue().forEach(v -> response.addHeader(header.getKey(), v));
                }
            }
            if (responseHeaders.getAccessControlAllowOrigin() == null) {
                response.addHeader("Access-Control-Allow-Origin", "*");
            }
            if (responseHeaders.getAccessControlAllowMethods() == null) {
                response.addHeader("Access-Control-Allow-Methods", "GET");
                response.addHeader("Access-Control-Allow-Methods", "OPTIONS");
            }

            MediaType contentType = responseHeaders.getContentType();
            if (method == HttpMethod.GET &&
                    (contentType.includes(MimeType.valueOf("application/x-mpegurl")) ||
                    contentType.includes(MimeType.valueOf("application/vnd.apple.mpegurl"))||
                            remotePath.contains(".m3u"))) {

                translatePlayList(responseEntity, response, remotePath);
            } else {
                response.setHeader("Content-Length", String.valueOf(responseHeaders.getContentLength()));
                streamOutput(response, responseEntity);
                System.out.println("Mirrored successfully: " + remotePath);
            }
        } catch(HttpStatusCodeException e) {
            System.err.println("Error with request " + remotePath + ". Method: " + method);
            e.printStackTrace();
        }
    }

    @RequestMapping(value = "*")
    public void relativeRequest(@RequestBody(required = false) String body,
                                HttpMethod method,
                                HttpServletRequest request,
                                HttpServletResponse response) throws URISyntaxException {
        System.out.println("Relative path requested: " + request.getRequestURI());
        String requestURI = request.getRequestURI();
        if (requestURI != null && requestURI.startsWith("/") && lastRequestedUrl != null) {
            int slashIndex = lastRequestedUrl.lastIndexOf("/");
            String remotePath = lastRequestedUrl.substring(0, slashIndex) + requestURI;
            mirror(body, remotePath, method, request, response);
        }
    }

    private void streamOutput(HttpServletResponse response, ResponseEntity<Resource> responseEntity) {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream responseInputStream = null;
        try {
            responseInputStream = responseEntity.getBody().getInputStream();
            int read;
            while ((read = responseInputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, read);
                outputStream.flush();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void translatePlayList(ResponseEntity<Resource> responseEntity, HttpServletResponse response, String basePath) {
        response.setBufferSize(1000000);
        int linesWritten = 0;
        int size = 0;
        BufferedWriter outputWriter = null;
        try {
            outputWriter = new BufferedWriter(new OutputStreamWriter(response.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        BufferedReader responseReader = null;
        try {
            responseReader = new BufferedReader(new InputStreamReader(responseEntity.getBody().getInputStream()));
            String line;
            while ((line = responseReader.readLine()) != null) {
                if (!line.startsWith("#")) {
                    String resourceURL = null;
                    if (line.startsWith("http")) {
                        resourceURL = line;
                        //translate
                        line = urlPreffix + URLEncoder.encode(resourceURL, "UTF-8");
                    }

                }
                outputWriter.write(line);
                size += line.getBytes("UTF-8").length;
                outputWriter.newLine();
                size += 2;
                System.out.println(line);
                linesWritten++;
            }

            response.setHeader("Content-Length", String.valueOf(size));
            response.setHeader("Connection", "keep-alive");
            outputWriter.flush();
            response.setStatus(responseEntity.getStatusCodeValue(), responseEntity.getStatusCode().name());
            System.out.println("Translated successfully: " + basePath + ". Bytes: " + size);
        }
        catch (IOException e) {
            System.err.println("Playlist written:  " + linesWritten);
            e.printStackTrace();
        }
    }

}
