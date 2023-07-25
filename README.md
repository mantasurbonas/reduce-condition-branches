# building of this plugin

Gradle will build java 11 version of this plugin:  

```
gradlew publishToMavenLocal
```

You can build this plugin for Java version 7, but to do that use Maven and skip unit tests:

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
                <version>5.2.4</version>
                <configuration>
                    <activeRecipes>
                        <recipe>lt.twoday.ReduceConditionBranches</recipe>
                    </activeRecipes>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>lt.twoday</groupId>
                        <artifactId>reduce-condition-branches</artifactId>
                        <version>1.0</version>
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
    rewrite("lt.twoday:reduce-condition-branches:1.0")
}

rewrite {
    activeRecipe("lt.twoday.ReduceConditionBranches")
}
```

Now you can run `mvn rewrite:run` or `gradlew rewriteRun` to run openrewrite recipes on your project sources.
