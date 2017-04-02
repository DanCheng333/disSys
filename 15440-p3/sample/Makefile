all: Server.class ServerIntf.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
test:
	java Cloud 15440 ../lib/db1.txt c-2000-1 12
