# test_minimization project

Apply a test minimization technique over a set of test cases
to select the minimum set of tests that cover a given criteria.

Test Suite Minimization Criteria: 
1) Absolute criteria is to get the set of test cases with the minimum execution time (objt function)
2) Relative criteria is to get the set of test cases that have the same coverage that the original test suite.

ILP Problem Formulation:
(using LPSolve format)
- Variables: binary variables representing each test case in the test suite
- Objective Function: Minimize the number of test cases, each test case is weigthed by its executon time
- Constraints: Each statement covered by the original test suite should be covered.

Input parameters:
Use test_min.properties file to set up information about subject application

* subject.app.homedir: path to subject application directory
* subject.app.buildir: name of subject app's build directory
* subject.app.testdir: name of subject app's test directory
* maven.command: mvn 
* subject.app.testcases.file: name of file containing list of all subject app's test cases

Dependencies:
lp_solve http://lpsolve.sourceforge.net
place lp_solve executable in res folder

Run:
''' $ java -jar target/test_minimization-0.1.0.jar '''

TO-DO:
1) include criteria that considers test cases covering  a set of places in a subject application, where places are identified by
full qualified name of class and method in the app.

Include the clover plugin in the subject app pom.xml. 
(updated April, 2017) Inlcude version of clover plugin as 4.1.2. (no need for license since clover is open source now)
```
<plugin>
<groupId>com.atlassian.maven.plugins</groupId>
<artifactId>clover-maven-plugin</artifactId>
<version>4.1.2</version>
<!--<configuration>
<licenseLocation>/path/to/clover.license</licenseLocation>
</configuration>-->
</plugin>
```
