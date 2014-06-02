# Image finder application

The goal of this project is to identify images uploaded directly to components

### Installation
Prerequisites:
* Java SE 1.6
* Maven 2.x.x
* Git

Steps:

1. Clone the following repository:
    https://github.com/artem-zeltinsh/image-finder.git
2. Build client:
    mvn clean package
3. Copy the following from target/ to a directory on a target environment
    * lib
    * config
    * image-finder-1.0.jar
4. Set CRX server connection properties in config/crx-server.properties
5. Run the following command on that environment from that directory:
    java -jar image-finder-1.0.jar

