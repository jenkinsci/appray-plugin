
# build

.PHONY: build
build:
	docker run -it --rm -v "${PWD}":/usr/src/mymaven -v "${HOME}/.m2":/root/.m2 -v "${PWD}/target:/usr/src/mymaven/target" -w /usr/src/mymaven maven:3.6.3-jdk-8 mvn verify -Denforcer.skip=true -DskipTests=true -Dfindbugs.skip=true -Dspotbugs.skip=true

.PHONY: package
package:
	docker run -it --rm -v "${PWD}":/usr/src/mymaven -v "${HOME}/.m2":/root/.m2 -v "${PWD}/target:/usr/src/mymaven/target" -w /usr/src/mymaven maven:3.6.3-jdk-8 mvn package

.PHONY: clean
clean:
	docker run -it --rm -v "${PWD}":/usr/src/mymaven -v "${HOME}/.m2":/root/.m2 -v "${PWD}/target:/usr/src/mymaven/target" -w /usr/src/mymaven maven:3.6.3-jdk-8 mvn clean

.PHONY: run
run:
	docker run -it --rm -p 8090:8090 -v "${PWD}":/usr/src/mymaven -v "${HOME}/.m2":/root/.m2 -v "${PWD}/target:/usr/src/mymaven/target" -w /usr/src/mymaven maven:3.6.3-jdk-8 mvn hpi:run -Djetty.port=8090 -Dhost=0.0.0.0

