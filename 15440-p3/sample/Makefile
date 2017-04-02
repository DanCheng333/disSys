all: Server.class IServer.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
test:
	java Cloud 15440 ../lib/db1.txt c-1000-13 15
