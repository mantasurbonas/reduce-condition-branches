package lt.twoday.extractmethodmarker;

import java.util.ArrayList;
import java.util.List;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.ForLoop;
import org.openrewrite.java.tree.J.If.Else;
import org.openrewrite.java.tree.Statement;

public class BlockComplexityVisitor extends JavaVisitor<ExecutionContext> {
        
    public interface ComplexityCriteria{
        public boolean apply(int lineCount, int nestingDepth);
    }

    public static final ComplexityCriteria DEFAULT_COMPLEXITY_CRITERIA = (lineCount, nestingDepth) -> {
        if (nestingDepth <= 1)
            return false;

        if (nestingDepth <= 3)
            return lineCount >= 7;
        
        return lineCount >= 3;
    };
    
    private ComplexityCriteria complexityCriteria = DEFAULT_COMPLEXITY_CRITERIA;

    
    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext p) {
        
        Block body = method.getBody();
        if (body == null)
            return (J.MethodDeclaration) super.visitMethodDeclaration(method, p);
        
        BlockMark initialComplexity = new BlockMark(Tree.randomId());
        
        List<Statement> revisedStatements = new ArrayList<>();
        
        for (Statement statement: body.getStatements())
            revisedStatements.add( assignBlockMarkersRecursively(statement, initialComplexity) );
        
        method = method.withBody(
                    body.withStatements(revisedStatements)
                                );
        
        return (J.MethodDeclaration) super.visitMethodDeclaration(method, p);
    }

    private Statement assignBlockMarkersRecursively(Statement statement, BlockMark parentBlockComplexity) {
        if (statement == null)
            return statement;
        
        BlockMark blockComplexity = parentBlockComplexity.clone(Tree.randomId());
        blockComplexity.nestingDepth ++;
        
        if (statement instanceof J.Block)
            return markBlockComplexity((J.Block) statement, blockComplexity);
        
        if (statement instanceof J.ForLoop) {
            ForLoop forr = (J.ForLoop)statement;
            return forr.withBody( 
                    assignBlockMarkersRecursively(forr.getBody(), blockComplexity)
                                );
        }
        
        if (statement instanceof J.ForEachLoop) {
            J.ForEachLoop foreach = (J.ForEachLoop)statement;
            return foreach.withBody( 
                    assignBlockMarkersRecursively(foreach.getBody(), blockComplexity)
                                );
        }
        
        if (statement instanceof J.WhileLoop) {
            J.WhileLoop whil = (J.WhileLoop)statement;
            return whil.withBody( 
                    assignBlockMarkersRecursively(whil.getBody(), blockComplexity)
                                );
        }
        
        if (statement instanceof J.DoWhileLoop){
            J.DoWhileLoop doWhil = (J.DoWhileLoop)statement;
            return doWhil.withBody( 
                    assignBlockMarkersRecursively(doWhil.getBody(), blockComplexity)
                                );
        }
        
        if (statement instanceof J.Try) {
            J.Try tr = (J.Try)statement;
            return tr.withBody( 
                    markBlockComplexity(tr.getBody(), blockComplexity)
                                );
        }
        
        if (statement instanceof J.If) {
            J.If iff = (J.If)statement;
            return iff.withThenPart(
                        assignBlockMarkersRecursively(iff.getThenPart(), blockComplexity)
                    )
                    .withElsePart(
                        assignBlockMarkersRecursively(iff.getElsePart(), blockComplexity)
                    );
        }
        
        if (statement instanceof J.Switch) {
            J.Switch sw = (J.Switch)statement;
            return sw.withCases( 
                    markBlockComplexity(sw.getCases(), blockComplexity)
                                );
        }
        
        return statement;
    }

    private J.Block markBlockComplexity(J.Block block, BlockMark parentBlockMarker) {
        if (block == null)
            return block;
        
        List<Statement> statements = block.getStatements();
        if (statements == null || statements.isEmpty())
            return block;
        
        BlockMark blockMarker = parentBlockMarker; //.clone(Tree.randomId());
//        blockMarker.nestingDepth ++;
        blockMarker.statementCount = statements.size();
        
        List<Statement> revised = new ArrayList<>();
        
        for (Statement st: statements)
            revised.add( assignBlockMarkersRecursively(st, blockMarker) );
        
        if (! blockMarker.hasMarkedChildren)
            if (! BlockCommentManager.isMarkedAsFailedRefactoring(block) )
                if ( complexityCriteria.apply(blockMarker.statementCount, blockMarker.nestingDepth) )
                    blockMarker.setFitsForExtractMethod();

        return block
                .withStatements(revised)
                .withMarkers(
                        block.getMarkers().add(blockMarker)
                            );
    }

    private Else assignBlockMarkersRecursively(Else elsePart, BlockMark blockComplexity) {
        if (elsePart == null)
            return elsePart;
        
        return elsePart.withBody(
                    assignBlockMarkersRecursively(elsePart.getBody(), blockComplexity)
                );
    }
    
}
