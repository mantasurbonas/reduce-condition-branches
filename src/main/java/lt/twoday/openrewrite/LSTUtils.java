package lt.twoday.openrewrite;

import java.util.Arrays;
import java.util.List;

import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.If.Else;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;

/**
 * helpers to manipulate Lossless-Semantic-Tree nodes
 */
public class LSTUtils {

    public static boolean isEmpty(Statement stmnt) {
    	if (stmnt == null)
    		return true;
    	
    	if (stmnt instanceof J.Empty)
    		return true;
    	
    	if (! (stmnt instanceof J.Block))
    		return false;
    	
    	return isEmpty(((J.Block)stmnt).getStatements());
    }

    public static boolean isEmpty(List<Statement> statements) {
    	if (statements == null)
    		return true;
    	
    	if (statements.size() == 0)
    		return true;
    	
    	boolean empty = true;
    	for (Statement st: statements)
    		empty = empty && isEmpty(st);
    	
    	return empty;
    }

    public static boolean isEmpty(J.If.Else else_) {
    	if (else_ == null)
    		return true;
    
    	return isEmpty(else_.getBody());
    }

    public static boolean hasGuaranteedReturn(Statement statement) {
    	if (statement == null)
    		return false;
    	
    	if (statement instanceof J.Return)
    		return true;
    	
    	if (statement instanceof J.Throw)
    		return true;
    	
    	if (statement instanceof J.Block)
    		return hasGuaranteedReturn( ((J.Block)statement).getStatements() );
    	
    	if (statement instanceof J.If)
    		return hasGuaranteedReturn( (J.If) statement);
    	
    	return false;
    }

    public static boolean hasGuaranteedReturn (J.If iff) {
        Statement thenPart = iff.getThenPart();
        if (thenPart == null)
            return false;
        
        if (!hasGuaranteedReturn(thenPart))
            return false;
        
        Else elsePart = iff.getElsePart();
        return elsePart != null && hasGuaranteedReturn( elsePart.getBody()) ;
    }

    public static boolean hasGuaranteedReturn(List<Statement> statements) {
    	if (statements == null)
    		return false;
    	
    	if (statements.size() == 0)
    		return false;
    	
    	Statement lastStatement = statements.get(statements.size()-1);
    	if (hasGuaranteedReturn(lastStatement))
    		return true;
    	
    	return false;
    }
    
    public static boolean isLiteralTrue(Expression expression) {
        return expression instanceof J.Literal 
            && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
    }

    public static boolean isLiteralFalse(Expression expression) {
        return expression instanceof J.Literal 
            && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
    }
    
    public static J removeAllSpace(J j) {
        return new JavaIsoVisitor<Integer>() {
            @Override
            public Space visitSpace(Space space, Space.Location loc, Integer integer) {
                return Space.EMPTY;
            }
        }.visit(j, 0);
    }

    public static boolean isThrow(Statement s) {
        if (s == null)
            return false;
        
        if (s instanceof J.Throw)
            return true;
        
        if (!(s instanceof J.Block))
            return false;
        
        List<Statement> statements = ((J.Block) s).getStatements();
        if (statements == null || statements.isEmpty() || statements.size() > 1)
            return false;
        
        return isThrow(statements.get(0));
    }
    
    public static J.Block embrace(Statement statement) {
        return J.Block
                .createEmptyBlock()
                    .withStatements(Arrays.asList(statement));
    }

    public static boolean isThrow(Else elsePart) {
        return elsePart != null && elsePart.getBody() != null && isThrow(elsePart.getBody());
    }
}
