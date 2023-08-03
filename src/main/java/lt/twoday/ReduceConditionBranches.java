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
import java.util.Arrays;
import java.util.List;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.CountLinesVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.format.TabsAndIndentsVisitor;
import org.openrewrite.java.format.WrappingAndBracesVisitor;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.style.WrappingAndBracesStyle;
import org.openrewrite.java.style.WrappingAndBracesStyle.IfStatement;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.Block;
import org.openrewrite.java.tree.J.If;
import org.openrewrite.java.tree.J.If.Else;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.Markers;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class ReduceConditionBranches extends Recipe {

    private static final WrappingAndBracesStyle WRAPPING_AND_BRACES_STYLE = new WrappingAndBracesStyle(new IfStatement(true));
    private static final TabsAndIndentsStyle TABS_AND_INDENTS_STYLE = new TabsAndIndentsStyle(false, 4, 4, 8, false, new TabsAndIndentsStyle.MethodDeclarationParameters(true));

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
                
                Block reviewed = reviewMethod(methodBody, executionContext);

                if (methodBody != reviewed)
                    method = autoformat(method.withBody(reviewed), executionContext, getCursor());
                
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
	                boolean needsAutoformat = false;
                    Statement newThenPart = findAndReduceConditionBranches(elsePart.getBody(), executionContext);

                    if (needsPrefix(iff, newThenPart)) {
                        newThenPart = autoprefix(iff, newThenPart);
                        needsAutoformat = true;
                    }
                    
                    if (needsBraces(newThenPart))
                        newThenPart = embrace(newThenPart);
                    
                    If newIfPart = iff.withIfCondition( 
                                            MyInvertCondition.invert(iff.getIfCondition(), getCursor()) 
                                                )
                                        .withThenPart(newThenPart)
                                        .withElsePart(null);
				    
                    if (needsAutoformat)
                        newIfPart = autoformat(newIfPart, executionContext, getCursor());
                    
                    // the else part contains logic while the then part is empty - let's replace it with the else branch
					return super.visitIf(
							newIfPart, 
							executionContext );
				}
				
				// leaving if as it was otherwise
            	return super.visitIf(iff, executionContext);
            }
                        
            private Block reviewMethod(Block block, ExecutionContext executionContext) {
                if (block.getStatements().size() == 0)
                    return block;

                if (isSingleIfMethod(block)) {
                    J.If iff = (J.If) block.getStatements().get(0);
                    
                    if (LSTUtils.isEmpty(iff.getThenPart()) && LSTUtils.isLong(iff.getElsePart()))
                        return block.withStatements(createTypeAMethodBody(iff, executionContext));
                    
                    if (LSTUtils.isEmpty(iff.getElsePart()) && LSTUtils.isLong(iff.getThenPart()) && !LSTUtils.isThrow(iff.getThenPart()))
                        return block.withStatements(createTypeBMethodBody(iff, executionContext));
                    
//                    if (!LSTUtils.isEmpty(iff.getThenPart()) && !LSTUtils.isEmpty(iff.getElsePart()))
//                        return block.withStatements(createTypeCMethodBody(iff, executionContext));
                }
                
                return findAndReduceConditionBranches(block, executionContext);
            }

            private List<Statement> createTypeAMethodBody(J.If iff, ExecutionContext executionContext) {
                List<Statement> statements = new ArrayList<>();
                
                Else elsePart = iff.getElsePart();
                J.Return newThenPart = new J.Return(Tree.randomId(), 
                                elsePart.getPrefix(), 
                                Markers.EMPTY,
                                null);
                
                if (needsPrefix(iff, newThenPart))
                    newThenPart = (J.Return) autoprefix(iff, newThenPart);
                
                iff = iff.withElsePart(null)
                         .withThenPart(newThenPart);
                
                statements.add(iff);
                insertFlattened(statements, 1, findAndReduceConditionBranches(elsePart.getBody(), executionContext) );
                return statements;
            }

            private List<Statement> createTypeBMethodBody(J.If iff, ExecutionContext executionContext) {
                List<Statement> statements = new ArrayList<>();
                
                Statement thenPart = iff.getThenPart();
                J.Return newThenPart = new J.Return(Tree.randomId(), 
                                thenPart.getPrefix(), 
                                Markers.EMPTY,
                                null);
                
                if (needsPrefix(iff, newThenPart))
                    newThenPart = (J.Return) autoprefix(iff, newThenPart);
                
                iff = iff.withIfCondition(
                            MyInvertCondition.invert(iff.getIfCondition(), getCursor())
                                    )
                          .withThenPart(newThenPart);
                
                statements.add(iff);
                insertFlattened(statements, 1, findAndReduceConditionBranches(thenPart, executionContext) );
                return statements;
            }
            
            private boolean isSingleIfMethod(J.Block block) {
                return block.getStatements().size() == 1 && (block.getStatements().get(0) instanceof J.If);
            }
            
            private Statement findAndReduceConditionBranches(Statement statement, ExecutionContext executionContext) {
                if (statement instanceof J.Block)
                    return findAndReduceConditionBranches((J.Block) statement, executionContext);
                
                if (statement instanceof J.If)
                    return findAndReduceConditionBranches((J.If) statement, executionContext);
                
                return statement;
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
            
            
            private J.If findAndReduceConditionBranches(J.If iff, ExecutionContext executionContext) {
                if (iff == null)
                    return iff;
                
                return (J.If) visitIf(iff, executionContext);
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

            private boolean reduceConditionBranches(If ifStatement, List<Statement> allBlockLines, int ifStatementPosition, ExecutionContext executionContext) {
                Else elsePart = ifStatement.getElsePart();
                Statement thenPart = ifStatement.getThenPart();
                
                if (elsePart == null) {                    
                    Statement newThenPart = findAndReduceConditionBranches(thenPart, executionContext);
                    if (newThenPart != thenPart) {
                        
                        if (needsPrefix(ifStatement, newThenPart))
                            newThenPart = autoprefix(ifStatement, newThenPart);
                        
                        if (needsBraces(newThenPart))
                            newThenPart = embrace(newThenPart);
                        
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
                    Statement newThenPart = findAndReduceConditionBranches(thenPart, executionContext);;
                    if (newThenPart != thenPart) {
                        
                        if (needsPrefix(ifStatement, newThenPart))
                            newThenPart = autoprefix(ifStatement, newThenPart);
                        
                        if (needsBraces(newThenPart))
                            newThenPart = embrace(newThenPart);
                    }
                    
                    allBlockLines.set(ifStatementPosition,
                                      ifStatement
                                        .withElsePart(null)
                                        .withThenPart( newThenPart ));
                                
                    return true;
                }

                if (LSTUtils.isEmpty(thenPart)) {
                    // the else part contains logic while the then part is empty - let's replace it with the else branch
                    Statement newThenPart = findAndReduceConditionBranches(elseBody, executionContext);
                    
                    if (needsPrefix(ifStatement, newThenPart))
                        newThenPart = autoprefix(ifStatement, newThenPart);
                    
                    if (needsBraces(newThenPart))
                        newThenPart = embrace(newThenPart);
                    
                    J.If modifiedStatement = 
                            ifStatement.withIfCondition( 
                                    MyInvertCondition.invert(ifStatement.getIfCondition(), getCursor())
                                    )
                                .withThenPart( 
                                        newThenPart
                                    )
                                .withElsePart(null);
                    
                    allBlockLines.set(ifStatementPosition, modifiedStatement);
                    
                    return true;
                }
                
                if (LSTUtils.hasGuaranteedReturn(thenPart)) {                
                    // the thenPart has guaranteed return: 
                    // the elsePart shall get flattened.
                    
                    Statement newThenPart = findAndReduceConditionBranches(thenPart, executionContext);
                    if (newThenPart != thenPart) {
                        if (needsPrefix(ifStatement, newThenPart))
                            newThenPart = autoprefix(ifStatement, newThenPart);
                        
                        if (needsBraces(newThenPart))
                            newThenPart = embrace(newThenPart);
                    }
                    
                    J.If modifiedStatement = 
                                ifStatement.withIfCondition(
                                        ifStatement.getIfCondition()
                                        ) 
                                    .withThenPart(
                                        newThenPart
                                        )
                                    .withElsePart(null);
                    
                    allBlockLines.set(ifStatementPosition, modifiedStatement);
                    insertFlattened(allBlockLines, ifStatementPosition+1, findAndReduceConditionBranches(elseBody, executionContext) );
                    
                    return true;
                }
                
                if (LSTUtils.hasGuaranteedReturn(elseBody)) {
                    // the elsePart has guaranteed return:
                    // make it the thenPart, and flatten the thenPart instead.

                    Statement newThenPart = findAndReduceConditionBranches(elseBody, executionContext);
                    if (newThenPart != elseBody) {
                        if (needsPrefix(ifStatement, newThenPart))
                            newThenPart = autoprefix(ifStatement, newThenPart);
                        
                        if (needsBraces(newThenPart))
                            newThenPart = embrace(newThenPart);
                    }
                    
                    J.If modifiedStatement = 
                                ifStatement.withIfCondition( 
                                        MyInvertCondition.invert(ifStatement.getIfCondition(), getCursor())                                        
                                        )
                                    .withThenPart(
                                        newThenPart
                                        )
                                    .withElsePart(null);
                    
                    allBlockLines.set(ifStatementPosition, modifiedStatement);
                    insertFlattened(allBlockLines, ifStatementPosition+1, findAndReduceConditionBranches(thenPart, executionContext));
                    
                    return true;
                }

                Statement newElseBody = findAndReduceConditionBranches(elseBody, executionContext);
                Statement newThenBody = findAndReduceConditionBranches(thenPart, executionContext);
                
                boolean changed = newElseBody != elseBody || newThenBody != thenPart;
                
                if (changed) {
                    allBlockLines.set(ifStatementPosition, 
                                      ifStatement
                                          .withThenPart(newThenBody)
                                          .withElsePart( elsePart.withBody(newElseBody)) );
                }
                
                return changed;
            }
            
            private boolean needsPrefix(J.If originalIf, Statement thenPart) {
                if (thenPart instanceof J.Block)
                    return false;
                
                return ! thenPart.getPrefix().getWhitespace().contains("\n");
            }

            private Statement autoprefix(J.If iff, Statement newThenPart) {
                return newThenPart.withPrefix(
                        iff.getPrefix()
                                .withWhitespace("\n" 
                                            + iff.getPrefix().getIndent() 
                                            + "    ")
                                );
            }
            
            private boolean needsBraces(Statement statement) {
                if (statement instanceof J.Block)
                    return false;
                
                if (statement instanceof J.Try)
                    return false;
                
                return CountLinesVisitor.countLines(statement) > 2; 
            }
            
            private Statement embrace(Statement statement) {
                return J.Block
                        .createEmptyBlock()
                            .withStatements(Arrays.asList(statement));
            }
            
            private <T extends Tree> T autoformat(T method, ExecutionContext executionContext, Cursor cursor) {
                method = (T)new WrappingAndBracesVisitor<>(WRAPPING_AND_BRACES_STYLE)
                        .visit(method, 
                                executionContext, 
                                cursor.getParentTreeCursor().fork()
                            );
                
                method = (T)new TabsAndIndentsVisitor<>(TABS_AND_INDENTS_STYLE, null)
                        .visit(method, 
                               executionContext, 
                               cursor.getParentTreeCursor().fork());

                return method;
            }
        };
    }
}
