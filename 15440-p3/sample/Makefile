all: Server.class IServer.class Cache.class

%.class: %.java
	javac $<

clean:
	rm -f *.class

setPath:
	setenv CLASSPATH ../sample:../lib
	
test1000:
	java Cloud 15440 ../lib/db1.txt c-1000-13 15

test300:
	java Cloud 15440 ../lib/db1.txt c-300-13 60

test500:
	java Cloud 15440 ../lib/db1.txt c-500-13 60