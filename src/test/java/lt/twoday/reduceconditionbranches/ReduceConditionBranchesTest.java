package lt.twoday.reduceconditionbranches;

import static org.openrewrite.java.Assertions.java;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

class ReduceConditionBranchesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new ReduceConditionBranches())
            .parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true));
    }
    
    Consumer<RecipeSpec> createSpec() {
        return new Consumer<RecipeSpec>() {
            
            @Override
            public void accept(RecipeSpec spec) {
                spec
                    .cycles(1)
                    .parser(JavaParser
                                .fromJavaVersion()
                                .logCompilationWarningsAndErrors(false));
            }
        };
    }
    
    @Test
    void shouldRemoveEmptyElseBranch() {
        rewriteRun(
                createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a <= 1) {
                                System.out.println(a); // comment
                            }
                            else if (a <= 5) {
                                System.out.println(a+1); // comment 2
                                if (a < 9) {
                                    a++;
                                } else {
                                }
                            }
                            else
                            {
                                {
                                    ;
                                }
                            }
                        }
                    }
                """
                    ,
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a <= 1) {
                                System.out.println(a); // comment
                            }
                            else if (a <= 5) {
                                System.out.println(a+1); // comment 2
                                if (a < 9) {
                                    a++;
                                }
                            }
                        }
                    }
                """
            )
        );
    }
    
    
    @Test
    void shouldRemoveEmptyThenBranch() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a instanceof A)
                                ; // this comment will be deleted
                            else 
                                System.out.print(a);  // comment
                        }
                    }
                """
                    ,
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (!(a instanceof A))
                                System.out.print(a);  // comment
                        }
                    }
                """
            )
        );
    }
    
    @Test
    void shouldPreferBranchesWithExplicitReturns() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a <= 0) {
                                System.out.println("negative");
                            }
                            else {
                                System.out.print("non-negative"); // comment
                                return;
                            } 
                            System.out.println("something about negatives now");
                        }
                    }
                """, 
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a > 0) {
                                System.out.print("non-negative"); // comment
                                return;
                            }
                            System.out.println("negative");
                            System.out.println("something about negatives now");
                        }
                    }
                """
            )
        );
    }
    
    
    @Test
    void shouldPreferBranchesWithExplicitThrows() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a <= 0) {
                                System.out.println("negative");
                            }
                            else
                                throw new RuntimeException("non-negative");
                            System.out.println("something about negatives now");
                        }
                    }
                """, 
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a > 0)
                                throw new RuntimeException("non-negative");
                            System.out.println("negative");
                            System.out.println("something about negatives now");
                        }
                    }
                """
            )
        );
    }
    
    @Test
    void shouldFlattenElseBranchWheneverThenBranchHasGuaranteedReturn() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a < 0) {
                                System.out.println("error");
                                return;
                            }
                            else {
                                System.out.print(a);
                            } 
                        }
                    }
                """
                    ,
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a < 0) {
                                System.out.println("error");
                                return;
                            }
                            System.out.print(a);
                        }
                    }
                """
            )
        );
    }
    
    @Test
    void shouldMakeEarlyReturnInSingleIfMethodsWithLargeThenBranch() {
        rewriteRun(
                createSpec(),
                java(
                    """
                        class A {
                            void test(int a) {
                                if (a==0){
                                    System.out.println("statement1");
                                    System.out.println("statement2");
                                    System.out.println("statement3");
                                }
                            }
                        }
                    """
                        ,
                    """
                        class A {
                            void test(int a) {
                                if (a!=0)
                                    return;
                                System.out.println("statement1");
                                System.out.println("statement2");
                                System.out.println("statement3");
                            }
                        }
                    """
                )
            );
    }
    
    @Test
    void shouldNotRewriteShortSingleIfMethodsWithShortThenBranch() {
        rewriteRun(
                createSpec(),
                java(
                    """
                        class A {
                            void test(int a) {
                                if (a==0)
                                    System.out.println("statement1");
                            }
                        }
                    """
                )
            );
    }
    
    @Test
    void shouldMakeEarlyReturnInSingleElseMethodsWithLongBranch() {
        rewriteRun(
                createSpec(),
                java(
                    """
                        class A {
                            void test(int a) {
                                if (a==0)
                                    ;
                                else {
                                    System.out.println("statement1");
                                    System.out.println("statement2");
                                    System.out.println("statement3");
                                }
                            }
                        }
                    """
                        ,
                    """
                        class A {
                            void test(int a) {
                                if (a==0)
                                    return;
                                System.out.println("statement1");
                                System.out.println("statement2");
                                System.out.println("statement3");
                            }
                        }
                    """
                )
            );
    }
    
    @Test
    void shouldNotMakeEarlyReturnInSingleElseMethodsWithShortBranch() {
        rewriteRun(
                createSpec(),
                java(
                    """
                        class A {
                            void test(int a) {
                                if (a==0)
                                    ;
                                else
                                    System.out.println("statement1");
                            }
                        }
                    """
                        ,
                    """
                        class A {
                            void test(int a) {
                                if (a!=0)
                                    System.out.println("statement1");
                            }
                        }
                    """
                )
            );
    }
    
    @Test
    void shouldNotFlattenThenBranchWhenNotAllPathsHaveGuaranteedReturn() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a <= 0) {
                                System.out.println("non-positive"); // comment
                                if (a < 0)
                                    return;
                            }
                            else {
                                System.out.print("positive"); // comment
                            } 
                        }
                    }
                """
            )
        );
    }
    
    @Test
    void shouldNotFlattenElseBranchWhenNotAllPathsHaveGuaranteedReturn() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            if (a <= 0) {
                                System.out.println("non-positive"); // comment
                            }
                            else {
                                if (a == 0)
                                    return;
                                System.out.print("positive"); // comment
                            } 
                        }
                    }
                """
            )
        );
    }
        
    
    @Test
    void shouldCorrectlyInvertInstanceof() {
       rewriteRun(
               createSpec(),
               java("""
                       class A{
                           public static void test(Object a) {
                               if (! (a instanceof Double))
                                   ;
                               else
                                   throw new RuntimeException("must not be double!");
                           
                               if (a instanceof String)
                                   ;
                               else
                                   throw new RuntimeException("must be string!");
                           }
                       }
                       """,
                       """
                       class A{
                           public static void test(Object a) {
                               if (a instanceof Double)
                                   throw new RuntimeException("must not be double!");
                           
                               if (!(a instanceof String))
                                   throw new RuntimeException("must be string!");
                           }
                       }
                       """)
               );
    }
    
    @Test
    void shouldCorrectlyInvertConditions() {
       rewriteRun(
               createSpec(),
               java("""
                       class A{
                           public static void test(int a) {
                               if (a < 0 || a > 10)
                                   ;
                               else
                                   throw new RuntimeException("not in range");
                           
                               if (a > 0 && a < 10)
                                   ;
                               else
                                   throw new RuntimeException("not in range 2");
                           }
                       }
                       """,
                       """
                       class A{
                           public static void test(int a) {
                               if (!(a < 0 || a > 10))
                                   throw new RuntimeException("not in range");
                           
                               if (!(a > 0 && a < 10))
                                   throw new RuntimeException("not in range 2");
                           }
                       }
                       """)
               );
    }
    
    @Test
    void shouldCreateTypeAMethod() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void analyze(Object element) {
        if (element != null)
            ;
        else {
            System.out.println("a");
            System.out.println("b");
            System.out.println("c");
            System.out.println("d");
        }
    }
}
""",
"""
class A {
    public void analyze(Object element) {
        if (element != null)
            return;
        System.out.println("a");
        System.out.println("b");
        System.out.println("c");
        System.out.println("d");
    }
}
"""));
    }
    
    @Test
    void shouldCreateTypeBMethod() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void analyze(Object element) {
        if (element != null){
            System.out.println("a");
            System.out.println("b");
            System.out.println("c");
            System.out.println("d");
        }else {
        }
    }
}
""",
"""
class A {
    public void analyze(Object element) {
        if (element == null)
            return;
        System.out.println("a");
        System.out.println("b");
        System.out.println("c");
        System.out.println("d");
    }
}
"""));
    }
    
    @Test
    void shouldCreateTypeCMethod() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void analyze(Object element) {
        if (element != null){
            System.out.println("a");
            throw new RuntimeException("");            
        }else {
            System.out.println("a");
            System.out.println("b");
            System.out.println("c");
            System.out.println("d");
        }
    }
}
""",
"""
class A {
    public void analyze(Object element) {
        if (element != null){
            System.out.println("a");
            throw new RuntimeException("");            
        }
        System.out.println("a");
        System.out.println("b");
        System.out.println("c");
        System.out.println("d");
    }
}
"""));
    }
    
    @Test
    void shouldCreateTypeCMethodWithReturn() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void analyze(Object element) {
        if (element != null){
            System.out.println("a");       
        }else {
            System.out.println("a");
            System.out.println("b");
            System.out.println("c");
            System.out.println("d");
        }
    }
}
""",
"""
class A {
    public void analyze(Object element) {
        if (element != null){
            System.out.println("a");
            return;            
        }
        System.out.println("a");
        System.out.println("b");
        System.out.println("c");
        System.out.println("d");
    }
}
"""));
    }
    
    @Test
    void shouldPrioritizeThrowBranch() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void analyze(Object element) {
        if (element != null) {
            System.out.println("not null");
            if (element instanceof String)
                System.out.println("String");
            else if (element instanceof Integer) {
                System.out.println("Integer");
            }
        } else 
            throw new RuntimeException("null");
    }
}
""",
"""
class A {
    public void analyze(Object element) {
        if (element == null)
            throw new RuntimeException("null");
        System.out.println("not null");
        if (element instanceof String)
            System.out.println("String");
        else if (element instanceof Integer) {
            System.out.println("Integer");
        }
    }
}
"""
                   ));
    }
    
    @Test
    void shouldAddThenReturnInSingleIfMethod() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void test(int i){
        if (i == 0)
            ;
        else 
        if (i >= 1){
            if (i >= 2){
                System.out.println("b");
                if (i >= 3)
                    System.out.println("c");
            }
        }
    }
}
""",
"""
class A {
    public void test(int i){
        if (i == 0)
            return;
        if (i >= 1){
            if (i >= 2){
                System.out.println("b");
                if (i >= 3)
                    System.out.println("c");
            }
        }
    }
}
"""
                    ));
    }
    
    @Test
    void integrationTest() {
       rewriteRun(
               createSpec(),
               java(
"""
class A {
   public void analyze(Object element) {
       if (element != null) {
           if (! (element instanceof Double)) {
               if (element instanceof String) {
                   String s = element.toString();
                   if (s.length() != 0) {
                       if (s.startsWith("H"))
                           System.out.println("analyzing H word");
                       else if (s.startsWith("A"))
                           System.out.println("analyzing A word");
                       else
                           System.out.println("analyzing any other word");
                   } else {
                       System.out.println("empty string, will not analyze");
                       return;
                   }
               } else { 
                   if (element instanceof Integer) {
                       Integer i = (Integer) element;
                       System.out.println("analyzing integer " + i);
                       return;
                   } else 
                   if (element instanceof Float) {
                       System.out.println("analyzing Float! " + element);
                       return;
                   } else {
                       System.out.println("analyzing unknown type of element " + element);
                       return;
                   }
               }
           } else
               throw new IllegalArgumentException("handling of Double is not impemented!");
       } else 
           throw new IllegalArgumentException("param must not be null!");

       System.out.println("done");
   }
}
""",
"""
class A {
    public void analyze(Object element) {
        if (element == null)
            throw new IllegalArgumentException("param must not be null!");
        if (element instanceof Double)
            throw new IllegalArgumentException("handling of Double is not impemented!");
        if (!(element instanceof String)) {
            if (element instanceof Integer) {
                Integer i = (Integer) element;
                System.out.println("analyzing integer " + i);
                return;
            }
            if (element instanceof Float) {
                System.out.println("analyzing Float! " + element);
                return;
            }
            System.out.println("analyzing unknown type of element " + element);
            return;
        }
        String s = element.toString();
        if (s.length() == 0) {
            System.out.println("empty string, will not analyze");
            return;
        }
        if (s.startsWith("H"))
            System.out.println("analyzing H word");
        else if (s.startsWith("A"))
            System.out.println("analyzing A word");
        else
            System.out.println("analyzing any other word");

        System.out.println("done");
    }
}
"""
               ));
   }
    
    @Test
    void shouldPreserveMultilineConditionStatements() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        int test() {
                            int a = 0;
                            int b = 0;
                            if (a <= 0
                                 &&
                                 b <= 0) {
                                System.out.println("both non-positives");
                            }
                            else {
                                return 0;
                            } 
                            System.out.println("something about non-positives now"); // comment
                            return 1;
                        }
                    }
                """, 
                """
                    class A {
                        int test() {
                            int a = 0;
                            int b = 0;
                            if (!(a <= 0
                                    &&
                                    b <= 0)) {
                                return 0;
                            }
                            System.out.println("both non-positives"); 
                            System.out.println("something about non-positives now"); // comment
                            return 1;
                        }
                    }
                """
            )
        );
    }
    
    @Test
    void shouldNotReduceBlocksWhenRefactoring() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            { // block
                                if (a <= 0) {
                                    System.out.println("negative");
                                }
                                else {
                                    System.out.print("non-negative"); // comment
                                    return;
                                } 
                            } // block end
                            System.out.println("something about negatives now");
                        }
                    }
                """, 
                """
                    class A {
                        void test() {
                            int a = 0;
                            { // block
                                if (a > 0) {
                                    System.out.print("non-negative"); // comment
                                    return;
                                }
                                System.out.println("negative");
                            } // block end
                            System.out.println("something about negatives now");
                        }
                    }
                """
            )
        );
    }

    @Test
    void shouldRecursivelyReviewNestedIfs() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            int b = 0;
                            int c = 0;
                            if (a==0){
                               if (c==0){
                                  c++;
                               }
                               else{
                                  c=0; // comment
                                  return;
                               }
                            }
                        }
                    }
                """
                ,
                """
                class A {
                    void test() {
                        int a = 0;
                        int b = 0;
                        int c = 0;
                        if (a==0){
                            if (c!=0){
                                c=0; // comment
                                return;
                            }
                            c++;
                        }
                    }
                }
                """
            )
        );
    }
    
    @Test
    void shouldRecursivelyReviewNestedElseBranches() {
        rewriteRun(
            createSpec(),
            java(
                """
                    class A {
                        void test() {
                            int a = 0;
                            int b = 0;
                            int c = 0;
                            if (a==0){
                                  c++;
                            } else {
                                if (a == 1){
                                    c--;
                                }else{
                                  c=0;
                                  return;
                                }
                            }
                        }
                    }
                """
                ,
                """
                class A {
                    void test() {
                        int a = 0;
                        int b = 0;
                        int c = 0;
                        if (a==0){
                            c++;
                        }
                        else {
                            if (a != 1){
                                c=0;
                                return;
                            }
                            c--;
                        }
                    }
                }
                """
            )
        );
    }
    
    @Test
    void shouldRecursivelyReviewTryCatchBlock() {
        rewriteRun(
            createSpec(),
            java(
                """
                class A {
                    public String getText(Object element) {
                        try {
                            if (element instanceof String) {
                                String logbookRecord = (String) element;
                                if (logbookRecord.length() == 0) {
                                    return String.format("--- %s ---", logbookRecord);
                                } else if (logbookRecord.length() == 1) {
                                    String label = logbookRecord.substring(0);
                                    if ("a".equals(label)) {
                                        label += "A";
                                    } else {
                                        return label + "B";
                                    }
                                    return logbookRecord + label;
                                } else {
                                    return logbookRecord;
                                }
                            } else if (element instanceof Double) {
                                return "" + element;
                            } else {
                                return element == null ? "" : element.toString();
                            }
                        } catch (Exception e) {
                            return e.getMessage();
                        }
                    }
                }
                """,
                """
                class A {
                    public String getText(Object element) {
                        try {
                            if (element instanceof String) {
                                String logbookRecord = (String) element;
                                if (logbookRecord.length() == 0) {
                                    return String.format("--- %s ---", logbookRecord);
                                }
                                if (logbookRecord.length() == 1) {
                                    String label = logbookRecord.substring(0);
                                    if (!"a".equals(label)) {
                                        return label + "B";
                                    }
                                    label += "A";
                                    return logbookRecord + label;
                                }
                                return logbookRecord;
                            }
                            if (element instanceof Double) {
                                return "" + element;
                            }
                            return element == null ? "" : element.toString();
                        } catch (Exception e) {
                            return e.getMessage();
                        }
                    }
                }
                """
            )
        );
    }
    
    @Test
    void shouldRecursivelyReviewNestedTryCatchBlocks() {
        rewriteRun(
            createSpec(),
            java(
                """
                class A {
                    public String getText(Object element) {
                        if (element != null)
                        try {
                            if (element instanceof String) {
                                String logbookRecord = (String) element;
                                if (logbookRecord.length() == 0) {
                                    return String.format("--- %s ---", logbookRecord);
                                } else if (logbookRecord.length() == 1) {
                                    String label = logbookRecord.substring(0);
                                    if ("a".equals(label)) {
                                        label += "A";
                                    } else {
                                        return label + "B";
                                    }
                                    return logbookRecord + label;
                                } else {
                                    return logbookRecord;
                                }
                            } else if (element instanceof Double) {
                                return "" + element;
                            } else {
                                return element.toString();
                            }
                        } catch (Exception e) {
                            return e.getMessage();
                        }
                        else
                            return null;
                    }
                }
                """,
                """
                class A {
                    public String getText(Object element) {
                        if (element == null)
                            return null;
                        try {
                            if (element instanceof String) {
                                String logbookRecord = (String) element;
                                if (logbookRecord.length() == 0) {
                                    return String.format("--- %s ---", logbookRecord);
                                }
                                if (logbookRecord.length() == 1) {
                                    String label = logbookRecord.substring(0);
                                    if (!"a".equals(label)) {
                                        return label + "B";
                                    }
                                    label += "A";
                                    return logbookRecord + label;
                                }
                                return logbookRecord;
                            }
                            if (element instanceof Double) {
                                return "" + element;
                            }
                            return element.toString();
                        } catch (Exception e) {
                            return e.getMessage();
                        }
                    }
                }
                """)
            );
    }
     
     @Test
     void shouldSimplifySingleIfMethod() {
         rewriteRun(
                 createSpec(),
                 java(
 """
class A {
    public void analyze(Object element) {
        if (element instanceof String)
            System.out.println("String"); 
        else if (element instanceof Integer) {
            System.out.println("Integer");
            if (element == null)
                if (element.equals("prop1")) {
                    System.out.println("prop1");
                } else if (element.equals("prop2")) {
                    System.out.println("prop2");
                }
            }
    }
}
""",
"""
class A {
    public void analyze(Object element) {
        if (element instanceof String){
            System.out.println("String");
            return;
        }
        if (element instanceof Integer) {
            System.out.println("Integer");
            if (element == null)
                if (element.equals("prop1")) {
                    System.out.println("prop1");
                }
                else if (element.equals("prop2")) {
                    System.out.println("prop2");
                }
        }
    }
}
"""
                         
                         ));
     }
    
    @Test
    void regressionTest1() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void initialize(String eCase, String[] initializeProps) {
        if (eCase == null) {
            // object is null no op
        } else if (eCase.equals("prop1")) {
            System.out.println("prop1");
        } else if (eCase.equals("prop2")) {
            System.out.println("prop2");
        }
    }
}
""",
"""
class A {
    public void initialize(String eCase, String[] initializeProps) {
        if (eCase == null)
            return;
        if (eCase.equals("prop1")) {
            System.out.println("prop1");
        }
        else if (eCase.equals("prop2")) {
            System.out.println("prop2");
        }
    }
}
"""));
    }
    
    @Test
    void regressionTest2() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void test(Object envelope) {
        if (envelope == null) {
            // Nothing to do
        } else if (envelope instanceof String) {
            int size = ((String) envelope).length();
            if (size == 0) {
                // No point
            } else {
                System.out.println("something");
            }
            return;
        } else if (envelope instanceof Integer) {
            int size = ((Integer) envelope).intValue();
        } else if (envelope instanceof Double) {
            double c = ((Double) envelope).doubleValue();
        } else {
            // We should never get here
            System.err.println("should not happen");
        }
    }
}
""",
"""
class A {
    public void test(Object envelope) {
        if (envelope == null)
            return;
        if (envelope instanceof String) {
            int size = ((String) envelope).length();
            if (size != 0) {
                System.out.println("something");
            }
            return;
        }
        else if (envelope instanceof Integer) {
            int size = ((Integer) envelope).intValue();
        }
        else if (envelope instanceof Double) {
            double c = ((Double) envelope).doubleValue();
        } 
        else {
            // We should never get here
            System.err.println("should not happen");
        }
    }
}
    """));
    }
 
    @Test
    void regressionTest3() {
        rewriteRun(
                createSpec(),
                java(
"""
class A {
    public void test(Object envelope) {
        if (envelope != null){
            if (envelope instanceof String) {
                int size = ((String) envelope).length();
                if (size != 0) {
                    System.out.println("something");
                }
                return;
            }
            if (envelope instanceof Integer) {
                int size = ((Integer) envelope).intValue();
            }
            else if (envelope instanceof Double) {
                double c = ((Double) envelope).doubleValue();
            }
            else {
                // We should never get here
                System.err.println("should not happen");
            }
        }
    }
}
""",
"""
class A {
    public void test(Object envelope) {
        if (envelope == null)
            return;
        if (envelope instanceof String) {
            int size = ((String) envelope).length();
            if (size != 0) {
                System.out.println("something");
            }
            return;
        }
        if (envelope instanceof Integer) {
            int size = ((Integer) envelope).intValue();
        }
        else if (envelope instanceof Double) {
            double c = ((Double) envelope).doubleValue();
        }
        else {
            // We should never get here
            System.err.println("should not happen");
        }
    }
}
"""));
    }
        
    
/*** not supported refactoring yet:
    @Test
    void shouldReduceMultipleIfElseIfs() {
        rewriteRun(
                createSpec(),
                java(
"""
class A{
    void test(int i) {
        if (i==0) {
            System.out.println(i);
        }else
        if (i==1) {
            System.out.println(i);
        }else
        if (i==2) {
            System.out.println(i);
        }else 
        if (i==3){
            System.out.println(i);
        }else {
            System.out.println("more than 3");
        }
    }
}
"""));
    }
*/
    
    @Test
    void putYourCodeHere() {
        rewriteRun(
                createSpec(),
                java(
"""
"""));
    }
    
}

