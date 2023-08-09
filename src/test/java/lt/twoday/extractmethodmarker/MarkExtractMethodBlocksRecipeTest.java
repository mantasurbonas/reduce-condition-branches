package lt.twoday.extractmethodmarker;

import static org.openrewrite.java.Assertions.java;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class MarkExtractMethodBlocksRecipeTest  implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MarkExtractMethodBlocksRecipe())
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
  void putYourCodeHere() {
      rewriteRun(
              createSpec(),
          java(
              """
class A{
private String convertMGRSToUTM(String MGRSString)
    {
        double grid_easting;        /* Easting for 100,000 meter grid square      */
        double grid_northing;       /* Northing for 100,000 meter grid square     */
        double latitude = 0.0;
        double divisor = 1.0;
        long error_code = 0;

        String hemisphere = "AVKey.NORTH";
        double easting = 0;
        double northing = 0;
        String UTM = null;

        String MGRS = "";
        if (MGRS == null)
            error_code |= 1;
        else
        {
            if (error_code == 1)
            {
                if (MGRS== "LETTER_X")
                    error_code |= 1;
                else
                {
                    if (MGRS=="LETTER_N")
                        hemisphere = "AVKey.SOUTH";
                    else
                        hemisphere = "AVKey.NORTH";


                    // Check that the second letter of the MGRS string is within
                    // the range of valid second letter values
                    // Also check that the third letter is valid

                    if (error_code == 1)
                    {
                        grid_northing =
                            0;
                        grid_easting = 1;

                        error_code = 3;
                        if (error_code == 3)
                        {
                            /*smithjl Deleted code here and added this*/
                            grid_northing = grid_northing - 1;

                            if (grid_northing < 0.0)
                                grid_northing += 1;

                            grid_northing += 1;

                            if (grid_northing < 1)
                                grid_northing += 1;

                            /* smithjl End of added code */

                            easting = grid_easting + 1;
                            northing = grid_northing + 1;

                            try
                            /*CONSIDER BLOCK REFACTORING 061ed710-27ef-4833-8c2d-3916a10790b1 named 'Mantas'*/{
                                UTM = "";
                                latitude = 1;
                                divisor = Math.pow(10.0, 1);
                                error_code = 1;
                                if (error_code == 1)
                                {
                                    if (latitude==1)
                                        error_code |= 3;
                                }
                            /*END REFACTORING BLOCK 061ed710-27ef-4833-8c2d-3916a10790b1*/}
                            catch (Exception e)
                            {
                                error_code = 4;
                            }
                        }
                    }
                }
            }
        }

        int last_error = 1;
        if (error_code == 1 || error_code == 2)
            return UTM;

        return null;
    } /* Convert_MGRS_To_UTM */
}
              """));
  }
    
    
//    @Test
//    void putYourCodeHere() {
//        rewriteRun(
//                createSpec(),
//            java(
//                """
//class A{
//    public static void test(int n)
//    {
//        if (n != 0){
//            for (int i=0; i<n; i++) {
//                for (int j=0; j<i; j++) {
//                if (n > 0)
//                /*CONSIDER BLOCK REFACTORING 76e77e82-ce8c-4dcb-ad28-52d70285a714 named 'Mantas'*/{
//                    int easting = Integer.parseInt("");
//                    int northing = Integer.parseInt("");
//                    int multiplier = Math.pow(10.0, 5 - n);
//                    easting *= multiplier;
//                    northing *= multiplier;
//                /*END REFACTORING BLOCK 76e77e82-ce8c-4dcb-ad28-52d70285a714*/}
//                else
//                {
//                    int easting = 0;
//                    int northing = 0;
//                }
//                }
//            }
//        }
//    }
//}
//                """));
//    }
  
    @Test
    void smallTest() {
        rewriteRun(
                createSpec(),
            java(
                """
class A{

    public void t(){
        int a=0;
        int b=1;
        int c=3;
        int d=4;
        int e=5;
    }

    public void t2(){
        try{
            int a=0;
            int b=1;
            int c=3;
            int d=4;
            int e=5;
        }catch(Exception e){
        }
    }
        
    public void t3(){
        try{
            if (true){
                int a=0;
                int b=1;
                int c=3;
                int d=4;
                int e=5;
            }
        }catch(Exception e){
        }
    }

    public static void test(int n)
    {
        if (n != 0){
            for (int i=0; i<n; i++) {
                for (int j=0; j<i; j++) {
                    if (i!=j) {
                        int a=0;
                        int b=i+j;
                        int c=b/i;
                        int g=c/i;
                        int d=c/i;
                        System.out.println(c);
                    }
                }
            }
        }
    }

    public void t4(){
        try{
            if (true){
                while(false){
                    try{
                        int a=0;
                        int b=1;
                        int c=3;
                        int d=4;
                        int e=5;
                    }catch(Exception e){
                    }
                }
            }
        }catch(Exception e){
        }
    }
 
    public void t5(int i){
        try{
            if (true){
                while(false){
                    switch(i){
                        case 0: break;
                        case 1: break;
                        case 2: {
                            System.out.println("a");
                            System.out.println("b");
                            System.out.println("c");
                            System.out.println("d");
                        }break;
                        case 3:
                            System.out.println("a");
                            System.out.println("b");
                            System.out.println("c");
                            System.out.println("d");
                        break;
                    }
                }
            }
        }catch(Exception e){
        }
    }
    
    
}
                """));
    }
    
}

/***
         
 
*/