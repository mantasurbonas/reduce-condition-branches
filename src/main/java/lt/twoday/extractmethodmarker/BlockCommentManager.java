package lt.twoday.extractmethodmarker;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

public class BlockCommentManager {

    private static final String FAILED_REFACTORING_COMMENT = "complex block";
    
    private static final String REFACTORING_BLOCK_BEGIN_COMMENT = "CONSIDER BLOCK REFACTORING ";
    private static final String REFACTORING_BLOCK_END_COMMENT = "END REFACTORING BLOCK ";

    
    public static boolean isMarkedAsFailedRefactoring(J.Block block) {
        for (Comment a: block.getPrefix().getComments()) {
            if (a == null)
                continue;
            
            if (!(a instanceof TextComment))
                continue;
            
            TextComment tc = (TextComment) a;
            
            if (StringUtils.isBlank(tc.getText()))
                continue;
            
            if (FAILED_REFACTORING_COMMENT.equals(tc.getText().trim()))
                return true;
        }
        return false;
    }
    
    public static J.Block markAsFailedRefactoring(J.Block block){
        return block
                .withPrefix(
                    block.getPrefix()
                            .withComments(
                                makeFailedRefactoringComment()
                            )
                    )
                .withEnd(
                    block.getEnd()
                            .withComments(Collections.emptyList())
                    );
    }

    public static List<Comment> makeFailedRefactoringComment() {
        return Arrays.asList(
                new TextComment(true, 
                            FAILED_REFACTORING_COMMENT, 
                            null, 
                            Markers.EMPTY)
                        );
    }
    
    public static boolean isMarkedForRefactoring(J.Block block) {
        for (Comment a: block.getPrefix().getComments()) {
            if (a == null)
                continue;
            
            if (!(a instanceof TextComment))
                continue;
            
            TextComment tc = (TextComment) a;
            
            if (StringUtils.isBlank(tc.getText()))
                continue;
            
            if (tc.getText().trim().startsWith(REFACTORING_BLOCK_BEGIN_COMMENT))
                return true;
        }
        return false;
    }
    
    public static Block markDebugInfo(J.Block block, BlockMark debugInfo) {
        return block
                .withPrefix(
                    block.getPrefix().withComments(makeDebugComment(block, debugInfo))
                    );
    }
    
    public static Block markForRefactoring(J.Block block) {
        return block
                .withPrefix(
                    block.getPrefix().withComments(makeRefactoringPreComment(block))
                        )
                .withEnd(
                    block.getEnd().withComments(makeRefactoringEndComment(block))
                        );
    }
    
    public static List<Comment> makeRefactoringPreComment(J.Block block) {
        return Arrays.asList(
                new TextComment(true, 
                            REFACTORING_BLOCK_BEGIN_COMMENT+block.getId() + " named '"+ BlockNameCreator.createMethodName(block)+"'", 
                            null, 
                            Markers.EMPTY)
                        );
    }

    public static List<Comment> makeRefactoringEndComment(J.Block block) {
        return Arrays.asList(
                new TextComment(true, 
                            REFACTORING_BLOCK_END_COMMENT+block.getId(), 
                            null, 
                            Markers.EMPTY)
                        );
    }
    
    private static List<Comment> makeDebugComment(Block block, BlockMark debugInfo) {
        return Arrays.asList(
                new TextComment(true, 
                            debugInfo.toString(), 
                            null, 
                            Markers.EMPTY)
                        );
    }


}
