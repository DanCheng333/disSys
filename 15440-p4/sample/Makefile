all: Server.class UserNode.class Commit.class MsgSerializer.class MyMessage.class

%.class: %.java
	javac $<

clean:
	rm -f *.class
	
	
setPath:
	setenv CLASSPATH ../sample:../lib
	export CLASSPATH=$PWD:$PWD/../lib:$PWD/../sample

testSimple:
	java Project4 15440 ../test/scripts/1-simple-commits.txt
