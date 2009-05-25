#!/bin/sh

echo "--------------------"
echo "Wrapper Build System"
echo "--------------------"

$ANT_HOME/bin/ant -Dbits=64 $@ 
