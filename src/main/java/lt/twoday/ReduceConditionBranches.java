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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
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
                
                Block reviewed = findAndReduceConditionBranches(methodBody, executionContext);
                
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
									MyInvertCondition.invert(iff.getIfCondition(), getCursor()) 
										)
								.withThenPart(elsePart.getBody())
								.withElsePart(null), 
							executionContext );
				}
				
				// leaving if as it was otherwise
            	return super.visitIf(iff, executionContext);
            }
            
            private boolean reduceConditionBranches(If ifStatement, List<Statement> allBlockLines, int ifStatementPosition, ExecutionContext executionContext) {
                Else elsePart = ifStatement.getElsePart();
                Statement thenPart = ifStatement.getThenPart();
                
                if (elsePart == null) {
                    Statement newThenPart = findAndReduceConditionBranches(thenPart, executionContext);
                    if (newThenPart != thenPart) {
                        allBlockLines.set(ifStatementPosition, 
                                            ifStatement.withThenPart( newThenPart )
                                          );
                        return true;
                    }
                    return false;
                }
                
                Statement elseBody = elsePart.getBody();
                
                if (LSTUtils.isEmpty(elseBody)) {
                    // elsePart is empty, erasing it
                    J.If modifiedStatement = ifStatement
                            .withElsePart(null)
                            .withThenPart( findAndReduceConditionBranches(thenPart, executionContext) );
                    allBlockLines.set(ifStatementPosition, modifiedStatement);
                                
                    return true;
                }

                if (LSTUtils.isEmpty(thenPart)) {
                    // the else part contains logic while the then part is empty - let's replace it with the else branch
                    J.If modifiedStatement = 
                            ifStatement.withIfCondition( 
                                    MyInvertCondition.invert(ifStatement.getIfCondition(), getCursor())
                                    )
                                .withThenPart( 
                                        findAndReduceConditionBranches(elseBody, executionContext) 
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
                                        findAndReduceConditionBranches(thenPart, executionContext)
                                        )
                                    .withElsePart(null);
                    
                    allBlockLines.set(ifStatementPosition, modifiedStatement);
                    insertFlattened(allBlockLines, ifStatementPosition+1, findAndReduceConditionBranches(elseBody, executionContext) );
                    
                    return true;
                }
                
                if (LSTUtils.hasGuaranteedReturn(elseBody)) {
                    // the elsePart has guaranteed return:
                    // make it the thenPart, and flatten the thenPart instead.

                    J.If modifiedStatement = 
                                ifStatement.withIfCondition( 
                                        MyInvertCondition.invert(ifStatement.getIfCondition(), getCursor())                                        
                                        )
                                    .withThenPart(
                                        findAndReduceConditionBranches(elseBody, executionContext)
                                        )
                                    .withElsePart(null);
                    
                    allBlockLines.set(ifStatementPosition, modifiedStatement);
                    insertFlattened(allBlockLines, ifStatementPosition+1, findAndReduceConditionBranches(thenPart, executionContext));
                    
                    return true;
                }

                Statement newElseBody = findAndReduceConditionBranches(elseBody, executionContext);
                Statement newThenBody = findAndReduceConditionBranches(thenPart, executionContext);
                
                boolean changed = newElseBody != elseBody || newThenBody != thenPart;
                
                if (changed)
                    allBlockLines.set(ifStatementPosition, 
                                      ifStatement
                                          .withThenPart(newThenBody)
                                          .withElsePart( elsePart.withBody(newElseBody)) );
                
                return changed;
            }
            
            private Statement findAndReduceConditionBranches(Statement statement, ExecutionContext executionContext) {
                if (statement instanceof J.Block)
                    return findAndReduceConditionBranches((J.Block) statement, executionContext);
                
                return statement;
            }
            
            private boolean findAndReduceConditionBranches(List<Statement> statements, int position, ExecutionContext executionContext) {
                Statement statement = statements.get(position);
                
                if (statement instanceof J.If) {
                    J.If iff = (J.If)statement;
                    return reduceConditionBranches(iff, statements, position, executionContext);
                }
                
                if (statement instanceof J.Block) {
                    Block oldBlock = (J.Block)statement;
                    Block newBlock = findAndReduceConditionBranches(oldBlock, executionContext);
                    if (newBlock != oldBlock) {
                        statements.set(position, newBlock);
                        return true;
                    }
                }
                
                if (statement instanceof J.Try) {
                    J.Try tryy = (J.Try) statement;
                    Block oldBody = tryy.getBody();
                    Block newBody = findAndReduceConditionBranches(oldBody, executionContext);
                    if (newBody != oldBody) {
                        statements.set(position, tryy.withBody(newBody));
                        return true;
                    }
                }
                
                return false;
            }
            
            private Block findAndReduceConditionBranches(Block block, ExecutionContext executionContext) {
                if (block == null)
                    return block;
                
                boolean touched = false;

                List<Statement> statements = new ArrayList<>(block.getStatements());
                
                for (int i=0; i < statements.size(); i++) 
                    touched = findAndReduceConditionBranches(statements, i, executionContext) || touched;
                
                if (touched)
                    return block.withStatements(statements);
                
                return block;
            }
            
        };
    }
}
