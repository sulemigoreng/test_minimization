#!/bin/bash

USAGE="Usage (only works with grep linux): bash find_name_testcases <path_test_directory>"


if [ "$#" -lt "1" ]; then
    echo $USAGE
    exit -1
fi


APP_TEST_DIR=$1
#APP_TEST_DIR="test/jdepend/framework/"

GREP_COMMAND="grep -r -o -e '\test[[:alnum:]]*[_[[:alnum:]]*]*[\(][\)][:blank: ][\throws]' -r -o -e '\test[[:alnum:]]*[_[[:alnum:]]*]*[\(][\)][ ][\{]'$APP_TEST_DIR | grep -o -e '[\/[:alnum:]_*]*[.]\java[:][[:alnum:]_*]*\test[[:alnum:]]*[_[[:alnum:]]*]*'"


#GREP_COMMAND="grep -r -o -e '\test[[:alnum:]]*[_[[:alnum:]]*]*[\(][\)][ ][\{]' $APP_TEST_DIR | grep -o -e '[\/[:alnum:]_*]*[.]\java[:][[:alnum:]_*]*\test[[:alnum:]]*[_[[:alnum:]]*]*'"
# grep -r -o -e '\test[[:alnum:]]*[\(]' src/test/ | grep -o -e '[\/[:alnum:]_*]*[.]\java[:][[:alnum:]_*]*\test[[:alnum:]_*]*'
# grep -r -o -e '\test[[:alnum:]]*[\(]' src/test/ | grep -o -e '[\/[:alnum:]_*]*[.][[:alnum:]]*[:][[:alnum:]_*[:alnum:]]*\test[[:alnum:]_*[:alnum:]]*'
# grep -r -H -o -e '\test[[:alnum:]]*[\(]' $APP_TEST_DIR | grep -o -e '[[:alnum:]]*[.][[:alnum:]]*[:]\test[[:alnum:]]*'

eval $GREP_COMMAND
