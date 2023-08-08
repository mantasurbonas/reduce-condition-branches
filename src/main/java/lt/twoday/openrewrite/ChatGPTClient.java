package lt.twoday.openrewrite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

import org.json.JSONObject;
import org.openrewrite.internal.StringUtils;

public class ChatGPTClient {
    
    private static final String GPT_PROPERTIES = "gpt.properties";
    
    private static final String REQUEST_FORMAT =
"{\n"+
"   \"messages\": [\n"+
"      {\n"+
"       \"role\":\"user\",\n"+
"       \"content\":\"%s\"\n"+
"       }\n"+
"   ],\n"+
"   \"max_tokens\": 2000,\n"+
"   \"temperature\": %f,\n"+
"   \"frequency_penalty\": 0,\n"+
"   \"presence_penalty\": 0,\n"+
"   \"top_p\": 0.95,\n"+
"   \"stop\": null\n"+
"}";
    private String url;
    private String apiKey;
    
    public ChatGPTClient() {
        this.url = System.getenv("CHAT_GPT_URL");
        this.apiKey = System.getenv("CHAT_GPT_KEY");
        
        if (StringUtils.isBlank(url) || StringUtils.isBlank(apiKey))
            loadFromResources();
    }
    
    public ChatGPTClient(String url, String apiKey) {
        this.url = url;
        this.apiKey = apiKey;
    }
    
    public String ask(String question, float temperature) throws Exception {
        HttpURLConnection connection = null;
        OutputStream outputStream = null;
        
        try {
            connection = openConnection();
            outputStream = connection.getOutputStream();
            
            String body = String.format(REQUEST_FORMAT, question, temperature);
                
            outputStream.write(body.getBytes());
    
            String ret = parseOutput(readOutput(connection));
            
            System.out.println("The answer is ["+ret+"]");
            
            return ret;
        }catch(Exception e) {
            if (outputStream != null)
                outputStream.close();
            throw e;
        }
    }

    private void loadFromResources() {
        InputStream inputStream = null;
        try {
            inputStream = Thread
                            .currentThread()
                            .getContextClassLoader()
                            .getSystemResourceAsStream(GPT_PROPERTIES);
            
            Properties props = new Properties();
            props.load(inputStream);
            
            this.url=props.getProperty("CHAT_GPT_URL");
            this.apiKey=props.getProperty("CHAT_GPT_KEY");
        }catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    private HttpURLConnection openConnection() throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("api-key", apiKey);
        con.setDoOutput(true);
        
        return con;
    }
    
    private static String readOutput(HttpURLConnection con) throws IOException {
        return new BufferedReader(new InputStreamReader(con.getInputStream()))
                            .lines()
                            .reduce((a, b) -> a + b)
                            .get();
    }
    
    private static String parseOutput(String gptOutputJsonStr) {
        return new JSONObject(gptOutputJsonStr)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
    }

    public static void main(String[] args) throws Exception {
    
        new ChatGPTClient().ask("Hello, how are you?", 0f);
    }
}