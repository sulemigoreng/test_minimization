# Regression Test Minimization

original source : https://github.com/irenelizeth/test_minimization
<br/>
SUT : https://github.com/JodaOrg/joda-time

Example of a working project from the original source with the SUT.

What i configure from the original source : 
* Add a new list_testcases_jodatime_partial.txt 
* Change mvn command from 'mvn' to 'mvn.cmd' (Windows OS)
* Change test_min.properties to suit the SUT

What i configure from the SUT : 
* Add a clover dependency to the pom.xml
* Add a new directory test so that the minimization program can access it.

> This project is for study purposes. You can read how to run the minimization in the original source.
