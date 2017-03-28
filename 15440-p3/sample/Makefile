all: Server.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
