#!/bin/bash -e

rlwrap=$(which rlwrap) || "" &> /dev/null
java="$rlwrap $JAVA_HOME/bin/java $JAVA_OPTS"

if [ "`which shen.java`" != "" ]; then
  DIR=$(dirname `which shen.java`)
else
  DIR="."
fi

shen="find $DIR -name shen.java-*.jar"

test -z `$shen` && mvn -f $DIR"/pom.xml" package
$java -Xss1000k -jar `$shen`
