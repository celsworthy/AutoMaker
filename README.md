# AutoMaker
## Introduction
## Building AutoMaker
AutoMaker uses [Maven](http://maven.apache.org/download.cgi). You will either want to build the project directly using the maven command line, or use and IDE such as [Netbeans](http://netbeans.apache.org/download/index.html).

You will need JDK of 11.0.2 or higher e.g. [BellSoft Liberica 11.0.2](https://bell-sw.com/pages/java-11.0.2/)

In order to compile AutoMaker you will need to compile several other dependencies. The structure looks like this:

-> AutoMaker  
---> [CelTechCore](https://github.com/George-CEL/CELTechCore)  
------> [RoboxBase](https://github.com/George-CEL/RoboxBase)  
---------> [Language](https://github.com/George-CEL/Language)  
---------> [Licence](https://github.com/George-CEL/Licence)
------------> [Stenographer](https://github.com/George-CEL/Stenographer)
---------------> [Configuration](https://github.com/George-CEL/Configuration)

To build using maven only, then checkout each of these projects and run the command ```mvn clean install``` from inside each directory. Maven will build each jar and put it into a target folder within each directory as well as in the local maven repository.

## Running AutoMaker
AutoMaker needs a VM system property "libertySystems.configFile" set to the full path of the AutoMaker.configFile.xml file, which can be found in the AutoMaker repository. So in order to run the AutoMaker.jar from the command line use:
 
```java -DlibertySystems.configFile=\AutoMaker\AutoMaker.configFile.xml -jar AutoMaker.jar```
