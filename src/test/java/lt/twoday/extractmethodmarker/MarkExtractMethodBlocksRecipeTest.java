package lt.twoday.extractmethodmarker;

import static org.openrewrite.java.Assertions.java;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class MarkExtractMethodBlocksRecipeTest  implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MarkExtractMethodBlocksRecipe())
            .parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true));
    }
    
    Consumer<RecipeSpec> createSpec() {
        return new Consumer<RecipeSpec>() {
            
            @Override
            public void accept(RecipeSpec spec) {
                spec
                    .cycles(1)
                    .parser(JavaParser
                                .fromJavaVersion()
                                .logCompilationWarningsAndErrors(false));
            }
        };
    }
            
    @Test
    void putYourCodeHere() {
        rewriteRun(
                createSpec(),
            java(
                """
                """));
    }
    
}
