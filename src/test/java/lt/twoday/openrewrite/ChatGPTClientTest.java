package lt.twoday.openrewrite;

import org.junit.jupiter.api.Test;

class ChatGPTClientTest {

    // @Test
    void test() throws Exception {
        String answer = new ChatGPTClient().ask("what is the meaning of life?", 1.0f);
        System.out.println(answer);
    }

}
