package lt.twoday;

import java.util.concurrent.atomic.AtomicInteger;

import org.openrewrite.Tree;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Space;

public class AllLinesCounter extends JavaVisitor<AtomicInteger> {

    @Override
    public Space visitSpace(Space space, Space.Location loc, AtomicInteger count) {
        if (space.getWhitespace().contains("\n")) {
            count.incrementAndGet();
        }
        return super.visitSpace(space, loc, count);
    }

    public static int countLines(Tree tree) {
        return new AllLinesCounter().reduce(tree, new AtomicInteger()).get();
    }
}