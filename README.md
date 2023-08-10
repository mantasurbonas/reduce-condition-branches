## motivation

Readability of such code sucks:  

```java
    public void analyze(Object element) {
        if (element != null) {
            if (!(element instanceof Double)) {
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
                } else if (element instanceof Integer) {
                    Integer i = (Integer) element;
                    System.out.println("analyzing integer " + i);
                    return;
                } else if (element instanceof Float) {
                    System.out.println("analyzing Float! " + element);
                    return;
                } else {
                    System.out.println("analyzing unknown type of element " + element);
                    return;
                }
            } else {
                throw new IllegalArgumentException("handling of Double is not impemented!");
            }
        } else {
            throw new IllegalArgumentException("param must not be null!");
        }
    }
```

This Gradle \ Maven plugin automagically rewrites the above code into a more human readable form : 

```java
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
            } else 
            if (element instanceof Float) {
                System.out.println("analyzing Float! " + element);
                return;
            } else {
                System.out.println("analyzing unknown type of element " + element);
                return;
            }
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
    }
```

## building of this plugin

Gradle will test and build this plugin:  

```
gradlew publishToMavenLocal
```

You can build this plugin for Java version 11, but to do that you'll have use Maven build and skip unit tests:

```
mvn clean install -Dmaven.test.skip=true 
```

## usage of this plugin in your project


In your Maven project's pom.xml, make this recipe module a dependency:  

```
<project>
    <build>
        <plugins>
            <plugin>
                <groupId>org.openrewrite.maven</groupId>
                <artifactId>rewrite-maven-plugin</artifactId>
                <version>5.4.1</version>
                <configuration>
                    <activeRecipes>
                      <recipe>lt.twoday.reduceconditionbranches.ReduceConditionBranches</recipe>
                      <recipe>lt.twoday.extractmethodmarker.MarkExtractMethodBlocksRecipe</recipe>
                    </activeRecipes>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>lt.twoday</groupId>
                        <artifactId>reduce-condition-branches</artifactId>
                        <version>1.2</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

If your project uses Gradle, then it must be explicitly configured to resolve dependencies from maven local.
The root project of your gradle build, make your recipe module a dependency of the `rewrite` configuration:

```groovy
plugins {
    id("java")
    id("org.openrewrite.rewrite") version("6.1.8")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    rewrite("lt.twoday:reduce-condition-branches:1.2")
}

rewrite {
    activeRecipe("lt.twoday.reduceconditionbranches.ReduceConditionBranches")
}
```

Now you can run `mvn rewrite:run` or `gradlew rewriteRun` to run openrewrite recipes on your project sources.
