package lt.twoday.extractmethodmarker;

import org.openrewrite.PrintOutputCapture;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.tree.J.Block;

import lt.twoday.openrewrite.ChatGPTClient;

/*** creates a function name for a code block */
public class BlockNameCreator {
    
    private static final String CHAT_GPT_PROMPT = "Please suggest a better name for this Java function. Be laconic, only output the suggested function name without any comments and explanations";

    public static String createMethodName(Block block){
        String javaCode = toJavaCodeString(block);
        
        javaCode = stringify(javaCode);
        
        String question = CHAT_GPT_PROMPT + ": " + javaCode;
        try {
            return new ChatGPTClient().ask(question, 0f);
        }catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    private static String toJavaCodeString(Block block) {
        PrintOutputCapture<Object> out = new PrintOutputCapture<>(0, PrintOutputCapture.MarkerPrinter.DEFAULT);
        
        new JavaPrinter<>().visit(block, out);
                
        return out.getOut();
    }

    private static String stringify(String javaCode) {
        return String.join("\\n",
                javaCode.replace("\"", "\\\"")
                    .split("\n")
                    );
    }

}
