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
package lt.twoday;

import java.util.ArrayList;
import java.util.List;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.InvertCondition;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.UnwrapParentheses;
import org.openrewrite.java.format.AutoFormatVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.If;
import org.openrewrite.java.tree.J.If.Else;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.Statement;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class ReduceConditionBranches extends Recipe {

    public ReduceConditionBranches() {
        
    }
    
	@Override
    public String getDisplayName() {
        //language=markdown
        return "Reduces unncessary conditional branches";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Simplifies any IF statements by removing empty then or else blocks, re-ordering or inlining them.";
    }

    private static void insertFlattened(List<Statement> allStatements, int i, Statement statement) {
        if (statement instanceof J.Block) {
            allStatements.addAll(i, ((J.Block)statement).getStatements());
            return;
        }
        
        allStatements.add(i, statement);
    }
    
    private static boolean reduceConditionBranches(If ifStatement, List<Statement> allBlockLines, int ifStatementPosition) {
        Else elsePart = ifStatement.getElsePart();
        Statement thenPart = ifStatement.getThenPart();
        
        if (elsePart == null) {
            Statement newThenPart = findAndReduceConditionBranches(thenPart);
            if (newThenPart != thenPart) {
                allBlockLines.set(ifStatementPosition, ifStatement.withThenPart(newThenPart));
                return true;
            }
            return false;
        }
        
        Statement elseBody = elsePart.getBody();
        
        if (LSTUtils.isEmpty(elseBody)) {
            // elsePart is empty, erasing it
            J.If modifiedStatement = ifStatement
                    .withElsePart(null)
                    .withThenPart( findAndReduceConditionBranches(thenPart) );
            allBlockLines.set(ifStatementPosition, modifiedStatement);
                        
            return true;
        }

        if (LSTUtils.isEmpty(thenPart)) {
            // the else part contains logic while the then part is empty - let's replace it with the else branch
            J.If modifiedStatement = 
                    ifStatement.withIfCondition( 
                                LSTUtils.invert(ifStatement.getIfCondition()) 
                            )
                        .withThenPart( 
                                findAndReduceConditionBranches(elseBody) 
                            )
                        .withElsePart(null);
            
            allBlockLines.set(ifStatementPosition, modifiedStatement);
            
            return true;
        }
        
        if (LSTUtils.hasGuaranteedReturn(thenPart)) {
            // the thenPart has guaranteed return: 
            // the elsePart shall get flattened.
            J.If modifiedStatement = 
                        ifStatement.withIfCondition(
                                ifStatement.getIfCondition()
                                ) 
                            .withThenPart(
                                findAndReduceConditionBranches(thenPart)
                                )
                            .withElsePart(null);
            
            allBlockLines.set(ifStatementPosition, modifiedStatement);
            insertFlattened(allBlockLines, ifStatementPosition+1, findAndReduceConditionBranches(elseBody) );
            
            return true;
        }
        
        if (LSTUtils.hasGuaranteedReturn(elseBody)) {
            // the elsePart has guaranteed return:
            // make it the thenPart, and flatten the thenPart instead.

            J.If modifiedStatement = 
                        ifStatement.withIfCondition( 
                                LSTUtils.invert(ifStatement.getIfCondition()) 
                                )
                            .withThenPart(
                                findAndReduceConditionBranches(elseBody)
                                )
                            .withElsePart(null);
            
            allBlockLines.set(ifStatementPosition, modifiedStatement);
            insertFlattened(allBlockLines, ifStatementPosition+1, findAndReduceConditionBranches(thenPart));
            
            return true;
        }

        Statement newElseBody = findAndReduceConditionBranches(elseBody);
        Statement newThenBody = findAndReduceConditionBranches(thenPart);
        
        boolean changed = newElseBody != elseBody || newThenBody != thenPart;
        
        if (changed)
            allBlockLines.set(ifStatementPosition, 
                              ifStatement
                                  .withThenPart(newThenBody)
                                  .withElsePart( elsePart.withBody(newElseBody)) );
        
        return changed;
    }
    
    private static Statement findAndReduceConditionBranches(Statement statement) {
        if (statement instanceof J.Block)
            return findAndReduceConditionBranches((J.Block) statement);
        
        return statement;
    }
    
    private static boolean findAndReduceConditionBranches(List<Statement> statements, int position) {
        Statement statement = statements.get(position);
        
        if (statement instanceof J.If) {
            J.If iff = (J.If)statement;
            return reduceConditionBranches(iff, statements, position);
        }
        
        if (statement instanceof J.Block) {
            Block oldBlock = (J.Block)statement;
            Block newBlock = findAndReduceConditionBranches(oldBlock);
            if (newBlock != oldBlock) {
                statements.set(position, newBlock);
                return true;
            }
        }
        
        if (statement instanceof J.Try) {
            J.Try tryy = (J.Try) statement;
            Block oldBody = tryy.getBody();
            Block newBody = findAndReduceConditionBranches(oldBody);
            if (newBody != oldBody) {
                statements.set(position, tryy.withBody(newBody));
                return true;
            }
        }
        
        return false;
    }
    
    private static Block findAndReduceConditionBranches(Block block) {
        if (block == null)
            return block;
        
        boolean touched = false;

        List<Statement> statements = new ArrayList<>(block.getStatements());
        
        for (int i=0; i < statements.size(); i++) 
            touched = findAndReduceConditionBranches(statements, i) || touched;
        
        if (touched)
            return block.withStatements(statements);
        
        return block;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            @Override
            public J visitCompilationUnit(J.CompilationUnit compUnit, ExecutionContext executionContext) {
                // This next line could be omitted in favor of a breakpoint
                // if you'd prefer to use the debugger instead.
            	
                // System.out.println(TreeVisitingPrinter.printTree(getCursor()));
            	
                return super.visitCompilationUnit(compUnit, executionContext);
            }
            
            @Override
            public J visitMethodDeclaration(MethodDeclaration method, ExecutionContext executionContext) {                
                Block methodBody = method.getBody();
                if (methodBody == null)
                    return super.visitMethodDeclaration(method, executionContext);
                
                Block reviewed = findAndReduceConditionBranches(methodBody);
                
                if (reviewed != methodBody) {
                    method = maybeAutoFormat(
                                    method, 
                                    method.withBody(reviewed),
                                    executionContext);
                    
                    // super.doAfterVisit(new SimplifyBooleanExpression().getVisitor());
                }
                
                return super.visitMethodDeclaration(method, executionContext);
            }
            
            @Override
            public J visitIf(J.If iff, ExecutionContext executionContext) {
				Else elsePart = iff.getElsePart();
				
				if (elsePart == null)
					return super.visitIf(iff, executionContext); // No else part - ignoring
				
				if (LSTUtils.isEmpty(elsePart)) 
					return super.visitIf(iff.withElsePart(null), executionContext); // Else is empty, erasing it

				Statement thenPart = iff.getThenPart();
				
				if (LSTUtils.isEmpty(thenPart)) {
					// the else part contains logic while the then part is empty - let's replace it with the else branch
					return super.visitIf(
							iff.withIfCondition( 
									InvertCondition.invert(iff.getIfCondition(), getCursor()) 
										)
								.withThenPart(elsePart.getBody())
								.withElsePart(null), 
							executionContext );
				}
				
				// leaving if as it was otherwise
            	return super.visitIf(iff, executionContext);
            }
            
            //// copy from Simplify Boolean Expression 
            private static final String MAYBE_AUTO_FORMAT_ME = "MAYBE_AUTO_FORMAT_ME";
            
            @Override
            public J visitBinary(J.Binary binary, ExecutionContext ctx) {
                J j = super.visitBinary(binary, ctx);
                J.Binary asBinary = (J.Binary) j;
                
                System.out.println("visiting binary "+asBinary);

                if (asBinary.getOperator() == J.Binary.Type.And) {
                    if (LSTUtils.isLiteralFalse(asBinary.getLeft())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    } else 
                    if (LSTUtils.isLiteralFalse(asBinary.getRight())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getRight().withPrefix(asBinary.getRight().getPrefix().withWhitespace(""));
                    } else 
                    if (LSTUtils.removeAllSpace(asBinary.getLeft()).printTrimmed(getCursor())
                            .equals(
                        LSTUtils.removeAllSpace(asBinary.getRight()).printTrimmed(getCursor()))) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    }
                } else 
                if (asBinary.getOperator() == J.Binary.Type.Or) {
                    if (LSTUtils.isLiteralTrue(asBinary.getLeft())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    } else 
                    if (LSTUtils.isLiteralTrue(asBinary.getRight())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getRight().withPrefix(asBinary.getRight().getPrefix().withWhitespace(""));
                    } else 
                    if (LSTUtils.removeAllSpace(asBinary.getLeft()).printTrimmed(getCursor())
                            .equals(LSTUtils.removeAllSpace(asBinary.getRight()).printTrimmed(getCursor()))) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    }
                } else 
                if (asBinary.getOperator() == J.Binary.Type.Equal) {
                    if (LSTUtils.isLiteralTrue(asBinary.getLeft())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getRight().withPrefix(asBinary.getRight().getPrefix().withWhitespace(""));
                    } else 
                    if (LSTUtils.isLiteralTrue(asBinary.getRight())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    }
                } else 
                if (asBinary.getOperator() == J.Binary.Type.NotEqual) {
                    System.out.println("It's not equal");
                    if (LSTUtils.isLiteralFalse(asBinary.getLeft())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getRight().withPrefix(asBinary.getRight().getPrefix().withWhitespace(""));
                    } else 
                    if (LSTUtils.isLiteralFalse(asBinary.getRight())) {
                        maybeUnwrapParentheses();
                        j = asBinary.getLeft();
                    }
                }
                if (asBinary != j) {
                    System.out.println("Binary changed in visit binary");
                    getCursor().getParentTreeCursor().putMessage(MAYBE_AUTO_FORMAT_ME, "");
                }
                return j;
            }

            @Override
            public J postVisit(J tree, ExecutionContext ctx) {
                J j = super.postVisit(tree, ctx);
                if (getCursor().pollMessage(MAYBE_AUTO_FORMAT_ME) != null) {
                    j = new AutoFormatVisitor<>().visit(j, ctx, getCursor().getParentOrThrow());
                }
                return j;
            }

            @Override
            public J visitUnary(J.Unary unary, ExecutionContext ctx) {
                J j = super.visitUnary(unary, ctx);
                J.Unary asUnary = (J.Unary) j;

                System.out.println("visiting unary " + asUnary);
                
                if (asUnary.getOperator() == J.Unary.Type.Not) {
                    System.out.println("it's a unary NOT "+asUnary.getExpression());
                    
                    if (LSTUtils.isLiteralTrue(asUnary.getExpression())) {
                        maybeUnwrapParentheses();
                        j = ((J.Literal) asUnary.getExpression()).withValue(false).withValueSource("false");
                    } else 
                    if (LSTUtils.isLiteralFalse(asUnary.getExpression())) {
                        maybeUnwrapParentheses();
                        j = ((J.Literal) asUnary.getExpression()).withValue(true).withValueSource("true");
                    } else 
                    if (asUnary.getExpression() instanceof J.Unary) {
                        System.out.println("It's a unary expression ");
                         if(((J.Unary) asUnary.getExpression()).getOperator() == J.Unary.Type.Not) {
                            System.out.println("it's a NOT operator");
                            maybeUnwrapParentheses();
                            j = ((J.Unary) asUnary.getExpression()).getExpression();
                        }
                    }
                }
                if (asUnary != j) {
                    System.out.println("unary changed in visit unary");
                    getCursor().getParentTreeCursor().putMessage(MAYBE_AUTO_FORMAT_ME, "");
                }
                return j;
            }

            /**
             * Specifically for removing immediately-enclosing parentheses on Identifiers and Literals.
             * This queues a potential unwrap operation for the next visit. After unwrapping something, it's possible
             * there are more Simplifications this recipe can identify and perform, which is why visitCompilationUnit
             * checks for any changes to the entire Compilation Unit, and if so, queues up another SimplifyBooleanExpression
             * recipe call. This convergence loop eventually reconciles any remaining Boolean Expression Simplifications
             * the recipe can perform.
             */
            private void maybeUnwrapParentheses() {
                System.out.println("maybe unwrap parenthes");
                Cursor c = getCursor().getParentOrThrow().getParentTreeCursor();
                if (c.getValue() instanceof J.Parentheses) {
                    System.out.println("will unwrap parentheses");
                    doAfterVisit(new UnwrapParentheses<>(c.getValue()));
                }
            }

        };
    }
}
