@echo off

set "JAVA_HOME=C:\Program Files\jdk-8-fcs-bin-b129-windows-i586-07_feb_2014"
set "MAVEN_HOME=C:\Program Files\maven\apache-maven-2.2.1"

IF NOT EXIST "%JAVA_HOME%" (
    echo "%JAVA_HOME% does not exist"
    EXIT /B
)

IF NOT EXIST "%MAVEN_HOME%" (
    echo "%MAVEN_HOME% does not exist"
    EXIT /B
)

IF EXIST "./target/shen.java-0.1.0-SNAPSHOT.jar" (
    echo "Project has been already been build. See ./target directory"
) ELSE (
    echo "Building project"
    "%MAVEN_HOME%\bin\mvn.bat" package
)

"%JAVA_HOME%\bin\java" -Xss1000K -jar target/shen.java-0.1.0-SNAPSHOT.jar
