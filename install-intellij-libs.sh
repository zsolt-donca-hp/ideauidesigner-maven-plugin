#!/bin/sh

INTELLIJ_HOME=$1

if [ -z "$INTELLIJ_HOME" ]
then
  echo "Please provide the path to IntelliJ home directory (14.1.3). For example: install-intellij-libs.sh \"C:\Program Files (x86)\JetBrains\IntelliJ IDEA 14.1.3\""
  exit 1
fi

if [ ! -d "$INTELLIJ_HOME" ]
then
  echo "Directory does not exist: $INTELLIJ_HOME"
  exit 1
fi

echo 'Installing Intellij artifacts to Maven local repository'
echo "Intellij home: $INTELLIJ_HOME"

mvn install:install-file -Dfile="$INTELLIJ_HOME/redist/javac2.jar" -DgroupId=com.intellij -DartifactId=javac2 -Dversion=14.1.3 -Dpackaging=jar
mvn install:install-file -Dfile="$INTELLIJ_HOME/redist/src/src_javac2.zip" -DgroupId=com.intellij -DartifactId=javac2 -Dversion=14.1.3 -Dpackaging=jar -Dclassifier=sources
mvn install:install-file -Dfile="$INTELLIJ_HOME/redist/forms_rt.jar" -DgroupId=com.intellij -DartifactId=forms_rt -Dversion=14.1.3 -Dpackaging=jar
mvn install:install-file -Dfile="$INTELLIJ_HOME/redist/src/src_forms_rt.zip" -DgroupId=com.intellij -DartifactId=forms_rt -Dversion=14.1.3 -Dpackaging=jar -Dclassifier=sources
mvn install:install-file -Dfile="$INTELLIJ_HOME/lib/asm-all.jar" -DgroupId=com.intellij -DartifactId=asm-all -Dversion=14.1.3 -Dpackaging=jar
mvn install:install-file -Dfile="$INTELLIJ_HOME/lib/jdom.jar" -DgroupId=com.intellij -DartifactId=jdom -Dversion=14.1.3 -Dpackaging=jar
