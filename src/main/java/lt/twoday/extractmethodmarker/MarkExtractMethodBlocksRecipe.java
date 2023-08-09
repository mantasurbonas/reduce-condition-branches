package lt.twoday.extractmethodmarker;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class MarkExtractMethodBlocksRecipe  extends Recipe {

    @Override
    public String getDisplayName() {
        return "Marks blocks for extract method";
    }

    @Override
    public String getDescription() {
        return "Analyzes code tree and marks (with comments) the part of the code that is complex and long enough thus being a candidate for exractMethod refactoring.";
    }
    
    public static boolean isRefactorable(Statement statement) {
        return (statement instanceof J.If)
            || (statement instanceof J.Case)
            || (statement instanceof J.ForEachLoop)
            || (statement instanceof J.DoWhileLoop)
            || (statement instanceof J.ForLoop)
            || (statement instanceof J.Lambda);
    }
    
    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        
        return new JavaIsoVisitor<ExecutionContext>() {
            
            private boolean inMethod = false;
            
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext p) {
                
                // step1: mark code blocks within this method
                method = new BlockComplexityVisitor().visitMethodDeclaration(method, p);
                
                // step2: put appropriate the comments on the marked blocks
                inMethod = true;
                MethodDeclaration ret = (J.MethodDeclaration) super.visitMethodDeclaration(method, p);
                inMethod = false;
                return ret;
            }
            
            @Override
            public J.Block visitBlock(J.Block block, ExecutionContext p) {            
                if (!inMethod)
                    return (J.Block) super.visitBlock(block, p); // NOT visiting anything anything above method
                
                BlockMark complexityMarker = getComplexityMarker(block);
                if (complexityMarker == null)
                    return (J.Block) super.visitBlock(block, p); // NOT visiting anything that's not marked by BlockComplexityVisitor markings
                       
                if (complexityMarker.fitsForExtractMethod)
                    block = BlockCommentManager.markForRefactoring(block);
                else
                    ; //block = BlockCommentManager.markDebugInfo(block, complexityMarker);
                
                return (J.Block) super.visitBlock(block, p);
            }

            private BlockMark getComplexityMarker(J.Block block) {
                Markers markers = block.getMarkers();
                if (markers == null) 
                    return null;

                return markers.findFirst(BlockMark.class).orElse(null);
            }
            
        };
        
    }
    
}
