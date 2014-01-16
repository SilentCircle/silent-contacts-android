
export ANDROID_ADT=$HOME/opt/adt-bundle-linux-x86_64-20130729
export JAVA_JDK=$HOME/opt/jdk1.6.0_45/bin

export WORKSPACE=`pwd`

export PATH=$JAVA_JDK:$PATH

export GRADLE_USER_HOME=$WORKSPACE

echo "sdk.dir=$ANDROID_ADT/sdk" > local.properties

echo "build_environment=silentcircle.com"                 >> gradle.properties
echo "build_version=$BUILD_NUMBER"                        >> gradle.properties
echo "build_commit=$(git log -n 1 --pretty=format:'%h')"  >> gradle.properties
echo "build_date=$BUILD_ID"                               >> gradle.properties
echo "build_debug=true"                                   >> gradle.properties
echo "build_partners="                                    >> gradle.properties

#./gradlew tasks
./gradlew clean assembleDevelop


# for the time being use a softlink to create an artifact that appears like other projects
mkdir -p bin
if [ ! -e bin/SilentContacts.apk ]
then
  ln -s ../SilentContacts/build/apk/SilentContacts-develop.apk bin/SilentContacts.apk
fi

