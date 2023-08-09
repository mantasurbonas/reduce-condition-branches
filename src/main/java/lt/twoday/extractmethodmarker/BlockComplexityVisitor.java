package lt.twoday.extractmethodmarker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.ForLoop;
import org.openrewrite.java.tree.J.If.Else;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockComplexityVisitor extends JavaVisitor<ExecutionContext> {
        
    private static final Logger log  = LoggerFactory.getLogger(BlockComplexityVisitor.class);
    
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

        log.trace("visiting method {}", method.getName());
        
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
        
        MethodDeclaration ret = (J.MethodDeclaration) super.visitMethodDeclaration(method, p);
        
        log.trace("end visiting method {}", method.getName());
        
        return ret;
    }

    private static boolean hasExistingMarkers(Statement statement) {
        List<BlockMark> existingMarkers = statement.getMarkers().findAll(BlockMark.class);
        return existingMarkers != null && !existingMarkers.isEmpty();
    }
    
    private Statement assignBlockMarkersRecursively(Statement statement, BlockMark parentBlockComplexity) {
        if (statement == null)
            return statement;
        
        if (hasExistingMarkers(statement)) {
            log.warn("block marking error #1: statement already had existing marker, boiling out ({})", statement.getMarkers());
            return statement;
        }
        
        BlockMark blockComplexity = parentBlockComplexity.clone(Tree.randomId());
        blockComplexity.nestingDepth ++;
        
        if (statement instanceof J.Block)
            return markBlockComplexity(statement, (J.Block) statement, blockComplexity);
        
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
                    markBlockComplexity(tr, tr.getBody(), blockComplexity)
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
                    markBlockComplexity(sw, sw.getCases(), blockComplexity)
                                );
        }
        
        if (statement instanceof J.Case) {
            J.Case ccase = (J.Case)statement;
            return ccase
                    .withStatements(
                            assignBlockMarkersRecursively(ccase.getStatements(), blockComplexity)
                        );
        }
        
        return statement;
    }

    private J.Block markBlockComplexity(Statement parent, J.Block block, BlockMark parentBlockMarker) {
        if (block == null)
            return block;
        
        List<Statement> statements = block.getStatements();
        if (statements == null || statements.isEmpty())
            return block;
        
        if (BlockCommentManager.isMarked(block)) {
            log.error("block marking error #2: {} already has a refactoring comment: {}", block.getId(), block.getComments());
            return block;
        }
        
        if (hasExistingMarkers(block)) {
            log.error("block marking error #3: {} already has existing markers: {}", block.getId(), block.getMarkers());
            return block;
        }
        
        List<Statement> revised = new ArrayList<>();
        
        BlockMark blockMarker = parentBlockMarker.clone(Tree.randomId());
//        blockMarker.nestingDepth ++;
        blockMarker.statementCount = statements.size();
        
        //block = block.withMarkers(block.getMarkers().add(blockMarker));
        // block = block.withStatements(revised).withMarkers(block.getMarkers().add(blockMarker));

        
        for (Statement st: statements)
            revised.add( assignBlockMarkersRecursively(st, blockMarker) );
        
        if (BlockCommentManager.isMarked(block) ) {
            log.error("block marking error #4: block {} got refactoring comment: {}", block.getId(), block.getComments());
            return block.withStatements(revised);
        }
        
        if (! blockMarker.hasMarkedChildren) {
            if (MarkExtractMethodBlocksRecipe.isRefactorable(parent)) {
                if ( complexityCriteria.apply(blockMarker.statementCount, blockMarker.nestingDepth) ) {
                    blockMarker.setFitsForExtractMethod();
                }
            }
        }

        return block
                .withMarkers(Markers.build(Arrays.asList(blockMarker)))
                .withStatements(revised);
    }

    private Else assignBlockMarkersRecursively(Else elsePart, BlockMark blockComplexity) {
        if (elsePart == null)
            return elsePart;
        
        return elsePart.withBody(
                    assignBlockMarkersRecursively(elsePart.getBody(), blockComplexity)
                );
    }
    
    private List<Statement> assignBlockMarkersRecursively(List<Statement> statements, BlockMark blockComplexity) {
        if (statements == null)
            return statements;
        
        return statements.stream()
                .map(st -> assignBlockMarkersRecursively(st, blockComplexity))
                .collect(Collectors.toList());
    }


}
