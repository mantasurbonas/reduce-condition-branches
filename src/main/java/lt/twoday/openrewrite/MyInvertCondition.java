/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package lt.twoday.openrewrite;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.cleanup.SimplifyBooleanExpressionVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.J.Unary;
import org.openrewrite.marker.Markers;

import static org.openrewrite.Tree.randomId;

/***
 * properly inverts conditions, including the instance of.
 * 
 * (a copy from a standard openrewrite InvertCondition with a special case for instanceof)
 */
public class MyInvertCondition extends JavaVisitor<ExecutionContext> {

    @SuppressWarnings("unchecked")
    public static <J2 extends J> J.ControlParentheses<J2> invert(J.ControlParentheses<J2> controlParentheses, Cursor cursor) {
        //noinspection ConstantConditions
        return (J.ControlParentheses<J2>) new MyInvertCondition()
                .visit(controlParentheses, new InMemoryExecutionContext(), cursor.getParentOrThrow());
    }

    @Nullable
    @Override
    public J visit(@Nullable Tree tree, ExecutionContext ctx) {
        J t;
        if (tree instanceof Expression && 
          !(tree instanceof J.ControlParentheses) && 
          !(tree instanceof J.Binary)) {            
            t = negate((Expression) tree);
        } else {
            t = super.visit(tree, ctx);
        }

        return (J) new SimplifyBooleanExpressionVisitor().visit(t, ctx, getCursor().getParentOrThrow());
    }

    private Expression negate(Expression expression) {
        if (expression instanceof J.InstanceOf)
            return notInstanceof((J.InstanceOf)expression);
        
        if (expression instanceof J.Unary)
            return notUnary((J.Unary)expression);
        
        return notExpression(expression);
    }

    @Override
    public J visitBinary(J.Binary binary, ExecutionContext ctx) {
        switch (binary.getOperator()) {
            case LessThan:
                return binary.withOperator(J.Binary.Type.GreaterThanOrEqual);
            case GreaterThan:
                return binary.withOperator(J.Binary.Type.LessThanOrEqual);
            case LessThanOrEqual:
                return binary.withOperator(J.Binary.Type.GreaterThan);
            case GreaterThanOrEqual:
                return binary.withOperator(J.Binary.Type.LessThan);
            case Equal:
                return binary.withOperator(J.Binary.Type.NotEqual);
            case NotEqual:
                return binary.withOperator(J.Binary.Type.Equal);
            default:
                return notBinary(binary);
        }
    }

    private Unary notExpression(Expression expression) {
        return new J.Unary(
                        randomId(), 
                        expression.getPrefix(), 
                        Markers.EMPTY,
                        JLeftPadded.build(J.Unary.Type.Not), 
                        expression.withPrefix(Space.EMPTY), 
                        expression.getType()
                );
    }
    
    private Unary notInstanceof(J.InstanceOf expression) {
        return new J.Unary(
                        randomId(), 
                        expression.getPrefix(), 
                        Markers.EMPTY,
                        JLeftPadded.build(J.Unary.Type.Not), 
                        new J.Parentheses<>(
                                randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                JRightPadded.build(expression.withPrefix(Space.EMPTY))
                        ),
                        expression.getType()
                );
    }
    
    private Expression notUnary(J.Unary unary) {
        switch(unary.getOperator()) {
        case Not:
            return maybeUnwrap(unary.getExpression());
        default:
            return notExpression(unary);
        }
    }
    
    private Expression maybeUnwrap(Expression expression) {
        if (expression instanceof J.Parentheses<?>)
            return ((J.Parentheses<?>)expression).unwrap();
        return expression;
    }

    private Unary notBinary(J.Binary binary) {
        return new J.Unary(
                        randomId(),
                        binary.getPrefix(),
                        Markers.EMPTY,
                        JLeftPadded.build(J.Unary.Type.Not),
                        new J.Parentheses<>(
                                randomId(),
                                Space.EMPTY,
                                Markers.EMPTY,
                                JRightPadded.build(binary.withPrefix(Space.EMPTY))
                        ),
                        binary.getType()
                );
    }
}
