all: Server.class IServer.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
test:
	java Cloud 15440 ../lib/db1.txt c-300-13 60
